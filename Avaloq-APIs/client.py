#!/usr/bin/env python3
"""
Ready-to-copy client script for your FastAPI service.

Usage:
  1) pip install requests
  2) Set BASE_URL (or export API_BASE_URL)
  3) python api_client.py
"""

import os
import json
import requests
from typing import Any, Dict


# --- CONFIG ---
BASE_URL = os.getenv("API_BASE_URL", "http://127.0.0.1:8000").rstrip("/")
TIMEOUT_S = float(os.getenv("API_TIMEOUT", "20"))

API_TOKEN = os.getenv("API_TOKEN", "").strip()


def _headers() -> Dict[str, str]:
    h = {"Accept": "application/json", "Content-Type": "application/json"}
    if API_TOKEN:
        h["Authorization"] = f"Bearer {API_TOKEN}"
    return h


def _print_response(resp: requests.Response) -> None:
    print(f"\n--- {resp.request.method} {resp.url}")
    print(f"Status: {resp.status_code}")
    ct = resp.headers.get("content-type", "")
    if "application/json" in ct.lower():
        try:
            print(json.dumps(resp.json(), indent=2, ensure_ascii=False))
        except Exception:
            print(resp.text)
    else:
        print(resp.text)


def health_check() -> None:
    resp = requests.get(f"{BASE_URL}/health", headers=_headers(), timeout=TIMEOUT_S)
    _print_response(resp)
    resp.raise_for_status()


def create_country(payload: Dict[str, Any]) -> str:
    resp = requests.post(
        f"{BASE_URL}/countries",
        headers=_headers(),
        json=payload,
        timeout=TIMEOUT_S,
    )
    _print_response(resp)
    resp.raise_for_status()
    data = resp.json()
    return data["resourceId"]


def list_countries(limit: int = 50, offset: int = 0) -> Dict[str, Any]:
    resp = requests.get(
        f"{BASE_URL}/countries",
        headers=_headers(),
        params={"limit": limit, "offset": offset},
        timeout=TIMEOUT_S,
    )
    _print_response(resp)
    resp.raise_for_status()
    return resp.json()


def get_country(resource_id: str) -> Dict[str, Any]:
    resp = requests.get(
        f"{BASE_URL}/countries/{resource_id}",
        headers=_headers(),
        timeout=TIMEOUT_S,
    )
    _print_response(resp)
    resp.raise_for_status()
    return resp.json()


def replace_country(resource_id: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    resp = requests.put(
        f"{BASE_URL}/countries/{resource_id}",
        headers=_headers(),
        json=payload,
        timeout=TIMEOUT_S,
    )
    _print_response(resp)
    resp.raise_for_status()
    return resp.json()


def patch_country(resource_id: str, patch: Dict[str, Any]) -> Dict[str, Any]:
    resp = requests.patch(
        f"{BASE_URL}/countries/{resource_id}",
        headers=_headers(),
        json=patch,
        timeout=TIMEOUT_S,
    )
    _print_response(resp)
    resp.raise_for_status()
    return resp.json()


def delete_country(resource_id: str) -> None:
    resp = requests.delete(
        f"{BASE_URL}/countries/{resource_id}",
        headers=_headers(),
        timeout=TIMEOUT_S,
    )
    _print_response(resp)
    resp.raise_for_status()


def seed_countries() -> None:
    countries = [
        {"id": 41, "bdeRecVersion": 0, "openDate": "2024-01-01", "closeDate": "2099-12-31", "sortAlpha": "CH", "sortNr": 41},
        {"id": 276, "bdeRecVersion": 0, "openDate": "2024-01-01", "closeDate": "2099-12-31", "sortAlpha": "DE", "sortNr": 276},
        {"id": 250, "bdeRecVersion": 0, "openDate": "2024-01-01", "closeDate": "2099-12-31", "sortAlpha": "FR", "sortNr": 250},
    ]

    print("\nSeeding countries (create if missing)...")
    for c in countries:
        try:
            rid = create_country(c)
            print(f"Seeded {c['sortAlpha']} -> resourceId={rid}")
        except requests.HTTPError as e:
            resp = e.response
            if resp is not None and resp.status_code in (400, 409):
                # 409 Conflict is typical for duplicates; some APIs use 400
                print(f"Already exists (skipping): {c['sortAlpha']} (id={c['id']})")
                continue
            raise

def main() -> int:
    print(f"BASE_URL = {BASE_URL}")
    print(f"Using token: {'YES' if API_TOKEN else 'NO'}")

    health_check()

    seed_countries()

    # Stop here if seeding is all you want:
    list_countries(limit=10, offset=0)

    country_payload: Dict[str, Any] = {
        "id": 1,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2024-12-31",
        "sortAlpha": "CH",
        "sortNr": 41,
    }

    resource_id = create_country(country_payload)
    print(f"\nCreated resourceId: {resource_id}")

    list_countries(limit=10, offset=0)
    get_country(resource_id)
    patch_country(resource_id, {"sortAlpha": "CHE", "sortNr": 999})

    replaced_payload = dict(country_payload)
    replaced_payload.update({"sortAlpha": "CH-REPLACED", "sortNr": 123})
    replace_country(resource_id, replaced_payload)

    delete_country(resource_id)
    print("\nDeleted OK.")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except requests.HTTPError as e:
        print("\nHTTP error:", e)
        raise SystemExit(2)
    except requests.RequestException as e:
        print("\nRequest failed:", e)
        raise SystemExit(3)