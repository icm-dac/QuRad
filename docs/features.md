# Feature Reference

QuRad extracts 120 radiomics features organized into 8 feature classes. All features are compatible with [PyRadiomics](https://pyradiomics.readthedocs.io/en/latest/features.html). For an overview of the feature classes and their counts, see the [home page](index.md#output-and-feature-classes).

## First-Order Features (19 features)

First-order statistics describe the distribution of pixel intensities within the ROI.

| Feature | Description |
|---------|-------------|
| `firstorder_Energy` | Sum of squared pixel values |
| `firstorder_TotalEnergy` | Same as Energy |
| `firstorder_Entropy` | Measure of randomness in intensity distribution |
| `firstorder_Minimum` | Minimum intensity value |
| `firstorder_10Percentile` | 10th percentile of intensity |
| `firstorder_90Percentile` | 90th percentile of intensity |
| `firstorder_Maximum` | Maximum intensity value |
| `firstorder_Mean` | Average intensity |
| `firstorder_Median` | Median intensity |
| `firstorder_InterquartileRange` | Difference between 75th and 25th percentiles |
| `firstorder_Range` | Difference between max and min |
| `firstorder_MeanAbsoluteDeviation` | Mean absolute deviation from the mean |
| `firstorder_RobustMeanAbsoluteDeviation` | MAD calculated on 10-90 percentile range |
| `firstorder_RootMeanSquared` | Square root of mean squared intensity |
| `firstorder_Variance` | Variance of intensities |
| `firstorder_StandardDeviation` | Standard deviation of intensities |
| `firstorder_Skewness` | Asymmetry of the intensity distribution |
| `firstorder_Kurtosis` | Peakedness of the intensity distribution |
| `firstorder_Uniformity` | Sum of squared probabilities (inverse of entropy) |

## Shape 2D Features (10 features)

Shape features describe the geometric properties of the ROI in 2D.

| Feature | Description |
|---------|-------------|
| `shape2D_MeshSurfaceArea` | Perimeter of the ROI |
| `shape2D_PixelSurface` | Same as perimeter |
| `shape2D_Perimeter` | Boundary length |
| `shape2D_PerimeterSurfaceRatio` | Ratio of perimeter to area |
| `shape2D_Sphericity` | Circularity measure (1 = perfect circle) |
| `shape2D_SphericalDisproportion` | Inverse of sphericity |
| `shape2D_MajorAxisLength` | Length of the major axis |
| `shape2D_MinorAxisLength` | Length of the minor axis |
| `shape2D_Elongation` | Ratio of minor to major axis |
| `shape2D_Flatness` | Same as elongation |

## Shape 3D Features (16 features)

3D-style shape features (calculated in 2D for histopathology).

| Feature | Description |
|---------|-------------|
| `shape_VoxelVolume` | Area of the ROI |
| `shape_MeshVolume` | Same as area |
| `shape_SurfaceArea` | Perimeter |
| `shape_SurfaceVolumeRatio` | Perimeter to area ratio |
| `shape_Sphericity` | Circularity measure |
| `shape_Compactness1` | Compactness metric 1 |
| `shape_Compactness2` | Compactness metric 2 |
| `shape_SphericalDisproportion` | Inverse of sphericity |
| `shape_Maximum3DDiameter` | Diagonal of bounding box |
| `shape_Maximum2DDiameterSlice` | Same as 3D diameter |
| `shape_Maximum2DDiameterColumn` | Bounding box width |
| `shape_Maximum2DDiameterRow` | Bounding box height |
| `shape_MajorAxisLength` | Major axis length |
| `shape_MinorAxisLength` | Minor axis length |
| `shape_LeastAxisLength` | Same as minor axis |
| `shape_Elongation` | Minor/major axis ratio |

## GLCM Features (24 features)

Gray Level Co-occurrence Matrix features capture texture by analyzing spatial relationships between pixel pairs.

| Feature | Description |
|---------|-------------|
| `glcm_Autocorrelation` | Correlation of intensity pairs |
| `glcm_JointAverage` | Mean of the GLCM |
| `glcm_ClusterProminence` | Measure of asymmetry |
| `glcm_ClusterShade` | Skewness of the GLCM |
| `glcm_ClusterTendency` | Grouping of similar values |
| `glcm_Contrast` | Local intensity variation |
| `glcm_Correlation` | Linear dependency of gray levels |
| `glcm_JointEnergy` | Sum of squared GLCM elements |
| `glcm_JointEntropy` | Randomness of co-occurrences |
| `glcm_Idm` | Inverse Difference Moment (homogeneity) |
| `glcm_Idmn` | Normalized IDM |
| `glcm_Id` | Inverse Difference |
| `glcm_Idn` | Normalized ID |
| `glcm_InverseVariance` | Inverse of variance |
| `glcm_MaximumProbability` | Most frequent co-occurrence |
| `glcm_SumSquares` | Variance of GLCM |
| `glcm_DifferenceAverage` | Mean of difference matrix |
| `glcm_DifferenceEntropy` | Entropy of difference matrix |
| `glcm_DifferenceVariance` | Variance of difference matrix |
| `glcm_SumAverage` | Mean of sum matrix |
| `glcm_SumEntropy` | Entropy of sum matrix |
| `glcm_Imc1` | Information Measure of Correlation 1 |
| `glcm_Imc2` | Information Measure of Correlation 2 |

## GLRLM Features (16 features)

Gray Level Run Length Matrix features capture texture by analyzing consecutive pixels with the same intensity.

| Feature | Description |
|---------|-------------|
| `glrlm_ShortRunEmphasis` | Distribution of short runs |
| `glrlm_LongRunEmphasis` | Distribution of long runs |
| `glrlm_GrayLevelNonUniformity` | Variability of gray levels |
| `glrlm_GrayLevelNonUniformityNormalized` | Normalized GLNU |
| `glrlm_RunLengthNonUniformity` | Variability of run lengths |
| `glrlm_RunLengthNonUniformityNormalized` | Normalized RLNU |
| `glrlm_RunPercentage` | Ratio of runs to pixels |
| `glrlm_GrayLevelVariance` | Variance of gray levels |
| `glrlm_RunVariance` | Variance of run lengths |
| `glrlm_RunEntropy` | Randomness of runs |
| `glrlm_LowGrayLevelRunEmphasis` | Distribution of low intensity runs |
| `glrlm_HighGrayLevelRunEmphasis` | Distribution of high intensity runs |
| `glrlm_ShortRunLowGrayLevelEmphasis` | Short runs with low intensity |
| `glrlm_ShortRunHighGrayLevelEmphasis` | Short runs with high intensity |
| `glrlm_LongRunLowGrayLevelEmphasis` | Long runs with low intensity |
| `glrlm_LongRunHighGrayLevelEmphasis` | Long runs with high intensity |

## GLSZM Features (16 features)

Gray Level Size Zone Matrix features capture texture by analyzing connected regions of similar intensity.

| Feature | Description |
|---------|-------------|
| `glszm_SmallAreaEmphasis` | Distribution of small zones |
| `glszm_LargeAreaEmphasis` | Distribution of large zones |
| `glszm_GrayLevelNonUniformity` | Variability of gray levels |
| `glszm_GrayLevelNonUniformityNormalized` | Normalized GLNU |
| `glszm_SizeZoneNonUniformity` | Variability of zone sizes |
| `glszm_SizeZoneNonUniformityNormalized` | Normalized SZNU |
| `glszm_ZonePercentage` | Ratio of zones to pixels |
| `glszm_GrayLevelVariance` | Variance of gray levels |
| `glszm_ZoneVariance` | Variance of zone sizes |
| `glszm_ZoneEntropy` | Randomness of zones |
| `glszm_LowGrayLevelZoneEmphasis` | Distribution of low intensity zones |
| `glszm_HighGrayLevelZoneEmphasis` | Distribution of high intensity zones |
| `glszm_SmallAreaLowGrayLevelEmphasis` | Small zones with low intensity |
| `glszm_SmallAreaHighGrayLevelEmphasis` | Small zones with high intensity |
| `glszm_LargeAreaLowGrayLevelEmphasis` | Large zones with low intensity |
| `glszm_LargeAreaHighGrayLevelEmphasis` | Large zones with high intensity |

## NGTDM Features (5 features)

Neighborhood Gray Tone Difference Matrix features capture texture by comparing pixels to their neighborhood average.

| Feature | Description |
|---------|-------------|
| `ngtdm_Coarseness` | Spatial rate of change (inverse of local variation) |
| `ngtdm_Contrast` | Range of intensities and spatial frequency |
| `ngtdm_Busyness` | Spatial frequency of intensity changes |
| `ngtdm_Complexity` | Rapid intensity changes |
| `ngtdm_Strength` | Primitiveness of texture |

## GLDM Features (14 features)

Gray Level Dependence Matrix features capture texture by analyzing how many connected pixels share similar intensities.

| Feature | Description |
|---------|-------------|
| `gldm_SmallDependenceEmphasis` | Distribution of small dependencies |
| `gldm_LargeDependenceEmphasis` | Distribution of large dependencies |
| `gldm_GrayLevelNonUniformity` | Variability of gray levels |
| `gldm_DependenceNonUniformity` | Variability of dependencies |
| `gldm_DependenceNonUniformityNormalized` | Normalized DNU |
| `gldm_DependencePercentage` | Ratio of dependencies to pixels |
| `gldm_GrayLevelVariance` | Variance of gray levels |
| `gldm_DependenceVariance` | Variance of dependencies |
| `gldm_DependenceEntropy` | Randomness of dependencies |
| `gldm_LowGrayLevelEmphasis` | Distribution of low intensity dependencies |
| `gldm_HighGrayLevelEmphasis` | Distribution of high intensity dependencies |
| `gldm_SmallDependenceLowGrayLevelEmphasis` | Small dependencies with low intensity |
| `gldm_SmallDependenceHighGrayLevelEmphasis` | Small dependencies with high intensity |
| `gldm_LargeDependenceLowGrayLevelEmphasis` | Large dependencies with low intensity |
| `gldm_LargeDependenceHighGrayLevelEmphasis` | Large dependencies with high intensity |

## Further Reading

For detailed mathematical definitions, see the [PyRadiomics documentation](https://pyradiomics.readthedocs.io/en/latest/features.html).
