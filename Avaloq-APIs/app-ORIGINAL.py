from __future__ import annotations

import uuid
from typing import Any, Dict, Optional

from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import JSONResponse
from jsonschema import Draft7Validator, RefResolver, validate
from jsonschema.exceptions import ValidationError

app = FastAPI(title="obj_country REST API", version="1.0.0")

@app.get("/")
def root():
    return {"message": "API is running", "docs": "/docs"}

# --- Your schema (paste as-is). IMPORTANT: keep it identical to what you provided.
SCHEMA: Dict[str, Any] = {
  "$id": "https://avaloq.com/obj_country.schema.json",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$ref": "#/definitions/obj_country",
  "definitions": {
    "code_tab_ref": {
      "type": "object",
      "properties": {
        "_href": {"description": "Reference/link", "type": "string"},
        "id": {"description": "Numeric reference", "type": "integer"},
        "ident": {"description": "Unique symbolic reference", "type": "string"},
        "intlId": {"description": "Symbolic reference", "type": "string"},
      },
    },
    "code_tab_ref_action": {
      "type": "object",
      "properties": {
        "action": {"description": "enum reference to action", "type": "string"},
        "id": {"description": "Numeric reference", "type": "integer"},
        "ident": {"description": "Unique symbolic reference", "type": "string"},
      },
    },
    "doc_ref": {
      "type": "object",
      "properties": {
        "_href": {"description": "Reference/link", "type": "string"},
        "_ident": {
          "description": "Only used for workItem use cases, cross-reference in workItem context.",
          "type": "string",
        },
        "id": {"description": "Numeric reference", "type": "integer"},
      },
    },
    "mba$addr_ctrl": {
      "type": "object",
      "properties": {
        "fldList": {"type": "array", "items": {"$ref": "#/definitions/mba$addr_ctrl_mba$addr_fld"}},
        "fmtList": {"type": "array", "items": {"$ref": "#/definitions/mba$addr_ctrl_mba$addr_fmt"}},
      },
    },
    "mba$addr_ctrl_mba$addr_fld": {
      "type": "object",
      "properties": {
        "addrFldId": {"type": "integer"},
        "cond": {"type": "string"},
        "isMand": {"type": "boolean"},
        "lbl": {"type": "string"},
        "mandCondExpr": {
          "description": "Referencing: `/mba$code-script-exprs/{id}`",
          "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
        },
        "validRegexp": {"type": "string"},
      },
    },
    "mba$addr_ctrl_mba$addr_fmt": {
      "type": "object",
      "properties": {
        "addrFmtRef": {
          "description": "Referencing: `/mba$code-addr-fmt-refs/{id}`",
          "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
        },
        "fmt": {"type": "string"},
        "postalAddrDlvType": {
          "description": "Referencing: `/mba$code-postal-addr-dlv-types/{id}`",
          "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
        },
      },
    },
    "mba$admin_divn": {
      "type": "object",
      "properties": {
        "adminDivnList": {"type": "array", "items": {"$ref": "#/definitions/mba$admin_divn_mba$admin_divn_lvl"}},
      },
    },
    "mba$admin_divn_mba$admin_divn_lvl": {
      "type": "object",
      "properties": {
        "adminDivn": {
          "description": "Referencing: `/mba$code-admin-divns/{id}`",
          "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
        },
        "adminDivnLvl": {"type": "number"},
        "isPostalCodeUplRelv": {"type": "boolean"},
      },
    },
    "mba$det": {
      "type": "object",
      "properties": {
        "bbanFmt": {
          "description": "Referencing: `/mba$code-bban-fmts/{id}`",
          "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
        },
        "isOmsLangRegion": {"type": "boolean"},
        "majorityAge": {"type": "number"},
        "parent": {
          "description": "Referencing: `/obj-countrys/{id}`",
          "allOf": [{"$ref": "#/definitions/obj_ref"}],
        },
        "vatNrLen": {"type": "number"},
      },
    },
    "mba$ftr_risk": {
      "type": "object",
      "properties": {
        "ftrRisk": {
          "description": "Referencing: `/mba$code-ftr-risks/{id}`",
          "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
        }
      },
    },
    "mba$pay_chan_ctrl": {
      "type": "object",
      "properties": {
        "payChanList": {"type": "array", "items": {"$ref": "#/definitions/mba$pay_chan_ctrl_mba$pay_chan"}},
      },
    },
    "mba$pay_chan_ctrl_mba$pay_chan": {
      "type": "object",
      "properties": {
        "curry": {"description": "Referencing: `/obj-assets/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
        "fldList": {"type": "array", "items": {"$ref": "#/definitions/mba$pay_chan_ctrl_pay_chan_list_mba$pay_fld"}},
        "ot": {"description": "Referencing: `/code-order-types/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
        "payChan": {"description": "Referencing: `/code-pay-chans/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
      },
    },
    "mba$pay_chan_ctrl_pay_chan_list_mba$pay_fld": {
      "type": "object",
      "properties": {"isMand": {"type": "boolean"}, "payFldId": {"type": "integer"}},
    },
    "mba$qi": {"type": "object", "properties": {"code": {"type": "string"}}},
    "mba$tech": {
      "type": "object",
      "properties": {
        "config": {"type": "string"},
        "dataOrigin": {"description": "Referencing: `/mba$code-data-origins/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
      },
    },
    "mba$week": {"type": "object", "properties": {"weekdayList": {"type": "array", "items": {"$ref": "#/definitions/mba$week_mba$weekday"}}}},
    "mba$week_mba$weekday": {"type": "object", "properties": {"weekday": {"description": "Referencing: `/code-weekdays/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]}}},
    "mba$zip": {"type": "object", "properties": {"zipDet": {"type": "array", "items": {"$ref": "#/definitions/mba$zip_mba$zip_det"}}}},
    "mba$zip_mba$zip_det": {
      "type": "object",
      "properties": {
        "city": {"type": "string"},
        "isJurisdiction": {"type": "string"},
        "jurisdictionId": {"type": "number"},
        "state": {"description": "Referencing: `/code-country-states/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
        "zip": {"type": "string"},
        "zipSelPrio": {"type": "number"},
      },
    },

    # --- many defs omitted in this snippet to keep the example manageable ---
    # IMPORTANT:
    # For full strict validation of *all* nested objects, keep ALL definitions from your schema.
    # Since your schema is very large, you should paste the full "definitions" block here unchanged.

    "obj_country": {
      "type": "object",
      "properties": {
        "id": {"description": "IDReferencing: `/objs/{id}`", "type": "integer"},
        "bdeRecVersion": {"type": "integer"},
        "closeDate": {"description": "Closing date", "type": "string", "format": "date"},
        "openDate": {"description": "Opening date", "type": "string", "format": "date"},
        "sortAlpha": {"description": "Alpha-numeric sort value", "type": "string"},
        "sortNr": {"description": "Numeric sort value", "type": "integer"},
        # Keep the rest of obj_country properties (and refs) from your schema for complete validation
      },
    },
  },
}

# --- Compile validator once
_resolver = RefResolver.from_schema(SCHEMA)
_validator = Draft7Validator(SCHEMA, resolver=_resolver)

# In-memory store: key -> obj_country (dict)
DB: Dict[str, Dict[str, Any]] = {}


def _validate_obj_country(payload: Dict[str, Any]) -> None:
    """Validate request payload against the schema root ($ref -> obj_country)."""
    errors = sorted(_validator.iter_errors(payload), key=lambda e: e.path)
    if errors:
        # return a friendly list of schema violations
        details = []
        for e in errors[:50]:
            loc = ".".join([str(p) for p in e.path]) or "<root>"
            details.append({"path": loc, "message": e.message})
        raise HTTPException(status_code=422, detail={"schema_errors": details})


@app.exception_handler(ValidationError)
async def jsonschema_validation_exception_handler(_: Request, exc: ValidationError):
    return JSONResponse(status_code=422, content={"detail": exc.message})


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/countries", status_code=201)
async def create_country(body: Dict[str, Any]):
    _validate_obj_country(body)
    rid = str(uuid.uuid4())
    DB[rid] = body
    return {"resourceId": rid, "data": body}


@app.get("/countries")
def list_countries(limit: int = 50, offset: int = 0):
    items = list(DB.items())
    slice_ = items[offset : offset + limit]
    return {
        "total": len(DB),
        "limit": limit,
        "offset": offset,
        "items": [{"resourceId": k, "data": v} for k, v in slice_],
    }


@app.get("/countries/{resource_id}")
def get_country(resource_id: str):
    if resource_id not in DB:
        raise HTTPException(status_code=404, detail="Not found")
    return {"resourceId": resource_id, "data": DB[resource_id]}


@app.put("/countries/{resource_id}")
async def replace_country(resource_id: str, body: Dict[str, Any]):
    if resource_id not in DB:
        raise HTTPException(status_code=404, detail="Not found")
    _validate_obj_country(body)
    DB[resource_id] = body
    return {"resourceId": resource_id, "data": body}


@app.patch("/countries/{resource_id}")
async def patch_country(resource_id: str, body: Dict[str, Any]):
    if resource_id not in DB:
        raise HTTPException(status_code=404, detail="Not found")

    # Merge patch into existing
    current = DB[resource_id]
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="PATCH body must be an object")

    merged = dict(current)
    merged.update(body)

    _validate_obj_country(merged)
    DB[resource_id] = merged
    return {"resourceId": resource_id, "data": merged}


@app.delete("/countries/{resource_id}", status_code=204)
def delete_country(resource_id: str):
    if resource_id not in DB:
        raise HTTPException(status_code=404, detail="Not found")
    del DB[resource_id]
    return Response(status_code=204)
