"""Leak-free classification utilities shared by the QuRad application notebooks.

All data-dependent preprocessing (median imputation, constant-feature removal, correlation
pruning, standardisation) is fitted inside each training fold through a scikit-learn Pipeline
and applied to the held-out fold. Nothing is fitted on the full dataset before cross-validation.
"""
import re
import os
import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import (classification_report, confusion_matrix, f1_score, precision_recall_fscore_support)
from sklearn.model_selection import GroupKFold, LeaveOneGroupOut
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
try:
    import qurad_style as st
    st.apply()
except ImportError:
    st = None

FEATURE_CLASSES = ['firstorder', 'shape2D', 'glcm', 'glrlm', 'glszm', 'ngtdm', 'gldm', 'shape']
TEXTURE_CLASSES = ['glcm', 'glrlm', 'glszm', 'ngtdm', 'gldm']
TWO_D_CLASSES = ['firstorder', 'shape2D'] + TEXTURE_CLASSES
BASIC_FEATURES = ['shape2D_PixelSurface', 'shape2D_Perimeter', 'shape2D_MajorAxisLength', 'shape2D_MinorAxisLength',
                  'shape2D_Elongation', 'shape2D_Sphericity', 'firstorder_Mean', 'firstorder_StandardDeviation',
                  'firstorder_Minimum', 'firstorder_Maximum']


def feature_columns(df, classes=TWO_D_CLASSES):
    return [c for c in df.columns if '_' in c and c.split('_')[0] in classes]


def feature_sets(df):
    """Named feature subsets used for the ablation study."""
    sets = {
        'basic morphology/intensity (10)': [c for c in BASIC_FEATURES if c in df.columns],
        'first-order only': feature_columns(df, ['firstorder']),
        'shape2D only': feature_columns(df, ['shape2D']),
        'texture only (GLCM, GLRLM, GLSZM, NGTDM, GLDM)': feature_columns(df, TEXTURE_CLASSES),
        'first-order + shape2D': feature_columns(df, ['firstorder', 'shape2D']),
        'first-order + texture (no shape)': feature_columns(df, ['firstorder'] + TEXTURE_CLASSES),
        'all 2D features (default output)': feature_columns(df, TWO_D_CLASSES),
    }
    legacy = feature_columns(df, ['shape'])
    if legacy:
        sets['all 2D + legacy 3D-named shape'] = feature_columns(df, TWO_D_CLASSES) + legacy
    return sets


class CorrelationFilter(BaseEstimator, TransformerMixin):
    """Drop constant columns, then greedily prune pairs with |Pearson r| > threshold.

    Of two correlated features, the one with the higher mean absolute correlation to all other
    features is dropped. Fitted on training data only.
    """

    def __init__(self, threshold=0.90, eps=1e-12):
        self.threshold = threshold
        self.eps = eps

    def fit(self, X, y=None):
        X = pd.DataFrame(X)
        var = X.var(axis=0, ddof=0).fillna(0.0)
        keep = [c for c in X.columns if var[c] > self.eps]
        corr = X[keep].corr().abs().fillna(0.0)
        mean_corr = corr.mean(axis=0)
        upper = corr.where(np.triu(np.ones(corr.shape), k=1).astype(bool))
        to_drop = set()
        for col in upper.columns:
            for row in upper.index[upper[col] > self.threshold]:
                if row in to_drop or col in to_drop:
                    continue
                to_drop.add(col if mean_corr[col] >= mean_corr[row] else row)
        self.keep_idx_ = [i for i, c in enumerate(X.columns) if c in keep and c not in to_drop]
        self.n_features_in_ = X.shape[1]
        return self

    def transform(self, X):
        return np.asarray(X)[:, self.keep_idx_]


def make_pipeline(seed=0, max_depth=15, n_estimators=500, threshold=0.90):
    return Pipeline([
        ('impute', SimpleImputer(strategy='median')),
        ('filter', CorrelationFilter(threshold=threshold)),
        ('scale', StandardScaler()),
        ('rf', RandomForestClassifier(n_estimators=n_estimators, max_depth=max_depth, min_samples_split=2,
                                      class_weight='balanced', random_state=seed, n_jobs=1)),
    ])


def run_cv(X, y, groups, cv, seed=0, max_depth=15, n_estimators=500, threshold=0.90):
    """Grouped cross-validation with fold-internal preprocessing. Returns per-fold and pooled results."""
    X = X.replace([np.inf, -np.inf], np.nan)
    cols = list(X.columns)
    y = np.asarray(y)
    groups = np.asarray(groups)
    classes = sorted(np.unique(y))
    oof = np.empty(len(y), dtype=object)
    folds = []
    importances = []
    kept = []
    for k, (tr, te) in enumerate(cv.split(X, y, groups)):
        pipe = make_pipeline(seed, max_depth, n_estimators, threshold)
        pipe.fit(X.iloc[tr], y[tr])
        pred = pipe.predict(X.iloc[te])
        oof[te] = pred
        keep_cols = [cols[i] for i in pipe.named_steps['filter'].keep_idx_]
        kept.append(keep_cols)
        imp = pd.Series(pipe.named_steps['rf'].feature_importances_, index=keep_cols)
        importances.append(imp)
        p, r, f, s = precision_recall_fscore_support(y[te], pred, labels=classes, zero_division=0)
        folds.append(dict(fold=k + 1, test_groups=sorted(set(groups[te])), n_train=len(tr), n_test=len(te),
                          n_features=len(keep_cols), macro_f1=f1_score(y[te], pred, average='macro'),
                          accuracy=float(np.mean(y[te] == pred)),
                          **{f'F1[{c}]': f[i] for i, c in enumerate(classes)}))
    fold_df = pd.DataFrame(folds)
    pooled_f1 = f1_score(y, oof, average='macro')
    imp_df = pd.concat(importances, axis=1).fillna(0.0)
    freq = pd.Series({c: sum(c in kc for kc in kept) for c in cols})
    summary = dict(pooled_macro_f1=pooled_f1, fold_mean=fold_df.macro_f1.mean(), fold_sd=fold_df.macro_f1.std(ddof=1),
                   n_features_mean=fold_df.n_features.mean(), n_features_min=fold_df.n_features.min(),
                   n_features_max=fold_df.n_features.max())
    return dict(oof=oof, y=y, groups=groups, classes=classes, folds=fold_df, importances=imp_df,
                selection_frequency=freq, summary=summary, kept=kept,
                report=classification_report(y, oof, labels=classes, zero_division=0, output_dict=True),
                confusion=confusion_matrix(y, oof, labels=classes))


def describe(res, title=''):
    s = res['summary']
    print(f"=== {title} ===")
    print(f"Pooled out-of-fold macro F1 = {s['pooled_macro_f1']:.3f}; fold-level macro F1 = {s['fold_mean']:.3f} +/- {s['fold_sd']:.3f} (SD over {len(res['folds'])} folds)")
    print(f"Features retained per fold: mean {s['n_features_mean']:.1f} (range {s['n_features_min']}-{s['n_features_max']})")
    print(res['folds'].round(3).to_string(index=False))
    print(classification_report(res['y'], res['oof'], labels=res['classes'], zero_division=0, digits=3))


def export_predictions(res, path, object_ids):
    pd.DataFrame({'ObjectID': object_ids, 'group': res['groups'], 'truth': res['y'],
                  'prediction': res['oof']}).to_csv(path, index=False)


def per_group_metrics(res):
    rows = []
    for g in sorted(set(res['groups'])):
        m = res['groups'] == g
        rows.append(dict(group=g, n=int(m.sum()), accuracy=float(np.mean(res['y'][m] == res['oof'][m])),
                         macro_f1=f1_score(res['y'][m], res['oof'][m], average='macro', labels=res['classes'], zero_division=0)))
    return pd.DataFrame(rows)


def ablation(df, y, groups, cv, sets, seed=0, max_depth=15, n_estimators=500):
    rows, results = [], {}
    for name, cols in sets.items():
        res = run_cv(df[cols], y, groups, cv, seed=seed, max_depth=max_depth, n_estimators=n_estimators)
        results[name] = res
        s = res['summary']
        rows.append(dict(feature_set=name, n_input=len(cols), n_kept=f"{s['n_features_min']}-{s['n_features_max']}",
                         fold_macro_f1=f"{s['fold_mean']:.3f} +/- {s['fold_sd']:.3f}", pooled_macro_f1=round(s['pooled_macro_f1'], 3)))
    return pd.DataFrame(rows), results


def top_features(res, n=10):
    imp = res['importances']
    mean_imp = imp.mean(axis=1).sort_values(ascending=False)
    freq = res['selection_frequency']
    out = pd.DataFrame({'mean_importance': mean_imp.round(4), 'folds_selected': [int(freq[f]) for f in mean_imp.index]})
    return out.head(n)


def class_profile(df, y, features):
    """Class-wise means of z-scored features (descriptive; computed on all objects)."""
    Z = (df[features] - df[features].mean()) / df[features].std(ddof=0)
    Z['class'] = np.asarray(y)
    return Z.groupby('class').mean()


def correlation_matrix_figure(df, features, path, title=None, label=None):
    import matplotlib.pyplot as plt
    order = sorted(features, key=lambda c: (FEATURE_CLASSES.index(c.split('_')[0]), c))
    corr = df[order].replace([np.inf, -np.inf], np.nan).corr().abs()
    fig, ax = plt.subplots(figsize=(3.46, 3.1))
    im = ax.imshow(corr.values, cmap='viridis', vmin=0, vmax=1, interpolation='nearest')
    bounds, prev = [], None
    for i, c in enumerate(order):
        cls = c.split('_')[0]
        if cls != prev:
            bounds.append(i); prev = cls
    bounds.append(len(order))
    for b in bounds[1:-1]:
        ax.axhline(b - 0.5, color='white', lw=0.6); ax.axvline(b - 0.5, color='white', lw=0.6)
    centers = [(bounds[i] + bounds[i + 1]) / 2 - 0.5 for i in range(len(bounds) - 1)]
    names = [order[bounds[i]].split('_')[0] for i in range(len(bounds) - 1)]
    ax.set_xticks(centers); ax.set_xticklabels(names, rotation=45, ha='right')
    ax.set_yticks(centers); ax.set_yticklabels(names)
    for sp in ax.spines.values(): sp.set_visible(True)
    cb = fig.colorbar(im, ax=ax, fraction=0.046, pad=0.04); cb.set_label('|Pearson r|')
    if label: fig.text(0.01, 0.99, label, fontsize=10, fontweight='bold', ha='left', va='top')
    fig.tight_layout(); fig.savefig(path, dpi=300, bbox_inches='tight'); fig.savefig(path.replace('.png', '.pdf'), bbox_inches='tight'); plt.close(fig)
    return corr


def correlation_block_summary(corr, threshold=0.9):
    cols = list(corr.columns)
    cls = [c.split('_')[0] for c in cols]
    rows = []
    for a in sorted(set(cls), key=FEATURE_CLASSES.index):
        for b in sorted(set(cls), key=FEATURE_CLASSES.index):
            if FEATURE_CLASSES.index(b) < FEATURE_CLASSES.index(a):
                continue
            ia = [i for i, c in enumerate(cls) if c == a]; ib = [i for i, c in enumerate(cls) if c == b]
            block = corr.values[np.ix_(ia, ib)]
            if a == b:
                vals = block[np.triu_indices_from(block, k=1)]
            else:
                vals = block.ravel()
            vals = vals[np.isfinite(vals)]
            if len(vals):
                rows.append(dict(class_a=a, class_b=b, n_pairs=len(vals), frac_r_gt_threshold=round(float(np.mean(vals > threshold)), 3), median_r=round(float(np.median(vals)), 3)))
    return pd.DataFrame(rows)


def umap_figure(df, features, labels, palette, path, seed=0, n_neighbors=30, min_dist=0.3, max_per_class=None, title=None, size=(2.5, 2.35), legend_labels=None):
    """Unsupervised UMAP of standardised, correlation-pruned features (descriptive only)."""
    import matplotlib.pyplot as plt
    from umap import UMAP
    X = df[features].replace([np.inf, -np.inf], np.nan)
    X = X.fillna(X.median())
    labels = np.asarray(labels)
    idx = np.arange(len(X))
    if max_per_class:
        rng = np.random.default_rng(seed)
        idx = np.concatenate([rng.choice(np.where(labels == c)[0], size=min(max_per_class, int((labels == c).sum())), replace=False)
                              for c in sorted(set(labels))])
    filt = CorrelationFilter(0.90).fit(X.iloc[idx])
    Xs = StandardScaler().fit_transform(filt.transform(X.iloc[idx]))
    emb = UMAP(n_neighbors=n_neighbors, min_dist=min_dist, random_state=seed).fit_transform(Xs)
    fig, ax = plt.subplots(figsize=size)
    order = np.random.default_rng(seed).permutation(len(idx))
    for c in palette:
        m = labels[idx][order] == c
        ax.scatter(emb[order][m, 0], emb[order][m, 1], s=7, alpha=0.7, color=palette[c], edgecolors='none', label=(legend_labels or {}).get(c, c), rasterized=True)
    ax.set_xlabel('UMAP 1'); ax.set_ylabel('UMAP 2'); ax.set_xticks([]); ax.set_yticks([])
    for sp in ax.spines.values(): sp.set_visible(False)
    labels = [str((legend_labels or {}).get(c, c)) for c in palette]
    ncol = 1 if max(len(t) for t in labels) > 16 else min(3, len(palette))
    ax.legend(loc='upper center', bbox_to_anchor=(0.5, -0.06), ncol=ncol, handletextpad=0.2, columnspacing=0.8, markerscale=1.8)
    fig.tight_layout(pad=0.3); fig.savefig(path, dpi=300, bbox_inches='tight', pad_inches=0.02); fig.savefig(path.replace('.png', '.pdf'), bbox_inches='tight', pad_inches=0.02); plt.close(fig)
    return emb, idx, Xs.shape[1]

def latest_runs(folder, stem_pattern):
    """Newest QuRad CSV per image in a folder of QuPath outputs.

    QuPath writes <image>_radiomics_<YYYYMMDD_HHMMSS>.csv and a matching _settings.json.
    stem_pattern is a regex whose group 1 is the image identifier (tile or slide name).
    Returns [(stem, csv_path, settings_path_or_None)], one entry per image, sorted by stem."""
    import glob, os, re
    best = {}
    for f in sorted(glob.glob(os.path.join(folder, '*_radiomics_*.csv'))):
        name = os.path.basename(f)
        m = re.match(stem_pattern, name)
        ts = re.search(r'_radiomics_(\d{8}_\d{6})\.csv$', name)
        if m and ts and (m.group(1) not in best or ts.group(1) > best[m.group(1)][0]):
            best[m.group(1)] = (ts.group(1), f)
    runs = []
    for stem in sorted(best):
        f = best[stem][1]
        settings = f[:-4] + '_settings.json'
        runs.append((stem, f, settings if os.path.exists(settings) else None))
    return runs


def describe_runs(runs):
    """Print which file, and which QuRad version, is being analysed for every image."""
    import json, os
    rows = []
    for stem, f, settings in runs:
        version = json.load(open(settings)).get('version', '?') if settings else 'no settings file'
        with open(f) as fh:
            n = sum(1 for _ in fh) - 1
        rows.append(dict(image=stem, file=os.path.basename(f), qurad_version=version, rows=n))
    table = pd.DataFrame(rows)
    print(table.to_string(index=False))
    return table
