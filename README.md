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

📖 **Documentation:** https://icm-dac.github.io/QuRad/

---

## Features

- **120 Radiomics Features** - Complete pyRadiomics feature set
- **8 Feature Classes** - First-order, Shape 2D/3D, GLCM, GLRLM, GLSZM, NGTDM, GLDM
- **Fast Processing** - up to 200 cells/second
- **CSV Export** - Ready for machine learning workflows
- **Batch Processing** - Process entire slides with 100k+ cells

---

## Feature Classes

| Class | Features | Description |
|-------|----------|-------------|
| **First-order** | 19 | Intensity statistics (mean, variance, entropy, etc.) |
| **Shape 2D** | 10 | 2D geometric features (area, perimeter, sphericity) |
| **Shape 3D** | 16 | 3D geometric features (volume, surface area) |
| **GLCM** | 23 | Gray Level Co-occurrence Matrix texture features |
| **GLRLM** | 16 | Gray Level Run Length Matrix features |
| **GLSZM** | 16 | Gray Level Size Zone Matrix features |
| **NGTDM** | 5 | Neighborhood Gray Tone Difference Matrix |
| **GLDM** | 15 | Gray Level Dependence Matrix features |

**Total: 120 features + 5 metadata columns = 125 CSV columns**

---

## Quick Start

QuRad comes in two interchangeable forms that share the same validated feature code:

### Option A — Extension (recommended)
1. Download `qupath-extension-qurad-<version>.jar` from [Releases](https://github.com/icm-dac/QuRad/releases) (or build it: `cd extension && ./gradlew build`, requires a JDK 21 toolchain)
2. Drag the jar onto QuPath (or **Extensions → Manage extensions**)
3. Run **Extensions → QuRad → Extract radiomics features…** and adjust the settings dialog

Built for QuPath 0.6 and 0.7. See [`extension/README.md`](extension/README.md) for build/test details.

### Option B — Script
1. Download `src/QuPath_Radiomics_v3.groovy`
2. Open in QuPath Script Editor
3. Run on your image with cell detections

Built for **QuPath 0.6 and 0.7**. Can be used for annotated regions, or following segmentation with native QuPath cell segmentation, StarDist or Cellpose.

### Basic Usage
```groovy
// Configure output
def outputDir = "/radiomics"
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
└── 125 columns (120 features + 5 metadata)
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

## Example data

The sample image under `example_data/breast_cancer/` (`ytma10_010704_benign1_ccd.tif`)
is a single breast-cancer tissue-microarray tile from the **UCSB Bio-Segmentation
benchmark**, licensed under **CC BY 3.0**. It is included here, under that license and with
attribution, as a small worked example for validating QuRad's output; QuRad does not claim
authorship of this image. If you use it, please cite the original source:

> E. Drelie Gelasca, J. Byun, B. Obara and B. S. Manjunath, "Evaluation and benchmark for
> biological image segmentation," *2008 15th IEEE International Conference on Image
> Processing (ICIP)*, San Diego, CA, 2008, pp. 1816–1819.

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
