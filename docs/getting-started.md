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
3. Adjust the settings in the dialog (bin width, which objects, which feature classes, output options) and click **OK**

The dialog mirrors the [configuration](#configuration) options described below. Results are written to the measurement table and/or a timestamped CSV, exactly like the script.

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
    distances: [1],            // Pixel distances for texture computation
    angles: 4                  // Number of angles for GLCM (4 = horizontal, vertical, both diagonals)
]
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `binWidth` | `25` | Intensity discretization width. Smaller values capture finer intensity differences but may be sensitive to noise. Use `25` to match PyRadiomics defaults. |
| `voxelArrayShift` | `0` | Constant value added to all intensities before feature calculation. |
| `force2D` | `true` | Calculate all features in 2D mode. Appropriate for histopathology images. |
| `distances` | `[1]` | Pixel distances for GLCM calculation. `[1]` considers only immediately adjacent pixels. |
| `angles` | `4` | Number of angles for GLCM: horizontal, vertical, and both diagonals. |

### Feature selection

Enable or disable specific feature classes:

```groovy
def enabledFeatures = [
    'firstorder': true,        // 19 intensity statistics
    'shape': true,             // 16 3D-style shape features
    'shape2D': true,           // 10 2D shape features
    'glcm': true,              // 23 GLCM texture features
    'glrlm': true,             // 16 GLRLM texture features
    'glszm': true,             // 16 GLSZM texture features
    'ngtdm': true,             // 5 NGTDM texture features
    'gldm': true               // 15 GLDM texture features
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

### Output options

```groovy
def outputDir = buildFilePath(PROJECT_BASE_DIR, "radiomics")
def exportCSV = true           // Save results to a CSV file
def addToMeasurements = true   // Add features to QuPath's measurement table
```

- **`exportCSV`**: saves all features to a timestamped CSV file in the `radiomics` folder of your project.
- **`addToMeasurements`**: adds features to QuPath's measurement system, enabling measurement maps and the measurements table.

## Output

### CSV file

The CSV file is saved to your project's `radiomics` folder with a timestamped filename:

```
your_project/
└── radiomics/
    └── image_name_radiomics_20250126_143052.csv
```

The CSV contains one row per object and **125 columns** (120 features + 5 metadata):

| Column | Description |
|--------|-------------|
| `ObjectID` | Unique identifier |
| `ObjectType` | Detection or Annotation |
| `Classification` | Object class (if assigned) |
| `Centroid_X`, `Centroid_Y` | Spatial coordinates |
| `firstorder_*` | 19 first-order features |
| `shape2D_*` | 10 shape features |
| `shape_*` | 16 3D shape features |
| `glcm_*` | 23 GLCM features |
| `glrlm_*` | 16 GLRLM features |
| `glszm_*` | 16 GLSZM features |
| `ngtdm_*` | 5 NGTDM features |
| `gldm_*` | 15 GLDM features |

### QuPath measurements

If `addToMeasurements = true`, all features are added to each object's measurements. You can:

- View them in the **Measurements** table
- Use them for **Measurement maps** visualization
- Export them via **Measure → Export measurements**

## Verify it worked

After running the script, you should see output like:

```
================================================================================
QuPath Radiomics Extraction - v3 (PyRadiomics-style binning)
================================================================================
  ✓ firstorder
  ✓ shape
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
