"""Generates Ondalune's adaptive launcher icons: a crescent moon and a beamed note setting into
layered waves under a starry sky. Writes VectorDrawable layers, adaptive-icon XML, legacy PNG
mipmaps (via rsvg-convert) and preview PNGs.

usage: python3 tools/icongen.py <res_dir> <out_dir>
"""
import math
import os
import subprocess
import sys

RES, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)

# ---------------------------------------------------------------- geometry (108 x 108 viewport)
def crescent(cx, cy, R, ix, iy, r):
    """Outer circle (cx,cy,R) minus inner circle (ix,iy,r), opening towards the inner circle."""
    dx, dy = ix - cx, iy - cy
    d = math.hypot(dx, dy)
    a = (R * R - r * r + d * d) / (2 * d)
    h = math.sqrt(R * R - a * a)
    mx, my = cx + a * dx / d, cy + a * dy / d
    p1 = (mx - h * dy / d, my + h * dx / d)
    p2 = (mx + h * dy / d, my - h * dx / d)
    f = lambda p: f"{p[0]:.2f},{p[1]:.2f}"
    return (f"M{f(p1)} A{R},{R} 0 1,1 {f(p2)} A{r},{r} 0 1,0 {f(p1)} Z")


def ellipse(cx, cy, rx, ry, deg):
    k = 0.5523
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    p = lambda x, y: (cx + x * c - y * s, cy + x * s + y * c)
    pts = [(p(rx, 0), p(rx, k * ry), p(k * rx, ry), p(0, ry)),
           (p(0, ry), p(-k * rx, ry), p(-rx, k * ry), p(-rx, 0)),
           (p(-rx, 0), p(-rx, -k * ry), p(-k * rx, -ry), p(0, -ry)),
           (p(0, -ry), p(k * rx, -ry), p(rx, -k * ry), p(rx, 0))]
    f = lambda q: f"{q[0]:.2f},{q[1]:.2f}"
    d = f"M{f(pts[0][0])} "
    for _, b1, b2, e in pts:
        d += f"C{f(b1)} {f(b2)} {f(e)} "
    return d + "Z"


def poly(*pts):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"


def note(cx, cy, s):
    """Beamed eighth notes centred on (cx, cy), scaled by s from a 45-unit design."""
    t = lambda x, y: (cx + (x - 54) * s, cy + (y - 54) * s)
    parts = [
        poly(t(46.0, 36.5), t(77.0, 29.5), t(77.0, 40.0), t(46.0, 47.0)),
        poly(t(46.0, 42.0), t(51.5, 42.0), t(51.5, 70.0), t(46.0, 70.0)),
        poly(t(71.5, 35.0), t(77.0, 35.0), t(77.0, 64.0), t(71.5, 64.0)),
    ]
    hx, hy = t(41.5, 70.5)
    parts.append(ellipse(hx, hy, 9.8 * s, 7.2 * s, -22))
    hx, hy = t(67.5, 64.0)
    parts.append(ellipse(hx, hy, 9.8 * s, 7.2 * s, -22))
    return " ".join(parts)


def sparkle(cx, cy, r):
    """Four-point star."""
    w = r * 0.28
    return (f"M{cx},{cy - r} Q{cx + w},{cy - w} {cx + r},{cy} Q{cx + w},{cy + w} {cx},{cy + r} "
            f"Q{cx - w},{cy + w} {cx - r},{cy} Q{cx - w},{cy - w} {cx},{cy - r} Z")


MOON = crescent(48.5, 48.0, 20.5, 58.0, 42.0, 17.0)
NOTE = note(63.5, 45.0, 0.5)
MOON_MONO = crescent(47.0, 54.0, 23.0, 57.5, 47.5, 19.0)
NOTE_MONO = note(64.5, 52.0, 0.56)
STARS = [sparkle(31.5, 31.5, 2.4), sparkle(76.0, 29.5, 2.2), sparkle(82.0, 42.0, 1.6)]
DOTS = [(44.0, 24.5, 0.8), (86.0, 52.0, 0.7), (24.5, 50.0, 0.6)]

BACK_WAVE = "M0,57 C18,52 36,55 54,60 C74,65 92,61 108,53 L108,108 L0,108 Z"
FRONT_WAVES = [
    "M-8,63 C8,57 28,58 46,62 C64,66 86,68 116,58 L116,116 L-8,116 Z",
    "M-8,70 C14,62 34,75 56,71 C76,67 94,61 116,66 L116,116 L-8,116 Z",
    "M-8,78 C18,72 40,84 64,79 C84,75 100,73 116,77 L116,116 L-8,116 Z",
    "M-8,85 C22,80 46,90 72,85 C92,82 106,83 116,84 L116,116 L-8,116 Z",
]

# name: sky stops (top->bottom), glow, star colour, back wave, moon stops, note stops,
#       front wave stops [(c1, c2)] — or a flat (bg, glyph) pair for minimal tiles.
SCENES = {
    "dusk": dict(sky=["#15152B", "#2C1530", "#6E1A2A"], glow="#F7A27A", star="#F4B48C",
                 back="#2B3150", moon=["#FFE0C2", "#F8A57A", "#E8664E"], note=["#FFD9BC", "#F49A73", "#E2564B"],
                 waves=[("#3F4868", "#262B45"), ("#F8AE84", "#E8664E"), ("#C42A3B", "#8C1A2B"), ("#2E3350", "#1D2036")]),
    "tide": dict(sky=["#0A1228", "#11264F", "#173E6E"], glow="#9CC9FF", star="#CFE3FF",
                 back="#1C2B4D", moon=["#F4F8FF", "#C4DCFA", "#8AB6EE"], note=["#F4F8FF", "#BCD6F7", "#86B1EA"],
                 waves=[("#2A4170", "#18284A"), ("#5FB6E8", "#2F7FC4"), ("#1E5A99", "#123D6D"), ("#14213D", "#0C162B")]),
    "amethyst": dict(sky=["#120C26", "#2E1446", "#5E1A63"], glow="#E9A2F2", star="#F2C8F5",
                     back="#2A2150", moon=["#FBE6FF", "#E7A8F2", "#C46BDB"], note=["#FBE6FF", "#E0A0EE", "#B862D3"],
                     waves=[("#3D2E6B", "#251C47"), ("#E58BD9", "#A44BC2"), ("#7A2A8E", "#4E1A61"), ("#221A3E", "#150F29")]),
    "moss": dict(sky=["#08161A", "#0F2E2B", "#17463A"], glow="#C8F08A", star="#E3F7C2",
                 back="#163A35", moon=["#F4FCE0", "#D2EE9C", "#9CCB5C"], note=["#F4FCE0", "#CBE98F", "#93C355"],
                 waves=[("#1F4A43", "#12302B"), ("#A8D86E", "#5EA24A"), ("#2F7A55", "#1B5039"), ("#0F2622", "#091A17")]),
}
MINIMAL = {
    "cream": ("#F6E9DA", "#1C1D33"),
    "night": ("#1B1C26", "#D9BC94"),
    "peach": ("#F6B48E", "#5C1424"),
    "wine": ("#5E1A26", "#EE7262"),
}
ORDER = ["dusk", "tide", "amethyst", "moss", "cream", "night", "peach", "wine"]

# ---------------------------------------------------------------- VectorDrawable output
HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by icongen/ondalune.py -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" '
        'android:viewportWidth="108" android:viewportHeight="108">\n')


def lin(attr, stops, x1, y1, x2, y2):
    items = "".join(f'<item android:offset="{i / (len(stops) - 1):.2f}" android:color="{c}" />' for i, c in enumerate(stops))
    return (f'<aapt:attr name="android:{attr}"><gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
            f'android:endX="{x2}" android:endY="{y2}">{items}</gradient></aapt:attr>')


def rad(attr, color, cx, cy, r, alpha):
    a = f"#{int(alpha * 255):02X}{color[1:]}"
    return (f'<aapt:attr name="android:{attr}"><gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" '
            f'android:gradientRadius="{r}"><item android:offset="0" android:color="{a}" />'
            f'<item android:offset="1" android:color="#00{color[1:]}" /></gradient></aapt:attr>')


def path(d, fill=None, alpha=None, inner=""):
    a = f' android:fillAlpha="{alpha}"' if alpha is not None else ""
    fc = f' android:fillColor="{fill}"' if fill else ""
    return f'  <path android:pathData="{d}"{fc}{a}>{inner}</path>\n'


def write(p, text):
    with open(p, "w") as fh:
        fh.write(text)


def vd_scene(name, v):
    bg = HEAD + path("M0,0h108v108h-108z", inner=lin("fillColor", v["sky"], 0, 0, 0, 108))
    bg += path("M0,0h108v108h-108z", inner=rad("fillColor", v["glow"], 50, 50, 34, 0.30))
    for s in STARS:
        bg += path(s, v["star"])
    for x, y, r in DOTS:
        bg += path(ellipse(x, y, r, r, 0), v["star"], 0.8)
    bg += path(BACK_WAVE, v["back"], 0.9)
    write(f"{RES}/drawable/ic_launcher_bg_{name}.xml", bg + "</vector>\n")

    fg = HEAD
    fg += path(MOON, inner=lin("fillColor", v["moon"], 34, 32, 60, 72))
    fg += f'  <group android:translateX="0.6" android:translateY="1.2">\n  {path(NOTE, "#000000", 0.22)}  </group>\n'
    fg += path(NOTE, inner=lin("fillColor", v["note"], 55, 34, 72, 62))
    for (c1, c2), d in zip(v["waves"], FRONT_WAVES):
        fg += path(d, inner=lin("fillColor", [c1, c2], 0, 60, 108, 100))
    write(f"{RES}/drawable/ic_launcher_fg_{name}.xml", fg + "</vector>\n")


def vd_minimal(name, bgc, glyph):
    write(f"{RES}/drawable/ic_launcher_bg_{name}.xml", HEAD + path("M0,0h108v108h-108z", bgc) + "</vector>\n")
    write(f"{RES}/drawable/ic_launcher_fg_{name}.xml", HEAD + path(MOON_MONO, glyph) + path(NOTE_MONO, glyph) + "</vector>\n")


def adaptive(name, mip):
    s = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
         f'    <background android:drawable="@drawable/ic_launcher_bg_{name}" />\n'
         f'    <foreground android:drawable="@drawable/ic_launcher_fg_{name}" />\n'
         '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')
    write(f"{RES}/mipmap-anydpi-v26/{mip}.xml", s)
    write(f"{RES}/mipmap-anydpi-v26/{mip}_round.xml", s)


# ---------------------------------------------------------------- SVG (legacy PNGs + previews)
def svg_scene(v):
    def g(gid, stops, x1, y1, x2, y2):
        st = "".join(f'<stop offset="{i / (len(stops) - 1):.2f}" stop-color="{c}"/>' for i, c in enumerate(stops))
        return f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>'
    defs = g("sky", v["sky"], 0, 0, 0, 108) + g("moon", v["moon"], 34, 32, 60, 72) + g("note", v["note"], 55, 34, 72, 62)
    defs += (f'<radialGradient id="glow" gradientUnits="userSpaceOnUse" cx="50" cy="50" r="34"><stop offset="0" '
             f'stop-color="{v["glow"]}" stop-opacity="0.3"/><stop offset="1" stop-color="{v["glow"]}" stop-opacity="0"/></radialGradient>')
    b = '<rect width="108" height="108" fill="url(#sky)"/><rect width="108" height="108" fill="url(#glow)"/>'
    b += "".join(f'<path d="{s}" fill="{v["star"]}"/>' for s in STARS)
    b += "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{v["star"]}" fill-opacity="0.8"/>' for x, y, r in DOTS)
    b += f'<path d="{BACK_WAVE}" fill="{v["back"]}" fill-opacity="0.9"/>'
    b += f'<path d="{MOON}" fill="url(#moon)"/>'
    b += f'<path d="{NOTE}" transform="translate(0.6 1.2)" fill="#000" fill-opacity="0.22"/><path d="{NOTE}" fill="url(#note)"/>'
    for i, ((c1, c2), d) in enumerate(zip(v["waves"], FRONT_WAVES)):
        defs += g(f"w{i}", [c1, c2], 0, 60, 108, 100)
        b += f'<path d="{d}" fill="url(#w{i})"/>'
    return defs, b


def svg_minimal(bgc, glyph):
    return "", f'<rect width="108" height="108" fill="{bgc}"/><path d="{MOON_MONO}" fill="{glyph}"/><path d="{NOTE_MONO}" fill="{glyph}"/>'


def svg_doc(defs, body, clip):
    c = '<circle cx="54" cy="54" r="36"/>' if clip == "round" else '<rect x="18" y="18" width="72" height="72" rx="16"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><defs>{defs}<clipPath id="c">{c}</clipPath></defs>'
            f'<g clip-path="url(#c)">{body}</g></svg>')


write(f"{RES}/drawable/ic_launcher_monochrome.xml",
      HEAD + path(MOON_MONO, "#FFFFFF") + path(NOTE_MONO, "#FFFFFF") + "</vector>\n")

DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name in ORDER:
    if name in SCENES:
        vd_scene(name, SCENES[name])
        defs, body = svg_scene(SCENES[name])
    else:
        vd_minimal(name, *MINIMAL[name])
        defs, body = svg_minimal(*MINIMAL[name])
    mip = "ic_launcher" if name == "dusk" else f"ic_launcher_{name}"
    adaptive(name, mip)
    for clip, suffix in (("square", ""), ("round", "_round")):
        sp = f"{OUT}/{name}{suffix}.svg"
        write(sp, svg_doc(defs, body, clip))
        for dname, px in DENS.items():
            subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), sp, "-o", f"{RES}/mipmap-{dname}/{mip}{suffix}.png"], check=True)
    subprocess.run(["rsvg-convert", "-w", "256", "-h", "256", f"{OUT}/{name}.svg", "-o", f"{OUT}/{name}.png"], check=True)
# Full-bleed 512px store icon (no mask) for README / F-Droid metadata.
d, b = svg_scene(SCENES["dusk"])
write(f"{OUT}/store_icon.svg", f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><defs>{d}</defs>{b}</svg>')
subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{OUT}/store_icon.svg", "-o", f"{OUT}/store_icon.png"], check=True)
print("ok")
