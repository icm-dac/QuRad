# Configuration

QuRad provides several configuration options to customize the feature extraction process.

## Basic Settings

```groovy
def settings = [
    binWidth: 25,              // Intensity binning width
    voxelArrayShift: 0,        // Intensity shift
    force2D: true,             // Force 2D processing
    distances: [1],            // Pixel distances for texture
    angles: 4                  // Number of angles for GLCM
]
```

### binWidth

**Default:** `25`

The bin width for intensity discretization. This affects all texture features (GLCM, GLRLM, GLSZM, NGTDM, GLDM).

- **Smaller values** (e.g., 10): More bins, captures finer intensity differences, but may be sensitive to noise
- **Larger values** (e.g., 50): Fewer bins, more robust to noise, but may lose fine details

!!! tip "PyRadiomics compatibility"
    Use `binWidth: 25` to match PyRadiomics default behavior.

### voxelArrayShift

**Default:** `0`

A constant value added to all intensities before feature calculation. Useful when dealing with images that have negative values or to match specific preprocessing pipelines.

### force2D

**Default:** `true`

When `true`, all features are calculated in 2D mode. This is appropriate for histopathology images where each cell/region is a 2D slice.

### distances

**Default:** `[1]`

Pixel distances used for GLCM calculation. The default `[1]` means only immediately adjacent pixels are considered.

### angles

**Default:** `4`

Number of angles for GLCM calculation. With 4 angles, the GLCM considers horizontal, vertical, and both diagonal directions.

## Feature Selection

Enable or disable specific feature classes:

```groovy
def enabledFeatures = [
    'firstorder': true,    // 19 features
    'shape': true,         // 16 features (3D-style)
    'shape2D': true,       // 10 features
    'glcm': true,          // 24 features
    'glrlm': true,         // 16 features
    'glszm': true,         // 16 features
    'ngtdm': true,         // 5 features
    'gldm': true           // 14 features
]
```

!!! example "Extract only intensity and shape features"
    ```groovy
    def enabledFeatures = [
        'firstorder': true,
        'shape': false,
        'shape2D': true,
        'glcm': false,
        'glrlm': false,
        'glszm': false,
        'ngtdm': false,
        'gldm': false
    ]
    ```

## Processing Options

```groovy
def processAnnotations = false  // Process annotation objects
def processDetections = true    // Process detection objects
def selectedOnly = false        // Only process selected objects
def progressInterval = 10000    // Print progress every N objects
```

### processAnnotations

Set to `true` to extract features from annotation objects (e.g., tissue regions).

### processDetections

Set to `true` to extract features from detection objects (e.g., cells).

### selectedOnly

When `true`, only processes objects that are currently selected in QuPath.

### progressInterval

Controls how often progress messages are printed. Default is every 10,000 objects.

## Output Options

```groovy
def outputDir = buildFilePath(PROJECT_BASE_DIR, "radiomics")
def exportCSV = true
def addToMeasurements = true
```

### outputDir

Directory where CSV files are saved. By default, creates a `radiomics` folder in your project directory.

### exportCSV

When `true`, exports all features to a CSV file.

### addToMeasurements

When `true`, adds all features to QuPath's measurement system. This enables:

- Viewing features in the Measurements table
- Creating Measurement maps for visualization
- Exporting via QuPath's built-in export functions

## Example Configurations

### High-throughput cell analysis

```groovy
def settings = [binWidth: 25, force2D: true, distances: [1], angles: 4]
def enabledFeatures = [
    'firstorder': true, 'shape2D': true, 'glcm': true,
    'shape': false, 'glrlm': false, 'glszm': false, 
    'ngtdm': false, 'gldm': false
]
def processDetections = true
def processAnnotations = false
```

### Tissue region analysis

```groovy
def settings = [binWidth: 25, force2D: true, distances: [1], angles: 4]
def enabledFeatures = [
    'firstorder': true, 'shape': true, 'shape2D': true,
    'glcm': true, 'glrlm': true, 'glszm': true, 
    'ngtdm': true, 'gldm': true
]
def processDetections = false
def processAnnotations = true
```
