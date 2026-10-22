"""Copy the offscreen renders (build/render-smoke, from RenderSmokeTest) into docs/images.

Dialog renders are cropped to the dialog itself; everything is downscaled for the repo.
Run after `./gradlew :composeApp:desktopTest`:  python tools/make_doc_images.py [android_screenshots_dir]
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent.parent
RENDERS = ROOT / "composeApp/build/render-smoke"
OUT = ROOT / "docs/images"

TABLES = {
    "kit_desktop_colored": "desktop-colored",
    "kit_desktop_neutral": "desktop-neutral",
    "kit_table_neutral": "phone-neutral",
    "kit_table_colored": "phone-colored",
    "phone_8": "phone-8-players",
    "feature_board_game_timer_phone": "phone-timer",
}
DIALOGS = ["keypad", "utilities", "log", "menu", "edit_all", "counters_all", "library", "editor"]


def save(img: Image.Image, name: str, max_width: int):
    if img.width > max_width:
        img = img.resize((max_width, round(img.height * max_width / img.width)), Image.LANCZOS)
    img.convert("RGB").save(OUT / f"{name}.png", optimize=True)


def crop_to_content(img: Image.Image) -> Image.Image:
    """Trim the flat scrim around a dialog render."""
    rgb = img.convert("RGB")
    bg = Image.new("RGB", rgb.size, rgb.getpixel((0, 0)))
    box = ImageChops.difference(rgb, bg).getbbox()
    return rgb.crop(box) if box else rgb


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for src, name in TABLES.items():
        save(Image.open(RENDERS / f"{src}.png"), name, 960)
    for d in DIALOGS:
        save(crop_to_content(Image.open(RENDERS / f"dialog_{d}.png")), f"dialog-{d.replace('_', '-')}", 480)
    if len(sys.argv) > 1:
        for shot in sorted(Path(sys.argv[1]).glob("android-*.png")):
            save(Image.open(shot), shot.stem, 540)
    print("\n".join(sorted(p.name for p in OUT.glob("*.png"))))


if __name__ == "__main__":
    main()
