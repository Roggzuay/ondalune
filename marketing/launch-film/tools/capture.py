"""Captures real Ondalune screens from the Creative Commons demo server into assets/screens/.

Needs the emulator (emulator-5554) signed in to the demo server from the ondalune-promo project
(tools/ccserver/server.py 4533). Usage: capture.py <step> [theme]
"""
import os
import sys
import time

from droid import adb, find, nodes, swipe, tap, tap_label

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "screens")


def shot(name):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p"))


def back(wait=1.2):
    adb("shell", "input", "keyevent", "BACK")
    time.sleep(wait)


def labels():
    print([(t, c) for t, _, c, _ in nodes() if t])


def tabs(theme):
    tap_label("Home", wait=2); shot(f"{theme}_home")
    tap_label("Library", wait=2); shot(f"{theme}_library")
    tap(180, 700, wait=2.5); shot(f"{theme}_album"); back()
    tap_label("Artists", wait=2); shot(f"{theme}_artists")
    tap_label("Playlists", wait=2); shot(f"{theme}_playlists")
    tap_label("Favourites", wait=2); shot(f"{theme}_favourites")


def player(theme):
    tap_label("Home", wait=2)
    tap_label("Play your mix", wait=5)
    for k in range(3):
        shot(f"{theme}_player_{k}")
        swipe(800, 900, 200, 900, 250)  # swipe the cover to skip
        time.sleep(3.5)


if __name__ == "__main__":
    step = sys.argv[1]
    {"tabs": tabs, "player": player}.get(step, lambda *_: labels())(*sys.argv[2:])
