# Example Application

This guide walks through a complete radiomics workflow: from image with cell detections to feature extraction, visualization, and downstream analysis.

## Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Image + Cells  │ -> │  Run QuRad      │ -> │  Analyze        │
│  in QuPath      │    │  Script         │    │  Features       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Step 1: Prepare Your Image

Start with an image in QuPath that has cell detections. These can come from:

- QuPath's built-in cell detection
- StarDist extension
- Cellpose extension
- Imported annotations (GeoJSON)

<!-- TODO: Add QuPath screenshot showing image with cell detections -->
!!! note "Image placeholder"
    *Screenshot: QuPath with H&E image and cell detections*

### Importing External Annotations

If you have annotations from external tools:

1. Go to **File → Import objects**
2. Select your GeoJSON file
3. Annotations appear as detection objects

## Step 2: Run the QuRad Script

1. Open **Automate → Script editor**
2. Load `QuPath_Radiomics.groovy`
3. Configure settings if needed:

```groovy
def processDetections = true
def exportCSV = true
def addToMeasurements = true
```

4. Click **Run**

<!-- TODO: Add screenshot of script output -->
!!! note "Image placeholder"
    *Screenshot: Script editor with QuRad running*

The script will process all cells and output:

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
2. Select a feature (e.g., `firstorder_Entropy`)
3. Cells are colored by feature value

<!-- TODO: Add measurement map screenshot -->
!!! note "Image placeholder"
    *Screenshot: Measurement map showing cells colored by firstorder_Entropy*

This helps identify spatial patterns in your data, such as:

- Regions with high texture complexity
- Clusters of cells with similar morphology
- Gradients across tissue regions

### Histogram View

View the distribution of any feature:

1. Open **Measure → Show measurement maps**
2. The histogram appears below the dropdown
3. Adjust the color scale with min/max sliders

<!-- TODO: Add histogram screenshot -->
!!! note "Image placeholder"
    *Screenshot: Histogram of feature distribution*

## Step 4: Export and Analyze

### Export CSV

The CSV file is saved to your project's `radiomics` folder:

```
project/
└── radiomics/
    └── image_radiomics_20250126_143052.csv
```

### Load in Python

```python
import pandas as pd

# Load radiomics data
df = pd.read_csv('path/to/radiomics.csv')

# Preview
print(f"Cells: {len(df)}")
print(f"Features: {len(df.columns) - 3}")  # Exclude metadata

# Feature columns
feature_cols = [c for c in df.columns 
                if c.startswith(('firstorder_', 'shape', 'glcm_', 
                                 'glrlm_', 'glszm_', 'ngtdm_', 'gldm_'))]
```

## Step 5: Downstream Analysis

### Feature Correlation

Identify redundant features:

```python
import seaborn as sns
import matplotlib.pyplot as plt

# Calculate correlation matrix
corr = df[feature_cols].corr()

# Plot heatmap
plt.figure(figsize=(12, 10))
sns.heatmap(corr, cmap='coolwarm', center=0)
plt.title('Feature Correlation Matrix')
plt.tight_layout()
plt.show()
```

<!-- TODO: Add correlation heatmap -->
!!! note "Image placeholder"
    *Figure: Correlation matrix of radiomics features*

### Feature Selection

Remove highly correlated features:

```python
def remove_correlated_features(df, threshold=0.9):
    corr_matrix = df.corr().abs()
    upper = corr_matrix.where(
        np.triu(np.ones(corr_matrix.shape), k=1).astype(bool)
    )
    to_drop = [col for col in upper.columns 
               if any(upper[col] > threshold)]
    return df.drop(columns=to_drop)

X_filtered = remove_correlated_features(df[feature_cols])
print(f"Features after filtering: {len(X_filtered.columns)}")
```

### UMAP Visualization

Visualize cell populations in 2D:

```python
from umap import UMAP
from sklearn.preprocessing import StandardScaler

# Scale features
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X_filtered)

# UMAP embedding
umap = UMAP(n_neighbors=15, min_dist=0.3, random_state=42)
embedding = umap.fit_transform(X_scaled)

# Plot
plt.figure(figsize=(10, 8))
plt.scatter(embedding[:, 0], embedding[:, 1], 
            c=df['Classification'].astype('category').cat.codes,
            cmap='Set2', s=10, alpha=0.7)
plt.xlabel('UMAP 1')
plt.ylabel('UMAP 2')
plt.title('Cell Population UMAP')
plt.colorbar(label='Cell Type')
plt.show()
```

<!-- TODO: Add UMAP plot -->
!!! note "Image placeholder"
    *Figure: UMAP embedding showing cell type clusters*

### Classification

Train a classifier to distinguish cell types:

```python
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import cross_val_predict, GroupKFold
from sklearn.metrics import classification_report

# Prepare data
X = X_scaled
y = df['Classification']
groups = df['tile_id']  # Group by tile to prevent data leakage

# Cross-validation
cv = GroupKFold(n_splits=5)
clf = RandomForestClassifier(n_estimators=100, random_state=42)
y_pred = cross_val_predict(clf, X, y, cv=cv, groups=groups)

# Evaluate
print(classification_report(y, y_pred))
```

### Box Plots by Cell Type

Compare features between cell populations:

```python
# Select top discriminative features
feature = 'firstorder_Energy'

plt.figure(figsize=(8, 6))
df.boxplot(column=feature, by='Classification')
plt.title(f'{feature} by Cell Type')
plt.suptitle('')  # Remove automatic title
plt.ylabel(feature)
plt.show()
```

<!-- TODO: Add box plot -->
!!! note "Image placeholder"
    *Figure: Box plot comparing feature values between cell types*

## Complete Workflow Summary

1. **Prepare**: Load image with cell detections in QuPath
2. **Extract**: Run QuRad script to compute 120 features per cell
3. **Visualize**: Use measurement maps to explore spatial patterns
4. **Export**: Save CSV for downstream analysis
5. **Analyze**: 
   - Remove correlated features
   - Visualize with UMAP
   - Train classifiers
   - Compare cell populations

## Example Notebooks

For complete working examples, see:

- [PUMA Cell Classification](https://github.com/your-username/QuRad/blob/main/notebooks/example_application_puma.ipynb) - Tumor vs lymphocyte classification
- [TIGER Tissue Classification](https://github.com/your-username/QuRad/blob/main/notebooks/example_application_tiger.ipynb) - Tissue region classification
