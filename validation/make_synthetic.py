"""Deterministic synthetic images and ROIs with analytically predictable radiomic values."""
import json
from pathlib import Path
import numpy as np
from PIL import Image

out = Path(__file__).resolve().parent / "synthetic"
out.mkdir(exist_ok=True)
rng = np.random.default_rng(0)

def rect(x, y, w, h):
    return [[x, y], [x + w, y], [x + w, y + h], [x, y + h], [x, y]]

def circle(cx, cy, r, n=64):
    t = np.linspace(0, 2 * np.pi, n, endpoint=False)
    return [[float(cx + r * np.cos(a)), float(cy + r * np.sin(a))] for a in t] + [[float(cx + r), float(cy)]]

def star(cx, cy, r1, r2, n=7):
    pts = []
    for k in range(2 * n):
        r = r1 if k % 2 == 0 else r2
        a = np.pi * k / n
        pts.append([float(cx + r * np.cos(a)), float(cy + r * np.sin(a))])
    return pts + [pts[0]]

def feature(name, rings, expected=None):
    return {"type": "Feature", "geometry": {"type": "Polygon", "coordinates": rings},
            "properties": {"objectType": "detection", "name": name, "expected": expected or {}}}

def save(case, img, feats):
    Image.fromarray(img.astype(np.uint8)).save(out / f"{case}.png")
    (out / f"{case}.geojson").write_text(json.dumps({"type": "FeatureCollection", "features": feats}))
    print(case, img.shape, len(feats), "objects")

H = W = 96
def rgb(gray):
    return np.stack([gray, gray, gray], axis=-1)

# 1. uniform image: one-bin region, texture matrices degenerate
g = np.full((H, W), 128, np.uint8)
save("uniform", rgb(g), [feature("uniform_square", [rect(20, 20, 30, 30)],
      {"firstorder_Entropy": 0, "firstorder_Uniformity": 1, "firstorder_Mean": 128, "glcm_JointEnergy": 1,
       "glcm_Contrast": 0, "ngtdm_Coarseness": 1e6, "glszm_ZonePercentage": 1 / 900, "shape2D_PixelSurface": 900})])

# 2. checkerboard 0/100
yy, xx = np.mgrid[0:H, 0:W]
g = np.where((xx + yy) % 2 == 0, 0, 100).astype(np.uint8)
save("checkerboard", rgb(g), [feature("checker_square", [rect(10, 10, 20, 20)],
      {"firstorder_Mean": 50, "firstorder_Entropy": 1, "glcm_Contrast": 16 * (2 * 20 * 19) / (2 * 20 * 19 + 2 * 19 * 19),
       "glrlm_ShortRunEmphasis": None})])

# 3. horizontal gradient (value = 2*x, 0..190)
g = np.clip(2 * xx, 0, 255).astype(np.uint8)
save("gradient", rgb(g), [feature("gradient_rect", [rect(0, 30, 96, 20)], {"firstorder_Minimum": 0, "firstorder_Maximum": 190, "firstorder_Mean": 95})])

# 4. random noise with several ROI shapes (circle, elongated bar, diagonal bar, star, ring)
g = rng.integers(0, 256, size=(H, W)).astype(np.uint8)
feats = [feature("noise_circle", [circle(48, 48, 15)]),
         feature("noise_bar_1x40", [rect(5, 5, 40, 1)]),
         feature("noise_bar_2x40", [rect(5, 10, 40, 2)]),
         feature("noise_diagonal", [[[5, 20], [45, 60], [43, 62], [3, 22], [5, 20]]]),
         feature("noise_star", [star(70, 25, 20, 8)]),
         feature("noise_ring", [rect(50, 55, 30, 30), rect(60, 65, 10, 10)]),
         feature("noise_subpixel_square", [rect(10.3, 70.6, 20.4, 15.2)])]
save("noise", rgb(g), feats)

# 5. tiny ROIs on noise
feats = [feature("tiny_1px", [rect(10, 10, 1, 1)]), feature("tiny_1x2", [rect(20, 10, 2, 1)]),
         feature("tiny_2x2", [rect(30, 10, 2, 2)]), feature("tiny_3x3", [rect(40, 10, 3, 3)]),
         feature("tiny_5x5", [rect(50, 10, 5, 5)])]
save("tiny", rgb(g), feats)

# 6. ROIs touching / crossing the image border
feats = [feature("border_topleft", [rect(0, 0, 20, 20)]), feature("border_right", [rect(76, 40, 20, 20)]),
         feature("border_crossing", [rect(80, 80, 30, 30)])]
save("border", rgb(g), feats)

# 7. near-uniform: one different pixel (same bin) and one pixel in another bin
g = np.full((H, W), 128, np.uint8); g[30, 30] = 129; g[40, 40] = 160
save("nearuniform", rgb(g), [feature("near_uniform_same_bin", [rect(20, 20, 15, 15)], {"firstorder_Entropy": 0}),
                             feature("near_uniform_two_bins", [rect(20, 20, 30, 30)])])

# 8. two-level halves and a coloured (non-grey) image to exercise luminance conversion
g = np.where(xx < 48, 50, 200).astype(np.uint8)
col = np.stack([rng.integers(0, 256, (H, W)), rng.integers(0, 256, (H, W)), rng.integers(0, 256, (H, W))], -1).astype(np.uint8)
save("twolevel", rgb(g), [feature("two_level", [rect(28, 28, 40, 40)], {"firstorder_Mean": 125, "firstorder_Entropy": 1})])
save("colour", col, [feature("colour_circle", [circle(48, 48, 20)]), feature("colour_square", [rect(5, 5, 30, 30)])])

# 9. large ROI (512 x 512 noise)
big = rng.integers(0, 256, size=(600, 600)).astype(np.uint8)
save("large", rgb(big), [feature("large_512", [rect(40, 40, 512, 512)]), feature("large_circle", [circle(300, 300, 250, 256)])])
