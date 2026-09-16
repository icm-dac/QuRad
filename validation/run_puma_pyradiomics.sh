#!/bin/bash
# Waits for the PUMA QuRad batch, then runs PyRadiomics on the exact masks/grayscale of each tile.
cd /root/QuRad
until grep -q PUMA_DONE validation/results/puma_bench.log; do sleep 10; done
P=/root/miniconda3/envs/qurad-validation/bin/python
for d in validation/results/puma/training_set_primary_roi_*/; do
  tile=$(basename $d)
  $P validation/pyradiomics_extract.py --image example_data/puma_subset/$tile.tif --gray $d/gray.png --masks $d/masks --out $d/pyradiomics_noweighting.csv --weighting no_weighting --workers 24 2>&1 | grep -v "GLCM is symm" | tail -1
done
echo PYRAD_PUMA_DONE
