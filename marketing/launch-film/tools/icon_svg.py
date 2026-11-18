"""Converts the app's Moonlight launcher foreground (VectorDrawable) to assets/brand/mark.svg.

Each top-level shape keeps an id (moon, wave-1, wave-2, bars) so the film can animate it.
"""
import os
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "../../../app/src/main/res/drawable/ic_launcher_fg_moonlight.xml")
OUT = os.path.join(HERE, "../assets/brand/mark.svg")
A = "{http://schemas.android.com/apk/res/android}"

defs, body, n = [], [], 0


def fill(path):
    global n
    grad = path.find(".//gradient")
    if grad is None:
        return path.get(A + "fillColor", "#fff")
    n += 1
    stops = "".join(f'<stop offset="{i.get(A + "offset")}" stop-color="{i.get(A + "color")}"/>'
                    for i in grad.findall("item"))
    defs.append(f'<linearGradient id="g{n}" gradientUnits="userSpaceOnUse" x1="{grad.get(A + "startX")}" '
                f'y1="{grad.get(A + "startY")}" x2="{grad.get(A + "endX")}" y2="{grad.get(A + "endY")}">{stops}</linearGradient>')
    return f"url(#g{n})"


root = ET.parse(SRC).getroot()
ids = iter(["moon", "waves", "bars"])
for el in root:
    if el.tag == "path":
        body.append(f'<path id="{next(ids)}" d="{el.get(A + "pathData")}" fill="{fill(el)}"/>')
    elif el.tag == "group":
        clip = el.find("clip-path").get(A + "pathData")
        defs.append(f'<clipPath id="moon-clip"><path d="{clip}"/></clipPath>')
        waves = "".join(f'<path id="wave-{k + 1}" d="{p.get(A + "pathData")}" fill="{fill(p)}"/>'
                        for k, p in enumerate(el.findall("path")))
        body.append(f'<g id="{next(ids)}" clip-path="url(#moon-clip)">{waves}</g>')

with open(OUT, "w") as f:
    f.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>{"".join(defs)}</defs>{"".join(body)}</svg>\n')
