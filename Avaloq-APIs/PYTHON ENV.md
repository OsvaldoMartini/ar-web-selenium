# PYTHON ENVIRONMENT

## Step 1 — Set Env
```bash
	python -m venv venv
```

## Step 2 — Activate
```bash
	venv\Scripts\activate
```

## Step 2 — Install all you need	
```bash
	pip install fastapi uvicorn jsonschema
```


## Step 4 — Optional (recommended):
```bash
  pip install python-multipart
```

## Step 5 — Run the API properly
```bash
  uvicorn app:app --reload
  or
  uvicorn app:app --reload --host 0.0.0.0 --port 9001
  or
  uvicorn app:app --reload --host 127.0.0.1 --port 8000
  or
  ipconfig
  and
  uvicorn app:app --reload --host <YOUR_LAN_IP> --port 8000
```
### Then open:
```bash
 http://127.0.0.1:8000/docs
```

## Step 6 — Save dependencies (good practice) -> INSIDE OF THE ENVIRONMENT
```bash
pip freeze > requirements.txt
```

### Next time you just do:
```bash	
pip install -r requirements.txt
```