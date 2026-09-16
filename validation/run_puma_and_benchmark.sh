#!/bin/bash
# 1) QuRad v0.4 features + masks + grayscale for the 20 PUMA tiles; 2) benchmark protocol.
set -u
cd /root/QuRad/extension
R=/root/QuRad/validation/results
G="./gradlew headless -q --console=plain"
for tif in /root/QuRad/example_data/puma_subset/training_set_primary_roi_*.tif; do
  tile=$(basename $tif .tif)
  rm -rf $R/puma/$tile; mkdir -p $R/puma/$tile
  $G -PrunnerArgs="--image $tif --objects /root/QuRad/example_data/puma_subset/${tile}_nuclei.geojson --out $R/puma/$tile/qurad.csv --shape true --masks $R/puma/$tile/masks --timing $R/puma/$tile/timing.csv --grayOut $R/puma/$tile/gray.png" 2>&1 | grep -E "QuRad headless|rror|xception" | sed "s/^/[$tile] /"
done
echo PUMA_DONE
B=$R/benchmark; rm -rf $B; mkdir -p $B
T=/root/QuRad/example_data/puma_subset/training_set_primary_roi_001
for rep in 1 2 5 10 20 40; do
  $G -PrunnerArgs="--image $T.tif --objects ${T}_nuclei.geojson --out $B/scale_rep${rep}.csv --repeat $rep --json $B/scale_rep${rep}.json" 2>&1 | grep -E "QuRad headless|rror" | sed "s/^/[scale x$rep] /"
done
$G -PrunnerArgs="--image $T.tif --objects ${T}_nuclei.geojson --out $B/scale_rep10_withshape.csv --repeat 10 --shape true --json $B/scale_rep10_withshape.json" 2>&1 | grep -E "QuRad headless|rror" | sed "s/^/[scale x10 +shape] /"
for cls in firstorder shape2D glcm glrlm glszm ngtdm gldm shape; do
  $G -PrunnerArgs="--image $T.tif --objects ${T}_nuclei.geojson --out $B/class_${cls}.csv --repeat 5 --classes $cls --json $B/class_${cls}.json" 2>&1 | grep -E "QuRad headless|rror" | sed "s/^/[class $cls] /"
done
$G -PrunnerArgs="--image /root/QuRad/validation/synthetic/bench_squares.png --objects /root/QuRad/validation/synthetic/bench_squares.geojson --out $B/squares.csv --repeat 10 --timing $B/squares_timing.csv --json $B/squares.json" 2>&1 | grep -E "QuRad headless|rror" | sed "s/^/[squares] /"
$G -PrunnerArgs="--image /root/QuRad/example_data/breast_cancer/ytma10_010704_benign1_ccd.tif --objects /root/QuRad/example_data/breast_cancer/cell_detections.geojson --out $B/breast.csv --repeat 5 --timing $B/breast_timing.csv --json $B/breast.json" 2>&1 | grep -E "QuRad headless|rror" | sed "s/^/[breast x5] /"
echo BENCH_DONE
