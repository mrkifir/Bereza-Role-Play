#!/usr/bin/env python3
"""
Генератор manifest.json (схема 2) для Bereza RP Launcher. Працює в Termux, без бібліотек.

1) Створи manifest.src.json:
{
  "version": "2026.11.05-1",
  "minLauncherVersionCode": 1,
  "files": [
    {"path": "mods/bereza-ui.zip", "local": "bereza-ui.zip",
     "url": "https://www.dropbox.com/scl/fi/.../bereza-ui.zip?rlkey=...&dl=0",
     "extractTo": "mods/bereza-ui", "title": "Інтерфейс Bereza"},
    {"path": "skins/police.txd", "local": "police.txd", "url": "https://..."}
  ],
  "remove": [],
  "launcher": {"versionCode": 12, "versionName": "2.0.12", "local": "BerezaRP-Launcher.apk",
               "apkUrl": "https://github.com/.../BerezaRP-Launcher.apk", "notes": "Що нового"}
}
2) python3 make_manifest.py manifest.src.json manifest.json
   Скрипт порахує size і sha256 кожного локального файлу.
3) Завантаж файли на Dropbox, а manifest.json — туди, куди вказує manifestUrl лаунчера.

Важливо: SHA-256 рахується з ТОГО САМОГО файлу, що лежить на Dropbox. Змінив файл — перегенеруй маніфест.
"""
import hashlib, json, os, sys, zipfile


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def check_zip(path):
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            p = n.replace("\\", "/")
            if p.startswith("/") or ".." in p.split("/") or ":" in p:
                sys.exit(f"Небезпечний шлях в архіві {path}: {n}")


def main():
    if len(sys.argv) != 3:
        sys.exit("Використання: python3 make_manifest.py manifest.src.json manifest.json")
    src_path, out_path = sys.argv[1], sys.argv[2]
    base = os.path.dirname(os.path.abspath(src_path))
    src = json.load(open(src_path, encoding="utf-8"))
    out = {"schema": 2, "version": src["version"],
           "minLauncherVersionCode": src.get("minLauncherVersionCode", 1),
           "baseUrl": src.get("baseUrl", ""), "files": [], "remove": src.get("remove", [])}
    for f in src["files"]:
        local = os.path.join(base, f["local"])
        if f.get("extractTo") is not None:
            check_zip(local)
        e = {"path": f["path"], "url": f["url"], "size": os.path.getsize(local), "sha256": sha256(local)}
        for k in ("extractTo", "title", "required"):
            if k in f:
                e[k] = f[k]
        out["files"].append(e)
        print(f"{f['path']}: {e['size']} B  {e['sha256'][:16]}…")
    if "launcher" in src:
        l = dict(src["launcher"])
        apk = os.path.join(base, l.pop("local"))
        l["size"] = os.path.getsize(apk)
        l["sha256"] = sha256(apk)
        out["launcher"] = l
    with open(out_path, "w", encoding="utf-8") as fp:
        json.dump(out, fp, ensure_ascii=False, indent=2)
    print(f"Готово: {out_path} ({len(out['files'])} файлів)")


if __name__ == "__main__":
    main()
