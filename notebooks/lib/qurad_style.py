"""Shared figure style for the QuRad article (CSBJ figure guide: >= 6 pt symbols, >= 0.5 pt lines,
no grid lines, lower-case panel labels in brackets, colour-blind-safe palettes)."""
import matplotlib as mpl

FONT = ['Liberation Sans', 'DejaVu Sans', 'Helvetica', 'Arial']
BASE = 9          # pt, body text of labels
SMALL = 8         # pt, tick labels
PANEL = 11        # pt, panel labels (a), (b), ...
DOUBLE_COLUMN_IN = 7.09   # 180 mm
SINGLE_COLUMN_IN = 3.46   # 88 mm
DPI = 300

# Okabe-Ito colours: no red/green pairs, distinct hues
PUMA_PALETTE = {"Tumor": "#D55E00", "Lymphocyte": "#0072B2"}
TIGER_PALETTE = {"invasive tumor": "#D55E00", "tumor-associated stroma": "#0072B2", "healthy glands": "#000000"}
CLASS_COLOURS = {"firstorder": "#0072B2", "shape2D": "#E69F00", "glcm": "#009E73", "glrlm": "#D55E00",
                 "glszm": "#CC79A7", "ngtdm": "#56B4E9", "gldm": "#F0E442", "shape": "#999999"}


def _register_fonts():
    import glob, os
    from matplotlib import font_manager as fm
    for pattern in ['/usr/share/fonts/**/LiberationSans*.ttf', '/usr/share/fonts/**/DejaVuSans*.ttf']:
        for f in glob.glob(pattern, recursive=True):
            try:
                fm.fontManager.addfont(f)
            except Exception:
                pass


def apply():
    _register_fonts()
    mpl.rcParams.update({
        'font.family': 'sans-serif', 'font.sans-serif': FONT, 'mathtext.fontset': 'dejavusans',
        'font.size': BASE, 'axes.titlesize': BASE, 'axes.labelsize': BASE, 'xtick.labelsize': SMALL,
        'ytick.labelsize': SMALL, 'legend.fontsize': BASE, 'legend.title_fontsize': BASE, 'figure.titlesize': BASE,
        'axes.linewidth': 0.6, 'lines.linewidth': 1.0, 'lines.markersize': 4, 'patch.linewidth': 0.6,
        'xtick.major.width': 0.6, 'ytick.major.width': 0.6, 'xtick.major.size': 2.5, 'ytick.major.size': 2.5,
        'xtick.minor.visible': False, 'ytick.minor.visible': False, 'axes.grid': False,
        'legend.frameon': False, 'savefig.dpi': DPI, 'figure.dpi': 100, 'pdf.fonttype': 42, 'ps.fonttype': 42,
        'axes.spines.top': False, 'axes.spines.right': False,
    })


def panel_label(fig, text, x, y):
    """Bold lower-case panel label in brackets at figure coordinates (x, y)."""
    fig.text(x, y, text, fontsize=PANEL, fontweight='bold', ha='left', va='top')


def scale_bar(ax, length_px, x_frac=0.97, y_frac=0.04, colour='black', height_frac=0.012):
    """Horizontal scale bar of length_px data units in the lower-right corner of an image axis."""
    import matplotlib.patches as mpatches
    x0, x1 = ax.get_xlim(); y0, y1 = ax.get_ylim()
    w = abs(x1 - x0); h = abs(y1 - y0)
    top = max(y0, y1); bottom = min(y0, y1)
    xr = min(x0, x1) + x_frac * w
    yb = bottom + y_frac * h if y0 < y1 else top - y_frac * h - height_frac * h
    ax.add_patch(mpatches.Rectangle((xr - length_px, yb), length_px, height_frac * h, facecolor=colour, edgecolor='white', linewidth=0.4))
