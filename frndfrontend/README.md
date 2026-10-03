# KlickIt Frontend

A responsive quick-commerce storefront inspired by the general shopping flow of Indian 10-minute grocery apps. This is an original KlickIt interface, not a copy of Blinkit's branding or assets.

## Features
- Responsive storefront with category browsing, search, product cards and sale prices
- Add/remove quantities, cart drawer, delivery fee calculation and free-delivery threshold
- Login and registration forms connected to the backend
- Product catalogue loads from the backend when available, with demo products as a fallback
- Checkout request connected to the backend
- Vite development proxy for `/api` requests to `http://localhost:8080`

## Run locally

Requirements: Node.js 18+.

```bash
npm install
npm run dev
```

Open the local URL printed by Vite. Start the Spring Boot backend on port `8080` for API integration.

## Build for deployment

```bash
npm run build
npm run preview
```

Deploy the generated `dist/` directory to a static host such as Vercel, Netlify, or Cloudflare Pages. Configure `VITE_API_BASE_URL` to your deployed backend origin (e.g. `https://klickit-backend.onrender.com`, with NO trailing slash and must NOT end in `/api`) when frontend and backend are hosted separately. Rebuild after changing environment variables.

## Backend integration notes

The frontend currently calls these endpoints:
- `GET /api/products`
- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/orders`

It expects login/register responses to include a token under `token` or `accessToken` (or `data.token`). Product responses can be a JSON array, `{ content: [] }`, `{ data: [] }`, or `{ products: [] }`.

**Important:** verify the exact route names, checkout DTO field names, and response envelopes against your Spring Boot controllers before production deployment. The product list has a local demo fallback, but authentication and order placement require a running, correctly configured backend.
