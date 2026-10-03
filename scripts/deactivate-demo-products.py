#!/usr/bin/env python3
"""
deactivate-demo-products.py
Soft-deactivates the 6 baseline demo products (Maggi, Coke, Sprite, Notebook, Pen, Toothpaste)
via the Admin DELETE /api/products/{id} endpoint.
"""

import os
import sys
import json
import urllib.request
import urllib.error
from pathlib import Path

DEMO_PRODUCT_NAMES = {"maggi", "coke", "sprite", "notebook", "pen", "toothpaste"}


def get_admin_token(base_url):
    token = os.environ.get("ADMIN_TOKEN") or os.environ.get("KLICKIT_ADMIN_TOKEN")
    if token:
        return token

    email = os.environ.get("ADMIN_EMAIL") or os.environ.get("BOOTSTRAP_ADMIN_EMAIL") or os.environ.get("KLICKIT_ADMIN_EMAIL")
    password = os.environ.get("ADMIN_PASSWORD") or os.environ.get("BOOTSTRAP_ADMIN_PASSWORD") or os.environ.get("KLICKIT_ADMIN_PASSWORD")

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
        raise ValueError("Admin credentials not found.")

    login_url = f"{base_url}/auth/login"
    payload = json.dumps({"email": email, "password": password}).encode("utf-8")
    req = urllib.request.Request(
        login_url,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        return data["data"]["token"]


def deactivate_demo_products(base_url="http://localhost:8080/api"):
    token = get_admin_token(base_url)

    # Fetch all active products
    url = f"{base_url}/products"
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {token}"},
        method="GET"
    )
    with urllib.request.urlopen(req) as resp:
        products = json.loads(resp.read().decode("utf-8")).get("data", [])

    deactivated = []
    for p in products:
        name_lower = p["name"].strip().lower()
        if name_lower in DEMO_PRODUCT_NAMES:
            del_url = f"{base_url}/products/{p['id']}"
            del_req = urllib.request.Request(
                del_url,
                headers={"Authorization": f"Bearer {token}"},
                method="DELETE"
            )
            with urllib.request.urlopen(del_req) as del_resp:
                if del_resp.status == 200:
                    deactivated.append(p)
                    print(f"Deactivated demo product: {p['name']} (ID: {p['id']})")

    print(f"Total demo products deactivated: {len(deactivated)}")
    return deactivated


if __name__ == "__main__":
    base_url_arg = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api"
    deactivate_demo_products(base_url_arg)
