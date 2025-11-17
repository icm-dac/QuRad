# QuPath Radiomics Extension (QuRad)

```
 ██████╗ ██╗   ██╗██████╗  █████╗ ██████╗ 
██╔═══██╗██║   ██║██╔══██╗██╔══██╗██╔══██╗
██║   ██║██║   ██║██████╔╝███████║██║  ██║
██║▄▄ ██║██║   ██║██╔══██╗██╔══██║██║  ██║
╚██████╔╝╚██████╔╝██║  ██║██║  ██║██████╔╝
 ╚══▀▀═╝  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝
```

> **Comprehensive radiomics feature extraction for QuPath**
> 
> Extract 120 pyRadiomics-compatible features from cell detections and annotations

---

## Features

- **120 Radiomics Features** - Complete pyRadiomics feature set
- **8 Feature Classes** - First-order, Shape 2D/3D, GLCM, GLRLM, GLSZM, NGTDM, GLDM
- **Fast Processing** - 400-1000 cells/second
- **CSV Export** - Ready for machine learning workflows
- **Batch Processing** - Process entire slides with 100k+ cells

---

## Feature Classes

| Class | Features | Description |
|-------|----------|-------------|
| **First-order** | 19 | Intensity statistics (mean, variance, entropy, etc.) |
| **Shape 2D** | 10 | 2D geometric features (area, perimeter, sphericity) |
| **Shape 3D** | 16 | 3D geometric features (volume, surface area) |
| **GLCM** | 24 | Gray Level Co-occurrence Matrix texture features |
| **GLRLM** | 16 | Gray Level Run Length Matrix features |
| **GLSZM** | 16 | Gray Level Size Zone Matrix features |
| **NGTDM** | 5 | Neighborhood Gray Tone Difference Matrix |
| **GLDM** | 14 | Gray Level Dependence Matrix features |

**Total: 120 features + 3 metadata columns = 123 CSV columns**

---

## Quick Start

### Installation
1. Download `QuPath_Radiomics.groovy`
2. Open in QuPath Script Editor
3. Run on your image with cell detections

Can be used for annotated regions, or following segmentation with StarDist or Cellpose.

### Basic Usage
```groovy
// Configure output
def outputDir = "/output"
def exportCSV = true

// Select what to process
def processDetections = true
def processAnnotations = false

// Run the script
```

### Output
```
slide_name_ALL_120_FEATURES_20251116_220656.csv
├── 184,024 rows (one per cell)
└── 123 columns (120 features + 3 metadata)
```

---

## Configuration

```groovy
def settings = [
    binWidth: 25,              // Intensity binning width
    voxelArrayShift: 0,        // Intensity shift
    force2D: true,             // Force 2D processing
    distances: [1],            // Pixel distances for texture
    angles: 4                  // Number of angles for GLCM
]
```

---

## Citation

If you use this tool in your research, please cite:
- QuPath: https://qupath.github.io
- pyRadiomics: https://pyradiomics.readthedocs.io

---

## License

MIT License - Free to use for research and commercial applications

---

## Acknowledgments

Inspired by pyRadiomics and designed for seamless QuPath integration
