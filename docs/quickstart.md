# Quick Start

This guide will help you extract radiomics features from your first image in under 5 minutes.

## Prerequisites

- QuPath installed with an image loaded
- Cell detections or annotations on your image

## Step 1: Open the Script Editor

In QuPath, go to **Automate → Script editor**

## Step 2: Load the Script

Open the `QuPath_Radiomics.groovy` file in the script editor.

## Step 3: Configure Output (Optional)

At the top of the script, you can configure the output:

```groovy
// Output directory (relative to project)
def outputDir = buildFilePath(PROJECT_BASE_DIR, "radiomics")

// Export options
def exportCSV = true          // Save results to CSV
def addToMeasurements = true  // Add features to QuPath measurements
```

## Step 4: Select What to Process

Choose whether to process detections, annotations, or both:

```groovy
def processAnnotations = false  // Process annotation objects
def processDetections = true    // Process detection objects (cells)
def selectedOnly = false        // Only process selected objects
```

## Step 5: Run the Script

Click the **Run** button (or press `Ctrl+R` / `Cmd+R`).

You'll see progress output:

```
================================================================================
QuPath Radiomics Extraction - v3 (PyRadiomics-style binning)
================================================================================
Processing 5000 objects

Processed 1000/5000 (850.3 objects/sec)
Processed 2000/5000 (823.1 objects/sec)
...
```

## Step 6: Find Your Results

### CSV Output

The CSV file is saved to your project's `radiomics` folder:

```
your_project/
└── radiomics/
    └── image_name_radiomics_20250126_143052.csv
```

### QuPath Measurements

If `addToMeasurements = true`, all features are added to each object's measurements. You can:

- View them in the **Measurements** table
- Use them for **Measurement maps** visualization
- Export them via **Measure → Export measurements**

## Output Format

The CSV contains one row per object with columns:

| Column | Description |
|--------|-------------|
| `ObjectID` | Unique identifier |
| `ObjectType` | Detection or Annotation |
| `Classification` | Cell class (if assigned) |
| `Centroid_X`, `Centroid_Y` | Spatial coordinates |
| `firstorder_*` | 19 first-order features |
| `shape2D_*` | 10 shape features |
| `shape_*` | 16 3D shape features |
| `glcm_*` | 24 GLCM features |
| `glrlm_*` | 16 GLRLM features |
| `glszm_*` | 16 GLSZM features |
| `ngtdm_*` | 5 NGTDM features |
| `gldm_*` | 14 GLDM features |

## Next Steps

- [Feature Reference](features.md) - Learn what each feature measures
- [Configuration](configuration.md) - Customize extraction parameters
- [Example Application](example-application.md) - Complete workflow tutorial
