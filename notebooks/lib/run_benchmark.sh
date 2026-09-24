#!/bin/bash
# Single-threaded timing benchmark of the QuRad calculator (article Section 2.5). Run on an idle machine.
# Writes example_data/benchmark/; summarise with notebooks/lib/benchmark_report.py.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
B=${QURAD_BENCHMARK:-$ROOT/example_data/benchmark}; mkdir -p "$B"
T=$ROOT/example_data/puma_subset/training_set_primary_roi_001
S=$ROOT/example_data/synthetic
cd "$ROOT/extension"
run() { local tag=$1; shift; ./gradlew headless -q --console=plain -PrunnerArgs="$*" 2>&1 | grep -E "QuRad headless|rror" | sed "s/^/[$tag] /"; }
for rep in 1 2 5 10 20 40; do
  run "scale x$rep" --image $T.tif --objects ${T}_nuclei.geojson --out $B/scale_rep$rep.csv --repeat $rep --json $B/scale_rep$rep.json
done
run "x10 +shape" --image $T.tif --objects ${T}_nuclei.geojson --out $B/scale_rep10_withshape.csv --repeat 10 --shape true --json $B/scale_rep10_withshape.json
for cls in firstorder shape2D glcm glrlm glszm ngtdm gldm shape; do
  run "class $cls" --image $T.tif --objects ${T}_nuclei.geojson --out $B/class_$cls.csv --repeat 5 --classes $cls --json $B/class_$cls.json
done
run squares --image $S/bench_squares.png --objects $S/bench_squares.geojson --out $B/squares.csv --repeat 10 --timing $B/squares_timing.csv --json $B/squares.json
run "breast x5" --image $ROOT/example_data/breast_cancer/ytma10_010704_benign1_ccd.tif --objects $ROOT/example_data/breast_cancer/cell_detections.geojson --out $B/breast.csv --repeat 5 --timing $B/breast_timing.csv --json $B/breast.json
rm -f $B/scale_rep*.csv $B/class_*.csv $B/squares.csv $B/breast.csv
