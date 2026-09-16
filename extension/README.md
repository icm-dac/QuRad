# QuRad — QuPath extension

This is the packaged QuPath extension build of QuRad. It exposes the same feature
extraction as the standalone script
[`src/QuPath_Radiomics_v3.groovy`](../src/QuPath_Radiomics_v3.groovy): the numerics in
[`RadiomicsCalculator.groovy`](src/main/groovy/qupath/ext/qurad/RadiomicsCalculator.groovy)
are the source of truth and the script's helper section is generated from them, so both
produce identical output. Every default feature agrees with PyRadiomics 3.0.1 to
floating-point precision on identical masks (see `notebooks/validation.ipynb`).

## Requirements

- Loads on QuPath **0.6 and 0.7**. The jar is built against QuPath 0.6 (a Java 21 baseline),
  and QuPath 0.7's newer Java 25 runtime reads that older bytecode without issue. The extension
  uses only API that is stable across both releases.
- To build: a **JDK 21** toolchain (matching QuPath 0.6). The Gradle wrapper is included.

## Build

```bash
cd extension
# With a JDK 21 on JAVA_HOME (or discoverable as a Gradle toolchain):
./gradlew build
```

The installable jar is written to `build/libs/qupath-extension-qurad-<version>.jar`
(the plain jar, not the `-sources`/`-javadoc` ones). `./gradlew build` also runs the
unit tests that guard the feature numerics.

## Install

Drag `qupath-extension-qurad-<version>.jar` onto a running QuPath window, or use
**Extensions → Manage extensions**. Then run it via **Extensions → QuRad → Extract
radiomics features…**, which opens a settings dialog (bin width, GLCM distance, which objects,
which feature classes, CSV/measurement output). The optional legacy 3D-named shape class is
off by default.

## Headless use and benchmarking

The test source set contains a headless runner that applies the calculator to an image plus a
GeoJSON file of objects without a QuPath GUI (used for the PyRadiomics validation and the
benchmark in the article):

```bash
./gradlew headless -PrunnerArgs="--image tile.tif --objects nuclei.geojson --out features.csv \
    [--shape true] [--binWidth 25] [--distance 1] [--classes firstorder,glcm] [--repeat N] \
    [--masks masks_dir] [--grayOut gray.png] [--timing timing.csv] [--json settings.json]"
./gradlew profile -PrunnerArgs="tile.tif nuclei.geojson 200"   # per-stage timing
```

## Layout

| Path | Purpose |
|------|---------|
| `src/main/groovy/qupath/ext/qurad/RadiomicsCalculator.groovy` | Feature math (statically compiled hot paths), rasterisation, CSV and settings-JSON writers. **Do not change numerics without re-running `notebooks/validation.ipynb`.** |
| `src/main/groovy/qupath/ext/qurad/QuRadExtension.groovy` | Registers the menu command. |
| `src/main/groovy/qupath/ext/qurad/RadiomicsCommand.groovy` | Settings dialog + run loop + measurement output. |
| `src/test/groovy/qupath/ext/qurad/RadiomicsCalculatorTest.groovy` | Unit tests with analytically known values (uniform, checkerboard, tiny inputs, rasterisation rule, NGTDM isolated pixels). |
| `src/test/groovy/qupath/ext/qurad/HeadlessRunner.groovy` | Headless extraction (+ mask/grayscale export) for validation and benchmarking. |
| `src/test/groovy/qupath/ext/qurad/Profile.groovy` | Per-stage profiler. |

CI builds and tests this project on every push touching `extension/` (see
`.github/workflows/extension-build.yml`).
