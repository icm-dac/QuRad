# What is in this repository

QuRad extracts radiomic features from cell detections and annotation regions inside QuPath.
This file explains what every directory and file is for, who needs it, and how to use it.

The repository holds four separate things. Knowing which one you want saves a lot of time:

| If you want to… | Go to |
|---|---|
| Install and use QuRad | `extension/` (or `src/` for the paste-in script) |
| Reproduce the paper's results | `notebooks/` — three notebooks, see below |
| Re-check the features against PyRadiomics yourself | `validation/` |
| Rebuild the manuscript | `internal/` — author-only, not published |

---

## Reproducing the results: the three notebooks

Everything reported in the paper comes out of three notebooks. Run them in this order.

| # | Notebook | What it shows | Reads | Writes | Runtime |
|---|---|---|---|---|---|
| 1 | `notebooks/validation.ipynb` | Feature-by-feature agreement with PyRadiomics 3.0.1 on identical masks; the synthetic edge cases | `validation/results/breast/`, `validation/results/synthetic/`, `validation/results/summary/` | Supplementary Tables S1–S2, Figures S1–S2 | ~15 s |
| 2 | `notebooks/example_application_puma.ipynb` | Tumour vs lymphocyte classification on 20 PUMA melanoma tiles | `example_data/puma_subset/radiomics/` (newest CSV per tile) | Figure 2a–b, Figure S4a, Figure S5, `validation/results/ml/puma_*` | ~9 min |
| 3 | `notebooks/example_application_tiger.ipynb` | Tissue-compartment classification on 6 TIGER slides | `example_data/tiger_subset/radiomics/` (newest CSV per slide) | Figure 2c–d, Figure S4b, `validation/results/ml/tiger_*` | ~40 s |

```bash
pip install -r requirements.txt
jupyter lab notebooks/
```

Notebook 1 additionally imports PyRadiomics. That is the hardest install in the stack; if you
only want the classification results, notebooks 2 and 3 do not need it.

Three environment variables change where the notebooks read and write. All have working
defaults, so you can ignore them unless you want outputs somewhere else:

| Variable | Default | Meaning |
|---|---|---|
| `QURAD_RESULTS` | `../validation/results` | where the extracted feature tables live |
| `QURAD_FIGURES` | `../internal/article_figures` | where generated figure panels are written |
| `QURAD_BENCHMARK` | `../validation/results/benchmark` | which timing run the benchmark table is built from |

---

## Every file, and why it is here

### `extension/` — the product

The installable QuPath extension, and the canonical copy of the feature code.

| Path | Why it is here |
|---|---|
| `src/main/groovy/qupath/ext/qurad/RadiomicsCalculator.groovy` | All the feature mathematics. The single source of truth. |
| `src/main/groovy/qupath/ext/qurad/RadiomicsCommand.groovy` | The menu command and the settings dialog. |
| `src/main/groovy/qupath/ext/qurad/QuRadExtension.groovy` | Registers the extension with QuPath. |
| `src/test/groovy/…/RadiomicsCalculatorTest.groovy` | 15 unit tests, including analytically known synthetic cases. Run with `./gradlew test`. |
| `src/test/groovy/…/HeadlessRunner.groovy` | Runs the calculator without the QuPath GUI, and exports the pixel masks and grayscale image it used. This is what makes the PyRadiomics comparison possible. |
| `src/test/groovy/…/Profile.groovy` | The timing harness behind the throughput benchmark. |
| `build.gradle.kts`, `settings.gradle.kts`, `gradlew` | Gradle build. `./gradlew build` produces the jar. |

### `src/` — the standalone script

`QuPath_Radiomics_v3.groovy` is the same feature code in one file you paste into QuPath's
script editor, for users who do not want to install an extension. Its HELPERS section is
copied verbatim from the extension's calculator; the two must never drift apart.

### `notebooks/` — the reproduction path

| Path | Why it is here |
|---|---|
| `validation.ipynb` | Notebook 1 above. Imports `agreement.py` and `summarize_validation.py` from `validation/`. |
| `example_application_puma.ipynb` | Notebook 2 above. |
| `example_application_tiger.ipynb` | Notebook 3 above. |
| `qurad_ml.py` | The analysis code the two application notebooks run: the leakage-free scikit-learn pipeline, the correlation filter, cross-validation, ablations, UMAP and correlation-matrix figures, feature importances. Both notebooks import it so that they cannot drift apart — that was a specific review requirement. Delete it and notebooks 2 and 3 stop working. |
| `qurad_style.py` | Shared matplotlib settings and the colour palettes, so every figure in the paper looks the same. Imported by the notebooks and by the figure scripts. |

### `example_data/` — inputs

| Path | Why it is here |
|---|---|
| `breast_cancer/ytma10_010704_benign1_ccd.tif` | The UCSB benchmark tile (CC BY 3.0), 410 labelled cells. The numerical validation image. |
| `breast_cancer/ytma10_010704_benign1_ccd_labels.tif` | Its label image — the raster ground truth the masks are checked against. |
| `breast_cancer/cell_detections.geojson` | The 410 cell polygons, traced from the label image. |
| `puma_subset/training_set_primary_roi_0NN.tif` + `_nuclei.geojson` | 20 PUMA melanoma tiles and their annotated nuclei. Needed only to re-extract features from pixels. |
| `puma_subset/radiomics/` | The QuRad output for the 20 tiles, exactly as QuPath writes it (`<tile>_radiomics_<timestamp>.csv` + `_settings.json`). Notebook 2 uses the newest file per tile, so re-extracting in QuPath and dropping the files here is all it takes to re-run the analysis on your own numbers. `archived_v0.3/` keeps the superseded QuRad 0.3 tables. |
| `tiger_subset/radiomics/*B_radiomics_*.csv` | The QuRad output for the six slides, as QuPath writes it. Notebook 3 uses the newest file per slide. The files currently there are the archived QuRad 0.3 tables the paper's TIGER numbers come from. |
| `tiger_subset/*.tif`, `*.xml`, `*.geojson` | The six TIGER whole-slide images (98–188 MB each, from AWS Open Data `s3://tiger-training/`), their ASAP XML annotations, and the GeoJSON versions QuPath imports. The TIFFs are gitignored; re-download with `aws s3 cp --no-sign-request`. |

### `validation/` — the PyRadiomics comparison harness

This is how the agreement numbers were produced. You need it only if you want to redo the
extraction rather than trust the shipped feature tables.

| Path | Why it is here |
|---|---|
| `pyradiomics_extract.py` | Runs PyRadiomics 3.0.1 on the masks and grayscale image that QuRad exported, so both tools see byte-identical input. |
| `agreement.py` | Computes the agreement statistics per feature: Pearson r, Lin's CCC, ICC(2,1), absolute and relative error, regression slope, Bland–Altman. Imported by notebook 1. |
| `summarize_validation.py` | Turns those statistics into Supplementary Tables S1–S2 and Figures S1–S2. Imported by notebook 1. |
| `make_synthetic.py` | Generates the 25 deterministic synthetic ROIs with analytically known feature values. |
| `benchmark_report.py` | Turns raw timing JSONs into Table S3 and Figure S3. |
| `export_benchmark_geojson.py` | Builds the replicated-nuclei inputs for the throughput benchmark. |
| `run_*.sh` | Driver scripts for the headless runs. They contain absolute paths and are superseded by `internal/reproducibility/run.py`. |
| `results/breast/`, `results/puma/`, `results/synthetic/` | The extracted feature tables the notebooks read. |
| `results/summary/` | The generated tables and figures the manuscript includes. |
| `results/ml/` | Cross-validation predictions and per-fold metrics, written by notebooks 2 and 3. |

### `docs/` and `site/` — the documentation website

`docs/` holds the MkDocs Markdown source (`index.md`, `getting-started.md`, `features.md`,
`example-application.md`, plus `requirements.txt` for the build). `site/` is the generated
HTML — never edit it by hand, regenerate with `mkdocs build`. `mkdocs.yml` and
`.readthedocs.yaml` configure the build; `.github/workflows/docs.yml` publishes it.

### `.github/workflows/` — continuous integration

`extension-build.yml` builds and tests the extension on every push. `extension-release.yml`
publishes a jar when a `v*` tag is pushed. `docs.yml` rebuilds and deploys the site.

### `internal/` — author-only, not part of the published repository

Nothing here is needed to use QuRad or to reproduce the results. It is the machinery behind
the manuscript.

| Path | Why it is here |
|---|---|
| `article/` | The LaTeX manuscript, the supplement, the bibliography and the built PDFs. `revision/` holds the response to reviewers and the cover letter. |
| `article_figures/` | The scripts that draw the paper's figures (`make_flow_figure.py` for Figure 1, `compose_figure2.py` for Figure 2) and the panels they assemble. |
| `reproducibility/build_paper.py` | Builds the manuscript: latexdiff, three pdfLaTeX passes with Biber, the marked-up copy, and the letters. Fails the build if the article exceeds the journal's limits. |
| `reproducibility/package.py` | Assembles the numbered files that are uploaded to the journal, into `internal/submission/`. |
| `reproducibility/verify_submission.py` | Independent re-derivation of the reported F1 scores, a check that the extension and the standalone script are byte-identical, and a check of the built PDF. |
| `reproducibility/run.py` | Re-runs the whole chain end to end: extraction, PyRadiomics, agreement, notebooks, figures. |
| `reproducibility/VERIFY.md` | The step-by-step manual for an editor or reviewer who wants to redo everything. |
| `reproducibility/requirements*.txt`, `environment.freeze.txt` | The pinned Python environment. |
| `reproducibility/reference/benchmark/` | The original measured timing JSONs behind Table S3. These are measurements, not outputs — they cannot be regenerated on different hardware. |
| `reproducibility/DATA_SOURCES.md`, `RESULTS_MAP.md` | Where each dataset came from, and which script produces which table or figure. |
| `guidelines.txt` | The journal's author guidelines, kept for reference while revising. |

---

## What is committed and what is not

`.gitignore` keeps local working material out of the published repository: `internal/`,
`site/`, and the large intermediate outputs under `validation/results/`. The reviewer-facing
slice — the extension, the script, the three notebooks with their two imported modules, the
feature tables they read, and the documentation source — is committed.

The pixel data is large. `example_data/puma_subset/` alone is 136 MB, and it is only needed
to re-extract features from pixels; the feature tables that the notebooks actually read are a
few megabytes. If the repository is published without the pixel data, notebooks 2 and 3 still
run from the committed feature tables.
