"""Aggregate the QuRad-vs-PyRadiomics comparison into supplementary tables and figures.

Inputs: example_data/{breast_cancer,puma_subset,synthetic}/pyradiomics/ produced by HeadlessRunner and pyradiomics_extract.py.
Outputs (notebooks/results/tables and figures): table_S1_feature_agreement.csv/.tex, table_S2_synthetic.csv, figure_S1_agreement.png,
figure_S2_bland_altman.png, validation_summary.json.
"""
import glob, json, os, sys
import numpy as np, pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agreement import compare, CLASSES
import qurad_style as st
st.apply()

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.environ.get('QURAD_DATA', os.path.join(HERE, '..', '..', 'example_data'))
RES = os.environ.get('QURAD_RESULTS', os.path.join(HERE, '..', 'results'))
OUT = os.path.join(RES, 'tables'); FIG = os.path.join(RES, 'figures'); os.makedirs(OUT, exist_ok=True); os.makedirs(FIG, exist_ok=True)

NOTES = {
    'gldm_DependencePercentage': 'removed in 0.4 (always 1)',
}
LEGACY_NOTE = ('2D quantity under PyRadiomics 3D name; PyRadiomics evaluates a one-voxel-thick volume, so values are not expected to agree')

def load_pair(qurad_csv, pyrad_csv):
    q = pd.read_csv(qurad_csv); p = pd.read_csv(pyrad_csv)
    q['index'] = np.arange(len(q))
    return q, p

def pooled_puma():
    qs, ps = [], []
    for d in sorted(glob.glob(os.path.join(DATA, 'puma_subset', 'pyradiomics', 'training_set_primary_roi_*'))):
        if not os.path.exists(os.path.join(d, 'pyradiomics_noweighting.csv')):
            continue
        q, p = load_pair(os.path.join(d, 'qurad.csv'), os.path.join(d, 'pyradiomics_noweighting.csv'))
        tile = os.path.basename(d)
        q['ObjectID'] = tile + ':' + q['ObjectID'].astype(str); p['ObjectID'] = tile + ':' + p['ObjectID'].astype(str)
        qs.append(q); ps.append(p)
    q = pd.concat(qs, ignore_index=True); p = pd.concat(ps, ignore_index=True)
    if 'error' in p:
        n_err = int(p['error'].notna().sum()); p = p[p['error'].isna()]
    else:
        n_err = 0
    q.to_csv(os.path.join(OUT, 'puma_qurad_pooled.csv'), index=False); p.to_csv(os.path.join(OUT, 'puma_pyradiomics_pooled.csv'), index=False)
    return os.path.join(OUT, 'puma_qurad_pooled.csv'), os.path.join(OUT, 'puma_pyradiomics_pooled.csv'), n_err, len(qs)

def main():
    breast_df, breast_m = compare(os.path.join(DATA, 'breast_cancer', 'pyradiomics', 'qurad.csv'), os.path.join(DATA, 'breast_cancer', 'pyradiomics', 'pyradiomics_noweighting.csv'))
    breast_def, _ = compare(os.path.join(DATA, 'breast_cancer', 'pyradiomics', 'qurad.csv'), os.path.join(DATA, 'breast_cancer', 'pyradiomics', 'pyradiomics_default.csv'))
    pq, pp, n_err, n_tiles = pooled_puma()
    puma_df, puma_m = compare(pq, pp)
    for label, frame in [('breast', breast_df), ('breast_default', breast_def), ('puma', puma_df)]:
        frame.to_csv(os.path.join(OUT, f'agreement_{label}_full.csv'), index=False)

    # ---- Table S1: per-feature mapping + agreement (breast + PUMA) ----
    rows = []
    for _, r in breast_df.iterrows():
        f = r.feature; cls = r.cls
        pr = puma_df[puma_df.feature == f].iloc[0] if (puma_df.feature == f).any() else None
        legacy = cls == 'shape'
        if legacy:
            equiv = 'legacy'; note = LEGACY_NOTE
        else:
            equiv = 'direct'; note = ''
        if f in ('shape2D_MeshSurface', 'shape2D_Perimeter', 'shape2D_PerimeterSurfaceRatio', 'shape2D_Sphericity', 'shape2D_SphericalDisproportion', 'shape2D_MaximumDiameter'):
            note = 'computed from the ROI polygon; PyRadiomics uses a marching-squares mesh of the raster (identical for raster-derived polygons)'
        if f in ('shape2D_MajorAxisLength', 'shape2D_MinorAxisLength', 'shape2D_Elongation', 'shape2D_PixelSurface'):
            note = 'computed from the pixel mask (identical definition)'
        if f == 'firstorder_StandardDeviation' or f.endswith('SphericalDisproportion') or 'Compactness' in f:
            note = (note + '; ' if note else '') + 'optional in PyRadiomics (must be enabled explicitly)'
        rows.append(dict(feature=f, cls=cls, pyradiomics=r.pyradiomics_name if r.pyradiomics_name else '-', mapping=equiv,
                         dimensionality='2D' if not legacy else '2D under 3D name', default_on='no' if legacy else 'yes',
                         breast_n=int(r.n), breast_r=r.pearson_r, breast_ccc=r.ccc, breast_icc=r.icc, breast_max_rel_err=r.max_rel_err, breast_median_rel_err=r.median_rel_err, breast_status=r.status,
                         puma_n=int(pr.n) if pr is not None else 0, puma_r=pr.pearson_r if pr is not None else np.nan, puma_ccc=pr.ccc if pr is not None else np.nan,
                         puma_icc=pr.icc if pr is not None else np.nan, puma_max_rel_err=pr.max_rel_err if pr is not None else np.nan, puma_median_rel_err=pr.median_rel_err if pr is not None else np.nan,
                         puma_status=pr.status if pr is not None else '', default_agg_max_rel_err=float(breast_def[breast_def.feature == f].max_rel_err.iloc[0]), notes=note))
    s1 = pd.DataFrame(rows)
    s1.to_csv(os.path.join(OUT, 'table_S1_feature_agreement.csv'), index=False)

    def fmt(v, prec=3):
        if pd.isna(v): return 'n/a'
        if abs(v) < 1e-12: return '0'
        if abs(v) < 1e-3: return f'{v:.1e}'
        return f'{v:.{prec}f}'
    lines = []
    for _, r in s1.iterrows():
        pyname = r.pyradiomics.replace('original_', '') if r.pyradiomics != '-' else '--'
        lines.append(' & '.join([r.feature.replace('_', '\\_'), r.default_on, fmt(r.breast_r, 4), fmt(r.breast_ccc, 4), fmt(r.breast_max_rel_err), fmt(r.puma_ccc, 4), fmt(r.puma_max_rel_err), r.breast_status.split(' ')[0] + ' / ' + (r.puma_status.split(' ')[0] if r.puma_status else 'n/a')]) + ' \\\\')
    with open(os.path.join(OUT, 'table_S1_feature_agreement.tex'), 'w') as fh:
        fh.write('\n'.join(lines))

    # ---- headline numbers ----
    def headline(df, label):
        c = df[(df.cls != 'shape') & (df.pyradiomics_name != '')]
        return dict(label=label, n_compared=int(len(c)), n_exact=int((c.status == 'exact').sum()),
                    n_near_exact=int(c.status.str.startswith('near-exact').sum()), n_close=int(c.status.str.startswith('close').sum()),
                    n_discrepant=int((c.status == 'discrepant').sum()),
                    median_r=float(c.pearson_r.median()), min_r=float(c.pearson_r.min()), n_r_gt_0_95=int((c.pearson_r > 0.95).sum()),
                    median_ccc=float(c.ccc.median()), min_ccc=float(c.ccc.min()), n_ccc_gt_0_99=int((c.ccc > 0.99).sum()),
                    median_icc=float(c.icc.median()), min_icc=float(c.icc.min()),
                    max_rel_err_overall=float(c.max_rel_err.max()), median_of_max_rel_err=float(c.max_rel_err.median()),
                    max_abs_err_overall=float(c.max_abs_err.max()),
                    features_max_rel_err=c.sort_values('max_rel_err', ascending=False)[['feature', 'max_rel_err']].head(5).to_dict(orient='records'))
    legacy = breast_df[breast_df.cls == 'shape']
    legacy_summary = dict(n=int(len(legacy)), n_exact=int((legacy.status == 'exact').sum()), exact_features=legacy[legacy.status == 'exact'].feature.tolist(),
                          discrepant_features=legacy[legacy.status != 'exact'][['feature', 'pearson_r', 'ccc']].round(3).to_dict(orient='records'))
    cdef = breast_def[(breast_def.cls != 'shape')]
    default_agg = dict(n_exact=int((cdef.status == 'exact').sum()), n_discrepant=int((cdef.status == 'discrepant').sum()),
                       classes_affected=sorted(cdef[cdef.status == 'discrepant'].cls.unique().tolist()),
                       median_rel_err_glcm=float(cdef[cdef.cls == 'glcm'].median_rel_err.median()), max_rel_err_glcm=float(cdef[cdef.cls == 'glcm'].max_rel_err.max()),
                       median_rel_err_glrlm=float(cdef[cdef.cls == 'glrlm'].median_rel_err.median()), max_rel_err_glrlm=float(cdef[cdef.cls == 'glrlm'].max_rel_err.max()),
                       worst=cdef.sort_values('max_rel_err', ascending=False)[['feature', 'median_rel_err', 'max_rel_err', 'ccc']].head(8).round(4).to_dict(orient='records'))
    summary = dict(breast=headline(breast_df, 'UCSB breast benchmark tile'), puma=headline(puma_df, f'PUMA melanoma tiles'),
                   puma_n_tiles=n_tiles, puma_n_objects=int(puma_df.n.max()), puma_n_extracted=len(pd.read_csv(pq)), puma_pyradiomics_refused=n_err,
                   breast_n_objects=int(breast_df.n.max()), legacy_shape=legacy_summary, pyradiomics_default_aggregation=default_agg)

    # ---- Table S2: synthetic cases ----
    rows = []
    for d in sorted(glob.glob(os.path.join(DATA, 'synthetic', 'pyradiomics', '*/'))):
        case = os.path.basename(d.rstrip('/'))
        q = pd.read_csv(d + 'qurad.csv'); p = pd.read_csv(d + 'pyradiomics_noweighting.csv')
        gj = json.load(open(os.path.join(DATA, 'synthetic', f'{case}.geojson')))
        m = q.merge(p, on='ObjectID')
        feats = [c for c in q.columns if c.split('_')[0] in CLASSES and 'original_' + c in m.columns and not c.startswith('shape_')]
        for _, r in m.iterrows():
            name = gj['features'][int(r['index'])]['properties']['name']
            if isinstance(r.get('error'), str):
                rows.append(dict(case=case, roi=name, n_pixels=int(r['NumPixels']), n_compared=0, n_exact=0, pyradiomics='refused: ' + r['error'][:70], non_exact='')); continue
            x = np.array([r['original_' + f] for f in feats], float); y = np.array([r[f] for f in feats], float)
            ok = np.isfinite(x) & np.isfinite(y)
            exact = (np.abs(y - x) <= 1e-9 * np.maximum(1, np.abs(x))) & ok
            rows.append(dict(case=case, roi=name, n_pixels=int(r['NumPixels']), n_compared=int(ok.sum()), n_exact=int(exact.sum()), pyradiomics='ok',
                             non_exact=', '.join(f.replace('shape2D_', '') for f, e, o in zip(feats, exact, ok) if o and not e)))
    s2 = pd.DataFrame(rows); s2.to_csv(os.path.join(OUT, 'table_S2_synthetic.csv'), index=False)
    summary['synthetic'] = dict(n_rois=int(len(s2)), n_compared_rois=int((s2.pyradiomics == 'ok').sum()),
                                n_rois_all_intensity_texture_exact=int(((s2.pyradiomics == 'ok') & s2.non_exact.apply(lambda t: all(x in ('MeshSurface', 'Perimeter', 'PerimeterSurfaceRatio', 'Sphericity', 'SphericalDisproportion', 'MaximumDiameter') for x in [s.strip() for s in t.split(',') if s.strip()]))).sum()),
                                refused=s2[s2.pyradiomics != 'ok'][['case', 'roi', 'n_pixels', 'pyradiomics']].to_dict(orient='records'))

    # ---- Figure S1: max relative error per feature (breast + PUMA) ----
    fig, axes = plt.subplots(1, 2, figsize=(st.DOUBLE_COLUMN_IN, 2.7), sharey=True)
    for ax, (df, lab) in zip(axes, [(breast_df, '(a)'), (puma_df, '(b)')]):
        c = df[(df.cls != 'shape')].copy()
        c['err'] = np.clip(c.max_rel_err.fillna(0), 1e-17, None)
        for cls, g in c.groupby('cls', sort=False):
            ax.scatter(np.arange(len(c))[c.cls == cls], g.err, s=9, color=st.CLASS_COLOURS[cls], label=f'{cls} ({len(g)})', edgecolors='none')
        ax.set_yscale('log'); ax.axhline(1e-9, ls='--', color='grey', lw=0.6); ax.set_ylim(1e-17, 10)
        ax.set_xlabel('Feature (ordered by class)'); ax.set_yticks([1e-16, 1e-12, 1e-8, 1e-4, 1]); ax.yaxis.set_minor_locator(matplotlib.ticker.NullLocator())
        ax.text(-0.02, 1.02, lab, transform=ax.transAxes, fontsize=st.PANEL, fontweight='bold', ha='right', va='bottom')
    axes[0].set_ylabel('Maximum relative error over objects')
    axes[1].legend(fontsize=st.SMALL, ncol=2, loc='upper left', handletextpad=0.2, columnspacing=0.8)
    fig.tight_layout(pad=0.4); fig.savefig(os.path.join(FIG, 'figure_S1_agreement.png'), dpi=st.DPI); fig.savefig(os.path.join(FIG, 'figure_S1_agreement.pdf')); plt.close(fig)

    # ---- Figure S2: scatter + Bland-Altman for representative features (breast) ----
    reps = ['firstorder_Mean', 'firstorder_Entropy', 'shape2D_Sphericity', 'glcm_Contrast', 'glcm_Imc2', 'glrlm_RunEntropy', 'glszm_ZoneEntropy', 'ngtdm_Busyness', 'gldm_DependenceVariance']
    fig, axes = plt.subplots(3, 6, figsize=(10.5, 7.5))
    for k, f in enumerate(reps):
        r, cidx = divmod(k, 3)
        x = breast_m['original_' + f].values; y = breast_m[f].values
        ax = axes[r, 2 * cidx]; ax.scatter(x, y, s=5, alpha=0.6, color=st.CLASS_COLOURS[f.split('_')[0]], edgecolors='none')
        lo, hi = np.nanmin(x), np.nanmax(x); ax.plot([lo, hi], [lo, hi], color='black', lw=0.6, ls='--')
        ax.set_xlabel('PyRadiomics', fontsize=st.SMALL); ax.set_ylabel('QuRad', fontsize=st.SMALL); ax.tick_params(labelsize=6)
        ax.set_title(f.replace('_', '\n', 1) + f"\nCCC = {breast_df[breast_df.feature == f].ccc.iloc[0]:.4f}", fontsize=6, loc='left', pad=2)
        ax = axes[r, 2 * cidx + 1]; d = y - x; mean = (x + y) / 2
        ax.scatter(mean, d, s=5, alpha=0.6, color=st.CLASS_COLOURS[f.split('_')[0]], edgecolors='none')
        ax.axhline(np.mean(d), color='black', lw=0.6); ax.axhline(np.mean(d) + 1.96 * np.std(d), color='grey', ls='--', lw=0.6); ax.axhline(np.mean(d) - 1.96 * np.std(d), color='grey', ls='--', lw=0.6)
        ax.set_xlabel('Mean of methods', fontsize=st.SMALL); ax.set_ylabel('QuRad − PyRadiomics', fontsize=st.SMALL); ax.tick_params(labelsize=6)
        def sci(v):
            if v == 0: return '0'
            e = int(np.floor(np.log10(abs(v)))); m = v / 10 ** e
            return f"${m:.1f}\\times10^{{{e}}}$"
        ax.set_title(f"bias = {sci(np.mean(d))}\nSD = {sci(np.std(d))}", fontsize=6, loc='left', pad=2)
        fmt = matplotlib.ticker.ScalarFormatter(useMathText=True); fmt.set_powerlimits((-2, 2)); ax.yaxis.set_major_formatter(fmt); ax.yaxis.get_offset_text().set_fontsize(6)
    fig.tight_layout(pad=1.2, w_pad=1.8, h_pad=2.0)
    for ext in ('png', 'pdf'):
        fig.savefig(os.path.join(FIG, 'figure_S2_bland_altman.' + ext), dpi=st.DPI, bbox_inches='tight', pad_inches=0.15)
    plt.close(fig)

    json.dump(summary, open(os.path.join(OUT, 'validation_summary.json'), 'w'), indent=1, default=float)
    print(json.dumps({k: v for k, v in summary.items() if k in ('breast', 'puma', 'puma_n_tiles', 'puma_n_objects', 'puma_pyradiomics_refused', 'synthetic')}, indent=1, default=float))
    print('legacy:', json.dumps(legacy_summary, default=float)[:600])
    print('default aggregation:', json.dumps(default_agg, default=float)[:900])

if __name__ == '__main__':
    main()
