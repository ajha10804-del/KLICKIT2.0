# KLICKIT 2.0

Fast Quick-Commerce Ordering and Delivery Platform with Cash on Delivery (COD) and Strict Role Separation.

---

## 1. Prerequisites

- **Java**: JDK 21+ (configured in `JAVA_HOME`)
- **Node.js**: v20+ with npm
- **Database**: PostgreSQL 16+ (or cloud-hosted PostgreSQL such as Neon / Render)
- **Build Tools**: Maven wrapper (`./mvnw` or `.\mvnw.cmd`) and Vite included in repository

---

## 2. User Roles & Security Model

The system enforces three distinct user roles:

| Role | Access Scope |
|---|---|
| `CUSTOMER` | Public storefront browsing, add to cart, checkout (COD), order tracking |
| `ADMIN` | Manage products, review placed orders, approve/reject orders, assign active delivery partners, view all orders |
| `DELIVERY_PARTNER` | Dedicated delivery dashboard, inspect assigned order line items & COD collection amounts, mark orders `OUT_FOR_DELIVERY` and `DELIVERED` |

---

## 3. Order Lifecycle Workflow

```
[CUSTOMER CHECKOUT]
         │
         ▼
     [PLACED] ──────(Admin Rejection)─────► [REJECTED] (Terminal)
         │
         ├──────(Customer / Admin Cancel)─► [CANCELLED] (Terminal)
         │
  (Admin Approval)
         ▼
 [READY_TO_ASSIGN] ──(Admin Cancel)───────► [CANCELLED] (Terminal)
         │
  (Admin Driver Assignment)
         ▼
     [ASSIGNED] ─────(Admin Cancel)───────► [CANCELLED] (Terminal)
         │
   (Driver Start)
         ▼
 [OUT_FOR_DELIVERY] ─(Admin Cancel)───────► [CANCELLED] (Terminal)
         │
 (Driver Completion & COD Collection)
         ▼
    [DELIVERED] (Terminal)
```

- **Non-reversible terminal states**: `DELIVERED`, `CANCELLED`, `REJECTED`.
- **Payment Method**: Pure Cash on Delivery (COD). Total amount to collect is authoritatively derived from the snapshot sum of line items + delivery fee.
- **Snapshot Pricing**: `order_items` stores checkout price snapshots (`product_name`, `quantity`, `price`). Catalog updates after order creation never mutate historical line items.

---

## 4. Environment Configuration

### Backend (`.env` or system environment variables)
Copy `.env.example` to `.env` in the project root:

```bash
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/klickit
DB_USERNAME=klickit
DB_PASSWORD=<your-database-password>

# Server Port
PORT=8080

# Security (HMAC-SHA256 Base64-encoded key of >= 256 bits)
JWT_SECRET=<your-base64-encoded-jwt-secret>
JWT_EXPIRATION=86400000

# Store Notification Destination
ADMIN_EMAIL=admin@yourdomain.com

# First-boot Admin Bootstrap (run once to seed initial admin)
BOOTSTRAP_ADMIN_NAME=Store Admin
BOOTSTRAP_ADMIN_EMAIL=admin@yourdomain.com
BOOTSTRAP_ADMIN_PHONE=9876543210
BOOTSTRAP_ADMIN_PASSWORD=<secure-admin-password-min-12-chars>

# CORS Origins (comma-separated, no trailing slash)
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://your-app.vercel.app
```

### Frontend (`frndfrontend/.env`)
Copy `frndfrontend/.env.example` to `frndfrontend/.env`:

```bash
# Leave blank for local development to use the Vite dev proxy to localhost:8080
# For remote deployments (e.g. Vercel), set to the backend origin without trailing slash and WITHOUT /api
VITE_API_BASE_URL=
```

---

## 5. Running Locally

### Backend
1. Ensure PostgreSQL is running and database `klickit` is created:
   ```sql
   CREATE DATABASE klickit;
   ```
2. Start the Spring Boot application:
   ```bash
   # Linux / macOS
   ./mvnw spring-boot:run

   # Windows
   .\mvnw.cmd spring-boot:run
   ```
   The backend API starts on `http://localhost:8080/api`.

### Frontend
1. Navigate to `frndfrontend`:
   ```bash
   cd frndfrontend
   npm install
   npm run dev
   ```
2. Open `http://localhost:5173` in your browser.

---

## 6. Admin Bootstrapping & Provisioning Drivers

### Bootstrapping the First Administrator
On first startup, if `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are provided and no user with that email exists, `AdminBootstrapRunner` automatically creates the account with role `ROLE_ADMIN`. Once bootstrapped, the environment variables may be removed.

### Creating a Delivery Partner
Authenticated administrators can provision new delivery partners via:
- **API**: `POST /api/delivery/partners`
  ```json
  {
    "name": "Ramesh Kumar",
    "phone": "9876543211",
    "email": "driver@yourdomain.com",
    "password": "<secure-driver-password-min-12-chars>"
  }
  ```
- **Admin Dashboard UI**: Navigate to `/admin` $\rightarrow$ Delivery Partners $\rightarrow$ Add Partner.

---

## 7. Running Tests

### Backend Test Suite
Runs all 211+ Spring Boot unit, security, integration, and state machine tests:
```bash
# Windows
.\mvnw.cmd test -B

# Linux / macOS
./mvnw test -B
```

### Frontend Test Suite & Build
Runs route guard, lifecycle, and delivery card tests, followed by the production Vite build:
```bash
cd frndfrontend
npm test
npm run build
```


## End-to-end Blinkit-style flow

The frontend and backend are connected around one role-aware order lifecycle:

1. Customer enters an email and receives a 6-digit OTP.
2. OTP verification returns the account role from the database.
3. `CUSTOMER` opens the customer storefront/dashboard, `ADMIN` opens `/admin`, and `DELIVERY_PARTNER` opens `/delivery`.
4. Customer checkout creates a `PLACED` order.
5. Admin order queue refreshes automatically, approves the order, and assigns an active delivery partner.
6. The assigned partner sees the order automatically, starts delivery, and marks it delivered.
7. Customer order tracking automatically polls the authoritative order endpoint every 4 seconds, so status changes appear without a page reload.
8. JWT + backend role checks remain authoritative; hiding a dashboard button in the frontend is not treated as security.

For production-grade map tracking (driver GPS on a map), a separate location-update API/WebSocket layer is still required. The current flow provides live **order-status tracking**, which is the foundation for that feature.

## Email OTP sign-in

Customers, admins and delivery partners all sign in with an emailed 6-digit code.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/auth/otp/request` `{ "email" }` | Emails a code (same response whether or not the account exists) |
| `POST /api/auth/otp/verify` `{ "email", "code" }` | Returns a JWT and the account `role` |

* Existing account: signs in with the role stored in the database (`CUSTOMER` -> storefront, `ADMIN` -> `/admin`, `DELIVERY_PARTNER` -> `/delivery`).
* Unknown email: a `CUSTOMER` account is created. Admin and delivery accounts are never created or promoted this way; create them via the bootstrap admin or the admin panel first.
* Codes expire after 5 minutes, allow 5 attempts, are single-use, and are stored as an HMAC. Resend cooldown is 60s and 5 codes per email per hour.
* Needs a working `MAIL_API_KEY` / `MAIL_FROM`. With Resend's sandbox sender (`onboarding@resend.dev`) mail only reaches the account owner, so verify a domain before real customers sign in. For local work, run with the `dev` profile (or `OTP_DEV_LOG_CODE=true`) to have the code written to the server log when email is not configured.
* The old `/auth/login` and `/auth/register` endpoints are unchanged, but the UI no longer uses them.
