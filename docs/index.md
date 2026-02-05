# QuPath Radiomics Extension (QuRad)

```
 ██████╗ ██╗   ██╗██████╗  █████╗ ██████╗ 
██╔═══██╗██║   ██║██╔══██╗██╔══██╗██╔══██╗
██║   ██║██║   ██║██████╔╝███████║██║  ██║
██║▄▄ ██║██║   ██║██╔══██╗██╔══██║██║  ██║
╚██████╔╝╚██████╔╝██║  ██║██║  ██║██████╔╝
 ╚══▀▀═╝  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝
```

**Comprehensive radiomics feature extraction for QuPath**

Extract 120 PyRadiomics-compatible features from cell detections and annotations directly within QuPath.

---

## Features

- **120 Radiomics Features** - Complete PyRadiomics feature set
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

**Total: 120 features + metadata columns**

---

## Quick Links

- [Installation](installation.md) - Get started with QuRad
- [Quick Start](quickstart.md) - Run your first radiomics extraction
- [Feature Reference](features.md) - Complete list of all 120 features
- [Configuration](configuration.md) - Customize extraction settings
- [Example Application](example-application.md) - End-to-end workflow tutorial

---

## Citation

If you use QuRad in your research, please cite:

- **QuPath**: Bankhead, P. et al. (2017). QuPath: Open source software for digital pathology image analysis. *Scientific Reports*, 7, 16878.
- **PyRadiomics**: van Griethuysen, J.J.M. et al. (2017). Computational Radiomics System to Decode the Radiographic Phenotype. *Cancer Research*, 77(21), e104-e107.

---

## License

MIT License - Free to use for research and commercial applications.

---

## Acknowledgments

QuRad is inspired by PyRadiomics and designed for seamless QuPath integration.
