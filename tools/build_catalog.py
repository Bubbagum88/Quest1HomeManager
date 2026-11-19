#!/usr/bin/env python3
"""Build catalog/catalog.json, the home list the headset app reads.

Sources:
  1. Quest-Homes/Home-Environments on GitHub (the Quest Homes Discord's repo):
     every folder with a *_Environment.apk (standalone home, carries the
     com.oculus.environmentVersion tag) becomes one entry.
  2. Creator GitHub repos with a known standalone APK (EXTRA_GITHUB below).
  3. The Quest Homes Discord spreadsheet (Quest_Home_Environments.xlsx):
     every row with a link, the link's host classified so the app knows
     whether it can download it itself or must open a browser.

Usage:
  python3 tools/build_catalog.py --xlsx PATH/Quest_Home_Environments.xlsx
Needs git and openpyxl. Link liveness is filled in later by the link checker.
"""
import argparse, json, re, subprocess, sys, tempfile, urllib.parse
from datetime import date

QH_REPO = "https://github.com/Quest-Homes/Home-Environments"
QH_RAW = "https://raw.githubusercontent.com/Quest-Homes/Home-Environments/main/"

# Standalone APKs in creators' own repos, checked by hand (manifest read).
EXTRA_GITHUB = [
    {"name": "Outer Wilds", "creator": "Kosro",
     "package": "com.environment.outerwilds",
     "apk": "https://raw.githubusercontent.com/Robert-K/outer-wilds-quest-home/master/OuterWilds.apk",
     "page": "https://github.com/Robert-K/outer-wilds-quest-home",
     "checked": "2026-11-19: minSdk 25, environmentVersion 1"},
]

def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

def pretty(folder):
    s = re.sub(r"[._]+", " ", folder)
    s = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", s)   # AlienForestDay -> Alien Forest Day
    return re.sub(r"\s+", " ", s).strip()

def quest_homes_entries():
    with tempfile.TemporaryDirectory() as d:
        subprocess.run(["git", "clone", "-q", "--depth", "1", "--filter=blob:none",
                        "--no-checkout", QH_REPO, d], check=True)
        names = subprocess.run(["git", "-C", d, "ls-tree", "-r", "--name-only", "HEAD"],
                               check=True, capture_output=True, text=True).stdout.split("\n")
    out = []
    for p in sorted(names):
        if not p.endswith("_Environment.apk") or "/" not in p:
            continue
        folder = p.split("/")[0]
        silent = p[:-len(".apk")] + "_Silent.apk"
        dl = [{"url": QH_RAW + urllib.parse.quote(p), "kind": "apk", "variant": "audio"}]
        if silent in names:
            dl.append({"url": QH_RAW + urllib.parse.quote(silent), "kind": "apk", "variant": "silent"})
        out.append({"id": "qh-" + slug(folder), "name": pretty(folder), "creator": None,
                    "source": "quest-homes-github", "free": True, "downloads": dl,
                    "page": QH_REPO + "/tree/main/" + urllib.parse.quote(folder)})
    return out

def classify(url):
    host = urllib.parse.urlparse(url).netloc.lower()
    if url.lower().endswith(".apk") and ("github.com" in host or "githubusercontent" in host):
        return "apk"
    if "drive.google.com" in host:
        return "drive-folder" if "/folders/" in url else "drive-file"
    if "mega.nz" in host or "mega.co.nz" in host:
        return "mega-folder" if ("/folder/" in url or "#F!" in url) else "mega-file"
    if "dropbox.com" in host:
        return "dropbox"
    if "1drv.ms" in host or "onedrive" in host:
        return "onedrive"
    if "mediafire.com" in host:
        return "mediafire"
    if "cdn.discordapp.com" in host:
        return "expired"        # signed Discord attachment URLs expire
    if "hidrive.com" in host:
        return "hidrive"        # 2020-21 shares, many dead; the checker decides
    if "cdn.sidequestvr.com" in host:
        return "apk"
    if "sidequestvr.com" in host:
        return "sidequest-page"
    if "ko-fi.com" in host or "itch.io" in host or "gumroad.com" in host:
        return "store-page"     # paid or tip-gated: browser only
    if "github.com" in host:
        return "github-page"
    return "page"

def sheet_entries(xlsx):
    import openpyxl
    wb = openpyxl.load_workbook(xlsx, read_only=True)
    out = []
    for ws in wb:
        if ws.title in ("Notes", "Tools"):
            continue
        rows = list(ws.iter_rows(values_only=True))
        for r in rows[1:]:
            if not r or r[0] is None or not r[1]:
                continue
            links = [str(u).strip() for u in r[10:13] if u]
            if not links:
                continue
            out.append({
                "id": "sheet-" + slug(ws.title) + "-" + str(r[0]),
                "name": str(r[1]), "creator": r[4], "description": r[2],
                "source": "discord-sheet", "category": ws.title,
                "slot": r[6], "audio": r[7], "free": (r[8] or "").lower() != "paid",
                "posted": r[5].date().isoformat() if hasattr(r[5], "date") else None,
                "downloads": [{"url": u, "kind": classify(u)} for u in links],
                "note": r[14] if len(r) > 14 else None,
            })
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--xlsx", required=True)
    ap.add_argument("--out", default="catalog/catalog.json")
    a = ap.parse_args()
    homes = quest_homes_entries()
    for e in EXTRA_GITHUB:
        homes.append({"id": "gh-" + slug(e["name"]), "name": e["name"], "creator": e["creator"],
                      "source": "creator-github", "free": True, "package": e["package"],
                      "downloads": [{"url": e["apk"], "kind": "apk", "variant": "audio"}],
                      "page": e["page"], "checked": e["checked"]})
    homes += sheet_entries(a.xlsx)
    for h in homes:
        h.setdefault("status", "unchecked")
    cat = {"schema": 1, "generated": date.today().isoformat(), "homes": homes}
    with open(a.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(cat, f, ensure_ascii=False, indent=1)
        f.write("\n")
    by = {}
    for h in homes:
        by[h["source"]] = by.get(h["source"], 0) + 1
    print(len(homes), "homes", by, file=sys.stderr)

if __name__ == "__main__":
    main()
