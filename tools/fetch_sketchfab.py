#!/usr/bin/env python3
"""
Sunset Grove — Sketchfab GTA Model Search & Authenticated Downloader.

Sketchfab's public search API allows discovering downloadable GTA-themed 3D models,
while Sketchfab's download endpoint (/v3/models/{uid}/download) requires a free
Sketchfab API token (https://sketchfab.com/settings/password).

Usage:
    python tools/fetch_sketchfab.py --search
    python tools/fetch_sketchfab.py --token YOUR_SKETCHFAB_API_TOKEN
"""

from __future__ import annotations

import argparse
import io
import json
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Dict, List, Optional

ROOT = Path(__file__).resolve().parent.parent
OVERRIDES_DIR = ROOT / "app" / "src" / "main" / "assets" / "models" / "overrides"
CATALOG_PATH = ROOT / "tools" / "sketchfab_gta_catalog.json"

# Every slot searches Sketchfab with "gta" prefixed as requested
SKETCHFAB_SLOTS: Dict[str, str] = {
    "character_player.glb": "gta jason",
    "character_ballas.glb": "gta ballas",
    "character_pedestrian.glb": "gta san andreas pedestrian",
    "car_sedan.glb": "gta san andreas car",
    "car_muscle.glb": "gta muscle car",
    "car_police.glb": "gta police car",
    "car_hotring.glb": "gta sports car",
    "car_bloodring.glb": "gta derby car",
    "vehicle_tank.glb": "gta rhino tank",
    "vehicle_monster_truck.glb": "gta monster truck",
    "gear_jetpack.glb": "gta jetpack",
    "world_san_andreas_map.glb": "gta san andreas map",
    "prop_palm_tree.glb": "gta palm tree",
}


def search_sketchfab(query: str, count: int = 3) -> List[dict]:
    url = (
        "https://api.sketchfab.com/v3/search?type=models&downloadable=true"
        f"&count={count}&q={urllib.parse.quote(query)}"
    )
    req = urllib.request.Request(url, headers={"User-Agent": "SunsetGroveAndroid/1.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    results = []
    for item in data.get("results", []):
        lic = (item.get("license") or {}).get("slug", "standard")
        results.append({
            "uid": item["uid"],
            "name": item["name"],
            "faceCount": item.get("faceCount", 0),
            "license": lic,
            "viewerUrl": item.get("viewerUrl", ""),
        })
    return results


def run_search() -> Dict[str, List[dict]]:
    catalog: Dict[str, List[dict]] = {}
    for target_file, query in SKETCHFAB_SLOTS.items():
        try:
            hits = search_sketchfab(query, count=3)
            catalog[target_file] = hits
            print(f"=== {target_file} (query: '{query}') ===")
            for h in hits:
                print(f"  {h['uid']} | {h['name']} | faces={h['faceCount']} | lic={h['license']} | {h['viewerUrl']}")
        except Exception as exc:
            print(f"=== {target_file} ERR: {exc} ===")
            catalog[target_file] = []
    CATALOG_PATH.write_text(json.dumps(catalog, indent=2), encoding="utf-8")
    print(f"\nSaved Sketchfab GTA catalog to {CATALOG_PATH}")
    return catalog


def download_with_token(token: str, include_full_map: bool = False) -> None:
    if CATALOG_PATH.exists():
        catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    else:
        catalog = run_search()

    OVERRIDES_DIR.mkdir(parents=True, exist_ok=True)
    headers = {
        "Authorization": f"Token {token}",
        "User-Agent": "SunsetGroveAndroid/1.0",
    }

    for target_file, hits in catalog.items():
        if target_file == "world_san_andreas_map.glb" and not include_full_map:
            print(f"Skipping {target_file} (pass --include-map if you want to download a large full map mesh).")
            continue
        if not hits:
            continue
        uid = hits[0]["uid"]
        name = hits[0]["name"]
        dl_url = f"https://api.sketchfab.com/v3/models/{uid}/download"
        print(f"Requesting Sketchfab download for {target_file} <- {name} ({uid})...")
        try:
            req = urllib.request.Request(dl_url, headers=headers)
            meta = json.loads(urllib.request.urlopen(req, timeout=20).read().decode("utf-8"))
            glb_info = meta.get("glb") or meta.get("gltf")
            if not glb_info or "url" not in glb_info:
                print(f"  [skip] No GLB/GLTF archive available for {uid}")
                continue
            blob = urllib.request.urlopen(glb_info["url"], timeout=60).read()
            out_path = OVERRIDES_DIR / target_file
            if blob[:4] == b"glTF":
                out_path.write_bytes(blob)
                print(f"  [ok] Wrote direct GLB -> {out_path} ({len(blob) // 1024} KB)")
            else:
                # Extract first .glb inside zip archive if returned zipped
                with zipfile.ZipFile(io.BytesIO(blob)) as zf:
                    glb_names = [n for n in zf.namelist() if n.lower().endswith(".glb")]
                    if glb_names:
                        out_path.write_bytes(zf.read(glb_names[0]))
                        print(f"  [ok] Extracted {glb_names[0]} -> {out_path}")
                    else:
                        print(f"  [warn] Zip for {uid} contained glTF+bins instead of single GLB; manual Blender export needed.")
        except Exception as exc:
            print(f"  [err] {target_file}: {exc}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--search", action="store_true", help="Search Sketchfab for downloadable GTA models")
    parser.add_argument("--token", type=str, default=None, help="Your Sketchfab API token to download GLB overrides")
    parser.add_argument("--include-map", action="store_true", help="Also download the full GTA San Andreas map mesh")
    args = parser.parse_args()

    if args.token:
        download_with_token(args.token, include_full_map=args.include_map)
        return 0
    run_search()
    return 0


if __name__ == "__main__":
    sys.exit(main())
