# QuRad — QuPath extension

This is the packaged QuPath extension build of QuRad. It exposes the same
PyRadiomics-compatible feature extraction as the standalone script
[`src/QuPath_Radiomics_v3.groovy`](../src/QuPath_Radiomics_v3.groovy) — the numerics in
[`RadiomicsCalculator.groovy`](src/main/groovy/qupath/ext/qurad/RadiomicsCalculator.groovy)
are ported **verbatim** from that validated script, so output matches the published
validation (median Pearson *r* = 0.9992 vs PyRadiomics).

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
radiomics features…**, which opens a settings dialog (bin width, which objects, which
feature classes, CSV/measurement output).

## Layout

| Path | Purpose |
|------|---------|
| `src/main/groovy/qupath/ext/qurad/RadiomicsCalculator.groovy` | Feature math, ported verbatim from the script (lines 51–1207). **Do not change numerics without re-running `notebooks/validation.ipynb`.** |
| `src/main/groovy/qupath/ext/qurad/QuRadExtension.groovy` | Registers the menu command. |
| `src/main/groovy/qupath/ext/qurad/RadiomicsCommand.groovy` | Settings dialog + run loop + CSV/measurement output. |
| `src/test/groovy/qupath/ext/qurad/RadiomicsCalculatorTest.groovy` | Regression test locking known feature values. |

CI builds and tests this project on every push touching `extension/` (see
`.github/workflows/extension-build.yml`).
