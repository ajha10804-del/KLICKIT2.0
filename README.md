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
