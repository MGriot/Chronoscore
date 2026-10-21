"""Generate Chronoscore's app icons from one geometry (UI kit: four seat tiles + center hub).

Writes the Android adaptive icon (vector foreground/background/monochrome), legacy mipmap PNGs
for API 24-25, and desktop PNG/ICO. Run from the repo root:  python tools/make_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "composeApp/src/androidMain/res"
DESKTOP = ROOT / "composeApp/icons"
COMPOSE_RES = ROOT / "composeApp/src/commonMain/composeResources/drawable"

# Adaptive icon canvas is 108 units; the safe zone is the centered 66-unit circle.
BG = "#0D0D0D"
HUB_FILL = "#18181B"
HUB_STROKE = "#3F3F46"
# Seat accents deepened ~12% like the "Colored" seat style.
TILES = ["#1F4ECF", "#C61A40", "#13893F", "#BF6305"]

LO, HI, GAP, R = 31.0, 77.0, 2.5, 4.5
MID = (LO + HI) / 2
HUB_W, HUB_H = 26.0, 11.0
DOTS = [MID - 6.5, MID, MID + 6.5]
DOT_R = 1.35


def tile_rects():
    a, b = (LO, MID - GAP / 2), (MID + GAP / 2, HI)
    return [(a[0], a[0], a[1], a[1]), (b[0], a[0], b[1], a[1]), (a[0], b[0], a[1], b[1]), (b[0], b[0], b[1], b[1])]


HUB = (MID - HUB_W / 2, MID - HUB_H / 2, MID + HUB_W / 2, MID + HUB_H / 2)


def rrect_path(x0, y0, x1, y1, r):
    w, h = x1 - x0, y1 - y0
    return (
        f"M{x0 + r:.2f},{y0:.2f}h{w - 2 * r:.2f}a{r},{r} 0 0 1 {r},{r}v{h - 2 * r:.2f}"
        f"a{r},{r} 0 0 1 -{r},{r}h-{w - 2 * r:.2f}a{r},{r} 0 0 1 -{r},-{r}v-{h - 2 * r:.2f}"
        f"a{r},{r} 0 0 1 {r},-{r}z"
    )


def circle_path(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r},{r} 0 1 0 {2 * r:.2f},0a{r},{r} 0 1 0 -{2 * r:.2f},0z"


def vector(paths):
    body = "\n".join(
        f'    <path android:fillColor="{fill}"'
        + (f' android:strokeColor="{stroke}" android:strokeWidth="0.9"' if stroke else "")
        + f' android:pathData="{d}" />'
        for d, fill, stroke in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f"{body}\n</vector>\n"
    )


def foreground_paths(mono=False):
    paths = [(rrect_path(*t, R), "#FFFFFF" if mono else c, None) for t, c in zip(tile_rects(), TILES)]
    if mono:
        # Themed (monochrome) icons are tinted one color by the launcher: the four tiles alone.
        return paths
    paths.append((rrect_path(*HUB, HUB_H / 2), HUB_FILL, HUB_STROKE))
    paths += [(circle_path(x, MID, DOT_R), "#FFFFFF", None) for x in DOTS]
    return paths


def render(size, rounded_bg, scale_to_safe=False):
    """Raster render; 4x supersampled. Legacy icons use the full canvas scaled so tiles fill it."""
    ss = size * 4
    img = Image.new("RGBA", (ss, ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Legacy/desktop icons: show the 108 canvas cropped to its central 76 units.
    crop = 16.0 if scale_to_safe else 0.0
    k = ss / (108 - 2 * crop)

    def box(x0, y0, x1, y1):
        return [(x0 - crop) * k, (y0 - crop) * k, (x1 - crop) * k, (y1 - crop) * k]

    if rounded_bg:
        d.rounded_rectangle([0, 0, ss - 1, ss - 1], radius=ss * 0.22, fill=BG)
    else:
        d.rectangle([0, 0, ss, ss], fill=BG)
    for t, c in zip(tile_rects(), TILES):
        d.rounded_rectangle(box(*t), radius=R * k, fill=c)
    d.rounded_rectangle(box(*HUB), radius=HUB_H / 2 * k, fill=HUB_FILL, outline=HUB_STROKE, width=max(1, int(0.9 * k)))
    for x in DOTS:
        d.ellipse(box(x - DOT_R, MID - DOT_R, x + DOT_R, MID + DOT_R), fill="#FFFFFF")
    return img.resize((size, size), Image.LANCZOS)


def circle_mask(img):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, mask=mask)
    return out


def main():
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "drawable/ic_launcher_foreground.xml").write_text(vector(foreground_paths()), encoding="utf-8")
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(vector(foreground_paths(mono=True)), encoding="utf-8")
    (RES / "values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{BG}</color>\n</resources>\n',
        encoding="utf-8",
    )
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher", "ic_launcher_round"):
        (RES / f"mipmap-anydpi-v26/{name}.xml").write_text(adaptive, encoding="utf-8")

    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        render(px, rounded_bg=True, scale_to_safe=True).save(folder / "ic_launcher.png")
        circle_mask(render(px, rounded_bg=False, scale_to_safe=True)).save(folder / "ic_launcher_round.png")

    DESKTOP.mkdir(parents=True, exist_ok=True)
    COMPOSE_RES.mkdir(parents=True, exist_ok=True)
    master = render(512, rounded_bg=True, scale_to_safe=True)
    master.save(DESKTOP / "chronoscore.png")
    master.save(COMPOSE_RES / "app_icon.png")
    master.save(DESKTOP / "chronoscore.ico", sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
    render(1024, rounded_bg=True, scale_to_safe=True).save(ROOT / "docs/images/icon.png")
    print("icons written")


if __name__ == "__main__":
    (ROOT / "docs/images").mkdir(parents=True, exist_ok=True)
    main()
