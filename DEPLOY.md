# KLICKIT Deployment Guide

This guide details the deployment procedures for running the KLICKIT backend on **Render** (via Docker) and the frontend on **Vercel**, including database connection mapping, environment variables, and cryptographic secret generation.

---

## 1. Backend Deployment on Render

### Deployment Settings
- **Deployment Type:** Docker (uses the repository's root `Dockerfile`)
- **Health Check Path:** `/api/products` (returns HTTP 200 OK with product list)
- **Port:** The application listens on `${PORT:8080}`. Render sets `PORT` automatically (typically `10000`), which Spring Boot binds at boot.

### Required Environment Variables
The following environment variables must be defined in the Render Web Service dashboard under **Environment**:

| Variable | Description |
|---|---|
| `DB_URL` | JDBC PostgreSQL connection string in the format `jdbc:postgresql://<host>:<port>/<database>` |
| `DB_USERNAME` | Database username for the PostgreSQL database |
| `DB_PASSWORD` | Database password for the PostgreSQL user |
| `JWT_SECRET` | Base64-encoded HMAC-SHA256 signature key (must be at least 256 bits / 32 bytes) |
| `ADMIN_EMAIL` | Email address of the primary administrator/store manager to receive new order notifications |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins (e.g. `https://your-klickit-app.vercel.app`) |

### Optional Environment Variables
| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | Listening port (Render provides this automatically) |
| `BOOTSTRAP_ADMIN_NAME` | `KlickIt Admin` | Display name for the initial bootstrap administrator account |
| `BOOTSTRAP_ADMIN_EMAIL` | *empty* | Email for initial administrator account bootstrap |
| `BOOTSTRAP_ADMIN_PHONE` | *empty* | Phone number for initial administrator account bootstrap |
| `BOOTSTRAP_ADMIN_PASSWORD` | *empty* | Password for bootstrap admin (must be at least 12 characters) |
| `MAIL_API_KEY` | *empty* | Resend HTTP API key for transactional emails (optional; logs warning if unset) |
| `MAIL_FROM` | `onboarding@resend.dev` | Sender address for order notification emails |
| `MAIL_API_URL` | `https://api.resend.com/emails` | Resend API endpoint |
| `MAIL_TIMEZONE` | `Asia/Kolkata` | Timezone formatting for order email timestamps |
| `SEED_DEMO_PRODUCTS` | `false` | Gate for automatic catalog seeding at boot (set `false` in production) |

---

## 2. Converting Render PostgreSQL Connection URL

Render PostgreSQL databases provide a connection string in the format:
```
postgres://<username>:<password>@<host>:<port>/<database>
```
*(or `postgresql://<username>:<password>@<host>:<port>/<database>`)*

To configure the Spring Boot application, map this connection string to `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as follows:

1. **Obtain Username and Password Separately:**
   In the Render Dashboard for your PostgreSQL instance, navigate to **Info** $\rightarrow$ **Connections**. Render displays each connection attribute individually:
   - **User:** Copy this value into `DB_USERNAME`.
   - **Password:** Click to reveal and copy this value into `DB_PASSWORD`.
   - **Internal Database URL / External Database URL:** Used to determine host, port, and database name.

2. **Construct `DB_URL`:**
   Convert the URL scheme from `postgres://` to `jdbc:postgresql://`:
   - If using Render's **Internal Connection** (backend and database deployed in the same Render region):
     ```
     jdbc:postgresql://<host>:<port>/<database>
     ```
     Example format: `jdbc:postgresql://dpg-xxxxxxxx-a:5432/klickit_db`
   - If using Render's **External Connection** (connecting across regions or from outside Render):
     ```
     jdbc:postgresql://<host>:<port>/<database>?sslmode=require
     ```
     Example format: `jdbc:postgresql://dpg-xxxxxxxx-a.oregon-postgres.render.com:5432/klickit_db?sslmode=require`

---

## 3. Frontend Deployment on Vercel

### Deployment Settings
- **Framework Preset:** Vite
- **Root Directory:** `frndfrontend`
- **Build Command:** `npm run build`
- **Output Directory:** `dist`

### Environment Variables
Configure the following environment variable in the Vercel project settings (**Settings** $\rightarrow$ **Environment Variables**):

| Variable | Required Formatting Rules |
|---|---|
| `VITE_API_BASE_URL` | **Must have NO trailing slash.**<br>**Must NOT end in `/api`.**<br>Example: `https://klickit-backend.onrender.com` |

> **Critical Note:** The frontend application code automatically prepends `/api` to all request paths (e.g. `${API}/api/orders/checkout`). Setting `VITE_API_BASE_URL=https://klickit-backend.onrender.com/api` will result in duplicate `/api/api/...` paths and HTTP 404 errors.

### Cross-Origin Resource Sharing (CORS)
Once your Vercel project is deployed, update the `CORS_ALLOWED_ORIGINS` variable on Render to include your Vercel origin:
```
CORS_ALLOWED_ORIGINS=https://your-klickit-app.vercel.app
```
If using multiple custom domains or preview URLs, provide them as a comma-separated list without trailing slashes.

---

## 4. Cryptographic Secrets & Admin Bootstrap Generation

Do not use or commit default secrets. Generate secure random values before deployment:

### Generating `JWT_SECRET`
The application requires an HMAC-SHA256 secret key of at least 256 bits (32 bytes), base64-encoded.

- **Using OpenSSL (Linux / macOS / Git Bash):**
  ```bash
  openssl rand -base64 32
  ```
- **Using PowerShell (Windows):**
  ```powershell
  [Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Minimum 0 -Maximum 256) }))
  ```
- **Using Node.js:**
  ```bash
  node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
  ```

Assign the generated base64 string directly to `JWT_SECRET` on Render.

### Generating Bootstrap Admin Password
The bootstrap administrator password must be at least 12 characters long.

- **Using OpenSSL:**
  ```bash
  openssl rand -base64 16
  ```
- **Using PowerShell:**
  ```powershell
  -join ((33..126) | Get-Random -Count 20 | ForEach-Object { [char]$_ })
  ```

Configure the administrator variables on Render:
```
BOOTSTRAP_ADMIN_NAME=Store Administrator
BOOTSTRAP_ADMIN_EMAIL=admin@yourdomain.com
BOOTSTRAP_ADMIN_PHONE=+919876543210
BOOTSTRAP_ADMIN_PASSWORD=<generated-secure-password>
```
On initial startup, `AdminBootstrapRunner` creates the administrator account with `ROLE_ADMIN` if no user with that email exists.
