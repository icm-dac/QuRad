# Installation

## Requirements

- **QuPath** version 0.4.0 or later
- An image with cell detections or annotations

## Download

1. Download the `QuPath_Radiomics.groovy` script from the [GitHub repository](https://github.com/your-username/QuRad)

2. Save it to a location you can easily access

## Installation in QuPath

There are two ways to use the script:

### Option 1: Run Directly

1. Open QuPath
2. Load an image with cell detections
3. Go to **Automate → Script editor**
4. Open the `QuPath_Radiomics.groovy` file
5. Click **Run**

### Option 2: Add to Project Scripts

1. Open your QuPath project
2. Go to **Automate → Project scripts → Open scripts directory**
3. Copy `QuPath_Radiomics.groovy` into this folder
4. The script will now appear under **Automate → Project scripts**

## Preparing Your Data

QuRad works with:

- **Cell detections** from QuPath's built-in cell detection
- **Cell detections** from StarDist or Cellpose extensions
- **Annotations** drawn manually or imported

### Importing Annotations

To import annotations from external sources (e.g., GeoJSON files):

1. Go to **File → Import objects**
2. Select your GeoJSON file
3. The annotations will appear in the image

!!! tip "Supported formats"
    QuPath can import annotations from GeoJSON, and other formats. GeoJSON is recommended for interoperability with Python workflows.

## Verify Installation

After running the script on a test image, you should see:

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
