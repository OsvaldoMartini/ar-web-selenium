#!/usr/bin/env python3

import os
import sys
import json
import requests

# Default: local FastAPI server
BASE_URL = os.getenv("API_BASE_URL", "http://127.0.0.1:8000").rstrip("/")
TIMEOUT_S = 20

API_TOKEN = os.getenv("API_TOKEN", "").strip()


def headers():
    h = {"Accept": "application/json", "Content-Type": "application/json"}
    if API_TOKEN:
        h["Authorization"] = f"Bearer {API_TOKEN}"
    return h


def insert_country(payload):
    response = requests.post(
        f"{BASE_URL}/countries",
        headers=headers(),
        json=payload,
        timeout=TIMEOUT_S,
    )

    print(f"\nPOST /countries -> Status {response.status_code}")
    response.raise_for_status()

    data = response.json()
    print(json.dumps(data, indent=2, ensure_ascii=False))
    return data["resourceId"]


def main():
    print(f"Using API: {BASE_URL}")

    countries = [
        {
            "id": 41,
            "bdeRecVersion": 0,
            "openDate": "2024-01-01",
            "closeDate": "2099-12-31",
            "sortAlpha": "CH",
            "sortNr": 41
        },
        {
            "id": 276,
            "bdeRecVersion": 0,
            "openDate": "2024-01-01",
            "closeDate": "2099-12-31",
            "sortAlpha": "DE",
            "sortNr": 276
        },
        {
            "id": 250,
            "bdeRecVersion": 0,
            "openDate": "2024-01-01",
            "closeDate": "2099-12-31",
            "sortAlpha": "FR",
            "sortNr": 250
        }
    ]

    created_ids = []

    for country in countries:
        rid = insert_country(country)
        created_ids.append(rid)

    print("\nCreated resourceIds:")
    for rid in created_ids:
        print(" -", rid)


def run_insert_countries():
    main()

if __name__ == "__main__":
    try:
        run_insert_countries()
    except requests.HTTPError as e:
        print("HTTP error:", e)
        sys.exit(2)
    except requests.RequestException as e:
        print("Request failed:", e)
        sys.exit(3)