#!/usr/bin/env python3
"""
import-catalog.py
Idempotently imports products from data/catalog.csv into KlickIt backend via admin endpoints.
Matching key: product name (case-insensitive trimmed).
"""

import os
import sys
import csv
import json
import urllib.request
import urllib.error
from pathlib import Path


def get_admin_token(base_url):
    # Check if pre-existing token is provided in environment
    token = os.environ.get("ADMIN_TOKEN") or os.environ.get("KLICKIT_ADMIN_TOKEN")
    if token:
        return token

    # Check for credentials in environment
    email = os.environ.get("ADMIN_EMAIL") or os.environ.get("BOOTSTRAP_ADMIN_EMAIL") or os.environ.get("KLICKIT_ADMIN_EMAIL")
    password = os.environ.get("ADMIN_PASSWORD") or os.environ.get("BOOTSTRAP_ADMIN_PASSWORD") or os.environ.get("KLICKIT_ADMIN_PASSWORD")

    # If not set in environment, check if local-settings.txt exists outside repo
    if not email or not password:
        settings_path = Path("C:/Users/starl/klickit-local/local-settings.txt")
        if settings_path.exists():
            with open(settings_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        k, v = k.strip(), v.strip()
                        if k in ("ADMIN_EMAIL", "BOOTSTRAP_ADMIN_EMAIL") and not email:
                            email = v
                        elif k in ("ADMIN_PASSWORD", "BOOTSTRAP_ADMIN_PASSWORD") and not password:
                            password = v

    if not email or not password:
        raise ValueError("Admin credentials not found. Please set ADMIN_TOKEN or ADMIN_EMAIL and ADMIN_PASSWORD in environment.")

    # Authenticate via /api/auth/login
    login_url = f"{base_url}/auth/login"
    payload = json.dumps({"email": email, "password": password}).encode("utf-8")
    req = urllib.request.Request(
        login_url,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data["data"]["token"]
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        raise RuntimeError(f"Admin authentication failed (HTTP {e.code}): {err_msg}")


def fetch_all_products(base_url, token):
    url = f"{base_url}/products"
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {token}"},
        method="GET"
    )
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        return data.get("data", [])


def create_product(base_url, token, product_data):
    url = f"{base_url}/products"
    payload = json.dumps({
        "name": product_data["name"],
        "description": product_data.get("description") or product_data["name"],
        "price": float(product_data["price"]),
        "category": product_data["category"]
    }).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        },
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))["data"]


def update_product_price(base_url, token, product_id, new_price):
    url = f"{base_url}/products/{product_id}"
    payload = json.dumps({
        "price": float(new_price)
    }).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        },
        method="PUT"
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))["data"]


def import_catalog(csv_path="data/catalog.csv", base_url="http://localhost:8080/api"):
    csv_file = Path(csv_path)
    if not csv_file.exists():
        raise FileNotFoundError(f"Catalog file not found: {csv_file}")

    token = get_admin_token(base_url)
    existing_list = fetch_all_products(base_url, token)

    # Build map of existing products by normalized name
    existing_map = {p["name"].strip().lower(): p for p in existing_list}

    with open(csv_file, "r", encoding="utf-8") as f:
        reader = list(csv.DictReader(f))

    csv_names_set = set()
    created_count = 0
    updated_count = 0
    skipped_count = 0

    for row in reader:
        name = row["name"].strip()
        norm_name = name.lower()
        csv_names_set.add(norm_name)
        category = row["category"].strip()
        csv_price = float(row["price"])

        if norm_name not in existing_map:
            # Create product
            create_product(base_url, token, {
                "name": name,
                "category": category,
                "price": csv_price,
                "description": f"{name} ({category})"
            })
            created_count += 1
        else:
            existing = existing_map[norm_name]
            existing_price = float(existing["price"])
            if abs(existing_price - csv_price) > 0.001:
                # Price changed -> update
                update_product_price(base_url, token, existing["id"], csv_price)
                updated_count += 1
            else:
                # Identical price -> no-op
                skipped_count += 1

    # Find untouched existing products (in DB but not in CSV)
    untouched = [p for p in existing_list if p["name"].strip().lower() not in csv_names_set]

    print("========================================")
    print("Catalog Import Summary")
    print("========================================")
    print(f"Total rows processed: {len(reader)}")
    print(f"Created: {created_count}")
    print(f"Updated: {updated_count}")
    print(f"Skipped: {skipped_count}")
    print(f"Untouched existing products in DB: {len(untouched)}")
    for p in untouched:
        print(f"  - [{p['id'][:8]}] {p['name']} ({p['category']}) - Rs {p['price']} (active={p['active']})")
    print("========================================")

    return {
        "created": created_count,
        "updated": updated_count,
        "skipped": skipped_count,
        "untouched": untouched
    }


if __name__ == "__main__":
    csv_arg = sys.argv[1] if len(sys.argv) > 1 else "data/catalog.csv"
    base_url_arg = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8080/api"
    import_catalog(csv_arg, base_url_arg)
