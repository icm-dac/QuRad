# Welcome to QuRad

```
 ██████╗ ██╗   ██╗██████╗  █████╗ ██████╗ 
██╔═══██╗██║   ██║██╔══██╗██╔══██╗██╔══██╗
██║   ██║██║   ██║██████╔╝███████║██║  ██║
██║▄▄ ██║██║   ██║██╔══██╗██╔══██║██║  ██║
╚██████╔╝╚██████╔╝██║  ██║██║  ██║██████╔╝
 ╚══▀▀═╝  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝
```

---

## What is QuRad?

QuRad is an open-source [QuPath](https://qupath.github.io/) tool that extracts up to 119 radiomic features (103 enabled by default) directly from cell detections and user-defined regions, without any external plugins or libraries. Its output has been validated feature by feature against PyRadiomics. It is available in two interchangeable forms that share the same validated feature-extraction code:

- an **installable QuPath extension** (a `.jar` with a menu command and settings dialog), and
- a **self-contained Groovy script** that you paste into QuPath's Script Editor.

QuRad operates on **2D whole slide images (WSI)** in 8-bit RGB, extracting features from a grayscale image derived from RGB luminance. It has been validated on H&E histopathology images and is built for **QuPath 0.6 and 0.7**.

Extracted features can be used to **classify cells**, **characterize tissue regions**, **assess tissue quality**, and feed **downstream machine learning workflows**. By keeping the entire process within QuPath, QuRad streamlines the analytical workflow and preserves interactivity.

This documentation covers **QuRad 0.4** (script `QuPath_Radiomics_v3.groovy`, extension `qupath-extension-qurad-0.4.0`). Source code and releases are available on [GitHub](https://github.com/icm-dac/QuRad).

!!! warning "Research use"
    QuRad is a research tool provided under the MIT license. It is intended for research purposes and has not been validated for clinical use.

---

## Output and Feature Classes

QuRad extracts 103 features by default, organized into 7 classes, plus an optional legacy class. For a detailed description of each feature, see the [Feature Reference](features.md).

| Class | Features | Default | Description |
|-------|----------|---------|-------------|
| **First-order** | 19 | on | Intensity statistics (mean, variance, entropy, etc.) |
| **Shape 2D** | 10 | on | 2D geometric features (area, perimeter, sphericity, axis lengths) |
| **GLCM** | 23 | on | Gray Level Co-occurrence Matrix texture features |
| **GLRLM** | 16 | on | Gray Level Run Length Matrix features |
| **GLSZM** | 16 | on | Gray Level Size Zone Matrix features |
| **NGTDM** | 5 | on | Neighborhood Gray Tone Difference Matrix |
| **GLDM** | 14 | on | Gray Level Dependence Matrix features |
| **Shape (legacy 3D names)** | 16 | off | 2D quantities under PyRadiomics' 3D shape names; not recommended |

---

## Quick Links

- [Getting Started](getting-started.md) - Set up and run your first radiomics extraction
- [Feature Reference](features.md) - Definitions, conventions and PyRadiomics mapping of all features
- [Example Application](example-application.md) - End-to-end workflow tutorial

---

## Citation

If you use QuRad in your research, please cite:

- **QuPath**: Bankhead, P. et al. (2017). QuPath: Open source software for digital pathology image analysis. *Scientific Reports*, 7, 16878.
- **PyRadiomics**: van Griethuysen, J.J.M. et al. (2017). Computational Radiomics System to Decode the Radiographic Phenotype. *Cancer Research*, 77(21), e104-e107.

---

## Acknowledgments

QuRad was developed at Sorbonne Université, Institut du Cerveau — ICM (CNRS, Inria, Inserm, AP-HP, Hôpital de la Pitié-Salpêtrière, Paris), within the DAC team.

This project is co-funded by the European Union's Horizon Europe research and innovation programme Cofund SOUND.AI under the Marie Skłodowska-Curie Grant Agreement No 101081674. It is also supported by Agence Nationale de la Recherche (ANR) JCJC LOChrom (ANR-23-CE17-0027-01), by the BRAINTWIN project funded under France 2030 through the PEPR Santé Numérique programme (ref. 2025-PEPR-121554), and by the MultiPOLA project funded by the Institut national du cancer (INCa; OSIRIS25).

---

## License

MIT License - Free to use for research and commercial applications.
