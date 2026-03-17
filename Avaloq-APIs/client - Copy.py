#!/usr/bin/env python3
"""
Ready-to-copy client script for your FastAPI service.

Usage:
  1) pip install requests
  2) Set BASE_URL (or export API_BASE_URL)
  3) python api_client.py
"""

import os
import sys
import json
import requests
from typing import Any, Dict, Optional


# --- CONFIG ---
# BASE_URL = os.getenv("API_BASE_URL", "https://avaloq-delivery-tool-svc").rstrip("/")
BASE_URL = os.getenv("API_BASE_URL", "http://127.0.0.1:8000").rstrip("/")
TIMEOUT_S = float(os.getenv("API_TIMEOUT", "20"))

# Optional: set a bearer token via env var API_TOKEN (leave empty if not needed)
API_TOKEN = os.getenv("API_TOKEN", "").strip()

# Optional: provide a CA bundle path via env var REQUESTS_CA_BUNDLE (recommended for internal CAs)
# requests automatically respects REQUESTS_CA_BUNDLE / CURL_CA_BUNDLE if set.


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
    # For 204, resp.json() would fail; just check status.
    resp.raise_for_status()


def main() -> int:
    print(f"BASE_URL = {BASE_URL}")
    print(f"Using token: {'YES' if API_TOKEN else 'NO'}")

    # 1) Health
    health_check()

    # 2) Minimal payload that matches your current obj_country snippet
    country_payload: Dict[str, Any] = {
        "id": 1,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2024-12-31",
        "sortAlpha": "CH",
        "sortNr": 41,
    }

    # 3) Create
    resource_id = create_country(country_payload)
    print(f"\nCreated resourceId: {resource_id}")

    # 4) List
    list_countries(limit=10, offset=0)

    # 5) Get
    get_country(resource_id)

    # 6) Patch (partial update)
    patch_country(resource_id, {"sortAlpha": "CHE", "sortNr": 999})

    # 7) Replace (full replace)
    replaced_payload = dict(country_payload)
    replaced_payload.update({"sortAlpha": "CH-REPLACED", "sortNr": 123})
    replace_country(resource_id, replaced_payload)

    # 8) Delete
    delete_country(resource_id)
    print("\nDeleted OK.")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except requests.HTTPError as e:
        print("\nHTTP error:", e)
        return_code = 2
        raise SystemExit(return_code)
    except requests.RequestException as e:
        print("\nRequest failed:", e)
        return_code = 3
        raise SystemExit(return_code)