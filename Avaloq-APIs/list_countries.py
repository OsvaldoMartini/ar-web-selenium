#!/usr/bin/env python3

import os
import sys
import json
import requests

# Default: local FastAPI server
# BASE_URL = os.getenv("API_BASE_URL", "https://avaloq-delivery-tool-svc").rstrip("/")
BASE_URL = os.getenv("API_BASE_URL", "http://127.0.0.1:8000").rstrip("/")
TIMEOUT_S = 20

# Optional token support
API_TOKEN = os.getenv("API_TOKEN", "").strip()


def headers():
    h = {"Accept": "application/json"}
    if API_TOKEN:
        h["Authorization"] = f"Bearer {API_TOKEN}"
    return h


def main():
    print(f"Calling: {BASE_URL}/countries")

    response = requests.get(
        f"{BASE_URL}/countries",
        headers=headers(),
        timeout=TIMEOUT_S,
    )

    print(f"Status: {response.status_code}")
    response.raise_for_status()

    data = response.json()
    print(json.dumps(data, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except requests.HTTPError as e:
        print("HTTP error:", e)
        sys.exit(2)
    except requests.RequestException as e:
        print("Request failed:", e)
        sys.exit(3)