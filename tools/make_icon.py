import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

root = sys.argv[1]
res = os.path.join(root, "app/src/main/res")
SS = 4

FONTS = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
]


def font(px):
    for f in FONTS:
        if os.path.exists(f):
            return ImageFont.truetype(f, px)
    return ImageFont.load_default(px)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def background(n):
    W = n * SS
    im = Image.new("RGB", (W, W))
    d = ImageDraw.Draw(im)
    c1, c2 = (18, 110, 230), (40, 200, 90)  # blue -> green, diagonal
    for i in range(2 * W):
        d.line([(i, 0), (0, i)], fill=lerp(c1, c2, i / (2 * W)), width=2)
    glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([-W * .25, -W * .25, W * .6, W * .6], fill=(255, 255, 255, 40))
    g.ellipse([W * .5, W * .55, W * 1.3, W * 1.35], fill=(0, 60, 40, 50))
    im = Image.alpha_composite(im.convert("RGBA"), glow)
    return im.convert("RGB").resize((n, n), Image.LANCZOS)


def label(n, scale=1.0):
    """Transparent layer: 'BP' monogram and 'PLAYZ' caption."""
    W = n * SS
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    big = font(int(W * .46 * scale))
    small = font(int(W * .115 * scale))
    d = ImageDraw.Draw(im)

    def centered(text, f, cy):
        bb = d.textbbox((0, 0), text, font=f)
        w, h = bb[2] - bb[0], bb[3] - bb[1]
        return (W / 2 - w / 2 - bb[0], cy - h / 2 - bb[1])

    p1 = centered("BP", big, W * .43)
    p2 = centered("PLAYZ", small, W * .74)
    shadow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.text((p1[0], p1[1] + W * .015), "BP", font=big, fill=(0, 30, 40, 150))
    sd.text((p2[0], p2[1] + W * .008), "PLAYZ", font=small, fill=(0, 30, 40, 150))
    im.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(W * .01)))
    d = ImageDraw.Draw(im)
    d.text(p1, "BP", font=big, fill=(255, 255, 255, 255))
    d.text(p2, "PLAYZ", font=small, fill=(255, 235, 120, 255))
    return im.resize((n, n), Image.LANCZOS)


def composite(n):
    im = background(n).convert("RGBA")
    im.alpha_composite(label(n, 1.0))
    return im


def mask(n, round_=False):
    m = Image.new("L", (n * SS, n * SS), 0)
    dd = ImageDraw.Draw(m)
    if round_:
        dd.ellipse([0, 0, n * SS - 1, n * SS - 1], fill=255)
    else:
        dd.rounded_rectangle([0, 0, n * SS - 1, n * SS - 1], radius=int(n * SS * .22), fill=255)
    return m.resize((n, n), Image.LANCZOS)


dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
master = composite(1024)
for k, px in dens.items():
    d = os.path.join(res, f"mipmap-{k}")
    os.makedirs(d, exist_ok=True)
    base = master.resize((px, px), Image.LANCZOS)
    a = base.copy(); a.putalpha(mask(px)); a.save(os.path.join(d, "ic_launcher.png"))
    b = base.copy(); b.putalpha(mask(px, True)); b.save(os.path.join(d, "ic_launcher_round.png"))

ad = os.path.join(res, "mipmap-anydpi-v26"); os.makedirs(ad, exist_ok=True)
dr = os.path.join(res, "drawable-nodpi"); os.makedirs(dr, exist_ok=True)
background(432).save(os.path.join(dr, "ic_launcher_background.png"))
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
fg.alpha_composite(label(432, 0.72))  # keep lettering inside the adaptive-icon safe zone
fg.save(os.path.join(dr, "ic_launcher_foreground.png"))
xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    open(os.path.join(ad, name), "w").write(xml)
if len(sys.argv) > 2:
    master.save(sys.argv[2])
print("ok")
