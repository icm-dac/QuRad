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

QuRad is an open-source [QuPath](https://qupath.github.io/) Groovy-based extension that extracts 120 radiomic features directly from cell detections and user-defined regions. It is implemented as a self-contained Groovy script that runs within QuPath and does not require any external plugins or libraries.

QuRad operates on **2D whole slide images (WSI)**, extracting features from grayscale images derived from RGB luminance. It has been validated on H&E histopathology images, and is compatible with **QuPath 0.5.0 and 0.6.0**.

Extracted features can be used to **classify cells**, **characterize tissue regions**, **assess tissue quality**, and feed **downstream machine learning workflows**. By keeping the entire process within QuPath, QuRad streamlines the analytical workflow and preserves interactivity.

This documentation covers **QuRad v3**. Source code and releases are available on [GitHub](https://github.com/icm-dac/QuRad).

!!! warning "Research use"
    QuRad is a research tool provided under the MIT license. It is intended for research purposes and has not been validated for clinical use.

---

## Output and Feature Classes

QuRad extracts 120 features organized into 8 classes. For a detailed description of each feature, see the [Feature Reference](features.md).

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

---

## Quick Links

- [Getting Started](getting-started.md) - Set up and run your first radiomics extraction
- [Feature Reference](features.md) - Detailed description of all 120 features
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
