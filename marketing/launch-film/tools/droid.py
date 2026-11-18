"""Small adb helpers for driving Ondalune on the emulator (ANDROID_SERIAL=emulator-5554)."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
os.environ.setdefault("ANDROID_SERIAL", "emulator-5554")


def adb(*args):
    return subprocess.run([ADB, *map(str, args)], capture_output=True).stdout


def nodes():
    """(label, class, centre, bounds) for every on-screen node; label is text or content-desc."""
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/u.xml"))
    for n in root.iter("node"):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        label = n.get("text") or n.get("content-desc") or ""
        yield label, n.get("class"), ((x1 + x2) // 2, (y1 + y2) // 2), (x1, y1, x2, y2)


def find(label, contains=False):
    for t, _, c, b in nodes():
        if (label in t) if contains else (t == label):
            return c, b
    return None


def tap(x, y, wait=0.8):
    adb("shell", "input", "tap", x, y)
    time.sleep(wait)


def tap_label(label, wait=0.8, contains=False):
    hit = find(label, contains)
    if not hit:
        raise SystemExit(f"not on screen: {label}")
    tap(*hit[0], wait=wait)
    return hit


def swipe(x1, y1, x2, y2, ms):
    adb("shell", "input", "swipe", x1, y1, x2, y2, ms)


def type_into(centre, text):
    tap(*centre, wait=0.5)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * 40))
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.3)
    adb("shell", "input", "keyevent", "BACK")  # hides the keyboard
    time.sleep(0.6)


def edit_texts():
    return [c for _, cls, c, _ in nodes() if cls == "android.widget.EditText"]
