# Getting Started

This guide walks you through setting up and running QuRad, from preparing your data to extracting radiomics features.

## Requirements

- **QuPath** — version 0.6 or 0.7
- A **QuPath project** with at least one image
- **Detections or annotations** on your image (cells, tissue regions, or imported objects)

QuRad comes in two interchangeable forms that produce identical results:

| Form | Best for | How you run it |
|------|----------|----------------|
| **Extension** (`.jar`) | Most users; repeated use | Menu command **Extensions → QuRad** with a settings dialog |
| **Script** (`.groovy`) | One-off runs, custom edits | Paste into the Script Editor and **Run** |

Follow the section that matches the form you prefer below.

## Preparing Your Data

Before running QuRad, you need a QuPath project with objects to analyze.

### 1. Create or open a QuPath project

Open QuPath, then create a new project or open an existing one. Add your image(s) to the project.

### 2. Create detections or annotations

QuRad can process any combination of:

- **Cell detections** from QuPath's built-in cell detection, StarDist, or Cellpose
- **Annotations** drawn manually or imported from external sources

### 3. Import annotations (optional)

To import annotations from external tools (e.g., GeoJSON files):

1. Go to **File → Import objects**
2. Select your GeoJSON file
3. The annotations will appear in the image

!!! tip "Supported formats"
    QuPath can import annotations from GeoJSON and other formats. GeoJSON is recommended for interoperability with Python workflows.

## Option A — Install the extension

The extension wraps the same feature code behind a menu command and settings dialog. Recommended for repeated use.

### 1. Get the jar

Download `qupath-extension-qurad-<version>.jar` from the [Releases](https://github.com/icm-dac/QuRad/releases) page, or build it yourself:

```bash
cd extension
./gradlew build      # requires a JDK 21 toolchain
# jar is written to build/libs/qupath-extension-qurad-<version>.jar
```

The built jar runs on QuPath 0.6 and 0.7.

### 2. Install it

Drag the jar onto a running QuPath window (or use **Extensions → Manage extensions → installed extensions directory** and copy it in), then restart QuPath if prompted.

### 3. Run it

1. Open your project and select an image with detections/annotations
2. Go to **Extensions → QuRad → Extract radiomics features…**
3. Adjust the settings in the dialog (bin width, GLCM distance, which objects, which feature classes, output options) and click **OK**

The dialog mirrors the [configuration](#configuration) options described below. Results are written to the measurement table and/or a timestamped CSV (plus a `_settings.json` file), exactly like the script.

!!! warning "Image type"
    QuRad works on **8-bit RGB brightfield images** (the standard for H&E whole-slide images). Fluorescence,
    multichannel or 16-bit images are refused with an explicit message.

## Option B — Run the script

Download the script [`QuPath_Radiomics_v3.groovy`](https://github.com/icm-dac/QuRad/blob/main/src/QuPath_Radiomics_v3.groovy) from the GitHub repository. Always use the latest version available. Save it to a location you can easily access.

There are two ways to use the script:

### Option B1: Run directly

1. Open your QuPath project and select an image
2. Go to **Automate → Script editor**
3. Open the `QuPath_Radiomics_v3.groovy` file
4. Adjust the [configuration](#configuration) if needed
5. Click **Run** (or press `Ctrl+R` / `Cmd+R`)

### Option B2: Add to project scripts

1. Open your QuPath project
2. Go to **Automate → Project scripts → Open scripts directory**
3. Copy `QuPath_Radiomics_v3.groovy` into this folder
4. The script will now appear under **Automate → Project scripts**

## Configuration

At the top of the script, you will find the configuration section. Modify these settings to match your analysis before running.

### What to process

Choose which objects to analyze by setting these variables to `true` or `false`:

```groovy
def processAnnotations = false // Process annotation objects (tissue regions, ROIs)
def processDetections = true   // Process detection objects (cells)
def selectedOnly = false       // Only process currently selected objects
```

### Radiomics parameters

```groovy
def settings = [
    binWidth: 25,              // Intensity binning width (PyRadiomics default: 25)
    voxelArrayShift: 0,        // Intensity shift before binning
    force2D: true,             // Force 2D processing (recommended for histopathology)
    distances: [1],            // GLCM pixel distance
    angles: 4                  // GLCM/GLRLM directions (fixed: 0, 45, 90, 135 degrees, matrices summed)
]
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `binWidth` | `25` | Fixed bin width for intensity discretisation of the 0–255 gray scale (bins aligned to multiples of the bin width from 0, as in PyRadiomics). Smaller values capture finer intensity differences but are more sensitive to noise. |
| `voxelArrayShift` | `0` | Constant added to all intensities before `Energy`, `TotalEnergy` and `RootMeanSquared` are computed. |
| `force2D` | `true` | All features are two-dimensional; this flag is kept for PyRadiomics compatibility and cannot be changed. |
| `distances` | `[1]` | Offset in **pixels** between co-occurring pixels for the GLCM. Only the first value is used. |
| `angles` | `4` | Informational: GLCM and GLRLM always use the four in-plane directions and sum the matrices (PyRadiomics `weightingNorm='no_weighting'`). |

All other conventions (grayscale conversion, pixel-centre mask rule, edge-case handling) are fixed and documented in the
[Feature Reference](features.md#conventions-that-determine-the-values).

!!! note "Physical scale"
    Features are computed at the image's native resolution, in pixel units. A GLCM distance of 1 pixel therefore
    corresponds to a different physical distance on a 0.25 µm/px slide than on a 0.5 µm/px slide. QuRad records the
    pixel calibration of every image in the CSV (`PixelWidth_um`, `PixelHeight_um`) and in the settings file so that
    you can account for it; when comparing slides scanned at different resolutions, either resample the images to a
    common resolution in QuPath or set `distances` per slide (e.g. `[2]` on a 0.25 µm/px slide to match `[1]` on a
    0.5 µm/px slide). Length and area features are reported in pixels and can be converted with the calibration
    columns.

### Feature selection

Enable or disable specific feature classes:

```groovy
def enabledFeatures = [
    'firstorder': true,        // 19 intensity statistics
    'shape2D': true,           // 10 2D shape features
    'glcm': true,              // 23 GLCM texture features
    'glrlm': true,             // 16 GLRLM texture features
    'glszm': true,             // 16 GLSZM texture features
    'ngtdm': true,             // 5 NGTDM texture features
    'gldm': true,              // 14 GLDM texture features
    'shape': false             // 16 legacy 3D-named shape features (2D quantities; not recommended)
]
```

The default selection yields **103 features**, all of which are two-dimensional quantities with a direct PyRadiomics
equivalent. The optional `shape` class reports 2D quantities under PyRadiomics' 3D shape names; it is disabled by
default because those names suggest measurements that do not exist for a single histology section (see the
[Feature Reference](features.md#legacy-3d-named-shape-features-16-optional)).

!!! example "Extract only intensity and shape features"
    ```groovy
    def enabledFeatures = [
        'firstorder': true,
        'shape2D': true,
        'glcm': false,
        'glrlm': false,
        'glszm': false,
        'ngtdm': false,
        'gldm': false,
        'shape': false
    ]
    ```

### Output options

```groovy
def outputDir = buildFilePath(PROJECT_BASE_DIR, "radiomics")
def exportCSV = true           // Save results to a CSV file
def addToMeasurements = true   // Add features to QuPath's measurement table
```

- **`exportCSV`**: saves all features to a timestamped CSV file in the `radiomics` folder of your project, together with a `_settings.json` file that records the software version, QuPath version, image name, pixel calibration, all parameters and conventions, and the enabled feature classes.
- **`addToMeasurements`**: adds features to QuPath's measurement system, enabling measurement maps and the measurements table.

## Output

### CSV file

The CSV file is saved to your project's `radiomics` folder with a timestamped filename:

```
your_project/
└── radiomics/
    ├── image_name_radiomics_20260904_143052.csv
    └── image_name_radiomics_20260904_143052_settings.json
```

The CSV contains one row per object and, with the default settings, **112 columns** (103 features + 9 metadata
columns; 128 columns if the legacy `shape` class is enabled):

| Column | Description |
|--------|-------------|
| `Image` | Image name (for multi-slide analyses) |
| `ObjectID` | Unique identifier of the QuPath object |
| `ObjectType` | Detection or Annotation |
| `Classification` | Object class (if assigned) |
| `Centroid_X`, `Centroid_Y` | Centroid in pixel coordinates |
| `NumPixels` | Number of pixels inside the ROI (and inside the image) used for the intensity/texture features |
| `PixelWidth_um`, `PixelHeight_um` | Pixel calibration of the image (empty if unknown) |
| `firstorder_*` | 19 first-order features |
| `shape2D_*` | 10 shape features |
| `glcm_*` | 23 GLCM features |
| `glrlm_*` | 16 GLRLM features |
| `glszm_*` | 16 GLSZM features |
| `ngtdm_*` | 5 NGTDM features |
| `gldm_*` | 14 GLDM features |
| `shape_*` | 16 legacy 3D-named shape features (only if enabled) |

Values are written with full double precision. Objects whose polygon contains no pixel centre are skipped and
reported in the log.

!!! tip "Minimum object size"
    Texture matrices of very small objects are degenerate (a 1-pixel ROI has one gray level and no pixel pairs).
    PyRadiomics refuses masks smaller than 2 pixels in any dimension; QuRad computes features for any non-empty ROI but
    we recommend filtering objects with `NumPixels` below about 10 pixels downstream, and checking that nuclei are
    segmented at a resolution where they span at least a few dozen pixels.

### QuPath measurements

If `addToMeasurements = true`, all features are added to each object's measurements. You can:

- View them in the **Measurements** table
- Use them for **Measurement maps** visualization
- Export them via **Measure → Export measurements**

## Verify it worked

After running the script, you should see output like:

```
================================================================================
QuPath Radiomics Extraction - QuRad 0.4.0
================================================================================
  ✓ firstorder
  ✓ shape2D
  ✓ glcm
  ✓ glrlm
  ✓ glszm
  ✓ ngtdm
  ✓ gldm
================================================================================
Processing 1000 objects
...
```

If you see this output, QuRad is working correctly.

## Next Steps

- [Feature Reference](features.md) - Learn what each feature measures
- [Example Application](example-application.md) - Complete workflow tutorial
