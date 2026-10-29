#!/usr/bin/env python3
"""
Генератор manifest.json для Bereza RP Launcher.

Працює в Termux / Linux / Windows (Python 3.8+), без сторонніх бібліотек.

Використання:
    python3 make_manifest.py <папка_з_файлами> --base-url https://cdn.example.com/bereza/1.0.0/ \
        --version 1.0.0 [--extract textures.zip:textures/] [--out manifest.json]

Що робить:
  * обходить папку, рахує розмір і SHA-256 кожного файлу;
  * для ZIP, зазначених через --extract, рахує розмір після розпакування і позначає extract=true;
  * якщо manifest.json уже існує в --out, зберігає з нього блоки server / client / launch
    та файли з "source": "user" (їх не можна згенерувати автоматично).

Файли із "source": "user" — це файли оригінальної гри, які гравець бере з власної копії.
Їх SHA-256 треба порахувати один раз зі своєї легальної копії й вписати вручну.
"""
import argparse
import hashlib
import json
import os
import sys
import zipfile
from urllib.parse import quote


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def unpacked_size(path: str) -> int:
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            name = info.filename.replace("\\", "/")
            if name.startswith("/") or ".." in name.split("/") or ":" in name:
                sys.exit(f"Небезпечний шлях в архіві {path}: {name}")
        return sum(i.file_size for i in z.infolist())


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("root")
    ap.add_argument("--base-url", required=True, help="HTTPS-адреса, з якої роздається папка (зі слешем у кінці)")
    ap.add_argument("--version", required=True)
    ap.add_argument("--extract", action="append", default=[], help="архів.zip:цільова/папка/")
    ap.add_argument("--min-launcher", type=int, default=1)
    ap.add_argument("--out", default="manifest.json")
    a = ap.parse_args()

    if not a.base_url.startswith("https://"):
        sys.exit("--base-url має бути HTTPS")
    base = a.base_url if a.base_url.endswith("/") else a.base_url + "/"
    extract = {}
    for e in a.extract:
        arc, _, to = e.partition(":")
        extract[arc.replace("\\", "/")] = to

    old = {}
    if os.path.exists(a.out):
        with open(a.out, encoding="utf-8") as f:
            old = json.load(f)

    files = []
    for dirpath, _, names in os.walk(a.root):
        for n in sorted(names):
            full = os.path.join(dirpath, n)
            rel = os.path.relpath(full, a.root).replace("\\", "/")
            if rel.startswith(".") or rel == os.path.basename(a.out):
                continue
            entry = {
                "path": rel,
                "size": os.path.getsize(full),
                "sha256": sha256(full),
                "source": "remote",
                "url": base + quote(rel),
            }
            if rel in extract:
                entry["extract"] = True
                entry["extractTo"] = extract[rel]
                entry["unpackedSize"] = unpacked_size(full)
            files.append(entry)
            print(f"  {rel}  {entry['size']} B")

    files += [f for f in old.get("files", []) if f.get("source") == "user"]

    manifest = {
        "manifestVersion": 1,
        "gameVersion": a.version,
        "minLauncherVersionCode": a.min_launcher,
        "files": files,
    }
    for key in ("server", "client", "launch"):
        if key in old:
            manifest[key] = old[key]

    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    total = sum(f["size"] for f in files if f.get("source") == "remote")
    print(f"\nГотово: {a.out} · файлів {len(files)} · завантаження {total / 1048576:.1f} МБ")


if __name__ == "__main__":
    main()
