"""Per-feature agreement between QuRad and PyRadiomics on identical masks.

Metrics: Pearson r, Lin's concordance correlation coefficient (CCC), ICC(2,1) (two-way random,
absolute agreement, single measurement), mean/median/max absolute error, median/max relative error,
OLS slope/intercept of QuRad on PyRadiomics, and the fraction of objects agreeing to 1e-6 relative.
"""
import sys
import numpy as np
import pandas as pd

CLASSES = ['firstorder', 'shape2D', 'shape', 'glcm', 'glrlm', 'glszm', 'ngtdm', 'gldm']

def ccc(x, y):
    mx, my = x.mean(), y.mean()
    vx, vy = x.var(), y.var()
    cov = ((x - mx) * (y - my)).mean()
    d = vx + vy + (mx - my) ** 2
    return 2 * cov / d if d > 0 else (1.0 if np.allclose(x, y) else np.nan)

def icc21(x, y):
    data = np.column_stack([x, y]); n, k = data.shape
    gm = data.mean()
    msr = k * ((data.mean(1) - gm) ** 2).sum() / (n - 1)
    msc = n * ((data.mean(0) - gm) ** 2).sum() / (k - 1)
    sse = ((data - data.mean(1, keepdims=True) - data.mean(0, keepdims=True) + gm) ** 2).sum()
    mse = sse / ((n - 1) * (k - 1))
    d = msr + (k - 1) * mse + k * (msc - mse) / n
    return (msr - mse) / d if d > 0 else (1.0 if np.allclose(x, y) else np.nan)

def status(max_rel, max_abs):
    if max_rel < 1e-9 or max_abs < 1e-9: return 'exact'
    if max_rel < 1e-6: return 'near-exact (<1e-6)'
    if max_rel < 1e-3: return 'close (<1e-3)'
    return 'discrepant'

def compare(qurad_csv, pyrad_csv, key='ObjectID'):
    q = pd.read_csv(qurad_csv)
    p = pd.read_csv(pyrad_csv)
    if 'error' in p.columns:
        p = p[p['error'].isna()]
    if key not in q.columns or key not in p.columns:
        key = 'index'
        q[key] = np.arange(len(q))
    m = q.merge(p, on=key, suffixes=('', '_pyrad'))
    assert len(m) == len(p), f'pairing failed: {len(m)} of {len(p)} PyRadiomics rows matched'
    feats = [c for c in q.columns if c.split('_')[0] in CLASSES and '_' in c]
    rows = []
    for f in feats:
        pf = 'original_' + f
        if pf not in m.columns:
            rows.append(dict(feature=f, cls=f.split('_')[0], pyradiomics_name='', n=len(m)))
            continue
        x = m[pf].astype(float).values; y = m[f].astype(float).values
        ok = np.isfinite(x) & np.isfinite(y)
        x, y = x[ok], y[ok]
        err = y - x
        absr = np.abs(err)
        scale = np.abs(x)
        rel = absr / np.where(scale > 1e-12, scale, np.nan)
        rel_ok = rel[np.isfinite(rel)]
        r = np.corrcoef(x, y)[0, 1] if x.std() > 0 and y.std() > 0 else (1.0 if np.allclose(x, y) else np.nan)
        slope, intercept = (np.polyfit(x, y, 1) if x.std() > 0 else (np.nan, np.nan))
        rows.append(dict(feature=f, cls=f.split('_')[0], pyradiomics_name=pf, n=int(ok.sum()),
                         pearson_r=r, ccc=ccc(x, y), icc=icc21(x, y),
                         mae=absr.mean(), median_abs_err=np.median(absr), max_abs_err=absr.max(),
                         mean_difference=err.mean(), sd_difference=err.std(ddof=1),
                         loa_lower=err.mean() - 1.96 * err.std(ddof=1),
                         loa_upper=err.mean() + 1.96 * err.std(ddof=1),
                         median_rel_err=np.median(rel_ok) if len(rel_ok) else np.nan,
                         max_rel_err=rel_ok.max() if len(rel_ok) else np.nan,
                         frac_within_1e6=float(np.mean(absr <= 1e-6 * np.maximum(1.0, scale))),
                         frac_within_1e3=float(np.mean(absr <= 1e-3 * np.maximum(1.0, scale))),
                         slope=slope, intercept=intercept, mean_pyrad=x.mean(), sd_pyrad=x.std(),
                         status=status(rel_ok.max() if len(rel_ok) else absr.max(), absr.max())))
    return pd.DataFrame(rows), m

def summarize(df, label=''):
    c = df[df.pyradiomics_name != '']
    print(f"{label}: {len(c)} comparable features of {len(df)}")
    print(c['status'].value_counts().to_string())
    print(f"  Pearson r: median {c.pearson_r.median():.4f}, min {c.pearson_r.min():.4f}, n(r>0.95)={int((c.pearson_r>0.95).sum())}")
    print(f"  CCC: median {c.ccc.median():.4f}, min {c.ccc.min():.4f}, n(CCC>0.99)={int((c.ccc>0.99).sum())}, n(CCC>0.95)={int((c.ccc>0.95).sum())}")
    print(f"  ICC(2,1): median {c.icc.median():.4f}, min {c.icc.min():.4f}")
    print(f"  median rel err: median {c.median_rel_err.median():.2e}; max rel err: median {c.max_rel_err.median():.2e}")
    print("  discrepant/close features:")
    print(c[c.status.isin(['discrepant', 'close (<1e-3)'])][['feature', 'pearson_r', 'ccc', 'median_rel_err', 'max_rel_err', 'slope', 'intercept']].round(4).to_string(index=False))

if __name__ == '__main__':
    df, _ = compare(sys.argv[1], sys.argv[2])
    if len(sys.argv) > 3:
        df.to_csv(sys.argv[3], index=False)
    summarize(df, sys.argv[1])
