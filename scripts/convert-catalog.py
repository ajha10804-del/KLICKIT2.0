#!/usr/bin/env python3
"""
convert-catalog.py
Normalizes and merges two catalog source CSVs (145 + 101 = 246 rows) into canonical data/catalog.csv.
"""

import os
import sys
import csv
import re
from pathlib import Path

# Category maps
FILE1_CATEGORY_MAP = {
    'Dairy & Eggs': ['Milk', 'Protein Milk', 'Probiotic', 'Eggs', 'Curd', 'Greek Yogurt', 'Butter', 'Cheese'],
    'Munchies': ['Chips', 'Nachos', 'Namkeen', 'Biscuits', 'Cookies', 'Digestive', 'Rusk', 'Popcorn', 'Chocolate', 'Gum', 'Candy', 'Cake'],
    'Bakery': ['Bread', 'Pav'],
    'Cold Drinks': ['Soft Drink', 'Energy Drink', 'Water', 'Cold Coffee', 'Iced Tea', 'Juice', 'Electrolyte', 'Sports Drink'],
    'Instant Food': ['Noodles', 'Cup Noodles', 'Pasta'],
    'Personal Care': ['Toothpaste', 'Toothbrush', 'Mouthwash', 'Soap', 'Shampoo', 'Face Wash', 'Deodorant'],
    'Home Care': ['Detergent', 'Detergent Bar', 'Mosquito Repellent', 'Tissue', 'Garbage Bags'],
    'Stationery & Electronics': ['Battery', 'Multi Plug', 'Extension Board', 'Notebook', 'Pen', 'Highlighter', 'Glue']
}
PROD_TO_CAT = {prod: cat for cat, prods in FILE1_CATEGORY_MAP.items() for prod in prods}

FILE2_CATEGORY_MAP = {
    'Fruits & Veg': ['Fruits', 'Vegetables'],
    'Staples': ['Atta & Flour', 'Dal & Pulses', 'Rice', 'Grains & Breakfast', 'Cooking Essentials', 'Dry Fruits & Nuts']
}
RAW_CAT_TO_CAT = {c: cat for cat, cs in FILE2_CATEGORY_MAP.items() for c in cs}

VAGUE_QUANTITIES = {'pack', 'regular pack', 'regular', 'single'}


def normalize_quantity(q_str):
    q = q_str.strip()
    # Normalize quantity range (e.g. 70–75 g, 70–72.6 g, 140–150 g) to lower bound
    m = re.match(r'^([\d\.]+)\s*[\u2013\u2014\-]\s*[\d\.]+\s*([a-zA-Z]+)$', q)
    if m:
        num = m.group(1)
        if num.endswith('.0'):
            num = num[:-2]
        return f"{num} {m.group(2)}"
    return q


def convert_catalog(source_dir, output_csv):
    source_path = Path(source_dir)
    file1 = source_path / "hostel_products_145.csv"
    file2 = source_path / "fruits_vegetables_grocery_staples.csv"

    if not file1.exists():
        raise FileNotFoundError(f"Source file not found: {file1}")
    if not file2.exists():
        raise FileNotFoundError(f"Source file not found: {file2}")

    products = []
    vague_rows = []

    # Process File 1 (145 rows)
    with open(file1, "r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for r in reader:
            prod = r["Product"].strip()
            brand_var = r["Brand / Variant"].strip()
            raw_qty = r["Quantity"].strip()
            price_str = r["Price (INR)"].strip()

            if prod not in PROD_TO_CAT:
                raise ValueError(f"Unmapped product in File 1 row {r.get('S.No.')}: {r}")
            category = PROD_TO_CAT[prod]

            norm_qty = normalize_quantity(raw_qty)
            is_vague = norm_qty.lower() in VAGUE_QUANTITIES
            if is_vague:
                vague_rows.append({
                    "source": "hostel_products_145.csv",
                    "sno": r["S.No."],
                    "brand_variant": brand_var,
                    "product": prod,
                    "quantity": raw_qty,
                    "price": price_str
                })

            # Name rule: {Brand / Variant} {Product} {Quantity}
            # Skip Product if Brand / Variant already contains it (case-insensitive substring)
            tokens = [brand_var]
            if not re.search(r'\b' + re.escape(prod) + r'\b', brand_var, re.IGNORECASE):
                tokens.append(prod)
            if not is_vague:
                tokens.append(norm_qty)

            name = re.sub(r'\s+', ' ', ' '.join(tokens)).strip()

            price_val = f"{float(price_str):.2f}"
            products.append({
                "name": name,
                "category": category,
                "price": price_val,
                "quantity": norm_qty if not is_vague else ""
            })

    # Process File 2 (101 rows)
    with open(file2, "r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for r in reader:
            raw_cat = r["Category"].strip()
            brand_prod = r["Brand / Product"].strip()
            variant = r["Variant"].strip()
            raw_qty = r["Quantity"].strip()
            price_str = r["Price (INR)"].strip()

            if raw_cat not in RAW_CAT_TO_CAT:
                raise ValueError(f"Unmapped category in File 2 row {r.get('S.No.')}: {r}")
            category = RAW_CAT_TO_CAT[raw_cat]

            norm_qty = normalize_quantity(raw_qty)
            is_vague = norm_qty.lower() in VAGUE_QUANTITIES
            if is_vague:
                vague_rows.append({
                    "source": "fruits_vegetables_grocery_staples.csv",
                    "sno": r["S.No."],
                    "brand_product": brand_prod,
                    "variant": variant,
                    "quantity": raw_qty,
                    "price": price_str
                })

            # Name rule: {Brand / Product} {Variant} {Quantity}
            # Drop Variant when it is just "Fresh"
            tokens = [brand_prod]
            if variant.lower() != 'fresh':
                tokens.append(variant)
            if not is_vague:
                tokens.append(norm_qty)

            name = re.sub(r'\s+', ' ', ' '.join(tokens)).strip()

            price_val = f"{float(price_str):.2f}"
            products.append({
                "name": name,
                "category": category,
                "price": price_val,
                "quantity": norm_qty if not is_vague else ""
            })

    # Assert total count
    if len(products) != 246:
        raise ValueError(f"Expected 246 products, got {len(products)}")

    # Uniqueness check
    seen = {}
    duplicates = []
    for p in products:
        n = p["name"]
        if n in seen:
            duplicates.append((n, seen[n], p))
        seen[n] = p

    if duplicates:
        raise ValueError(f"Found duplicate product names: {duplicates}")

    # Write output CSV
    output_path = Path(output_csv)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["name", "category", "price", "quantity"])
        writer.writeheader()
        writer.writerows(products)

    print(f"Successfully generated {output_csv} with {len(products)} products.")
    return products, vague_rows


if __name__ == "__main__":
    src = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\starl\klickit-local\catalog-source"
    dest = sys.argv[2] if len(sys.argv) > 2 else r"data/catalog.csv"
    prods, v_rows = convert_catalog(src, dest)
    print(f"Vague quantity rows stripped: {len(v_rows)}")
    for vr in v_rows:
        print(f"  [{vr['source']} S.No {vr['sno']}] {vr.get('brand_variant') or vr.get('brand_product')} | {vr['quantity']}")
