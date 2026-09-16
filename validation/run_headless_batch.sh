#!/bin/bash
# Runs the QuRad headless calculator on the synthetic cases and the PUMA tiles (masks exported for PyRadiomics).
set -u
cd /root/QuRad/extension
R=/root/QuRad/validation/results
for png in /root/QuRad/validation/synthetic/*.png; do
  case=$(basename $png .png)
  mkdir -p $R/synthetic/$case
  ./gradlew headless -q --console=plain -PrunnerArgs="--image $png --objects /root/QuRad/validation/synthetic/$case.geojson --out $R/synthetic/$case/qurad.csv --shape true --masks $R/synthetic/$case/masks --grayOut $R/synthetic/$case/gray.png" 2>&1 | grep -E "QuRad headless|rror|xception" | sed "s/^/[$case] /"
done
for tif in /root/QuRad/example_data/puma_subset/training_set_primary_roi_*.tif; do
  tile=$(basename $tif .tif)
  mkdir -p $R/puma/$tile
  ./gradlew headless -q --console=plain -PrunnerArgs="--image $tif --objects /root/QuRad/example_data/puma_subset/${tile}_nuclei.geojson --out $R/puma/$tile/qurad.csv --shape true --masks $R/puma/$tile/masks --timing $R/puma/$tile/timing.csv --grayOut $R/puma/$tile/gray.png" 2>&1 | grep -E "QuRad headless|rror|xception" | sed "s/^/[$tile] /"
done
echo BATCH_DONE
