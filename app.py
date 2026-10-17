from flask import Flask, render_template, request, jsonify, redirect, url_for, flash, Response
from pathlib import Path
import pandas as pd
import numpy as np
import io

app = Flask(__name__)
app.secret_key = "customerlens-demo-key"
ROOT = Path(__file__).resolve().parent
DATA_PATH = ROOT / "data" / "customers.csv"
df_current = None
filename_current = "customers.csv"

def load_default():
    global df_current, filename_current
    df_current = pd.read_csv(DATA_PATH)
    filename_current = "customers.csv"

def get_df():
    if df_current is None:
        load_default()
    return df_current.copy()

def clean_names(df):
    df = df.copy()
    df.columns = [str(c).strip().replace(" ", "") for c in df.columns]
    return df

def n(v):
    try:
        return round(float(v), 2) if pd.notna(v) and np.isfinite(float(v)) else None
    except (TypeError, ValueError):
        return None

def analyze(source):
    df = clean_names(source)
    numeric = list(df.select_dtypes(include=[np.number]).columns)
    stats = {}
    for col in numeric:
        s = pd.to_numeric(df[col], errors="coerce").dropna()
        if len(s):
            modes = s.mode()
            stats[col] = {"mean": n(s.mean()), "median": n(s.median()),
                          "mode": n(modes.iloc[0]) if len(modes) else None,
                          "variance": n(s.var()) if len(s)>1 else 0,
                          "std": n(s.std()) if len(s)>1 else 0,
                          "min": n(s.min()), "max": n(s.max()), "count": int(s.count())}
    categorical = {}
    for col in df.select_dtypes(exclude=[np.number]).columns:
        categorical[col] = {str(k): int(v) for k,v in df[col].fillna("Missing").value_counts().items()}
    seg_col = "Segment" if "Segment" in df.columns else ("Gender" if "Gender" in df.columns else None)
    segments = {str(k): int(v) for k,v in df[seg_col].fillna("Unknown").value_counts().items()} if seg_col else {}
    spend_bins = {}
    if "SpendingScore" in df.columns:
        s = pd.to_numeric(df["SpendingScore"], errors="coerce").dropna()
        if len(s):
            bins = pd.cut(s, [-np.inf,33,66,np.inf], labels=["Low","Medium","High"])
            spend_bins = {str(k): int(v) for k,v in bins.value_counts().reindex(["Low","Medium","High"], fill_value=0).items()}
    outliers = {}
    for col in numeric:
        s = pd.to_numeric(df[col], errors="coerce").dropna()
        if len(s) >= 4:
            q1,q3 = s.quantile(.25),s.quantile(.75); iqr=q3-q1
            outliers[col] = int(((s < q1-1.5*iqr)|(s > q3+1.5*iqr)).sum())
    corr = None
    if {"AnnualIncome","SpendingScore"}.issubset(df.columns):
        pair = df[["AnnualIncome","SpendingScore"]].apply(pd.to_numeric, errors="coerce").dropna()
        if len(pair)>1: corr=n(pair["AnnualIncome"].corr(pair["SpendingScore"]))
    missing = int(df.isna().sum().sum())
    preview = df.head(12).replace({np.nan: None}).to_dict(orient="records")
    return {"rows":int(len(df)), "columns_count":int(len(df.columns)), "columns":list(map(str,df.columns)),
            "missing":missing, "duplicates":int(df.duplicated().sum()),
            "complete_percent":round(100*(1-missing/max(len(df)*max(len(df.columns),1),1)),1),
            "numeric_columns":numeric, "stats":stats, "categorical":categorical,
            "segments":segments, "spending_bins":spend_bins, "outliers":outliers,
            "correlation":corr, "preview":preview, "filename":filename_current}

@app.route("/")
@app.route("/dashboard")
def index():
    d=get_df()
    return render_template("index.html", filename=filename_current, rows=len(d), cols=len(d.columns), page="dashboard")

@app.route("/api/analysis")
def api_analysis():
    return jsonify(analyze(get_df()))

@app.route("/api/clean", methods=["POST"])
def clean_data():
    global df_current
    df=get_df(); before_rows=len(df); before_missing=int(df.isna().sum().sum())
    df=clean_names(df).drop_duplicates()
    for col in df.columns:
        if pd.api.types.is_numeric_dtype(df[col]):
            med=df[col].median()
            if pd.notna(med): df[col]=df[col].fillna(med)
        else:
            modes=df[col].mode(dropna=True)
            df[col]=df[col].fillna(modes.iloc[0] if len(modes) else "Unknown")
    df_current=df
    result=analyze(df)
    result["cleaning"]={"rows_before":int(before_rows),"rows_after":int(len(df)),
      "missing_before":before_missing,"missing_after":int(df.isna().sum().sum()),
      "duplicates_removed":int(before_rows-len(df))}
    return jsonify(result)

@app.route("/api/reset", methods=["POST"])
def reset_data():
    load_default()
    return jsonify(analyze(get_df()))

@app.route("/upload", methods=["POST"])
def upload():
    global df_current, filename_current
    file=request.files.get("dataset")
    if not file or not file.filename:
        flash("Please choose a CSV file."); return redirect(url_for("index"))
    if Path(file.filename).suffix.lower() != ".csv":
        flash("Only CSV files are supported."); return redirect(url_for("index"))
    try:
        data=pd.read_csv(io.BytesIO(file.read()))
        if data.empty or len(data.columns)==0: raise ValueError("No usable data found.")
        if len(data)>100000 or len(data.columns)>60: raise ValueError("Upload limit: 100,000 rows and 60 columns.")
        df_current=data; filename_current=file.filename
        flash(f"Loaded {file.filename} successfully.")
    except Exception as e: flash(f"Could not load CSV: {e}")
    return redirect(url_for("index"))

@app.route("/api/export")
def export_data():
    out=io.StringIO(); get_df().to_csv(out,index=False)
    return Response(out.getvalue(), mimetype="text/csv",
      headers={"Content-Disposition":"attachment; filename=customer_behavior_data.csv"})

if __name__=="__main__":
    load_default()
    app.run(debug=True, host="127.0.0.1", port=5000)
