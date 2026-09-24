# QuRad

```
 ██████╗ ██╗   ██╗██████╗  █████╗ ██████╗ 
██╔═══██╗██║   ██║██╔══██╗██╔══██╗██╔══██╗
██║   ██║██║   ██║██████╔╝███████║██║  ██║
██║▄▄ ██║██║   ██║██╔══██╗██╔══██║██║  ██║
╚██████╔╝╚██████╔╝██║  ██║██║  ██║██████╔╝
 ╚══▀▀═╝  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝
```

Radiomics feature extraction inside QuPath.

QuRad computes 103 radiomic features for every cell detection or annotation region in an
image and writes them to a CSV — no export to Python, no external tools. The features are
the PyRadiomics definitions (first-order, 2D shape, GLCM, GLRLM, GLSZM, NGTDM, GLDM), and
every one of them has been checked against PyRadiomics 3.0.1 on identical pixel masks.

Documentation: https://icm-dac.github.io/QuRad/

## Install

QuRad works on QuPath 0.6 and 0.7 and comes in two forms that give identical results.

**Extension.** Download `qupath-extension-qurad-<version>.jar` from
[Releases](https://github.com/icm-dac/QuRad/releases), drag it onto the QuPath window and
restart. You get a menu entry, **Extensions ▸ QuRad ▸ Extract radiomics features…**, with a
settings dialog. To build it yourself: `cd extension && ./gradlew build` (JDK 21); the jar
lands in `extension/build/libs/`.

**Script.** Open `src/QuPath_Radiomics_v3.groovy` in QuPath's script editor and run it. The
settings are the block at the top of the file. Because QuPath can run a script on every image
in a project (**Run ▸ Run for project…**), this is the easy way to process a whole batch.

## Use

Open an image that has cell detections (from QuPath's own cell detection, StarDist, Cellpose,
or imported GeoJSON) or annotation regions, run QuRad, and choose what to process and which
feature classes you want. Images must be 8-bit RGB brightfield; anything else is refused with
a message.

For every image QuRad writes two files into `<project>/radiomics/`:

```
<image>_radiomics_<timestamp>.csv             one row per object, 112 columns
<image>_radiomics_<timestamp>_settings.json   QuRad and QuPath versions, image, calibration, every parameter
```

The CSV has 9 metadata columns (image, object ID, type, class, centroid, pixel count, pixel
size) and the 103 features, named `class_FeatureName` exactly as in PyRadiomics. The values can
also go straight into QuPath's measurement table for measurement maps and classifiers.

Defaults: bin width 25, GLCM distance 1 pixel, texture matrices summed over the four in-plane
directions (PyRadiomics `weightingNorm='no_weighting'`). A 16-feature class of 2D quantities
reported under PyRadiomics' 3D shape names is available for old pipelines but off by default.
Details of every convention are in the [feature reference](https://icm-dac.github.io/QuRad/features/).

Single-threaded, about 1,900 nuclei per second on a server CPU.

## Reproducing the paper

Three notebooks produce every number in the article, from feature tables included here:

| Notebook | Shows | Runtime |
|---|---|---|
| `notebooks/validation.ipynb` | feature-by-feature agreement with PyRadiomics 3.0.1, plus synthetic edge cases | ~15 s |
| `notebooks/example_application_puma.ipynb` | tumour vs lymphocyte classification on 20 PUMA melanoma tiles | ~9 min |
| `notebooks/example_application_tiger.ipynb` | tissue-compartment classification on 6 TIGER breast-cancer slides | ~1 min |

```bash
pip install -r requirements.txt
jupyter lab notebooks/
```

Only the first notebook needs PyRadiomics installed. The two application notebooks read the
QuRad output exactly as QuPath writes it (`example_data/*/radiomics/`), so if you re-extract in
QuPath and drop the new files there, they analyse yours.

Notebook 1 compares QuRad with PyRadiomics on identical pixel masks; both sides are in
`example_data/*/pyradiomics/` (headless QuRad run, exported masks, PyRadiomics output). To
regenerate them from the images:

```bash
cd extension && ./gradlew headless -PrunnerArgs="--image <tif> --objects <geojson> --out <dir>/qurad.csv --masks <dir>/masks --grayOut <dir>/gray.png"
python notebooks/lib/pyradiomics_extract.py --image <tif> --gray <dir>/gray.png --masks <dir>/masks --out <dir>/pyradiomics_noweighting.csv
```

The timing benchmark (Table S3, Figure S3) is rerun with `notebooks/lib/run_benchmark.sh` on an
idle machine and summarised with `python notebooks/lib/benchmark_report.py`; the raw timings,
including CPU model and JVM, are in `example_data/benchmark/`.

The code the notebooks import is in `notebooks/lib/`; everything they write goes to
`notebooks/results/` (tables, figures, per-object predictions). `QURAD_DATA` and
`QURAD_RESULTS` override the input and output folders.

## Data

- `example_data/breast_cancer/` — one tissue-microarray tile from the UCSB Bio-Segmentation
  benchmark (CC BY 3.0). If you use it, cite: E. Drelie Gelasca, J. Byun, B. Obara and
  B. S. Manjunath, "Evaluation and benchmark for biological image segmentation," *IEEE ICIP*
  2008, pp. 1816–1819.
- PUMA melanoma tiles: [PUMA challenge](https://puma.grand-challenge.org/) (CC0). The feature
  tables are included; the images are not.
- TIGER breast-cancer slides: [TIGER challenge](https://tiger.grand-challenge.org/), public on
  AWS Open Data (`s3://tiger-training/`). The feature tables are included; the slides are not.

## Citation

A manuscript describing QuRad and its validation is under review. Until it appears, please cite
the project archive, DOI [10.5281/zenodo.20628110](https://doi.org/10.5281/zenodo.20628110),
together with [QuPath](https://qupath.github.io) and [PyRadiomics](https://pyradiomics.readthedocs.io).

## License

MIT.
