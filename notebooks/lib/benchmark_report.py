"""Summarise the headless benchmark timings (example_data/benchmark) into Table S3 and Figure S3."""
import glob, json, os
import numpy as np, pandas as pd
import matplotlib, sys
matplotlib.use('Agg')
import matplotlib.pyplot as plt
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qurad_style as st
st.apply()

HERE = os.path.dirname(os.path.abspath(__file__))
B = os.environ.get('QURAD_BENCHMARK', os.path.join(HERE, '..', '..', 'example_data', 'benchmark'))
RES = os.environ.get('QURAD_RESULTS', os.path.join(HERE, '..', 'results'))
OUT = os.path.join(RES, 'tables'); FIG = os.path.join(RES, 'figures'); os.makedirs(OUT, exist_ok=True); os.makedirs(FIG, exist_ok=True)

def load(name):
    return json.load(open(os.path.join(B, name + '.json')))

rows = []
for f in sorted(glob.glob(os.path.join(B, 'scale_rep*.json'))):
    j = json.load(open(f)); rows.append(dict(run=os.path.basename(f)[:-5], objects=j['processed'], seconds=j['extractSeconds'], objects_per_s=j['objectsPerSecond'], csv_s=j['csvWriteSeconds'], peak_heap_MB=j['peakHeapMB'], shape=j['enabledFeatures']['shape']))
scale = pd.DataFrame(rows).sort_values('objects'); print(scale.round(2).to_string(index=False))
cls_rows = []
for f in sorted(glob.glob(os.path.join(B, 'class_*.json'))):
    j = json.load(open(f)); cls_rows.append(dict(feature_class=os.path.basename(f)[6:-5], objects=j['processed'], ms_per_object=1000 * j['extractSeconds'] / j['processed'], objects_per_s=j['objectsPerSecond']))
cls = pd.DataFrame(cls_rows); print(cls.round(2).to_string(index=False))
sq = pd.read_csv(os.path.join(B, 'squares_timing.csv'))
sqs = sq.groupby('NumPixels')['ms'].median().reset_index(); sqs['side'] = np.sqrt(sqs.NumPixels).round().astype(int); sqs['us_per_pixel'] = 1000 * sqs.ms / sqs.NumPixels
print(sqs.round(3).to_string(index=False))
breast = load('breast'); bt = pd.read_csv(os.path.join(B, 'breast_timing.csv'))
sysinfo = dict(java=breast['javaVersion'], cpus=breast['availableProcessors'], threads=breast['threads'], qupath=breast['qupathVersion'], qurad=breast['version'])
# steady-state throughput: largest scale run; extrapolation for a WSI
big = scale[~scale['shape']].iloc[-1]
steady = big.objects_per_s
summary = dict(scale=scale.to_dict(orient='records'), classes=cls.to_dict(orient='records'), squares=sqs.to_dict(orient='records'),
               breast=dict(objects=breast['processed'], objects_per_s=breast['objectsPerSecond'], median_ms=float(bt.ms.median()), peak_heap_MB=breast['peakHeapMB']),
               steady_state_objects_per_s=float(steady), minutes_per_100k_cells=100000 / steady / 60, minutes_per_500k_cells=500000 / steady / 60, system=sysinfo)
json.dump(summary, open(os.path.join(OUT, 'benchmark_summary.json'), 'w'), indent=1, default=float)
with open(os.path.join(OUT, 'table_S3_benchmark.tex'), 'w') as fh:
    fh.write('\\begin{tabular}{lrrrrr}\n\\toprule\nRun & Objects & Extraction (s) & Objects/s & CSV (s) & Peak heap (MB) \\\\\n\\midrule\n')
    for r in scale.itertuples():
        lab = 'PUMA tile 001 $\\times$ ' + r.run.replace('scale_rep', '').replace('_withshape', ' (+ legacy shape)')
        fh.write(f'{lab} & {r.objects} & {r.seconds:.2f} & {r.objects_per_s:.0f} & {r.csv_s:.2f} & {r.peak_heap_MB:.0f} \\\\\n')
    fh.write(f"UCSB tile $\\times$ 5 & {breast['processed']} & {breast['extractSeconds']:.2f} & {breast['objectsPerSecond']:.0f} & {breast['csvWriteSeconds']:.2f} & {breast['peakHeapMB']:.0f} \\\\\n")
    fh.write('\\midrule\n\\multicolumn{6}{l}{Single feature class enabled (PUMA tile 001 $\\times$ 5): ms per nucleus} \\\\\n')
    fh.write('\\multicolumn{6}{l}{' + '; '.join(f"{r.feature_class} {r.ms_per_object:.2f}" for r in cls.itertuples()) + '} \\\\\n')
    fh.write('\\midrule\n\\multicolumn{6}{l}{Square ROIs on noise (median of 10): side, ms per ROI} \\\\\n')
    fh.write('\\multicolumn{6}{l}{' + '; '.join(f"{r.side}: {r.ms:.2f}" for r in sqs.itertuples()) + '} \\\\\n\\bottomrule\n\\end{tabular}\n')
fig, axes = plt.subplots(1, 3, figsize=(st.DOUBLE_COLUMN_IN, 2.3))
s = scale[~scale['shape']]
axes[0].plot(s.objects, s.seconds, 'o-', color=st.CLASS_COLOURS['firstorder'], ms=4); axes[0].set_xlabel('Number of nuclei'); axes[0].set_ylabel('Extraction time (s)')
ax2 = axes[0].twinx(); ax2.plot(s.objects, s.objects_per_s, 's--', color=st.CLASS_COLOURS['shape2D'], ms=4); ax2.set_ylabel('Throughput (objects per second)'); ax2.spines['right'].set_visible(True)
axes[0].plot([], [], 'o-', color=st.CLASS_COLOURS['firstorder'], ms=4, label='Extraction time'); axes[0].plot([], [], 's--', color=st.CLASS_COLOURS['shape2D'], ms=4, label='Throughput'); axes[0].legend(loc='lower right', fontsize=st.SMALL)
axes[1].loglog(sqs.NumPixels, sqs.ms, 'o-', color=st.CLASS_COLOURS['firstorder'], ms=4); axes[1].set_xlabel('ROI area (pixels)'); axes[1].set_ylabel('Time per ROI (ms)')
axes[1].xaxis.set_minor_locator(matplotlib.ticker.NullLocator()); axes[1].yaxis.set_minor_locator(matplotlib.ticker.NullLocator())
axes[2].barh(cls.feature_class, cls.ms_per_object, color=[st.CLASS_COLOURS.get(c, '#999999') for c in cls.feature_class], edgecolor='black', linewidth=0.5); axes[2].set_xlabel('Time per nucleus (ms)')
for ax, lab in zip(axes, ['(a)', '(b)', '(c)']):
    ax.text(-0.05, 1.04, lab, transform=ax.transAxes, fontsize=st.PANEL, fontweight='bold', ha='right', va='bottom')
fig.tight_layout(pad=0.4, w_pad=1.2); fig.savefig(os.path.join(FIG, 'figure_S3_benchmark.png'), dpi=st.DPI); fig.savefig(os.path.join(FIG, 'figure_S3_benchmark.pdf'))
print(json.dumps({k: v for k, v in summary.items() if k not in ('scale', 'classes', 'squares')}, indent=1, default=float))
