import React, { useEffect, useState } from "react";
import "./dashboard.css";

const API = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

function StatusBadge({ status }) {
  const normStatus = (status || "").toLowerCase().replace(/_/g, "-");

  const displayLabels = {
    assigned: "ASSIGNED (Ready to Deliver)",
    "out-for-delivery": "OUT FOR DELIVERY",
    delivered: "DELIVERED ✓",
    cancelled: "CANCELLED",
  };

  return (
    <span className={`status-badge ${normStatus}`}>
      <span className="status-dot" />
      {displayLabels[normStatus] || status}
    </span>
  );
}

export default function DeliveryDashboard({ user, onSignOut, onNavigateStore, notify }) {
  const [activePage, setActivePage] = useState("Dashboard");
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(null);
  const [online, setOnline] = useState(true);

  const token = localStorage.getItem("klickit_token") || "";

  async function fetchAssignedOrders({ silent = false } = {}) {
    if (!silent) setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API}/api/delivery/orders`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      if (res.status === 401 || res.status === 403) {
        throw new Error("Delivery partner session unauthorized or expired.");
      }
      const body = await res.json().catch(() => ({}));
      if (!res.ok || !body.success || !Array.isArray(body.data)) {
        throw new Error(body.message || "Failed to load assigned orders.");
      }
      setOrders(body.data);
    } catch (err) {
      setError(err.message);
    } finally {
      if (!silent) setLoading(false);
    }
  }

  useEffect(() => {
    fetchAssignedOrders();

    // New assignments and status changes appear automatically for the driver.
    const refreshTimer = window.setInterval(() => {
      fetchAssignedOrders({ silent: true });
    }, 5000);

    return () => window.clearInterval(refreshTimer);
  }, []);

  // ASSIGNED -> OUT_FOR_DELIVERY
  async function handleStartDelivery(orderId) {
    setActionLoading(orderId);
    try {
      const res = await fetch(`${API}/api/delivery/orders/${orderId}/start`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(body.message || "Failed to start delivery.");
      }
      notify("Delivery started! Order is now OUT_FOR_DELIVERY.");
      await fetchAssignedOrders();
    } catch (err) {
      notify(err.message);
    } finally {
      setActionLoading(null);
    }
  }

  // OUT_FOR_DELIVERY -> DELIVERED
  async function handleMarkDelivered(orderId) {
    if (!window.confirm("Confirm delivery completed and Cash on Delivery collected?")) {
      return;
    }
    setActionLoading(orderId);
    try {
      const res = await fetch(`${API}/api/delivery/orders/${orderId}/delivered`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(body.message || "Failed to mark order as delivered.");
      }
      notify("Order successfully marked as DELIVERED!");
      await fetchAssignedOrders();
    } catch (err) {
      notify(err.message);
    } finally {
      setActionLoading(null);
    }
  }

  const assignedCount = orders.filter((o) => o.status === "ASSIGNED").length;
  const outCount = orders.filter((o) => o.status === "OUT_FOR_DELIVERY").length;
  const totalCodCollection = orders.reduce(
    (sum, o) => sum + Number(o.totalAmount || 0),
    0
  );

  function DashboardHome() {
    return (
      <>
        <div className="page-heading">
          <div>
            <h1>Hello, {user?.name || "Delivery Partner"} 👋</h1>
            <p>You have {orders.length} active order(s) assigned to you.</p>
          </div>

          <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
            <button className="outline-button" onClick={fetchAssignedOrders}>
              ↻ Refresh Orders
            </button>
            <div className="online-toggle">
              <span className={online ? "online-dot" : "offline-dot"} />
              <span>{online ? "Online" : "Offline"}</span>
              <button
                className={online ? "toggle on" : "toggle"}
                onClick={() => setOnline(!online)}
              >
                <span />
              </button>
            </div>
          </div>
        </div>

        <div className="stats-grid delivery-stats">
          <div className="stat-card">
            <div className="stat-icon green">₹</div>
            <div>
              <span>COD To Collect</span>
              <strong>₹{totalCodCollection.toFixed(0)}</strong>
              <small className="positive">Cash on Delivery</small>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon blue">▣</div>
            <div>
              <span>Assigned Orders</span>
              <strong>{assignedCount}</strong>
              <small>Ready for pickup / start</small>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon purple">🚴</div>
            <div>
              <span>Out for Delivery</span>
              <strong>{outCount}</strong>
              <small>En route to customer</small>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon orange">★</div>
            <div>
              <span>Delivery Status</span>
              <strong>Active</strong>
              <small className="positive">Partner verified</small>
            </div>
          </div>
        </div>

        <div className="delivery-layout">
          <div className="panel" style={{ width: "100%" }}>
            <div className="panel-header">
              <div>
                <h2>Your Assigned Delivery Orders</h2>
                <p>Deliver in sequence and collect Cash on Delivery at customer location.</p>
              </div>

              <button className="outline-button" onClick={fetchAssignedOrders}>
                Refresh
              </button>
            </div>

            {loading ? (
              <div style={{ padding: "40px", textAlign: "center", color: "#6b7280" }}>
                Loading assigned orders...
              </div>
            ) : error ? (
              <div style={{ padding: "30px", textAlign: "center", color: "#dc2626" }}>
                {error}
                <br />
                <button className="outline-button" style={{ marginTop: "10px" }} onClick={fetchAssignedOrders}>
                  Retry
                </button>
              </div>
            ) : orders.length === 0 ? (
              <div style={{ padding: "40px", textAlign: "center", color: "#6b7280" }}>
                No active orders assigned at the moment. Check back soon!
              </div>
            ) : (
              <div className="delivery-order-list">
                {orders.map((order) => (
                  <DeliveryOrderCard
                    key={order.id}
                    order={order}
                    handleStartDelivery={handleStartDelivery}
                    handleMarkDelivered={handleMarkDelivered}
                    actionLoading={actionLoading}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </>
    );
  }

  function OrdersPage() {
    return (
      <>
        <div className="page-heading">
          <div>
            <h1>My Active Orders ({orders.length})</h1>
            <p>Orders currently assigned to your delivery profile.</p>
          </div>
          <button className="outline-button" onClick={fetchAssignedOrders}>
            ↻ Refresh
          </button>
        </div>

        <div className="delivery-order-list">
          {orders.map((order) => (
            <DeliveryOrderCard
              key={order.id}
              order={order}
              handleStartDelivery={handleStartDelivery}
              handleMarkDelivered={handleMarkDelivered}
              actionLoading={actionLoading}
            />
          ))}
          {orders.length === 0 && (
            <div style={{ padding: "40px", textAlign: "center", color: "#6b7280" }}>
              No assigned orders.
            </div>
          )}
        </div>
      </>
    );
  }

  let pageContent = <DashboardHome />;
  if (activePage === "Orders") {
    pageContent = <OrdersPage />;
  }

  return (
    <div className="dashboard-app">
      <aside className="dashboard-sidebar delivery-sidebar">
        <div className="brand">
          <div className="brand-logo">K</div>
          <div>
            <strong>KLICKIT</strong>
            <span>Driver Portal</span>
          </div>
        </div>

        <div className="partner-profile">
          <div className="large-avatar">
            {user?.name?.charAt(0) || "D"}
          </div>

          <div>
            <strong>{user?.name || "Delivery Partner"}</strong>
            <span style={{ fontSize: "11px", color: "#6b7280" }}>
              {user?.email || "ROLE_DELIVERY_PARTNER"}
            </span>

            <div className="profile-status">
              <span />
              {online ? "Online" : "Offline"}
            </div>
          </div>
        </div>

        <div className="sidebar-section">
          <span className="sidebar-label">DELIVERY</span>

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
            My Orders
            {orders.length > 0 && <b className="sidebar-count">{orders.length}</b>}
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
          <div>
            <span style={{ fontSize: "13px", color: "#6b7280" }}>
              Delivery Partner Portal • <strong>KlickIt Fast Commerce</strong>
            </span>
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
              <div className="admin-avatar">D</div>
              <div>
                <strong>{user?.name || "Driver"}</strong>
                <span>{user?.phone || user?.email || ""}</span>
              </div>
            </div>
          </div>
        </header>

        <section className="dashboard-content">{pageContent}</section>
      </main>
    </div>
  );
}

function DeliveryOrderCard({
  order,
  handleStartDelivery,
  handleMarkDelivered,
  actionLoading,
}) {
  const isLoading = actionLoading === order.id;
  const isAssigned = order.status === "ASSIGNED";
  const isOutForDelivery = order.status === "OUT_FOR_DELIVERY";
  const isDelivered = order.status === "DELIVERED";

  return (
    <div className="delivery-order-card">
      <div className="delivery-order-top">
        <div>
          <span className="order-id" title={order.id}>
            #{order.id.slice(0, 8)}
          </span>
          <StatusBadge status={order.status} />
        </div>

        <div style={{ textAlign: "right" }}>
          <strong className="delivery-amount">
            ₹{Number(order.totalAmount || 0).toFixed(0)}
          </strong>
          <br />
          <small style={{ color: "#0c831f", fontSize: "11px", fontWeight: "600" }}>
            Collect Cash (COD)
          </small>
        </div>
      </div>

      <div className="customer-details">
        <div className="customer-icon">👤</div>

        <div>
          <strong>{order.customerName || "Customer"}</strong>
          <span>📞 {order.customerPhone || "No phone provided"}</span>
        </div>

        {order.customerPhone && (
          <a
            href={`tel:${order.customerPhone}`}
            className="call-button"
            style={{ textDecoration: "none" }}
          >
            ☎ Call
          </a>
        )}
      </div>

      <div className="address-box">
        <span>📍</span>

        <div>
          <strong>Delivery Address</strong>
          <p>{order.customerAddress || "Address not specified"}</p>
        </div>
      </div>

      {/* Order Line Items */}
      <div className="delivery-items-section">
        <div className="delivery-items-header">
          <strong>📦 Items to Deliver</strong>
          <span className="items-count-badge">{(order.items || []).length} items</span>
        </div>

        {order.items && order.items.length > 0 ? (
          <div className="delivery-items-list">
            {order.items.map((item, idx) => {
              const uPrice = Number(item.unitPrice ?? item.price ?? 0);
              const lTotal = Number(item.lineTotal ?? (uPrice * item.quantity));
              return (
                <div key={idx} className="delivery-item-row">
                  <div className="delivery-item-name-qty">
                    <span className="delivery-item-qty">{item.quantity}×</span>
                    <span className="delivery-item-name">{item.productName}</span>
                    <span className="delivery-item-rate">(@ ₹{uPrice.toFixed(0)})</span>
                  </div>
                  <span className="delivery-item-total">₹{lTotal.toFixed(0)}</span>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="delivery-no-items">No item details available</div>
        )}
      </div>

      {/* Authoritative COD Payment Breakdown */}
      <div className="delivery-payment-breakdown">
        <div className="breakdown-line">
          <span>Items Subtotal:</span>
          <span>
            ₹{Number(order.subtotal != null ? order.subtotal : ((order.totalAmount || 0) - (order.deliveryFee || 0))).toFixed(0)}
          </span>
        </div>
        <div className="breakdown-line">
          <span>Delivery Fee:</span>
          <span>
            {Number(order.deliveryFee || 0) > 0
              ? `₹${Number(order.deliveryFee).toFixed(0)}`
              : <span className="free-badge">FREE</span>}
          </span>
        </div>
        <div className="breakdown-collect-box">
          <div className="collect-label">Collect Cash on Delivery (COD):</div>
          <div className="collect-amount">₹{Number(order.totalAmount || 0).toFixed(0)}</div>
        </div>
      </div>

      <div className="order-meta">
        <span>🛍 {(order.items || []).length} items</span>
        <span>💵 Cash on Delivery</span>
        {order.deadline && (
          <span>
            ⏱ SLA: {new Date(order.deadline).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </span>
        )}
      </div>

      <div className="delivery-actions">
        {/* ASSIGNED -> Can Start Delivery */}
        {isAssigned && (
          <button
            className="primary-button"
            onClick={() => handleStartDelivery(order.id)}
            disabled={isLoading}
            style={{ width: "100%", padding: "12px", fontSize: "14px" }}
          >
            {isLoading ? "Starting Delivery..." : "Start Delivery 🚴"}
          </button>
        )}

        {/* OUT_FOR_DELIVERY -> Can Mark Delivered */}
        {isOutForDelivery && (
          <button
            className="primary-button"
            style={{ width: "100%", padding: "12px", fontSize: "14px", background: "#16a34a" }}
            onClick={() => handleMarkDelivered(order.id)}
            disabled={isLoading}
          >
            {isLoading ? "Completing Delivery..." : "Mark Delivered ✓"}
          </button>
        )}

        {/* DELIVERED */}
        {isDelivered && (
          <div style={{ textAlign: "center", color: "#16a34a", fontWeight: "600", padding: "10px" }}>
            ✓ Order Delivered & COD Collected
          </div>
        )}
      </div>
    </div>
  );
}