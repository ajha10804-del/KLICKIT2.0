import React, { useEffect, useMemo, useState } from "react";
import "./dashboard.css";

const API = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

function StatusBadge({ status }) {
  const normStatus = (status || "").toLowerCase().replace(/_/g, "-");

  const displayLabels = {
    placed: "PLACED (Awaiting Approval)",
    "ready-to-assign": "READY TO ASSIGN",
    assigned: "ASSIGNED",
    "out-for-delivery": "OUT FOR DELIVERY",
    delivered: "DELIVERED",
    cancelled: "CANCELLED",
    rejected: "REJECTED",
  };

  const label = displayLabels[normStatus] || status;

  return (
    <span className={`status-badge ${normStatus}`}>
      <span className="status-dot" />
      {label}
    </span>
  );
}

function AdminDashboard({ user, onSignOut, onNavigateStore, notify }) {
  const [activePage, setActivePage] = useState("Dashboard");
  const [orders, setOrders] = useState([]);
  const [partners, setPartners] = useState([]);
  const [products, setProducts] = useState([]);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [orderError, setOrderError] = useState(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [showAssign, setShowAssign] = useState(null);
  const [selectedPartnerId, setSelectedPartnerId] = useState("");
  const [assignLoading, setAssignLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(null);

  // New partner modal state
  const [showAddPartner, setShowAddPartner] = useState(false);
  const [partnerForm, setPartnerForm] = useState({
    name: "",
    phone: "",
    email: "",
    password: "",
  });
  const [partnerSubmitting, setPartnerSubmitting] = useState(false);

  const token = localStorage.getItem("klickit_token") || "";

  async function fetchOrders() {
    setLoadingOrders(true);
    setOrderError(null);
    try {
      const res = await fetch(`${API}/api/admin/orders`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      if (res.status === 401 || res.status === 403) {
        throw new Error("Admin session unauthorized or expired.");
      }
      const body = await res.json().catch(() => ({}));
      if (!res.ok || !body.success || !Array.isArray(body.data)) {
        throw new Error(body.message || "Failed to load orders.");
      }
      setOrders(body.data);
    } catch (err) {
      setOrderError(err.message);
    } finally {
      setLoadingOrders(false);
    }
  }

  async function fetchPartners() {
    try {
      const res = await fetch(`${API}/api/delivery/partners`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      const body = await res.json().catch(() => ({}));
      if (res.ok && body.success && Array.isArray(body.data)) {
        setPartners(body.data);
      }
    } catch {
      // silently ignore or handle
    }
  }

  async function fetchProducts() {
    try {
      const res = await fetch(`${API}/api/products`);
      const body = await res.json().catch(() => ({}));
      if (res.ok && Array.isArray(body.data)) {
        setProducts(body.data);
      }
    } catch {
      // ignore
    }
  }

  useEffect(() => {
    fetchOrders();
    fetchPartners();
    fetchProducts();
  }, []);

  const filteredOrders = useMemo(() => {
    return orders.filter((order) => {
      const matchSearch =
        `${order.id} ${order.customerName || ""} ${order.customerPhone || ""} ${order.status || ""}`
          .toLowerCase()
          .includes(search.toLowerCase());

      const matchStatus =
        statusFilter === "ALL" || order.status === statusFilter;

      return matchSearch && matchStatus;
    });
  }, [orders, search, statusFilter]);

  // Approve: PLACED -> READY_TO_ASSIGN
  async function handleApproveOrder(orderId) {
    setActionLoading(orderId);
    try {
      const res = await fetch(`${API}/api/admin/orders/${orderId}/approve`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(body.message || "Failed to approve order.");
      }
      notify("Order approved! Status is now READY_TO_ASSIGN.");
      await fetchOrders();
    } catch (err) {
      notify(err.message);
    } finally {
      setActionLoading(null);
    }
  }

  // Reject: PLACED -> REJECTED
  async function handleRejectOrder(orderId) {
    if (!window.confirm("Are you sure you want to reject this order? This action cannot be undone.")) {
      return;
    }
    setActionLoading(orderId);
    try {
      const res = await fetch(`${API}/api/admin/orders/${orderId}/reject`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(body.message || "Failed to reject order.");
      }
      notify("Order rejected.");
      await fetchOrders();
    } catch (err) {
      notify(err.message);
    } finally {
      setActionLoading(null);
    }
  }

  // Assign: READY_TO_ASSIGN -> ASSIGNED
  async function handleAssignPartner(orderId) {
    if (!selectedPartnerId) {
      notify("Please select a delivery partner.");
      return;
    }
    setAssignLoading(true);
    try {
      const res = await fetch(`${API}/api/admin/orders/${orderId}/assign`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ deliveryPartnerId: selectedPartnerId }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(body.message || "Failed to assign delivery partner.");
      }
      notify("Delivery partner assigned! Order is now ASSIGNED.");
      setShowAssign(null);
      setSelectedPartnerId("");
      await fetchOrders();
    } catch (err) {
      notify(err.message);
    } finally {
      setAssignLoading(false);
    }
  }

  // Cancel order (permitted from PLACED, READY_TO_ASSIGN, ASSIGNED, OUT_FOR_DELIVERY)
  async function handleCancelOrder(orderId) {
    if (!window.confirm("Are you sure you want to cancel this order?")) {
      return;
    }
    setActionLoading(orderId);
    try {
      const res = await fetch(`${API}/api/orders/${orderId}/cancel`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(body.message || "Failed to cancel order.");
      }
      notify("Order cancelled.");
      await fetchOrders();
    } catch (err) {
      notify(err.message);
    } finally {
      setActionLoading(null);
    }
  }

  // Provision new delivery partner
  async function handleCreatePartner(e) {
    e.preventDefault();
    if (!partnerForm.name || !partnerForm.phone || !partnerForm.email || !partnerForm.password) {
      notify("Please fill all partner details.");
      return;
    }
    if (partnerForm.password.length < 12) {
      notify("Partner password must be at least 12 characters.");
      return;
    }
    setPartnerSubmitting(true);
    try {
      const res = await fetch(`${API}/api/delivery/partners`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(partnerForm),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(body.message || "Failed to create delivery partner.");
      }
      notify(`Delivery partner ${body.data?.name || ""} created successfully!`);
      setShowAddPartner(false);
      setPartnerForm({ name: "", phone: "", email: "", password: "" });
      await fetchPartners();
    } catch (err) {
      notify(err.message);
    } finally {
      setPartnerSubmitting(false);
    }
  }

  // Metrics
  const totalRevenue = useMemo(() => {
    return orders
      .filter((o) => o.status !== "CANCELLED" && o.status !== "REJECTED")
      .reduce((sum, o) => sum + Number(o.totalAmount || 0), 0);
  }, [orders]);

  const placedCount = useMemo(
    () => orders.filter((o) => o.status === "PLACED").length,
    [orders]
  );
  const readyCount = useMemo(
    () => orders.filter((o) => o.status === "READY_TO_ASSIGN").length,
    [orders]
  );

  function renderDashboard() {
    return (
      <>
        <div className="page-heading">
          <div>
            <h1>Good day, {user?.name || "Admin"} 👋</h1>
            <p>Here's the live overview of the KLICKIT store today.</p>
          </div>

          <div style={{ display: "flex", gap: "10px" }}>
            <button className="outline-button" onClick={fetchOrders}>
              ↻ Refresh
            </button>
            <button className="primary-button" onClick={() => setActivePage("Orders")}>
              View All Orders ({orders.length})
            </button>
          </div>
        </div>

        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-icon green">₹</div>
            <div>
              <span>Active Revenue</span>
              <strong>₹{totalRevenue.toFixed(0)}</strong>
              <small className="positive">Across active orders</small>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon blue">▣</div>
            <div>
              <span>Total Orders</span>
              <strong>{orders.length}</strong>
              <small>Live from database</small>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon orange">◷</div>
            <div>
              <span>Awaiting Action</span>
              <strong>{placedCount + readyCount}</strong>
              <small style={{ color: "#d97706" }}>
                {placedCount} placed • {readyCount} ready to assign
              </small>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon purple">♟</div>
            <div>
              <span>Delivery Partners</span>
              <strong>{partners.length}</strong>
              <small>Registered drivers</small>
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-header">
            <div>
              <h2>Recent Orders</h2>
              <p>Latest orders requiring fulfillment</p>
            </div>

            <button className="text-button" onClick={() => setActivePage("Orders")}>
              View All ({orders.length}) →
            </button>
          </div>

          <OrderTable
            orders={orders.slice(0, 8)}
            partners={partners}
            setShowAssign={setShowAssign}
            handleApproveOrder={handleApproveOrder}
            handleRejectOrder={handleRejectOrder}
            handleCancelOrder={handleCancelOrder}
            actionLoading={actionLoading}
          />
        </div>
      </>
    );
  }

  function renderOrders() {
    return (
      <>
        <div className="page-heading">
          <div>
            <h1>Order Management</h1>
            <p>Approve, assign delivery drivers, and track lifecycle transitions.</p>
          </div>

          <button className="outline-button" onClick={fetchOrders}>
            ↻ Refresh Orders
          </button>
        </div>

        <div className="toolbar">
          <div className="search-box">
            <span>⌕</span>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by order ID, customer name, phone..."
            />
          </div>

          <select
            className="filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">All Statuses ({orders.length})</option>
            <option value="PLACED">PLACED (Awaiting Approval)</option>
            <option value="READY_TO_ASSIGN">READY_TO_ASSIGN (Needs Driver)</option>
            <option value="ASSIGNED">ASSIGNED (Driver En Route to Store)</option>
            <option value="OUT_FOR_DELIVERY">OUT_FOR_DELIVERY</option>
            <option value="DELIVERED">DELIVERED</option>
            <option value="REJECTED">REJECTED</option>
            <option value="CANCELLED">CANCELLED</option>
          </select>
        </div>

        <div className="panel">
          {loadingOrders ? (
            <div style={{ padding: "40px", textAlign: "center", color: "#6b7280" }}>
              Loading live orders from server...
            </div>
          ) : orderError ? (
            <div style={{ padding: "30px", textAlign: "center", color: "#dc2626" }}>
              {orderError}
              <br />
              <button className="outline-button" style={{ marginTop: "10px" }} onClick={fetchOrders}>
                Retry
              </button>
            </div>
          ) : filteredOrders.length === 0 ? (
            <div style={{ padding: "40px", textAlign: "center", color: "#6b7280" }}>
              No orders found matching the filter.
            </div>
          ) : (
            <OrderTable
              orders={filteredOrders}
              partners={partners}
              setShowAssign={setShowAssign}
              handleApproveOrder={handleApproveOrder}
              handleRejectOrder={handleRejectOrder}
              handleCancelOrder={handleCancelOrder}
              actionLoading={actionLoading}
            />
          )}
        </div>
      </>
    );
  }

  function renderPartners() {
    return (
      <>
        <div className="page-heading">
          <div>
            <h1>Delivery Partners</h1>
            <p>Manage and provision drivers for order fulfillment.</p>
          </div>

          <button className="primary-button" onClick={() => setShowAddPartner(true)}>
            + Add Delivery Partner
          </button>
        </div>

        <div className="partner-grid">
          {partners.map((partner) => (
            <div className="partner-card" key={partner.id}>
              <div className="partner-avatar">{partner.name?.charAt(0) || "D"}</div>

              <div className="partner-main">
                <h3>{partner.name}</h3>
                <p>📞 {partner.phone}</p>
                <small style={{ color: "#6b7280" }}>ID: {partner.id?.slice(0, 8)}...</small>
              </div>

              <div className="partner-stat">
                <StatusBadge status="ONLINE" />
              </div>
            </div>
          ))}
          {partners.length === 0 && (
            <div style={{ gridColumn: "1 / -1", padding: "40px", textAlign: "center", color: "#6b7280" }}>
              No delivery partners registered yet. Click "+ Add Delivery Partner" to create one.
            </div>
          )}
        </div>

        {showAddPartner && (
          <div className="modal-overlay" onClick={() => setShowAddPartner(false)}>
            <div className="modal-card" onClick={(e) => e.stopPropagation()}>
              <button className="modal-close" onClick={() => setShowAddPartner(false)}>
                ×
              </button>
              <h2>Add Delivery Partner</h2>
              <p>Create a driver login account with ROLE_DELIVERY_PARTNER.</p>

              <form onSubmit={handleCreatePartner}>
                <div style={{ marginBottom: "12px" }}>
                  <label style={{ display: "block", fontSize: "12px", fontWeight: "600", marginBottom: "4px" }}>
                    Full Name
                  </label>
                  <input
                    className="modal-select"
                    style={{ width: "100%", padding: "10px" }}
                    placeholder="e.g. Ramesh Kumar"
                    value={partnerForm.name}
                    onChange={(e) => setPartnerForm({ ...partnerForm, name: e.target.value })}
                    required
                  />
                </div>

                <div style={{ marginBottom: "12px" }}>
                  <label style={{ display: "block", fontSize: "12px", fontWeight: "600", marginBottom: "4px" }}>
                    Phone Number
                  </label>
                  <input
                    className="modal-select"
                    style={{ width: "100%", padding: "10px" }}
                    placeholder="e.g. 9876543210"
                    value={partnerForm.phone}
                    onChange={(e) => setPartnerForm({ ...partnerForm, phone: e.target.value })}
                    required
                  />
                </div>

                <div style={{ marginBottom: "12px" }}>
                  <label style={{ display: "block", fontSize: "12px", fontWeight: "600", marginBottom: "4px" }}>
                    Email Address
                  </label>
                  <input
                    type="email"
                    className="modal-select"
                    style={{ width: "100%", padding: "10px" }}
                    placeholder="e.g. driver@klickit.com"
                    value={partnerForm.email}
                    onChange={(e) => setPartnerForm({ ...partnerForm, email: e.target.value })}
                    required
                  />
                </div>

                <div style={{ marginBottom: "16px" }}>
                  <label style={{ display: "block", fontSize: "12px", fontWeight: "600", marginBottom: "4px" }}>
                    Password (min. 12 characters)
                  </label>
                  <input
                    type="password"
                    className="modal-select"
                    style={{ width: "100%", padding: "10px" }}
                    placeholder="Minimum 12 characters"
                    value={partnerForm.password}
                    onChange={(e) => setPartnerForm({ ...partnerForm, password: e.target.value })}
                    required
                    minLength={12}
                  />
                </div>

                <button
                  type="submit"
                  className="primary-button full-button"
                  disabled={partnerSubmitting}
                >
                  {partnerSubmitting ? "Creating Driver..." : "Create Delivery Partner"}
                </button>
              </form>
            </div>
          </div>
        )}
      </>
    );
  }

  function renderProducts() {
    return (
      <>
        <div className="page-heading">
          <div>
            <h1>Product Catalog ({products.length})</h1>
            <p>Authoritative product records loaded from database.</p>
          </div>
        </div>

        <div className="panel">
          <div className="product-grid">
            {products.map((product) => (
              <div className="product-admin-card" key={product.id}>
                <div className="product-image-placeholder">
                  {product.imageUrl ? (
                    <img
                      src={product.imageUrl}
                      alt={product.name}
                      style={{ width: "100%", height: "100%", objectFit: "cover", borderRadius: "8px" }}
                    />
                  ) : (
                    "🛒"
                  )}
                </div>

                <div className="product-info">
                  <span className="category-label">{product.category}</span>
                  <h3>{product.name}</h3>
                  <strong>₹{Number(product.price).toFixed(0)}</strong>
                  <p>Status: {product.active ? "Active" : "Inactive"}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </>
    );
  }

  function renderPage() {
    if (activePage === "Orders") return renderOrders();
    if (activePage === "Products") return renderProducts();
    if (activePage === "Partners") return renderPartners();
    return renderDashboard();
  }

  return (
    <div className="dashboard-app">
      <aside className="dashboard-sidebar">
        <div className="brand">
          <div className="brand-logo">K</div>
          <div>
            <strong>KLICKIT</strong>
            <span>Admin Portal</span>
          </div>
        </div>

        <div className="sidebar-section">
          <span className="sidebar-label">OPERATIONS</span>

          <button
            className={activePage === "Dashboard" ? "sidebar-link active" : "sidebar-link"}
            onClick={() => setActivePage("Dashboard")}
          >
            <span>▦</span>
            Dashboard
          </button>

          <button
            className={activePage === "Orders" ? "sidebar-link active" : "sidebar-link"}
            onClick={() => setActivePage("Orders")}
          >
            <span>▤</span>
            Orders
            {placedCount + readyCount > 0 && (
              <b className="sidebar-count" style={{ background: "#f59e0b" }}>
                {placedCount + readyCount}
              </b>
            )}
          </button>

          <button
            className={activePage === "Partners" ? "sidebar-link active" : "sidebar-link"}
            onClick={() => setActivePage("Partners")}
          >
            <span>♟</span>
            Delivery Partners
          </button>

          <button
            className={activePage === "Products" ? "sidebar-link active" : "sidebar-link"}
            onClick={() => setActivePage("Products")}
          >
            <span>▧</span>
            Products Catalog
          </button>
        </div>

        <div className="sidebar-section">
          <span className="sidebar-label">NAVIGATION</span>

          <button className="sidebar-link" onClick={onNavigateStore}>
            <span>🛒</span>
            Customer Storefront
          </button>
        </div>

        <div className="sidebar-bottom">
          <button className="sidebar-link logout" onClick={onSignOut}>
            <span>↪</span>
            Sign Out
          </button>
        </div>
      </aside>

      <main className="dashboard-main">
        <header className="dashboard-header">
          <div className="header-search">
            <span>⌕</span>
            <input
              placeholder="Search orders, customers..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className="header-actions">
            <button
              className="outline-button"
              style={{ fontSize: "12px", padding: "6px 12px" }}
              onClick={onNavigateStore}
            >
              🛒 View Storefront
            </button>

            <div className="admin-profile">
              <div className="admin-avatar">A</div>
              <div>
                <strong>{user?.name || "Administrator"}</strong>
                <span>{user?.email || "ROLE_ADMIN"}</span>
              </div>
            </div>
          </div>
        </header>

        <section className="dashboard-content">{renderPage()}</section>
      </main>

      {/* Driver Assignment Modal */}
      {showAssign && (
        <div className="modal-overlay" onClick={() => setShowAssign(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <button className="modal-close" onClick={() => setShowAssign(null)}>
              ×
            </button>

            <h2>Assign Delivery Partner</h2>
            <p style={{ color: "#4b5563", fontSize: "13px", marginBottom: "16px" }}>
              Order ID: <b>{showAssign}</b>
              <br />
              <small style={{ color: "#0d9488" }}>Status: READY_TO_ASSIGN</small>
            </p>

            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "12px", fontWeight: "600", marginBottom: "6px" }}>
                Select Active Delivery Driver:
              </label>
              <select
                value={selectedPartnerId}
                onChange={(e) => setSelectedPartnerId(e.target.value)}
                className="modal-select"
                style={{ width: "100%", padding: "10px" }}
              >
                <option value="">-- Choose a Delivery Partner --</option>
                {partners.map((partner) => (
                  <option key={partner.id} value={partner.id}>
                    {partner.name} ({partner.phone})
                  </option>
                ))}
              </select>
            </div>

            {partners.length === 0 && (
              <div style={{ fontSize: "12px", color: "#dc2626", marginBottom: "12px" }}>
                No delivery partners found. Please add a delivery partner first in the "Delivery Partners" tab.
              </div>
            )}

            <button
              className="primary-button full-button"
              onClick={() => handleAssignPartner(showAssign)}
              disabled={assignLoading || !selectedPartnerId}
            >
              {assignLoading ? "Assigning Driver..." : "Confirm Driver Assignment"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function OrderTable({
  orders,
  partners,
  setShowAssign,
  handleApproveOrder,
  handleRejectOrder,
  handleCancelOrder,
  actionLoading,
}) {
  return (
    <div className="table-container">
      <table className="orders-table">
        <thead>
          <tr>
            <th>Order ID</th>
            <th>Customer</th>
            <th>Address</th>
            <th>Amount (COD)</th>
            <th>Status</th>
            <th>Delivery Partner</th>
            <th>Actions</th>
          </tr>
        </thead>

        <tbody>
          {orders.map((order) => {
            const isPlaced = order.status === "PLACED";
            const isReady = order.status === "READY_TO_ASSIGN";
            const isAssigned = order.status === "ASSIGNED";
            const isOutForDelivery = order.status === "OUT_FOR_DELIVERY";
            const isDelivered = order.status === "DELIVERED";
            const isRejected = order.status === "REJECTED";
            const isCancelled = order.status === "CANCELLED";
            const isLoading = actionLoading === order.id;

            return (
              <tr key={order.id}>
                <td>
                  <strong title={order.id}>#{order.id.slice(0, 8)}</strong>
                  <br />
                  <small style={{ color: "#9ca3af", fontSize: "10px" }}>
                    {order.createdAt ? new Date(order.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : ""}
                  </small>
                </td>

                <td>
                  <strong>{order.customerName || "Customer"}</strong>
                  <br />
                  <small style={{ color: "#6b7280" }}>{order.customerPhone || ""}</small>
                </td>

                <td style={{ maxWidth: "200px" }}>
                  <span style={{ fontSize: "11px", color: "#374151" }} title={order.customerAddress}>
                    {order.customerAddress?.length > 40
                      ? `${order.customerAddress.substring(0, 38)}…`
                      : order.customerAddress || "N/A"}
                  </span>
                </td>

                <td>
                  <strong style={{ color: "#0c831f" }}>₹{Number(order.totalAmount || 0).toFixed(0)}</strong>
                  <br />
                  <small style={{ color: "#6b7280", fontSize: "10px" }}>
                    {order.items?.length || 0} item{order.items?.length !== 1 ? "s" : ""}
                  </small>
                </td>

                <td>
                  <StatusBadge status={order.status} />
                </td>

                <td>
                  {order.deliveryPartnerName ? (
                    <div>
                      <strong>🚴 {order.deliveryPartnerName}</strong>
                      <br />
                      <small style={{ color: "#6b7280" }}>{order.deliveryPartnerPhone || ""}</small>
                    </div>
                  ) : isReady ? (
                    <button
                      className="assign-button"
                      onClick={() => setShowAssign(order.id)}
                      disabled={isLoading}
                    >
                      Assign Driver
                    </button>
                  ) : isPlaced ? (
                    <span style={{ color: "#d97706", fontSize: "11px", fontStyle: "italic" }}>
                      Approve first
                    </span>
                  ) : (
                    <span style={{ color: "#9ca3af", fontSize: "11px" }}>Unassigned</span>
                  )}
                </td>

                <td>
                  <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                    {/* PLACED: Can Approve or Reject */}
                    {isPlaced && (
                      <>
                        <button
                          className="approve-button"
                          onClick={() => handleApproveOrder(order.id)}
                          disabled={isLoading}
                          title="Approve order (moves to READY_TO_ASSIGN)"
                        >
                          {isLoading ? "..." : "✓ Approve"}
                        </button>
                        <button
                          className="reject-button"
                          onClick={() => handleRejectOrder(order.id)}
                          disabled={isLoading}
                          title="Reject order (terminal state)"
                        >
                          ✕ Reject
                        </button>
                      </>
                    )}

                    {/* READY_TO_ASSIGN: Can Assign Driver or Cancel */}
                    {isReady && (
                      <>
                        <button
                          className="assign-button"
                          style={{ background: "#2563eb", color: "white" }}
                          onClick={() => setShowAssign(order.id)}
                          disabled={isLoading}
                          title="Assign delivery partner"
                        >
                          Assign Driver
                        </button>
                        <button
                          className="cancel-button"
                          onClick={() => handleCancelOrder(order.id)}
                          disabled={isLoading}
                          title="Cancel order"
                        >
                          Cancel
                        </button>
                      </>
                    )}

                    {/* ASSIGNED & OUT_FOR_DELIVERY: Can Cancel */}
                    {(isAssigned || isOutForDelivery) && (
                      <button
                        className="cancel-button"
                        onClick={() => handleCancelOrder(order.id)}
                        disabled={isLoading}
                        title="Cancel order"
                      >
                        Cancel
                      </button>
                    )}

                    {/* Terminal States */}
                    {isDelivered && (
                      <span style={{ color: "#16a34a", fontSize: "11px", fontWeight: "600" }}>
                        Completed ✓
                      </span>
                    )}

                    {isRejected && (
                      <span style={{ color: "#dc2626", fontSize: "11px", fontWeight: "600" }}>
                        Rejected ✕
                      </span>
                    )}

                    {isCancelled && (
                      <span style={{ color: "#6b7280", fontSize: "11px", fontWeight: "600" }}>
                        Cancelled ✕
                      </span>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default AdminDashboard;