"""Build the 9:16 cut (vertical/) from the 16:9 scenes.

Timing, copy and motion stay in the landscape files; this only re-lays them out
for 1080x1920. Each edit asserts its anchor, so a change to a landscape scene
that breaks an anchor fails loudly instead of producing a half-converted file.

Run from the launch-film folder: python3 tools/make_vertical.py
"""
import json
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "vertical")

SIZE = ('data-width="1920" data-height="1080"', 'data-width="1080" data-height="1920"')
PHONE = [
    ("left: 749px; top: 84px; width: 422px; height: 911px; border-radius: 58px;",
     "left: 305px; top: 780px; width: 470px; height: 1015px; border-radius: 64px;"),
    ("inset: 11px; border-radius: 47px;", "inset: 12px; border-radius: 52px;"),
]

EDITS = {
    "intro.html": [
        ('<canvas id="intro-dust" width="1920" height="1080">', '<canvas id="intro-dust" width="1080" height="1920">'),
        ("x: rnd() * 1920, y: rnd() * 1080,", "x: rnd() * 1080, y: rnd() * 1920,"),
        ("ctx.clearRect(0, 0, 1920, 1080);", "ctx.clearRect(0, 0, 1080, 1920);"),
        ("% 1080) + 1080) % 1080;", "% 1920) + 1920) % 1920;"),
        ("((m.x - 960) / 960)", "((m.x - 540) / 540)"),
        ("left: 360px; top: -230px;", "left: -96px; top: -40px;"),
        ("left: 810px; top: 190px;", "left: 364px; top: 330px; width: 280px; height: 280px;"),
        ("left: 0; right: 0; top: 520px;", "left: 120px; right: auto; width: 768px; top: 660px;"),
        ("left: 0; right: 0; top: 712px;", "left: 120px; right: auto; width: 768px; top: 820px;"),
        ('transformOrigin: "960px 340px"', 'transformOrigin: "504px 470px"'),
    ],
    "library.html": PHONE + [
        ("left: 700px; top: -300px;", "left: -210px; top: 560px;"),
        ("left: 150px; top: 330px; width: 820px;", "left: 120px; top: 300px; width: 760px;"),
        ("left: 150px; top: 640px; width: 640px;", "left: 120px; top: 580px; width: 720px;"),
        ('tl.fromTo("#library-phone", { x: 0 }, { x: 380, duration: 1.1, ease: spring(0.82) }, 1.25);\n', ""),
    ],
    "player.html": PHONE + [
        ("left: -100px; top: -520px; width: 2120px;", "left: -520px; top: -100px; width: 2120px;"),
        ("linear-gradient(90deg, rgba(5, 5, 8, 0.82) 0%, rgba(5, 5, 8, 0.55) 45%, rgba(5, 5, 8, 0.25) 100%)",
         "linear-gradient(180deg, rgba(5, 5, 8, 0.84) 0%, rgba(5, 5, 8, 0.6) 38%, rgba(5, 5, 8, 0.3) 100%)"),
        ("left: 150px; top: 250px; width: 760px;", "left: 120px; top: 300px; width: 760px;"),
        ("left: 150px; top: 610px; width: 640px;", "left: 120px; top: 600px; width: 720px;"),
        ('style="font-size: 72px; width: 740px"', 'style="font-size: 76px"'),
        ('{ x: 380 }, { x: 380, duration: 0.01 }', '{ x: 0 }, { x: 0, duration: 0.01 }'),
        ("{ scale: 1.55, duration: 1.0,", "{ scale: 1.2, duration: 1.0,"),
        ("{ x: 380 }, { x: 400, duration: 1.0,", "{ x: 0 }, { x: 0, duration: 1.0,"),
        ("{ scale: 1.55 }, { scale: 1, duration: 0.85,", "{ scale: 1.2 }, { scale: 1, duration: 0.7,"),
        ("{ x: 400 }, { x: 190, duration: 0.9,", "{ x: 0, scale: 1 }, { x: -215, scale: 0.78, duration: 0.9,"),
        ('{ x: 1100, opacity: 0 }, { x: 680, opacity: 1,',
         '{ x: 1100, opacity: 0, scale: 0.78, transformOrigin: "50% 36%" }, { x: 215, opacity: 1, scale: 0.78,'),
        ("{ x: -760, duration: 0.6,", "{ x: -1080, duration: 0.6,"),
    ],
    "custom.html": PHONE + [
        ("left: 640px; top: -360px;", "left: -210px; top: 560px;"),
        ("ellipse 80% 90% at 70% 45%", "ellipse 90% 70% at 50% 62%"),
        ("left: 150px; top: 330px; width: 800px;", "left: 120px; top: 300px; width: 760px;"),
        ("left: 150px; top: 560px; width: 660px;", "left: 120px; top: 560px; width: 720px;"),
        ("left: 0; right: 0; top: 958px;", "left: 120px; right: auto; width: 760px; text-align: left; top: 320px;"),
        ('class="custom-quiet on-light" style="top: 630px"', 'class="custom-quiet on-light" style="top: 640px"'),
        ('class="custom-title on-light" style="top: 270px"', 'class="custom-title on-light" style="top: 300px; font-size: 66px"'),
        ('class="custom-quiet on-light" style="top: 660px"', 'class="custom-quiet on-light" style="top: 640px"'),
        ("<span>fonts.</span> <span>Your</span>", "<span>fonts.</span><br /><span>Your</span>"),
        ('const PC = "1340px 540px";', 'const PC = "540px 1287px";'),
        ('{ x: 380 }, { x: 380, duration: 0.01 }', '{ x: 0 }, { x: 0, duration: 0.01 }'),
        ('{ x: 760 }, { x: 0, duration: 0.75,', '{ x: 1080 }, { x: 0, duration: 0.75,'),
        ("{ x: 0, scale: 0.8, opacity: 0 }, { x: 0, scale: 0.8, opacity: 0, duration: 0.01 }",
         "{ x: 0, scale: 0.55, opacity: 0 }, { x: 0, scale: 0.55, opacity: 0, duration: 0.01 }"),
        ("{ x: 380, scale: 1 }, { x: 0, scale: 0.86,", "{ x: 0, scale: 1 }, { x: 0, scale: 0.62,"),
        ("{ x: 0, scale: 0.8, opacity: 0 }, { x: -480, scale: 0.86,", "{ x: 0, scale: 0.55, opacity: 0 }, { x: -320, scale: 0.62,"),
        ("{ x: 0, scale: 0.8, opacity: 0 }, { x: 480, scale: 0.86,", "{ x: 0, scale: 0.55, opacity: 0 }, { x: 320, scale: 0.62,"),
        ("{ x: 1300 }, { x: 380, duration: 1.1,", "{ x: 1300 }, { x: 0, duration: 1.1,"),
    ],
    "ethos.html": [
        ("left: -174px; top: -266px; width: 2268px; height: 1612px;", "left: -280px; top: -250px; width: 1640px; height: 2624px;"),
        ("ellipse 55% 45% at 50% 50%", "ellipse 75% 28% at 50% 50%"),
        ("left: 0; right: 0; top: 460px;", "left: 120px; right: auto; width: 768px; top: 654px;"),
        ("font-size: 100px; top: 360px;", "font-size: 92px; top: 605px;"),
        ('<span class="ethos-p1">ads.</span> <span class="ethos-p2">', '<span class="ethos-p1">ads.</span><br /><span class="ethos-p2">'),
        ("{ x: -110 * k + 110 }, { x: -110 * (k + 1) + 110,", "{ y: -110 * k + 110 }, { y: -110 * (k + 1) + 110,"),
    ],
    "finale.html": [
        ('<canvas id="finale-dust" width="1920" height="1080">', '<canvas id="finale-dust" width="1080" height="1920">'),
        ("x: rnd() * 1920, y: rnd() * 1080,", "x: rnd() * 1080, y: rnd() * 1920,"),
        ("ctx.clearRect(0, 0, 1920, 1080);", "ctx.clearRect(0, 0, 1080, 1920);"),
        ("% 1080) + 1080) % 1080;", "% 1920) + 1920) % 1920;"),
        ("left: 160px; top: -170px;", "left: -46px; top: 0px;"),
        ("left: 0; right: 0; top: 470px;", "left: 120px; right: auto; width: 768px; top: 560px; line-height: 1.12;"),
        ("<span>without</span> <span>boundaries.</span>", "<span>without</span><br /><span>boundaries.</span>"),
        ("left: 520px; top: 300px;", "left: 389px; top: 380px;"),
        ("left: 770px; top: 340px;", "left: 120px; top: 650px; width: 768px; text-align: center;"),
        ("left: 0; right: 0; top: 590px;", "left: 120px; right: auto; width: 768px; top: 786px;"),
        ("left: 50%; top: 690px;", "left: 50%; top: 880px;"),
        ("left: 0; right: 0; top: 938px;", "left: 120px; right: auto; width: 768px; top: 1060px;"),
        ("(CC BY 4.0) · Covers:", "(CC BY 4.0)<br />Covers:"),
    ],
}

# Rules appended at the end of a scene's CSS, so they win over the landscape values.
APPEND = {
    "intro.html": "#intro-word { font-size: 112px; } #intro-tag { font-size: 40px; }",
    "custom.html": "#custom-caption { font-size: 52px; line-height: 1.25; text-align: left; }",
    "ethos.html": ".ethos-line { font-size: 104px; } #ethos-l4 { font-size: 92px; }",
    "finale.html": ("#finale-line { font-size: 80px; } #finale-word { font-size: 112px; } #finale-sub { font-size: 36px; }"
                    " #finale-repo { left: 244px; width: 520px; margin-left: 0; font-size: 32px; }"),
}

# Portrait cover positions for the library's parallax layers: (src, left, top).
LIBRARY_COVERS = {
    "library-far": [
        ("okkulta-deepspacetrip.jpg", 70, 860), ("GroveOfWhispersBetween.jpg", 870, 980),
        ("nchr010.jpg", 40, 1500), ("yuxta.jpg", 880, 1620), ("constants_202003.jpg", 110, 1760),
        ("secretcentre.jpg", 880, 80),
    ],
    "library-mid": [
        ("aquatone-mental-images.jpg", 40, 1080), ("dtrash210.jpg", 820, 1260),
        ("SRS06507.jpg", 30, 1620), ("AIOT_YesAnd.jpg", 830, 760),
    ],
}


def edit(s, pairs, name):
    for a, b in pairs:
        assert a in s, f"{name}: anchor not found: {a[:70]}"
        s = s.replace(a, b)
    return s


def main():
    os.makedirs(os.path.join(OUT, "compositions"), exist_ok=True)
    for name, pairs in EDITS.items():
        s = open(os.path.join(ROOT, "compositions", name)).read()
        s = edit(s, [SIZE] + pairs, name)
        if name in APPEND:
            s = edit(s, [("\n  </style>", f"\n    {APPEND[name]}\n  </style>")], name)
        if name == "library.html":
            for layer, covers in LIBRARY_COVERS.items():
                imgs = "".join(
                    f'\n      <img class="library-cover" src="assets/covers/{src}" style="left: {x}px; top: {y}px" />'
                    for src, x, y in covers)
                s, n = re.subn(rf'(<div id="{layer}" class="library-layer">).*?(\n    </div>)',
                               lambda m: m.group(1) + imgs + m.group(2), s, flags=re.S)
                assert n == 1, layer
        if name == "ethos.html":
            srcs = re.findall(r'<img class="ethos-tile" src="([^"]+)"', s)
            srcs = (srcs * 2)[:40]
            tiles = "".join(
                f'\n      <img class="ethos-tile" src="{src}" style="left: {(i % 5) * 328}px; top: {(i // 5) * 328}px" />'
                for i, src in enumerate(srcs))
            s, n = re.subn(r'(<div id="ethos-wall">).*?(\n    </div>)',
                           lambda m: m.group(1) + tiles + m.group(2), s, flags=re.S)
            assert n == 1, "ethos wall"
            s = re.sub(r'(<span>Your</span>) (<span style)', r"\1<br />\2", s)
        open(os.path.join(OUT, "compositions", name), "w").write(s)

    root = open(os.path.join(ROOT, "index.html")).read()
    root = edit(root, [
        ('content="width=1920, height=1080"', 'content="width=1080, height=1920"'),
        ("width: 1920px;\n        height: 1080px;", "width: 1080px;\n        height: 1920px;"),
        ("Ondalune — launch film</title>", "Ondalune — launch film (9:16)</title>"),
    ], "index.html")
    root = root.replace("width: 1920px;\n        height: 1080px;", "width: 1080px;\n        height: 1920px;")
    root = root.replace('data-width="1920"\n      data-height="1080"', 'data-width="1080"\n      data-height="1920"')
    root = root.replace('data-width="1920" data-height="1080"', 'data-width="1080" data-height="1920"')
    assert "1920px;\n        height: 1080px" not in root and 'data-height="1080"' not in root
    open(os.path.join(OUT, "index.html"), "w").write(root)

    for f in ("hyperframes.json", "package.json"):
        shutil.copy(os.path.join(ROOT, f), os.path.join(OUT, f))
    meta = json.load(open(os.path.join(ROOT, "meta.json")))
    meta.update({k: v + "-9x16" for k, v in meta.items() if k in ("id", "name") and isinstance(v, str)})
    json.dump(meta, open(os.path.join(OUT, "meta.json"), "w"), indent=2)
    link = os.path.join(OUT, "assets")
    if not os.path.islink(link):
        os.symlink("../assets", link)
    print("vertical/ written")


if __name__ == "__main__":
    main()
