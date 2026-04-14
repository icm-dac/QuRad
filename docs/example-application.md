# Example Application

This guide walks through a complete radiomics workflow: from an image with detections to feature extraction, visualization, and further analysis in QuPath.

## Workflow Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Prepare image  │ -> │  Run QuRad      │ -> │  Visualize      │ -> │  Export &        │
│  with objects   │    │  script         │    │  in QuPath      │    │  analyze         │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
```

1. **Prepare**: Load an image with cell detections or annotations in QuPath
2. **Extract**: Run the QuRad script to compute 120 features per object
3. **Visualize**: Use measurement maps to explore spatial patterns
4. **Export**: Save the CSV for further analysis (classification, clustering, etc.)

---

## Step 1: Prepare Your Image

Start with an image in QuPath that has detections or annotations. These can come from:

- QuPath's built-in cell detection
- StarDist extension
- Cellpose extension
- Imported annotations (GeoJSON)

In this example, we use a mouse glioblastoma H&E image:

![Raw H&E image](assets/raw_he_image.png)

*Mouse glioblastoma H&E image loaded in QuPath.*

After running cell detection (using StarDist, Cellpose, or QuPath's built-in detection):

![Cell segmentations](assets/cell_segmentations.png)

*Cell segmentations overlaid on the H&E image.*

### Importing External Annotations

If you have annotations from external tools:

1. Go to **File → Import objects**
2. Select your GeoJSON file
3. Annotations appear as detection objects

## Step 2: Run the QuRad Script

1. Open **Automate → Script editor**
2. Load `QuPath_Radiomics_v3.groovy`
3. Configure settings if needed (see [Getting Started — Configuration](getting-started.md#configuration)):

```groovy
def processDetections = true
def exportCSV = true
def addToMeasurements = true
```

4. Click **Run**

The script will process all objects and output:

```
================================================================================
QuPath Radiomics Extraction - v3
================================================================================
Processing 5000 objects

Processed 5000/5000 (892.3 objects/sec)

================================================================================
Complete
================================================================================
Processed: 5000 objects
Features per object: 120
```

## Step 3: Visualize in QuPath

### Measurement Maps

Color cells by any radiomics feature:

1. Go to **Measure → Show measurement maps**
2. Select a feature (e.g., `firstorder_Energy`)
3. Cells are colored by feature value

![Measurement map showing cells colored by firstorder_Energy](assets/measurement_map.png)

*Measurement map visualization: cells colored by `firstorder_Energy`. Dark violet indicates cells with lower energy values, yellow indicates cells with higher energy values.*

This helps identify spatial patterns in your data, such as:

- Regions with high texture complexity
- Clusters of cells with similar morphology
- Gradients across tissue regions

### Histogram View

View the distribution of any feature:

1. Open **Measure → Show measurement maps**
2. The histogram appears below the dropdown
3. Adjust the color scale with min/max sliders

![Histogram of firstorder_Entropy distribution](assets/histogram.png)

*Histogram showing the distribution of `firstorder_Entropy` across all detected cells.*

## Step 4: Export Data

### CSV Export

The CSV file is automatically saved to your project's `radiomics` folder:

```
project/
└── radiomics/
    └── image_radiomics_20250126_143052.csv
```

### Export Measurements Table

You can also export via QuPath's built-in export:

1. Go to **Measure → Export measurements**
2. Select output format (CSV, TSV)
3. Choose which measurements to include

## What Next?

The 120 radiomics features extracted by QuRad provide quantitative descriptors that can be used for a variety of downstream analyses:

- **Cell classification**: distinguish cell populations based on morphological and texture differences (e.g., tumor cells vs. lymphocytes)
- **Tissue region characterization**: describe the composition and architecture of tissue compartments (e.g., invasive tumor vs. stroma vs. healthy glands)
- **Quality assessment**: evaluate tissue quality or staining consistency across slides
- **Exploratory analysis**: use dimensionality reduction (UMAP, PCA) to identify clusters and spatial patterns
- **Machine learning**: feed features into classifiers (Random Forest, SVM, etc.) for automated phenotyping or outcome prediction

For more details on these applications, see the [Feature Reference](features.md).
