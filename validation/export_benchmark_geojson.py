"""Export the UCSB benchmark label mask as QuPath GeoJSON detections.

Contours are traced with marching squares at level 0.5 in pixel-index space and
shifted by +0.5 so that they live in QuPath's coordinate system, where pixel (i, j)
occupies [i, i+1) x [j, j+1). Rasterising these polygons with the pixel-centre rule
(the convention used by QuPath and QuRad) reproduces the original label mask exactly.
"""
import json, sys
from pathlib import Path
import numpy as np
from PIL import Image
from skimage import measure
from shapely.geometry import Polygon, MultiPolygon, mapping

base = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "example_data" / "breast_cancer"
labels = np.array(Image.open(base / "ytma10_010704_benign1_ccd_labels.tif")).astype(np.int32)
features = []
for label_id in np.unique(labels)[1:]:
    binary = np.pad((labels == label_id).astype(np.uint8), 1)
    rings = []
    for contour in measure.find_contours(binary, level=0.5, fully_connected="low"):
        pts = contour[:, ::-1] - 1.0 + 0.5  # (x, y) in QuPath coordinates
        ring = Polygon(pts)
        if ring.is_valid and ring.area > 0:
            rings.append(ring)
    rings.sort(key=lambda r: r.area, reverse=True)
    parts, holes_used = [], set()
    for i, outer in enumerate(rings):
        if i in holes_used:
            continue
        holes = []
        for j, inner in enumerate(rings):
            if j != i and j not in holes_used and outer.contains(inner):
                holes.append(inner.exterior.coords)
                holes_used.add(j)
        parts.append(Polygon(outer.exterior.coords, holes))
        holes_used.add(i)
    geom = parts[0] if len(parts) == 1 else MultiPolygon(parts)
    features.append({"type": "Feature", "geometry": mapping(geom),
                     "properties": {"objectType": "detection", "name": f"label_{int(label_id)}", "label_id": int(label_id)}})
out = base / "cell_detections.geojson"
out.write_text(json.dumps({"type": "FeatureCollection", "features": features}))
print(f"wrote {len(features)} detections to {out}")
