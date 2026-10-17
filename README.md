# Analyzing Customer Behavior — Vercel Edition

A compact, single-page customer data analysis project. Unneeded separate pages were removed; the key dashboard, charts, descriptive statistics, CSV preview, data cleaning, upload, and export features remain.

## Included
- `app.py` — Flask backend and Pandas/NumPy analysis
- `templates/index.html` — single-page frontend
- `static/style.css` and `static/app.js` — design and interactive charts
- `data/customers.csv` — sample dataset
- `api/index.py` and `vercel.json` — Vercel deployment entry point

## Deploy
1. Extract this ZIP.
2. Upload the **contents** of this folder to the root of a GitHub repository (do not upload the ZIP itself).
3. In Vercel, choose **Add New → Project**, import the repository, and deploy with the default settings.

## Run locally
```bash
pip install -r requirements.txt
python app.py
```
Then open `http://127.0.0.1:5000`.

## Note
The included dataset is sample data. On Vercel, uploaded/cleaned datasets live in the function's temporary in-memory runtime and are not guaranteed to persist between requests or deployments. Use a database or object storage if persistent user uploads are needed.
