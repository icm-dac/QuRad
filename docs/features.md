# Feature Reference

QuRad implements **119 radiomic features** in eight classes. **103 features are enabled by default**; they are all
two-dimensional quantities with a direct [PyRadiomics](https://pyradiomics.readthedocs.io/en/latest/features.html)
equivalent. The remaining **16 features of the `shape` class are optional** (off by default): they are 2D quantities
reported under PyRadiomics' 3D shape names for backwards compatibility and are *not* equivalent to PyRadiomics' 3D shape
features (see [below](#legacy-3d-named-shape-features-16-optional)).

| Class | Features | Default | PyRadiomics equivalent |
|-------|----------|---------|------------------------|
| `firstorder` | 19 | on | 19/19 (`StandardDeviation` is optional in PyRadiomics) |
| `shape2D` | 10 | on | 10/10 (`SphericalDisproportion` is optional in PyRadiomics) |
| `glcm` | 23 | on | 23/23 (PyRadiomics' `MCC` is not implemented) |
| `glrlm` | 16 | on | 16/16 |
| `glszm` | 16 | on | 16/16 |
| `ngtdm` | 5 | on | 5/5 |
| `gldm` | 14 | on | 14/14 (PyRadiomics' deprecated `DependencePercentage`, always 1, was removed in QuRad 0.4) |
| `shape` (legacy) | 16 | **off** | same names, different definitions |

## Naming and mapping to PyRadiomics

Column names follow `class_FeatureName`, e.g. `firstorder_Entropy`, `glcm_JointEnergy`. The corresponding PyRadiomics
name is obtained by prefixing `original_` (`original_glcm_JointEnergy`), so exported CSVs can be mapped
programmatically. Per-feature agreement with PyRadiomics 3.0.1 (Pearson r, concordance correlation coefficient, maximum
relative error) is tabulated in the validation notebook (`notebooks/validation.ipynb`) and in the article's
Supplementary Table S1.

## Conventions that determine the values

These conventions are fixed in the code and recorded in the `*_settings.json` file written next to every CSV.

| Aspect | QuRad convention | PyRadiomics setting reproducing it |
|--------|------------------|------------------------------------|
| Input | 8-bit RGB brightfield image, read at full resolution (downsample 1). Non-RGB images are refused. | – |
| Grayscale | `gray = floor((299 R + 587 G + 114 B) / 1000)` (integer arithmetic, values 0–255) | same conversion applied before extraction |
| Pixel mask | a pixel belongs to the ROI if its **centre** lies inside the ROI polygon (Java2D fill, pure geometry, no anti-aliasing); pixels outside the image are ignored | label mask |
| Discretisation | fixed bin width (default 25); bin edges aligned to multiples of the bin width from 0; bin index = floor((v − lowBound)/binWidth) + 1 | `binWidth=25`, `normalize=False`, `voxelArrayShift=0` |
| GLCM | symmetric matrix, distance 1 (configurable), four directions (0°, 45°, 90°, 135°), matrices **summed over directions** before features are computed | `force2D=True`, `distances=[1]`, `symmetricalGLCM=True`, `weightingNorm='no_weighting'` |
| GLRLM | runs along the same four directions, matrices summed | `weightingNorm='no_weighting'` |
| GLSZM | zones are 8-connected | default |
| NGTDM, GLDM | 8-neighbourhood, distance 1; GLDM coarseness parameter α = 0 | default |
| Percentiles, median | linear interpolation (NumPy default) | default |

!!! note "Angle aggregation"
    PyRadiomics' *default* computes GLCM and GLRLM features for each direction separately and averages the feature
    values. QuRad sums the matrices first, which corresponds to PyRadiomics with `weightingNorm='no_weighting'`. On the
    validation data the two conventions differ by a median relative error of about 0.5 % (GLCM) and 3 % (GLRLM), with
    maximum differences of up to 50 % for run-length emphasis features of individual small nuclei.

CSV headers include the union of feature names over all processed objects. If a feature is undefined for one object, its cell is blank; that object does not suppress valid columns for later objects.

### Edge cases and undefined values

| Situation | QuRad behaviour |
|-----------|-----------------|
| ROI polygon contains no pixel centre (e.g. degenerate sliver) | object skipped and counted as skipped; no row is written |
| ROI extends beyond the image | pixel-based features use the pixels inside the image; polygon-based shape features (`MeshSurface`, `Perimeter`, `MaximumDiameter`, sphericity) describe the polygon as drawn; `NumPixels` reports the pixels actually used |
| Flat region (single gray level) | `firstorder_Entropy = 0`, `Uniformity = 1`, `Skewness = Kurtosis = 0`, `glcm_Correlation = 1`, `glcm_Imc1 = Imc2 = 0`, `ngtdm_Coarseness = 10^6`, `ngtdm_Contrast = Busyness = Strength = 0` (all as in PyRadiomics) |
| Zero denominator elsewhere (perimeter 0, no run/zone, …) | feature is 0 |
| `glcm_Imc2` with HXY2 < HXY (rounding) | 0 (as in PyRadiomics) |
| Very small ROIs | QuRad computes all features from a single pixel upwards; PyRadiomics refuses masks with fewer than 2 pixels in any dimension. Texture matrices of ROIs with fewer than ~10 pixels are degenerate; use the `NumPixels` column to filter. |

## First-order features (19)

Computed on the gray values `x` of the `N` pixels in the ROI. Histogram-based features use the discretised values.

| Feature | Definition |
|---------|------------|
| `firstorder_Energy` | Σ x² |
| `firstorder_TotalEnergy` | identical to Energy (pixel area = 1) |
| `firstorder_Entropy` | −Σ p(i) log₂ p(i) over histogram bins |
| `firstorder_Minimum`, `Maximum`, `Range` | min, max, max − min |
| `firstorder_10Percentile`, `90Percentile`, `Median` | percentiles with linear interpolation |
| `firstorder_InterquartileRange` | P75 − P25 |
| `firstorder_Mean` | Σ x / N |
| `firstorder_MeanAbsoluteDeviation` | Σ \|x − mean\| / N |
| `firstorder_RobustMeanAbsoluteDeviation` | mean absolute deviation of the values within [P10, P90] |
| `firstorder_RootMeanSquared` | √(Σ x² / N) |
| `firstorder_Variance` | Σ (x − mean)² / N (population variance) |
| `firstorder_StandardDeviation` | √Variance |
| `firstorder_Skewness` | m₃ / m₂^1.5 (0 for flat regions) |
| `firstorder_Kurtosis` | m₄ / m₂² (not excess kurtosis; 0 for flat regions) |
| `firstorder_Uniformity` | Σ p(i)² |

## Shape 2D features (10)

`A` is the polygon area, `P` the polygon perimeter, `N` the number of pixels in the mask. Axis lengths use the
principal components of the pixel-centre coordinates (population covariance), as in PyRadiomics.

| Feature | Definition |
|---------|------------|
| `shape2D_MeshSurface` | polygon area A (PyRadiomics: marching-squares mesh area; identical for polygons traced from a raster) |
| `shape2D_PixelSurface` | number of pixels N |
| `shape2D_Perimeter` | polygon perimeter P |
| `shape2D_PerimeterSurfaceRatio` | P / A |
| `shape2D_Sphericity` | 2√(πA) / P |
| `shape2D_SphericalDisproportion` | P / (2√(πA)) |
| `shape2D_MaximumDiameter` | largest distance between two polygon vertices (computed on the convex hull) |
| `shape2D_MajorAxisLength` | 4√λ_major |
| `shape2D_MinorAxisLength` | 4√λ_minor |
| `shape2D_Elongation` | √(λ_minor / λ_major) |

!!! info "Polygon versus mesh"
    QuRad measures the ROI polygon as drawn in QuPath. PyRadiomics reconstructs a marching-squares mesh from the
    rasterised mask, which cuts the corners of a pixel outline. For polygons that were themselves traced from a raster
    (e.g. label masks converted with marching squares, as in the validation benchmark) both agree exactly; for
    smooth or hand-drawn polygons the mesh perimeter is typically a few per cent longer and the mesh area slightly
    smaller (Supplementary Table S2 of the article quantifies this on synthetic shapes).

## GLCM features (23)

Gray-level co-occurrence matrix `p(i,j)` (symmetric, summed over four directions, normalised to sum 1), with marginals
`p_x`, `p_y`, means `μ_x`, `μ_y`, standard deviations `σ_x`, `σ_y`, `N_g` = highest gray level present,
`p_{x+y}(k)`, `p_{x−y}(k)`, entropies `HX`, `HY`, `HXY`, `HXY1`, `HXY2` (all base 2).

| Feature | Definition |
|---------|------------|
| `glcm_Autocorrelation` | Σ i·j·p(i,j) |
| `glcm_JointAverage` | Σ i·p(i,j) |
| `glcm_ClusterProminence` | Σ (i + j − μ_x − μ_y)⁴ p(i,j) |
| `glcm_ClusterShade` | Σ (i + j − μ_x − μ_y)³ p(i,j) |
| `glcm_ClusterTendency` | Σ (i + j − μ_x − μ_y)² p(i,j) |
| `glcm_Contrast` | Σ (i − j)² p(i,j) |
| `glcm_Correlation` | Σ (i − μ_x)(j − μ_y) p(i,j) / (σ_x σ_y); 1 if σ_x σ_y = 0 |
| `glcm_DifferenceAverage` | Σ k p_{x−y}(k) |
| `glcm_DifferenceEntropy` | −Σ p_{x−y}(k) log₂ p_{x−y}(k) |
| `glcm_DifferenceVariance` | Σ (k − DifferenceAverage)² p_{x−y}(k) |
| `glcm_JointEnergy` | Σ p(i,j)² |
| `glcm_JointEntropy` | −Σ p(i,j) log₂ p(i,j) |
| `glcm_Imc1` | (HXY − HXY1) / max(HX, HY) |
| `glcm_Imc2` | √(1 − e^{−2(HXY2 − HXY)}) |
| `glcm_Idm` | Σ p(i,j) / (1 + (i − j)²) |
| `glcm_Idmn` | Σ p(i,j) / (1 + (i − j)²/N_g²) |
| `glcm_Id` | Σ p(i,j) / (1 + \|i − j\|) |
| `glcm_Idn` | Σ p(i,j) / (1 + \|i − j\|/N_g) |
| `glcm_InverseVariance` | Σ_{i≠j} p(i,j) / (i − j)² |
| `glcm_MaximumProbability` | max p(i,j) |
| `glcm_SumAverage` | Σ k p_{x+y}(k) |
| `glcm_SumEntropy` | −Σ p_{x+y}(k) log₂ p_{x+y}(k) |
| `glcm_SumSquares` | Σ (i − μ_x)² p(i,j) |

## GLRLM features (16)

Run-length matrix `P(i,j)` (gray level i, run length j) summed over four directions; `N_r` = number of runs,
`N_p` = Σ_j j·P(i,j) (number of pixels counted over all directions, as in PyRadiomics), `p = P / N_r`.

| Feature | Definition |
|---------|------------|
| `glrlm_ShortRunEmphasis` | Σ p(i,j) / j² |
| `glrlm_LongRunEmphasis` | Σ p(i,j) j² |
| `glrlm_GrayLevelNonUniformity` | Σ_i (Σ_j P(i,j))² / N_r |
| `glrlm_GrayLevelNonUniformityNormalized` | Σ_i (Σ_j P(i,j))² / N_r² |
| `glrlm_RunLengthNonUniformity` | Σ_j (Σ_i P(i,j))² / N_r |
| `glrlm_RunLengthNonUniformityNormalized` | Σ_j (Σ_i P(i,j))² / N_r² |
| `glrlm_RunPercentage` | N_r / N_p |
| `glrlm_GrayLevelVariance` | Σ (i − μ_i)² p(i,j) |
| `glrlm_RunVariance` | Σ (j − μ_j)² p(i,j) |
| `glrlm_RunEntropy` | −Σ p(i,j) log₂ p(i,j) |
| `glrlm_LowGrayLevelRunEmphasis` | Σ p(i,j) / i² |
| `glrlm_HighGrayLevelRunEmphasis` | Σ p(i,j) i² |
| `glrlm_ShortRunLowGrayLevelEmphasis` | Σ p(i,j) / (i² j²) |
| `glrlm_ShortRunHighGrayLevelEmphasis` | Σ p(i,j) i² / j² |
| `glrlm_LongRunLowGrayLevelEmphasis` | Σ p(i,j) j² / i² |
| `glrlm_LongRunHighGrayLevelEmphasis` | Σ p(i,j) i² j² |

## GLSZM features (16)

Size-zone matrix `P(i,j)` (gray level i, zone size j, zones 8-connected); `N_z` = number of zones, `N_p` = number of
pixels, `p = P / N_z`. Definitions mirror the GLRLM ones with zone size in place of run length:
`SmallAreaEmphasis`, `LargeAreaEmphasis`, `GrayLevelNonUniformity`, `GrayLevelNonUniformityNormalized`,
`SizeZoneNonUniformity`, `SizeZoneNonUniformityNormalized`, `ZonePercentage` (= N_z / N_p), `GrayLevelVariance`,
`ZoneVariance`, `ZoneEntropy`, `LowGrayLevelZoneEmphasis`, `HighGrayLevelZoneEmphasis`,
`SmallAreaLowGrayLevelEmphasis`, `SmallAreaHighGrayLevelEmphasis`, `LargeAreaLowGrayLevelEmphasis`,
`LargeAreaHighGrayLevelEmphasis`.

## NGTDM features (5)

For each gray level i present: `n_i` pixels, `p_i = n_i / N`, `s_i` = Σ \|i − mean of the 8-neighbourhood\|
(neighbours outside the ROI are ignored); `N_{g,p}` = number of gray levels present.

| Feature | Definition |
|---------|------------|
| `ngtdm_Coarseness` | 1 / Σ p_i s_i (10⁶ if the sum is 0) |
| `ngtdm_Contrast` | [Σ_i Σ_j p_i p_j (i − j)² / (N_{g,p}(N_{g,p} − 1))] · [Σ s_i / N] |
| `ngtdm_Busyness` | Σ p_i s_i / Σ_{i≠j} \|i p_i − j p_j\| |
| `ngtdm_Complexity` | Σ_i Σ_j \|i − j\| (p_i s_i + p_j s_j) / (p_i + p_j) / N |
| `ngtdm_Strength` | Σ_i Σ_j (p_i + p_j)(i − j)² / Σ s_i |

## GLDM features (14)

Dependence matrix `P(i,j)`: gray level i, dependence j = 1 + number of 8-neighbours with the same gray level (α = 0);
`N_z` = number of dependence zones (= number of pixels), `p = P / N_z`. Features: `SmallDependenceEmphasis`,
`LargeDependenceEmphasis`, `GrayLevelNonUniformity`, `DependenceNonUniformity`,
`DependenceNonUniformityNormalized`, `GrayLevelVariance`, `DependenceVariance`, `DependenceEntropy`,
`LowGrayLevelEmphasis`, `HighGrayLevelEmphasis`, `SmallDependenceLowGrayLevelEmphasis`,
`SmallDependenceHighGrayLevelEmphasis`, `LargeDependenceLowGrayLevelEmphasis`,
`LargeDependenceHighGrayLevelEmphasis` (same algebra as GLRLM with dependence in place of run length).

## Legacy 3D-named shape features (16, optional)

!!! warning "Not recommended for 2D histology"
    These features exist only so that pipelines expecting PyRadiomics' 3D `shape_*` column names keep working. They
    are computed from the same 2D polygon and mask as the `shape2D` class, and they are **not** numerically equivalent
    to PyRadiomics' 3D shape features, which treat a single slice as a one-voxel-thick volume. Enable them only if you
    know why you need them; use `shape2D_*` otherwise.

| Feature | How QuRad computes it | Equivalent 2D feature |
|---------|-----------------------|-----------------------|
| `shape_VoxelVolume` | number of pixels | `shape2D_PixelSurface` |
| `shape_MeshVolume` | polygon area | `shape2D_MeshSurface` |
| `shape_SurfaceArea` | polygon perimeter | `shape2D_Perimeter` |
| `shape_SurfaceVolumeRatio` | P / A | `shape2D_PerimeterSurfaceRatio` |
| `shape_Sphericity` | 2√(πA) / P | `shape2D_Sphericity` |
| `shape_Compactness1` | A / (√π · P^1.5) | – |
| `shape_Compactness2` | 36π A² / P³ | – |
| `shape_SphericalDisproportion` | P / (2√(πA)) | `shape2D_SphericalDisproportion` |
| `shape_Maximum3DDiameter`, `shape_Maximum2DDiameterSlice` | maximum polygon diameter | `shape2D_MaximumDiameter` |
| `shape_Maximum2DDiameterColumn`, `shape_Maximum2DDiameterRow` | bounding-box width, height | – |
| `shape_MajorAxisLength`, `shape_MinorAxisLength`, `shape_Elongation` | as in `shape2D` | `shape2D_*` |
| `shape_LeastAxisLength` | minor axis length | `shape2D_MinorAxisLength` |

## Further reading

Mathematical background for every feature: [PyRadiomics feature documentation](https://pyradiomics.readthedocs.io/en/latest/features.html)
and the IBSI reference manual. The validation against PyRadiomics is described in `notebooks/validation.ipynb`.
