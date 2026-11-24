"""Draws the Uzbek Keys icon (green, four keys: ö ğ ş ç).

Usage:
  python tools/make_icon.py android <res-dir>   # Android launcher PNGs (legacy + adaptive foreground)
  python tools/make_icon.py ico <file.ico>      # Windows icon
  python tools/make_icon.py png <file.png> <size>
Needs Pillow (pip install pillow).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

FONTS = [
    r"C:\Windows\Fonts\seguisb.ttf",
    r"C:\Windows\Fonts\segoeuib.ttf",
    r"C:\Windows\Fonts\arialbd.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf",
    "/System/Library/Fonts/SFNS.ttf",
]
TOP, BOTTOM = (51, 168, 84), (26, 107, 51)
INK, SHADOW, WHITE = (26, 92, 46), (20, 80, 40), (255, 255, 255)
LETTERS = ["ö", "ğ", "ş", "ç"]
BIG = 1024


def font(size):
    for path in FONTS:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    raise SystemExit("no suitable font found")


def gradient(size):
    img = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        d.line([(0, y), (size, y)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)))
    return img


def draw_keys(img, x0, y0, block):
    """Draws the 2x2 keys inside a square of side `block` at (x0, y0)."""
    d = ImageDraw.Draw(img)
    key = block * 330 / 720
    gap = block * 60 / 720
    rad = block * 64 / 720
    drop = block * 14 / 720
    f = font(int(block * 230 / 720))
    for i, ch in enumerate(LETTERS):
        x = x0 + (i % 2) * (key + gap)
        y = y0 + (i // 2) * (key + gap)
        d.rounded_rectangle([x, y + drop, x + key, y + key + drop], rad, fill=SHADOW)
        d.rounded_rectangle([x, y, x + key, y + key], rad, fill=WHITE)
        d.text((x + key / 2, y + key / 2), ch, font=f, fill=INK, anchor="mm")


def full_icon(rounded):
    img = gradient(BIG)
    m = (BIG - 720) / 2
    draw_keys(img, m, m, 720)
    if rounded:
        mask = Image.new("L", (BIG, BIG), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, BIG - 1, BIG - 1], int(BIG * 0.22), fill=255)
        out = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        img = out
    return img


def adaptive_foreground():
    """108dp canvas; keys kept inside the 66dp safe zone."""
    img = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
    block = BIG * 0.56
    m = (BIG - block) / 2
    draw_keys(img, m, m, block)
    return img


def android(res_dir):
    legacy = full_icon(rounded=True)
    fg = adaptive_foreground()
    for name, scale in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]:
        folder = os.path.join(res_dir, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        legacy.resize((int(48 * scale), int(48 * scale)), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
        fg.resize((int(108 * scale), int(108 * scale)), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_foreground.png"))
    print("android icons written to", res_dir)


def ico(path):
    full_icon(rounded=True).resize((256, 256), Image.LANCZOS).save(
        path, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("icon written to", path)


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    kind, target = sys.argv[1], sys.argv[2]
    if kind == "android":
        android(target)
    elif kind == "ico":
        ico(target)
    elif kind == "png":
        size = int(sys.argv[3]) if len(sys.argv) > 3 else BIG
        full_icon(rounded=False).convert("RGB").resize((size, size), Image.LANCZOS).save(target)
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
