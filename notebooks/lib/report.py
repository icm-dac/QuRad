"""Generate manuscript tables directly from executed notebook and validation outputs."""
import json
import re
from pathlib import Path
import sys
import pandas as pd

out = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'results').resolve()
dest = out / 'tables'
dest.mkdir(exist_ok=True)
p = json.loads((out / 'ml/puma_summary.json').read_text())
t = json.loads((out / 'ml/tiger_summary.json').read_text())

# Add reporting metadata without refitting models or changing stored scores.
from sklearn.metrics import f1_score
for summary, key, pred_name in [(p, 'folds', 'puma'), (t, 'folds_3fold', 'tiger'), (t, 'folds_lowo', 'tiger_lowo')]:
    pred = pd.read_csv(out / 'ml' / (pred_name + '_oof.csv'))
    for fold in summary[key]:
        held = pred[pred.group.isin(fold['test_groups'])]
        fold['n_classes'] = len(set(held.truth) | set(held.prediction))
        for cls in sorted(pred.truth.unique()):
            fold[f'Support[{cls}]'] = int((held.truth == cls).sum())
for summary, key, pred_name in [(p, 'per_tile', 'puma'), (t, 'per_wsi_3fold', 'tiger'), (t, 'per_wsi_lowo', 'tiger_lowo')]:
    pred = pd.read_csv(out / 'ml' / (pred_name + '_oof.csv'))
    classes = sorted(pred.truth.unique())
    if key not in summary:
        summary[key] = [dict(group=g, n=len(held), accuracy=float((held.truth == held.prediction).mean()),
                             macro_f1=f1_score(held.truth, held.prediction, labels=classes, average='macro', zero_division=0))
                        for g, held in pred.groupby('group')]
    for row in summary[key]:
        held = pred[pred.group == row['group']]
        row['n_classes'] = held.truth.nunique()
        for cls in classes:
            row[f'Support[{cls}]'] = int((held.truth == cls).sum())


def esc(s):
    return str(s).replace('_', r'\_').replace('%', r'\%').replace('&', r'\&')


def table(name, spec, headers, rows):
    text = '\\begin{tabular}{' + spec + '}\n\\toprule\n' + ' & '.join(headers) + r' \\' + '\n\\midrule\n'
    text += '\n'.join(' & '.join(row) + r' \\' for row in rows)
    text += '\n\\bottomrule\n\\end{tabular}\n'
    (dest / name).write_text(text)


rows = []
for label, summary, report in [('PUMA', p['main'], p['report']), ('TIGER', t['main_3fold'], t['report_3fold'])]:
    for cls, values in report.items():
        if cls in ['accuracy', 'weighted avg']:
            continue
        name = 'Macro average' if cls == 'macro avg' else cls.capitalize()
        rows.append([label, esc(name)] + [f'{values[k]:.2f}' for k in ['precision', 'recall', 'f1-score']])
    rows.append([label, r'Fold macro-F1', '--', '--', f"{summary['fold_mean']:.2f} $\\pm$ {summary['fold_sd']:.2f}"])
table('table_classification.tex', 'llccc', ['Dataset', 'Class', 'Precision', 'Recall', 'F1'], rows)

labels = ['Basic morphology/intensity', 'First-order only', 'Shape 2D only', 'Texture only',
          'First-order + shape 2D', 'First-order + texture', 'All non-legacy features', 'All + legacy shape']
rows = []
for label, a, b in zip(labels, p['ablation'], t['ablation']):
    def score(r):
        mean, sd = r['fold_macro_f1'].split(' +/- ')
        return f"{r['pooled_macro_f1']:.3f} [{float(mean):.2f} $\\pm$ {float(sd):.2f}]"
    rows.append([label, str(a['n_input']), score(a), score(b)])
table('table_ablation.tex', 'lrcc', ['Feature set', 'Features', 'PUMA', 'TIGER'], rows)

rows = []
for label, folds in [('PUMA', p['folds']), ('TIGER', t['folds_3fold']), ('TIGER LOWO', t['folds_lowo'])]:
    for f in folds:
        groups = ', '.join(str(int(g)) if isinstance(g, float) and g.is_integer() else str(g) for g in f['test_groups'])
        rows.append([label, str(f['fold']), groups, str(f['n_train']), str(f['n_test']), str(f['n_features']), str(f['n_classes']), f"{f['macro_f1']:.3f}"])
table('table_S4_folds.tex', 'llp{3.8cm}rrrrr', ['Dataset', 'Fold', 'Held-out tiles/slides', 'Train', 'Test', 'Kept', 'Classes', 'Macro-F1'], rows)
pd.concat([pd.DataFrame(p['folds']).assign(dataset='PUMA'), pd.DataFrame(t['folds_3fold']).assign(dataset='TIGER'),
           pd.DataFrame(t['folds_lowo']).assign(dataset='TIGER LOWO')]).to_csv(dest / 'table_S4_folds.csv', index=False)

rows = []
for label, summary in [('PUMA', p), ('TIGER', t)]:
    for feature, importance in summary['top_features']['mean_importance'].items():
        count = summary['top_features']['folds_selected'][feature]
        rows.append([label, esc(feature), f'{importance:.4f}', str(int(count))])
table('table_S5_importance.tex', 'llrr', ['Dataset', 'Feature', 'Mean importance', 'Folds retained'], rows)

rows, frames = [], []
for label, metrics in [('PUMA', p['per_tile']), ('TIGER', t['per_wsi_3fold']), ('TIGER LOWO', t['per_wsi_lowo'])]:
    frames.append(pd.DataFrame(metrics).assign(dataset=label))
    for item in metrics:
        group = item['group']
        if isinstance(group, float) and group.is_integer():
            group = int(group)
        rows.append([label, str(group), str(item['n']), str(item['n_classes']), f"{item['accuracy']:.3f}", f"{item['macro_f1']:.3f}"])
table('table_S6_per_image.tex', 'llrrrr', ['Dataset', 'Tile/slide', 'Objects', 'Classes present', 'Accuracy', 'Macro-F1'], rows)
pd.concat(frames, ignore_index=True).to_csv(dest / 'table_S6_per_image.csv', index=False)

s = pd.read_csv(dest / 'table_S2_synthetic.csv').fillna('')
rows = []
for r in s.itertuples():
    note = ('PyRadiomics refusal' if r.pyradiomics != 'ok' else r.non_exact)
    if r.non_exact:
        note = f"{len(r.non_exact.split(','))} polygon shape"
    rows.append([esc(r.case), esc(r.roi), str(r.n_pixels), str(r.n_compared), str(r.n_exact), note])
table('table_S2_synthetic.tex', 'llrrrp{4.0cm}', ['Image', 'ROI', 'Pixels', 'Compared', 'Pass', 'Difference / note'], rows)

# Export all full agreement metrics in one machine-readable supplementary table.
frames = []
for dataset in ['breast', 'puma', 'breast_default']:
    frames.append(pd.read_csv(dest / f'agreement_{dataset}_full.csv').assign(dataset=dataset))
pd.concat(frames, ignore_index=True).to_csv(dest / 'supplementary_agreement_full.csv', index=False)
mapping_tex = dest / 'table_S1_feature_agreement.tex'
mapping_tex.write_text(re.sub(r'(-?\d\.\d)e([+-])0*(\d+)', lambda m: f"${m[1]}\\times10^{{{'-' if m[2] == '-' else ''}{m[3]}}}$",
                              mapping_tex.read_text().replace('exact', 'pass').replace('discrepant', 'diff.')))
benchmark_tex = dest / 'table_S3_benchmark.tex'
benchmark_tex.write_text(benchmark_tex.read_text().replace('Peak heap (MB)', 'Heap peaks (MiB)'))
print('Generated classification, ablation, synthetic, fold and full-agreement tables from results.')
