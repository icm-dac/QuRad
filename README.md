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
> Extract PyRadiomics-validated radiomic features from cell detections and annotations, inside QuPath

📖 **Documentation:** https://icm-dac.github.io/QuRad/

---

## Features

- **103 radiomic features by default** (119 with the optional legacy shape class), each validated feature by feature against PyRadiomics 3.0.1
- **7 feature classes** - First-order, Shape 2D, GLCM, GLRLM, GLSZM, NGTDM, GLDM (plus optional legacy 3D-named shape)
- **Fast, single-threaded processing** - about 1,900 nuclei/s in steady state on a server CPU (25,360 nuclei in 13.6 s; see the article's benchmark)
- **CSV + settings export** - one CSV per image with full metadata and a JSON file recording every parameter
- **Batch processing** - whole slides with 100k+ cells

---

## Feature Classes

| Class | Features | Default | Description |
|-------|----------|---------|-------------|
| **First-order** | 19 | on | Intensity statistics (mean, variance, entropy, etc.) |
| **Shape 2D** | 10 | on | 2D geometric features (area, perimeter, sphericity, axis lengths) |
| **GLCM** | 23 | on | Gray Level Co-occurrence Matrix texture features |
| **GLRLM** | 16 | on | Gray Level Run Length Matrix features |
| **GLSZM** | 16 | on | Gray Level Size Zone Matrix features |
| **NGTDM** | 5 | on | Neighborhood Gray Tone Difference Matrix |
| **GLDM** | 14 | on | Gray Level Dependence Matrix features |
| **Shape (legacy 3D names)** | 16 | off | 2D quantities under PyRadiomics' 3D names; not recommended for 2D histology |

**Default output: 103 features + 9 metadata columns = 112 CSV columns** (128 with the legacy class). See the [feature reference](https://icm-dac.github.io/QuRad/features/) for definitions, conventions and the PyRadiomics mapping.

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
slide_name_radiomics_20260904_120000.csv            (one row per object, 112 columns)
slide_name_radiomics_20260904_120000_settings.json  (software/QuPath version, image, calibration, all parameters)
```

---

## Configuration

```groovy
def settings = [
    binWidth: 25,              // Intensity binning width (PyRadiomics default)
    voxelArrayShift: 0,        // Intensity shift
    force2D: true,             // All features are 2D
    distances: [1],            // GLCM pixel distance
    angles: 4                  // Four directions, matrices summed (PyRadiomics weightingNorm='no_weighting')
]
```

Input images must be 8-bit RGB brightfield images. Grayscale conversion, discretisation and mask conventions are fixed and documented in the feature reference.

## Validation

`notebooks/validation.ipynb` compares every feature with PyRadiomics 3.0.1 on identical masks: the bundled breast-cancer benchmark tile (410 cells), 20 PUMA melanoma tiles (>8,000 nuclei) and deterministic synthetic images with analytically known values. `validation/` contains the scripts (headless runner, PyRadiomics driver, agreement metrics, benchmark). Unit tests: `cd extension && ./gradlew test`.

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

## Reproducing the results

Everything reported in the paper comes out of three notebooks. They read the feature tables
included here, so no image processing or QuPath installation is needed.

```bash
pip install -r requirements.txt
jupyter lab notebooks/
```

| Notebook | What it reproduces | Runtime |
|---|---|---|
| `notebooks/validation.ipynb` | Feature-by-feature agreement with PyRadiomics 3.0.1, and the synthetic edge cases | ~15 s |
| `notebooks/example_application_puma.ipynb` | Tumour vs lymphocyte classification on 20 PUMA melanoma tiles | ~9 min |
| `notebooks/example_application_tiger.ipynb` | Tissue-compartment classification on 6 TIGER slides | ~40 s |

Only the first notebook needs PyRadiomics installed; the other two do not.

[`REPOSITORY.md`](REPOSITORY.md) explains what every directory and file in this repository is
for. The TIGER whole-slide images are public (AWS Open Data) but large and are not included;
the feature tables extracted from them are.
