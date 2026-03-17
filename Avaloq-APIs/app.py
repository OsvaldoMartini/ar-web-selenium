from __future__ import annotations

import uuid
from typing import Any, Dict

from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import JSONResponse
from jsonschema import Draft7Validator, RefResolver
from jsonschema.exceptions import ValidationError

app = FastAPI(title="obj_country + obj_balacc REST API", version="1.0.0")


@app.get("/")
def root():
    return {"message": "API is running", "docs": "/docs"}


# ============================================================
# COUNTRY SCHEMA (your existing one)
# ============================================================
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
                "fldList": {
                    "type": "array",
                    "items": {"$ref": "#/definitions/mba$addr_ctrl_mba$addr_fld"},
                },
                "fmtList": {
                    "type": "array",
                    "items": {"$ref": "#/definitions/mba$addr_ctrl_mba$addr_fmt"},
                },
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
                "adminDivnList": {
                    "type": "array",
                    "items": {"$ref": "#/definitions/mba$admin_divn_mba$admin_divn_lvl"},
                },
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
                "payChanList": {
                    "type": "array",
                    "items": {"$ref": "#/definitions/mba$pay_chan_ctrl_mba$pay_chan"},
                },
            },
        },
        "mba$pay_chan_ctrl_mba$pay_chan": {
            "type": "object",
            "properties": {
                "curry": {
                    "description": "Referencing: `/obj-assets/{id}`",
                    "allOf": [{"$ref": "#/definitions/obj_ref"}],
                },
                "fldList": {
                    "type": "array",
                    "items": {
                        "$ref": "#/definitions/mba$pay_chan_ctrl_pay_chan_list_mba$pay_fld"
                    },
                },
                "ot": {
                    "description": "Referencing: `/code-order-types/{id}`",
                    "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
                },
                "payChan": {
                    "description": "Referencing: `/code-pay-chans/{id}`",
                    "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
                },
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
                "dataOrigin": {
                    "description": "Referencing: `/mba$code-data-origins/{id}`",
                    "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
                },
            },
        },
        "mba$week": {
            "type": "object",
            "properties": {
                "weekdayList": {
                    "type": "array",
                    "items": {"$ref": "#/definitions/mba$week_mba$weekday"},
                }
            },
        },
        "mba$week_mba$weekday": {
            "type": "object",
            "properties": {
                "weekday": {
                    "description": "Referencing: `/code-weekdays/{id}`",
                    "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
                }
            },
        },
        "mba$zip": {
            "type": "object",
            "properties": {
                "zipDet": {
                    "type": "array",
                    "items": {"$ref": "#/definitions/mba$zip_mba$zip_det"},
                }
            },
        },
        "mba$zip_mba$zip_det": {
            "type": "object",
            "properties": {
                "city": {"type": "string"},
                "isJurisdiction": {"type": "string"},
                "jurisdictionId": {"type": "number"},
                "state": {
                    "description": "Referencing: `/code-country-states/{id}`",
                    "allOf": [{"$ref": "#/definitions/code_tab_ref"}],
                },
                "zip": {"type": "string"},
                "zipSelPrio": {"type": "number"},
            },
        },
        # IMPORTANT: keep your full schema definitions here in your real file.
        "obj_country": {
            "type": "object",
            "properties": {
                "id": {"description": "IDReferencing: `/objs/{id}`", "type": "integer"},
                "bdeRecVersion": {"type": "integer"},
                "closeDate": {"description": "Closing date", "type": "string", "format": "date"},
                "openDate": {"description": "Opening date", "type": "string", "format": "date"},
                "sortAlpha": {"description": "Alpha-numeric sort value", "type": "string"},
                "sortNr": {"description": "Numeric sort value", "type": "integer"},
            },
        },
    },
}

_resolver = RefResolver.from_schema(SCHEMA)
_validator = Draft7Validator(SCHEMA, resolver=_resolver)

DB: Dict[str, Dict[str, Any]] = {}


def _validate_obj_country(payload: Dict[str, Any]) -> None:
    errors = sorted(_validator.iter_errors(payload), key=lambda e: e.path)
    if errors:
        details = []
        for e in errors[:50]:
            loc = ".".join([str(p) for p in e.path]) or "<root>"
            details.append({"path": loc, "message": e.message})
        raise HTTPException(status_code=422, detail={"schema_errors": details})


# Auto-seed countries on startup (idempotent)
SEED_COUNTRIES = [
    {"id": 1, "bdeRecVersion": 0, "openDate": "2024-01-01", "closeDate": "2099-12-31", "sortAlpha": "CH", "sortNr": 1},
    {"id": 2, "bdeRecVersion": 0, "openDate": "2024-01-01", "closeDate": "2099-12-31", "sortAlpha": "DE", "sortNr": 2},
    {"id": 3, "bdeRecVersion": 0, "openDate": "2024-01-01", "closeDate": "2099-12-31", "sortAlpha": "FR", "sortNr": 3},
]


@app.on_event("startup")
def _seed_countries_on_startup() -> None:
    for c in SEED_COUNTRIES:
        _validate_obj_country(c)
        rid = str(c["id"])      # stable keys: "41", "276", "250"
        DB.setdefault(rid, c)   # don't overwrite


# ============================================================
# BALACC SCHEMA + VALIDATOR + DB  (NEW)
# ============================================================
BALACC_SCHEMA: Dict[str, Any] = {
    "$id": "https://avaloq.com/obj_balacc.schema.json",
    "$schema": "http://json-schema.org/draft-07/schema#",
    "$ref": "#/definitions/obj_balacc",
    "definitions": {
        # NOTE:
        # This is the schema you pasted. Keep it as-is (you can extend it with any missing defs).
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
        "obj_add": {
            "type": "object",
            "properties": {
                "boolVal": {"description": "Value", "type": "boolean"},
                "bu": {"description": "Business UnitReferencing: `/obj-bps/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "dateVal": {"description": "Value", "type": "string", "format": "date"},
                "idVal": {"description": "Value", "type": "integer"},
                "nrVal": {"description": "Value", "type": "number"},
                "objAdd": {"description": "Referencing: `/code-obj-adds/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
                "textVal": {"description": "Value", "type": "string"},
                "tswtzVal": {"description": "Value", "type": "string", "format": "date-time"},
                "valList": {"type": "array", "items": {"$ref": "#/definitions/obj_add_val"}},
            },
        },
        "obj_add_val": {
            "type": "object",
            "properties": {
                "dateVal": {"type": "string", "format": "date"},
                "idVal": {"type": "integer"},
                "nrVal": {"type": "number"},
                "seqNr": {"type": "integer"},
                "textVal": {"type": "string"},
                "tswtzVal": {"type": "string", "format": "date-time"},
            },
        },
        "obj_balacc": {
            "type": "object",
            "properties": {
                "acclevel": {"description": "Account Level", "type": "number"},
                "addList": {"type": "array", "items": {"$ref": "#/definitions/obj_add"}},
                "balaccRd": {"description": "Rounding Balance AccountReferencing: `/obj-balaccs/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "balaccType": {"description": "Balance Account TypeReferencing: `/code-balacc-types/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
                "balstru": {"description": "Balance StructureReferencing: `/obj-balstrus/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "bdeRecVersion": {"type": "integer"},
                "bu": {"description": "Business unitReferencing: `/obj-bps/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "classifList": {"type": "array", "items": {"$ref": "#/definitions/obj_classif"}},
                "closeDate": {"description": "Close Date", "type": "string", "format": "date"},
                "destBalacc": {"description": "Destination Balance AccountReferencing: `/obj-balaccs/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "destPosRefCmtd": {"description": "Destination Position Reference Calc. MethodReferencing: `/code-bal-pos-ref-cmtds/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
                "exportDet": {"description": "Export Details", "type": "boolean"},
                "extn": {"$ref": "#/definitions/obj_balacc_extn"},
                "globalOrderBy": {"description": "Global order by", "type": "number"},
                "id": {"description": "IDReferencing: `/objs/{id}`", "type": "integer"},
                "isAssetDedic": {"description": "Is asset dedicated", "type": "boolean"},
                "isBenefDedic": {"description": "Is beneficiary dedicated", "type": "boolean"},
                "isLeaf": {"description": "Is a leaf", "type": "boolean"},
                "isPl": {"description": "Is Profit & Loss", "type": "boolean"},
                "keyList": {"type": "array", "items": {"$ref": "#/definitions/obj_key"}},
                "lastTrans": {"$ref": "#/definitions/obj_trans"},
                "objName": {"allOf": [{"$ref": "#/definitions/obj_name"}]},
                "objNameList": {"type": "array", "items": {"$ref": "#/definitions/obj_name"}},
                "objType": {"description": "Object typeReferencing: `/code-obj-types/{id}`", "allOf": [{"$ref": "#/definitions/code_tab_ref"}]},
                "openDate": {"description": "Opening date", "type": "string", "format": "date"},
                "orderBy": {"description": "Order by", "type": "number"},
                "outpayIncomeBalacc": {"description": "Outpaid Income Balance AccountReferencing: `/obj-balaccs/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "purchIncomeBalacc": {"description": "Purchased Income Balance AccountReferencing: `/obj-balaccs/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "remarkList": {"type": "array", "items": {"$ref": "#/definitions/obj_remark"}},
                "sbu": {"description": "Sub Business UnitReferencing: `/obj-bps/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
                "sign": {"description": "Sign", "type": "number"},
                "transList": {"type": "array", "items": {"$ref": "#/definitions/obj_trans"}},
                "upperAcc": {"description": "Upper AccountReferencing: `/obj-balaccs/{id}`", "allOf": [{"$ref": "#/definitions/obj_ref"}]},
            },
        },
        "obj_balacc_extn": {
            "type": "object",
            "properties": {
                "balacc": {"type": "array", "items": {"$ref": "#/definitions/obj_key_single_"}},
                "balaccShort": {"type": "array", "items": {"$ref": "#/definitions/obj_key_single_"}},
                "frsGlType": {"$ref": "#/definitions/obj_classif_"},
                "frsGlTypeHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "frsIntangible": {"$ref": "#/definitions/obj_classif_"},
                "frsIntangibleHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "frsPosType": {"$ref": "#/definitions/obj_classif_"},
                "frsPosTypeHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "frsTangibleAsset": {"$ref": "#/definitions/obj_classif_"},
                "frsTangibleAssetHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "fundLedgerSubType": {"$ref": "#/definitions/obj_classif_"},
                "fundLedgerSubTypeHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "fundLedgerType": {"$ref": "#/definitions/obj_classif_"},
                "fundLedgerTypeHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "fundNavGrp": {"$ref": "#/definitions/obj_classif_"},
                "fundNavGrpHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "legEconCrt": {"$ref": "#/definitions/obj_classif_"},
                "legEconCrtHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "legLedgerMatLoss": {"$ref": "#/definitions/obj_classif_"},
                "legLedgerMatLossHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "mba$refinCond": {"$ref": "#/definitions/obj_classif_"},
                "mba$refinCondHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
                "rrlAssGroup": {"$ref": "#/definitions/obj_classif_"},
                "rrlAssGroupHist": {"type": "array", "items": {"$ref": "#/definitions/obj_classif_"}},
            },
        },
        "obj_balaccs": {"type": "array", "items": {"$ref": "#/definitions/obj_balacc"}, "uniqueItems": True},
        "obj_classif": {"type": "object", "properties": {}},
        "obj_classif_": {"type": "object", "properties": {}},
        "obj_key": {"type": "object", "properties": {}},
        "obj_key_ref_": {"type": "object", "properties": {}},
        "obj_key_single_": {"type": "object", "properties": {}},
        "obj_name": {"type": "object", "properties": {}},
        "obj_ref": {"type": "object", "properties": {}},
        "obj_remark": {"type": "object", "properties": {}},
        "obj_remark_det": {"type": "object", "properties": {}},
        "obj_trans": {"type": "object", "properties": {}},
        "user_ref": {"type": "object", "properties": {}},
    },
}

# IMPORTANT:
# Your pasted schema references many nested definitions (obj_ref, obj_key, obj_classif, etc.).
# If you want strict validation for those, paste their full "properties" blocks too.
# This file will still work for CRUD; validation may be lenient if you keep empty properties above.

_balacc_resolver = RefResolver.from_schema(BALACC_SCHEMA)
_balacc_validator = Draft7Validator(BALACC_SCHEMA, resolver=_balacc_resolver)

BALACC_DB: Dict[str, Dict[str, Any]] = {}


def _validate_obj_balacc(payload: Dict[str, Any]) -> None:
    errors = sorted(_balacc_validator.iter_errors(payload), key=lambda e: e.path)
    if errors:
        details = []
        for e in errors[:50]:
            loc = ".".join([str(p) for p in e.path]) or "<root>"
            details.append({"path": loc, "message": e.message})
        raise HTTPException(status_code=422, detail={"schema_errors": details})


# Optional: auto-seed balaccs on startup (empty by default)
SEED_BALACCS = [
    {
        "id": 1001,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2099-12-31",
        "acclevel": 1,
        "globalOrderBy": 1,
        "orderBy": 1,
        "sign": 1,
        "exportDet": True,
        "isLeaf": True,
        "isPl": False,
        "isAssetDedic": False,
        "isBenefDedic": False,
    },
    {
        "id": 1002,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2099-12-31",
        "acclevel": 2,
        "globalOrderBy": 2,
        "orderBy": 2,
        "sign": 1,
        "exportDet": False,
        "isLeaf": True,
        "isPl": True,
        "isAssetDedic": True,
        "isBenefDedic": False,
    },
    {
        "id": 1003,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2099-12-31",
        "acclevel": 3,
        "globalOrderBy": 3,
        "orderBy": 3,
        "sign": -1,
        "exportDet": True,
        "isLeaf": False,
        "isPl": False,
        "isAssetDedic": False,
        "isBenefDedic": True,
    },
]
# Optional: auto-seed positions on startup (linked to SEED_BALACCS)
SEED_POSITIONS = [
    {
        "id": 5001,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2099-12-31",
        "balacc": {"resourceId": "1001"},
        "curry": "CHF",
        "amount": 150000.75,
        "timestamp": "2024-01-01T10:00:00",
    },
    {
        "id": 5002,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2099-12-31",
        "balacc": {"resourceId": "1002"},
        "curry": "EUR",
        "amount": 24000.00,
        "timestamp": "2024-01-01T10:00:00",
    },
    {
        "id": 5003,
        "bdeRecVersion": 0,
        "openDate": "2024-01-01",
        "closeDate": "2099-12-31",
        "balacc": {"resourceId": "1003"},
        "curry": "USD",
        "amount": -1200.50,
        "timestamp": "2024-01-01T10:00:00",
    },
]

# ============================================================
# POSITION SCHEMA + VALIDATOR + DB (NEW)
# ============================================================

POSITION_SCHEMA: Dict[str, Any] = {
    "$id": "https://avaloq.com/obj_position.schema.json",
    "$schema": "http://json-schema.org/draft-07/schema#",
    "$ref": "#/definitions/obj_position",
    "definitions": {
        "obj_ref": {
            "type": "object",
            "properties": {
                "resourceId": {"type": "string"},
                "id": {"type": "integer"},
            },
            "additionalProperties": True,
        },
        "obj_position": {
            "type": "object",
            "properties": {
                "id": {"type": "integer"},
                "bdeRecVersion": {"type": "integer"},
                "openDate": {"type": "string", "format": "date"},
                "closeDate": {"type": "string", "format": "date"},

                # Link to balance account
                "balacc": {"allOf": [{"$ref": "#/definitions/obj_ref"}]},

                # Amount/value lives here (not in balacc)
                "curry": {"type": "string"},
                "amount": {"type": "number"},

                "timestamp": {"type": "string", "format": "date-time"},
            },
            "required": ["id", "openDate", "closeDate", "balacc", "curry", "amount"],
            "additionalProperties": True,
        },
    },
}

_position_resolver = RefResolver.from_schema(POSITION_SCHEMA)
_position_validator = Draft7Validator(POSITION_SCHEMA, resolver=_position_resolver)

POSITION_DB: Dict[str, Dict[str, Any]] = {}


def _validate_obj_position(payload: Dict[str, Any]) -> None:
    errors = sorted(_position_validator.iter_errors(payload), key=lambda e: e.path)
    if errors:
        details = []
        for e in errors[:50]:
            loc = ".".join([str(p) for p in e.path]) or "<root>"
            details.append({"path": loc, "message": e.message})
        raise HTTPException(status_code=422, detail={"schema_errors": details})

@app.on_event("startup")
def _seed_balaccs_on_startup() -> None:
    # Seed balaccs
    for b in SEED_BALACCS:
        _validate_obj_balacc(b)
        rid = str(b.get("id", uuid.uuid4()))
        BALACC_DB.setdefault(rid, b)

    # Seed positions (must reference existing balacc resourceIds)
    for p in SEED_POSITIONS:
        _validate_obj_position(p)
        balacc_rid = (p.get("balacc") or {}).get("resourceId")
        if balacc_rid and balacc_rid in BALACC_DB:
            rid = str(p.get("id", uuid.uuid4()))
            POSITION_DB.setdefault(rid, p)


# ============================================================
# COMMON ERROR HANDLING
# ============================================================
@app.exception_handler(ValidationError)
async def jsonschema_validation_exception_handler(_: Request, exc: ValidationError):
    return JSONResponse(status_code=422, content={"detail": exc.message})


@app.get("/health")
def health():
    return {"status": "ok"}


# ============================================================
# COUNTRIES CRUD
# ============================================================
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
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="PATCH body must be an object")

    current = DB[resource_id]
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


# ============================================================
# BALACCS CRUD (NEW)
# ============================================================
@app.post("/balaccs", status_code=201)
async def create_balacc(body: Dict[str, Any]):
    _validate_obj_balacc(body)
    rid = str(uuid.uuid4())
    BALACC_DB[rid] = body
    return {"resourceId": rid, "data": body}


@app.get("/balaccs")
def list_balaccs(limit: int = 50, offset: int = 0):
    items = list(BALACC_DB.items())
    slice_ = items[offset : offset + limit]
    return {
        "total": len(BALACC_DB),
        "limit": limit,
        "offset": offset,
        "items": [{"resourceId": k, "data": v} for k, v in slice_],
    }


@app.get("/balaccs/{resource_id}")
def get_balacc(resource_id: str):
    if resource_id not in BALACC_DB:
        raise HTTPException(status_code=404, detail="Not found")
    return {"resourceId": resource_id, "data": BALACC_DB[resource_id]}


@app.put("/balaccs/{resource_id}")
async def replace_balacc(resource_id: str, body: Dict[str, Any]):
    if resource_id not in BALACC_DB:
        raise HTTPException(status_code=404, detail="Not found")
    _validate_obj_balacc(body)
    BALACC_DB[resource_id] = body
    return {"resourceId": resource_id, "data": body}


@app.patch("/balaccs/{resource_id}")
async def patch_balacc(resource_id: str, body: Dict[str, Any]):
    if resource_id not in BALACC_DB:
        raise HTTPException(status_code=404, detail="Not found")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="PATCH body must be an object")

    current = BALACC_DB[resource_id]
    merged = dict(current)
    merged.update(body)

    _validate_obj_balacc(merged)
    BALACC_DB[resource_id] = merged
    return {"resourceId": resource_id, "data": merged}


@app.delete("/balaccs/{resource_id}", status_code=204)
def delete_balacc(resource_id: str):
    if resource_id not in BALACC_DB:
        raise HTTPException(status_code=404, detail="Not found")
    del BALACC_DB[resource_id]
    return Response(status_code=204)


@app.post("/positions", status_code=201)
async def create_position(body: Dict[str, Any]):
    _validate_obj_position(body)

    # Enforce balacc reference exists
    balacc_ref = body.get("balacc") or {}
    balacc_rid = balacc_ref.get("resourceId")
    if not balacc_rid or balacc_rid not in BALACC_DB:
        raise HTTPException(status_code=400, detail="balacc.resourceId must reference an existing /balaccs resourceId")

    rid = str(uuid.uuid4())
    POSITION_DB[rid] = body
    return {"resourceId": rid, "data": body}


@app.get("/positions")
def list_positions(limit: int = 50, offset: int = 0, balaccResourceId: str | None = None):
    items = list(POSITION_DB.items())

    # Optional filter by linked balacc
    if balaccResourceId:
        items = [
            (k, v) for (k, v) in items
            if isinstance(v, dict)
            and isinstance(v.get("balacc"), dict)
            and v["balacc"].get("resourceId") == balaccResourceId
        ]

    slice_ = items[offset : offset + limit]
    return {
        "total": len(items),
        "limit": limit,
        "offset": offset,
        "items": [{"resourceId": k, "data": v} for k, v in slice_],
    }


@app.get("/positions/{resource_id}")
def get_position(resource_id: str):
    if resource_id not in POSITION_DB:
        raise HTTPException(status_code=404, detail="Not found")
    return {"resourceId": resource_id, "data": POSITION_DB[resource_id]}


@app.put("/positions/{resource_id}")
async def replace_position(resource_id: str, body: Dict[str, Any]):
    if resource_id not in POSITION_DB:
        raise HTTPException(status_code=404, detail="Not found")

    _validate_obj_position(body)
    balacc_ref = body.get("balacc") or {}
    balacc_rid = balacc_ref.get("resourceId")
    if not balacc_rid or balacc_rid not in BALACC_DB:
        raise HTTPException(status_code=400, detail="balacc.resourceId must reference an existing /balaccs resourceId")

    POSITION_DB[resource_id] = body
    return {"resourceId": resource_id, "data": body}


@app.patch("/positions/{resource_id}")
async def patch_position(resource_id: str, body: Dict[str, Any]):
    if resource_id not in POSITION_DB:
        raise HTTPException(status_code=404, detail="Not found")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="PATCH body must be an object")

    current = POSITION_DB[resource_id]
    merged = dict(current)
    merged.update(body)

    _validate_obj_position(merged)
    balacc_ref = merged.get("balacc") or {}
    balacc_rid = balacc_ref.get("resourceId")
    if not balacc_rid or balacc_rid not in BALACC_DB:
        raise HTTPException(status_code=400, detail="balacc.resourceId must reference an existing /balaccs resourceId")

    POSITION_DB[resource_id] = merged
    return {"resourceId": resource_id, "data": merged}


@app.delete("/positions/{resource_id}", status_code=204)
def delete_position(resource_id: str):
    if resource_id not in POSITION_DB:
        raise HTTPException(status_code=404, detail="Not found")
    del POSITION_DB[resource_id]

if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="127.0.0.1", port=8000, reload=True)