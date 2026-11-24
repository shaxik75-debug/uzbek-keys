"""Builds uzbek_auto.exe on Windows without Ahk2Exe.

Copies an AutoHotkey v1.1 base file, embeds the script as the ">AUTOHOTKEY SCRIPT<" resource
(exactly what Ahk2Exe does) and replaces the main icon (group 159) with our .ico.

  python windows/tools/build.py --ahk <AutoHotkey folder> --script windows/uzbek_auto.ahk
                                --icon uzbek.ico --version 1.5 --whatsnew whatsnew.txt --out uzbek_auto.exe

--whatsnew puts the text of that file in place of the line "@@WHATSNEW@@" in the script.
"""
import argparse
import ctypes
import os
import re
import shutil
import struct
from ctypes import wintypes

RT_ICON = 3
RT_RCDATA = 10
RT_GROUP_ICON = 14
MAIN_ICON_GROUP = 159          # AutoHotkey's main icon (also used for the tray)
FIRST_ICON_ID = 300            # new RT_ICON ids, clear of the ones already in the base file
LANGS = (0x0409, 0x0000)       # write for en-US and neutral so either lookup finds our icon

BASE_NAMES = ["Unicode 32-bit.bin", "AutoHotkeyU32.exe", "AutoHotkeySC.bin"]


def find_base(ahk_dir):
    found = {}
    for root, _dirs, files in os.walk(ahk_dir):
        for f in files:
            found.setdefault(f, os.path.join(root, f))
    for name in BASE_NAMES:
        if name in found:
            return found[name]
    raise SystemExit("no AutoHotkey base file found in " + ahk_dir)


def kernel32():
    k = ctypes.WinDLL("kernel32", use_last_error=True)
    k.BeginUpdateResourceW.argtypes = [wintypes.LPCWSTR, wintypes.BOOL]
    k.BeginUpdateResourceW.restype = wintypes.HANDLE
    k.UpdateResourceW.argtypes = [wintypes.HANDLE, ctypes.c_void_p, ctypes.c_void_p, wintypes.WORD,
                                  ctypes.c_void_p, wintypes.DWORD]
    k.UpdateResourceW.restype = wintypes.BOOL
    k.EndUpdateResourceW.argtypes = [wintypes.HANDLE, wintypes.BOOL]
    k.EndUpdateResourceW.restype = wintypes.BOOL
    return k


def int_res(i):
    return ctypes.c_void_p(i)


def update(k, handle, rtype, name, lang, data):
    buf = ctypes.create_string_buffer(data, len(data))
    ok = k.UpdateResourceW(handle, rtype, name, lang, ctypes.cast(buf, ctypes.c_void_p), len(data))
    if not ok:
        raise OSError(ctypes.get_last_error(), "UpdateResource failed")


def read_ico(path):
    data = open(path, "rb").read()
    _reserved, kind, count = struct.unpack_from("<HHH", data, 0)
    if kind != 1 or count == 0:
        raise SystemExit("not an .ico file: " + path)
    images = []
    for i in range(count):
        w, h, colors, res, planes, bpp, size, offset = struct.unpack_from("<BBBBHHII", data, 6 + 16 * i)
        images.append((w, h, colors, res, planes or 1, bpp or 32, data[offset:offset + size]))
    return images


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ahk", required=True)
    ap.add_argument("--script", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--icon")
    ap.add_argument("--version")
    ap.add_argument("--whatsnew")
    args = ap.parse_args()

    base = find_base(args.ahk)
    print("base file:", base)

    text = open(args.script, "r", encoding="utf-8").read()
    if args.version:
        text, n = re.subn(r'^AppVersion := ".*"$', 'AppVersion := "%s"' % args.version, text, flags=re.M)
        print("version set to", args.version, "(%d line)" % n)
    if args.whatsnew:
        notes = open(args.whatsnew, "r", encoding="utf-8-sig").read().strip()
        # Inside an AutoHotkey continuation section: escape backticks and %; a line may not start
        # with ")" (that would end the section), so such a ")" becomes a full-width one.
        tick = chr(96)
        notes = notes.replace(tick, tick + tick).replace("%", tick + "%")
        notes = "\n".join(re.sub(r"^(\s*)\)", "\\1\uff09", l) for l in notes.splitlines())
        text, n = re.subn(r"^@@WHATSNEW@@$", lambda m: notes, text, flags=re.M)
        print("what's new inserted (%d place)" % n)
    # UTF-8 with BOM: AutoHotkey reads the embedded script as UTF-8 (checked by selftest.ahk).
    body = text.replace("\r\n", "\n").replace("\n", "\r\n").encode("utf-8-sig")

    shutil.copyfile(base, args.out)
    k = kernel32()
    handle = k.BeginUpdateResourceW(os.path.abspath(args.out), False)
    if not handle:
        raise OSError(ctypes.get_last_error(), "BeginUpdateResource failed")

    name = ctypes.c_wchar_p(">AUTOHOTKEY SCRIPT<")
    update(k, handle, int_res(RT_RCDATA), ctypes.cast(name, ctypes.c_void_p), 0x0409, body)

    if args.icon:
        images = read_ico(args.icon)
        group = struct.pack("<HHH", 0, 1, len(images))
        for i, (w, h, colors, res, planes, bpp, img) in enumerate(images):
            icon_id = FIRST_ICON_ID + i
            group += struct.pack("<BBBBHHIH", w, h, colors, res, planes, bpp, len(img), icon_id)
            for lang in LANGS:
                update(k, handle, int_res(RT_ICON), int_res(icon_id), lang, img)
        for lang in LANGS:
            update(k, handle, int_res(RT_GROUP_ICON), int_res(MAIN_ICON_GROUP), lang, group)
        print("icon replaced:", len(images), "images")

    if not k.EndUpdateResourceW(handle, False):
        raise OSError(ctypes.get_last_error(), "EndUpdateResource failed")
    print("built", args.out, os.path.getsize(args.out), "bytes")


if __name__ == "__main__":
    main()
