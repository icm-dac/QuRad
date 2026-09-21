"""Run PyRadiomics on the exact masks used by QuRad (exported by HeadlessRunner --masks).

Grayscale conversion, discretisation and angle aggregation are configured to match QuRad:
gray = floor((299 R + 587 G + 114 B) / 1000) (integer arithmetic); binWidth 25; force2D; weightingNorm='no_weighting'
(matrices summed over angles). With --weighting default the PyRadiomics default (per-angle
feature averaging) is used instead.
"""
import argparse, logging, sys, os
from pathlib import Path
from multiprocessing import Pool
import numpy as np
import pandas as pd
from PIL import Image
import SimpleITK as sitk
from radiomics import featureextractor
import radiomics

FEATURES = dict(
    firstorder=['Energy', 'TotalEnergy', 'Entropy', 'Minimum', '10Percentile', '90Percentile', 'Maximum', 'Mean', 'Median',
                'InterquartileRange', 'Range', 'MeanAbsoluteDeviation', 'RobustMeanAbsoluteDeviation', 'RootMeanSquared',
                'StandardDeviation', 'Skewness', 'Kurtosis', 'Variance', 'Uniformity'],
    shape2D=['MeshSurface', 'PixelSurface', 'Perimeter', 'PerimeterSurfaceRatio', 'Sphericity', 'SphericalDisproportion',
             'MaximumDiameter', 'MajorAxisLength', 'MinorAxisLength', 'Elongation'],
    shape=['MeshVolume', 'VoxelVolume', 'SurfaceArea', 'SurfaceVolumeRatio', 'Sphericity', 'Compactness1', 'Compactness2',
           'SphericalDisproportion', 'Maximum3DDiameter', 'Maximum2DDiameterSlice', 'Maximum2DDiameterColumn',
           'Maximum2DDiameterRow', 'MajorAxisLength', 'MinorAxisLength', 'LeastAxisLength', 'Elongation', 'Flatness'],
    glcm=[], glrlm=[], glszm=[], ngtdm=[], gldm=[])

def gray_from_rgb(img):
    a = np.asarray(img).astype(np.int64)
    if a.ndim == 2:
        return a.astype(np.float64)
    return ((299 * a[..., 0] + 587 * a[..., 1] + 114 * a[..., 2]) // 1000).astype(np.float64)

_state = {}
def _init(image_path, mask_dir, weighting, bin_width, gray_path=None, legacy_shape=True):
    logging.getLogger('radiomics').setLevel(logging.ERROR)
    if gray_path:
        _state['gray'] = np.asarray(Image.open(gray_path)).astype(np.float64)
    else:
        _state['gray'] = gray_from_rgb(Image.open(image_path).convert('RGB'))
    _state['mask_dir'] = Path(mask_dir)
    settings = dict(binWidth=bin_width, force2D=True, force2Ddimension=0, normalize=False, voxelArrayShift=0,
                    resampledPixelSpacing=None, distances=[1], symmetricalGLCM=True, padDistance=1)
    if weighting == 'no_weighting':
        settings['weightingNorm'] = 'no_weighting'
    ex = featureextractor.RadiomicsFeatureExtractor(**settings)
    ex.disableAllFeatures()
    ex.enableFeaturesByName(**{k: v for k, v in FEATURES.items() if k != 'shape' or legacy_shape})
    _state['ex'] = ex

def _one(row):
    idx, oid, x0, y0, w, h, fname = row
    gray = _state['gray']
    mask = np.asarray(Image.open(_state['mask_dir'] / fname)) > 0
    crop = gray[y0:y0 + h, x0:x0 + w]
    if crop.shape != mask.shape:
        return {'index': idx, 'ObjectID': oid, 'error': f'shape mismatch {crop.shape} vs {mask.shape}'}
    crop = np.pad(crop, 1)
    mask = np.pad(mask, 1)
    img3 = sitk.GetImageFromArray(crop[np.newaxis].astype(np.float64))
    msk3 = sitk.GetImageFromArray(mask[np.newaxis].astype(np.int32))
    try:
        res = _state['ex'].execute(img3, msk3, label=1)
        out = {k: float(v) for k, v in res.items() if not k.startswith('diagnostics')}
    except Exception as e:
        out = {'error': str(e)}
    out['index'] = idx; out['ObjectID'] = oid; out['NumPixels_pyrad'] = int(mask.sum())
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--image', required=True); ap.add_argument('--masks', required=True); ap.add_argument('--out', required=True)
    ap.add_argument('--weighting', default='no_weighting', choices=['no_weighting', 'default'])
    ap.add_argument('--binWidth', type=int, default=25); ap.add_argument('--workers', type=int, default=16)
    ap.add_argument('--gray', default=None, help='8-bit grayscale PNG exported by HeadlessRunner --grayOut (decoder-independent input)')
    ap.add_argument('--legacy-shape', choices=['true', 'false'], default='true')
    a = ap.parse_args()
    index = pd.read_csv(Path(a.masks) / 'index.csv')
    rows = [tuple(r) for r in index[['index', 'ObjectID', 'x0', 'y0', 'width', 'height', 'file']].itertuples(index=False)]
    with Pool(a.workers, initializer=_init, initargs=(a.image, a.masks, a.weighting, a.binWidth, a.gray, a.legacy_shape == 'true')) as pool:
        results = pool.map(_one, rows, chunksize=8)
    df = pd.DataFrame(results)
    df.attrs['pyradiomics_version'] = radiomics.__version__
    df.to_csv(a.out, index=False)
    n_err = df['error'].notna().sum() if 'error' in df else 0
    print(f"pyradiomics {radiomics.__version__}: {len(df)} objects, {n_err} errors -> {a.out}")

if __name__ == '__main__':
    main()
