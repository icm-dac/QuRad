#!/bin/bash
# QuRad features + masks + grayscale for the 20 PUMA tiles, then PyRadiomics on the same masks.
cd /root/QuRad/extension
R=/root/QuRad/validation/results
for tif in /root/QuRad/example_data/puma_subset/training_set_primary_roi_*.tif; do
  tile=$(basename $tif .tif)
  rm -rf $R/puma/$tile; mkdir -p $R/puma/$tile
  ./gradlew headless -q --console=plain -PrunnerArgs="--image $tif --objects /root/QuRad/example_data/puma_subset/${tile}_nuclei.geojson --out $R/puma/$tile/qurad.csv --shape true --masks $R/puma/$tile/masks --timing $R/puma/$tile/timing.csv --grayOut $R/puma/$tile/gray.png" 2>&1 | grep -E "QuRad headless|rror|xception" | sed "s/^/[$tile] /"
  cp $R/puma/$tile/qurad.csv /root/QuRad/example_data/puma_subset/radiomics/${tile}_tif_radiomics_20260904_000000.csv
  cp $R/puma/$tile/qurad_settings.json /root/QuRad/example_data/puma_subset/radiomics/${tile}_tif_radiomics_20260904_000000_settings.json
done
echo PUMA_DONE
cd /root/QuRad
export LD_LIBRARY_PATH=/root/miniconda3/envs/qurad-validation/lib
P=/root/miniconda3/envs/qurad-validation/bin/python
for d in validation/results/puma/training_set_primary_roi_*/; do
  tile=$(basename $d)
  $P validation/pyradiomics_extract.py --image example_data/puma_subset/$tile.tif --gray $d/gray.png --masks $d/masks --out $d/pyradiomics_noweighting.csv --weighting no_weighting --workers 16 2>&1 | grep -v "GLCM is symm" | tail -1
done
$P validation/summarize_validation.py 2>&1 | grep -v Warning | tail -60
echo PUMA_CHAIN_DONE
