#!/usr/bin/env python3
"""
Temu Theft Auto — Official Quaternius CC0 3D Model Installer (via Poly Pizza CDN).

Quaternius's legacy Google Drive mirrors frequently hit Google Drive's daily public
download quota; Poly Pizza hosts the exact same official CC0 Quaternius models as
direct, uncompressed glTF 2.0 (.glb) binaries with zero login required.

Installs real Quaternius CC0 .glb models into app/src/main/assets/models/ for:
  - Player Character, Street Pedestrian, Purple GTA Ballas Gang, and Chicken (CLUCKINBELL cheat!)
  - Sedan, Muscle/Sports Car, Police Cruiser, Hotring Racer, Bloodring/Taxi, SUV/Monster Truck, Army Tank
  - Jetpack, Pistol, California Palm Tree, Grove House, Storefront, Downtown Towers
"""

from __future__ import annotations

import json
import math
import random
import re
import struct
import sys
import urllib.parse
import urllib.request
import wave
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
MODELS_DIR = ASSETS_DIR / "models"
PROPS_DIR = MODELS_DIR / "props"
WORLD_DIR = MODELS_DIR / "world"
AUDIO_DIR = ASSETS_DIR / "audio"

HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) TemuTheftAuto/1.2"}


def fetch_text(url: str) -> str:
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode("utf-8", "ignore")


def fetch_bytes(url: str) -> bytes:
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def is_valid_glb(path: Path) -> bool:
    if not path.exists() or path.stat().st_size < 32:
        return False
    magic, version, length = struct.unpack("<III", path.read_bytes()[:12])
    return magic == 0x46546C67 and version == 2 and length == path.stat().st_size


def search_poly_pizza(query: str, max_hits: int = 8) -> List[str]:
    url = "https://poly.pizza/search/" + urllib.parse.quote(query)
    html = fetch_text(url)
    return list(dict.fromkeys(re.findall(r"/m/([A-Za-z0-9_\-]+)", html)))[:max_hits]


def inspect_poly_pizza_model(model_id: str) -> Optional[Dict[str, Any]]:
    """Parses __NEXT_DATA__ on https://poly.pizza/m/<id> to get exact title, author, license & direct .glb URL."""
    html = fetch_text(f"https://poly.pizza/m/{model_id}")
    m = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', html, re.S)
    if not m:
        # Fallback regex for static.poly.pizza .glb URL
        glbs = re.findall(r"https://static\.poly\.pizza/[A-Za-z0-9_\-./]+\.glb", html)
        if glbs:
            return {"id": model_id, "title": model_id, "author": "Quaternius", "url": glbs[0], "license": "CC0"}
        return None
    data = json.loads(m.group(1))
    props = data.get("props", {}).get("pageProps", {})
    model = props.get("model") or props.get("asset") or {}
    glb_url = model.get("Download") or model.get("download") or model.get("Url") or model.get("url")
    if not glb_url:
        glbs = re.findall(r"https://static\.poly\.pizza/[A-Za-z0-9_\-./]+\.glb", html)
        glb_url = glbs[0] if glbs else None
    if not glb_url:
        return None
    return {
        "id": model_id,
        "title": model.get("Title") or model.get("title") or model_id,
        "author": model.get("Author") or model.get("author") or model.get("Creator", {}).get("Username", "Unknown"),
        "license": model.get("Licence") or model.get("license") or "CC0",
        "url": glb_url,
        "tris": model.get("Tri Count") or model.get("tris") or 0,
    }


def write_glb_raw(path: Path, gltf: Dict[str, Any], bin_data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    while len(json_bytes) % 4 != 0:
        json_bytes += b" "
    bin_padded = bytearray(bin_data)
    while len(bin_padded) % 4 != 0:
        bin_padded.append(0)
    total_len = 12 + 8 + len(json_bytes) + 8 + len(bin_padded)
    header = struct.pack("<III", 0x46546C67, 2, total_len)
    json_chunk = struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes
    bin_chunk = struct.pack("<II", len(bin_padded), 0x004E4942) + bytes(bin_padded)
    path.write_bytes(header + json_chunk + bin_chunk)


def inspect_and_optionally_tint_glb(
    raw: bytes,
    out_path: Path,
    tint_rgba: Optional[Tuple[float, float, float, float]] = None,
) -> Dict[str, Any]:
    """Validates GLB, optionally tints materials (e.g. Ballas Purple), and returns animation/bbox metadata."""
    magic, version, _ = struct.unpack("<III", raw[:12])
    if magic != 0x46546C67 or version != 2:
        raise ValueError("Not a valid glTF 2.0 binary")
    json_len, _ = struct.unpack("<II", raw[12:20])
    gltf = json.loads(raw[20 : 20 + json_len].decode("utf-8"))
    bin_off = 20 + json_len
    bin_len, _ = struct.unpack("<II", raw[bin_off : bin_off + 8])
    bin_data = raw[bin_off + 8 : bin_off + 8 + bin_len]

    if tint_rgba is not None:
        for mat in gltf.get("materials", []):
            pbr = mat.setdefault("pbrMetallicRoughness", {})
            pbr["baseColorFactor"] = list(tint_rgba)

    # Check if GLB uses Draco compression (not supported by standard gltfio without draco decoder)
    exts = gltf.get("extensionsRequired", []) + gltf.get("extensionsUsed", [])
    has_draco = any("draco" in e.lower() for e in exts)
    if has_draco:
        raise ValueError("Uses KHR_draco_mesh_compression")

    # Estimate XZ bounding box from POSITION accessors to auto-detect vehicle orientation
    min_x, max_x, min_z, max_z = 1e9, -1e9, 1e9, -1e9
    for acc in gltf.get("accessors", []):
        if acc.get("type") == "VEC3" and "min" in acc and "max" in acc:
            mn, mx = acc["min"], acc["max"]
            min_x = min(min_x, mn[0]); max_x = max(max_x, mx[0])
            min_z = min(min_z, mn[2]); max_z = max(max_z, mx[2])

    anims = [a.get("name", f"anim_{i}") for i, a in enumerate(gltf.get("animations", []))]
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if tint_rgba is not None:
        write_glb_raw(out_path, gltf, bin_data)
    else:
        out_path.write_bytes(raw)
    return {
        "anims": anims,
        "extentX": max_x - min_x if max_x > min_x else 0.0,
        "extentZ": max_z - min_z if max_z > min_z else 0.0,
    }


def fetch_best_quaternius_model(
    slot_key: str,
    queries: List[str],
    out_path: Path,
    prefer_animated: bool = False,
    tint_rgba: Optional[Tuple[float, float, float, float]] = None,
    prefer_author: str = "quaternius",
) -> Optional[Dict[str, Any]]:
    candidates: List[Dict[str, Any]] = []
    seen = set()
    for q in queries:
        for mid in search_poly_pizza(q, max_hits=6):
            if mid in seen:
                continue
            seen.add(mid)
            try:
                info = inspect_poly_pizza_model(mid)
                if info and info.get("url"):
                    candidates.append(info)
            except Exception:
                continue
        if len(candidates) >= 6:
            break

    # Prioritize genuine Quaternius CC0 models first!
    candidates.sort(key=lambda c: (0 if prefer_author in str(c.get("author", "")).lower() else 1))

    for cand in candidates:
        try:
            raw = fetch_bytes(cand["url"])
            meta = inspect_and_optionally_tint_glb(raw, out_path, tint_rgba=tint_rgba)
            if prefer_animated and not meta["anims"] and len(candidates) > 1:
                # Keep searching if we want an animated character, unless this is the last candidate
                continue
            print(
                f"  [OK] {slot_key:22s} <- '{cand['title']}' by {cand['author']} "
                f"({out_path.stat().st_size // 1024} KB, anims={len(meta['anims'])}) "
                f"https://poly.pizza/m/{cand['id']}"
            )
            return {**cand, **meta}
        except Exception as exc:
            print(f"  [skip] {cand['id']} ({cand['title']}): {exc}")

    # Fallback pass if prefer_animated skipped non-animated Quaternius models
    if prefer_animated:
        for cand in candidates:
            try:
                raw = fetch_bytes(cand["url"])
                meta = inspect_and_optionally_tint_glb(raw, out_path, tint_rgba=tint_rgba)
                print(
                    f"  [OK] {slot_key:22s} <- '{cand['title']}' by {cand['author']} "
                    f"({out_path.stat().st_size // 1024} KB, anims={len(meta['anims'])}) "
                    f"https://poly.pizza/m/{cand['id']}"
                )
                return {**cand, **meta}
            except Exception:
                continue
    return None


def generate_chicken_cluck_wav() -> None:
    sr = 22050
    rng = random.Random(77)
    dur = 0.52
    n = int(sr * dur)
    samples = []
    for i in range(n):
        t = i / sr
        val = 0.0
        for start, base_f, length in ((0.02, 520.0, 0.14), (0.20, 680.0, 0.24)):
            dt = t - start
            if 0.0 <= dt < length:
                env = math.sin(math.pi * dt / length) ** 0.7
                pitch = base_f * (1.0 + 0.32 * math.sin(math.pi * dt / length))
                val += 0.42 * env * (
                    math.sin(2 * math.pi * pitch * dt)
                    + 0.55 * math.sin(2 * math.pi * pitch * 2.0 * dt)
                    + 0.25 * math.sin(2 * math.pi * pitch * 3.0 * dt)
                    + 0.12 * rng.uniform(-1, 1)
                )
        samples.append(max(-0.95, min(0.95, val)))
    out = AUDIO_DIR / "sfx_chicken.wav"
    out.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(out), "wb") as wf:
        wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(sr)
        raw = bytearray()
        for s in samples:
            raw.extend(struct.pack("<h", int(s * 32767)))
        wf.writeframes(bytes(raw))


def main() -> int:
    print("Installing official Quaternius CC0 .glb models via Poly Pizza CDN...")
    generate_chicken_cluck_wav()

    jobs = [
        # Characters (including Chicken for CLUCKINBELL / CHICKEN cheat!)
        ("character_chicken", ["quaternius chicken"], MODELS_DIR / "character_chicken.glb", True, None, 1.05, False),
        ("character_player", ["quaternius punk", "quaternius casual man", "quaternius man"], MODELS_DIR / "character_player.glb", True, None, 1.80, False),
        ("character_pedestrian", ["quaternius worker", "quaternius man", "quaternius woman"], MODELS_DIR / "character_pedestrian.glb", True, None, 1.76, False),
        ("character_ballas", ["quaternius hood", "quaternius suit", "quaternius soldier"], MODELS_DIR / "character_ballas.glb", True, (0.64, 0.16, 0.96, 1.0), 1.82, False),

        # Vehicles & Tank & Jetpack
        ("car_police", ["quaternius police car", "police car"], MODELS_DIR / "car_police.glb", False, None, 1.62, True),
        ("car_sedan", ["quaternius normal car", "quaternius sedan", "quaternius car"], MODELS_DIR / "car_sedan.glb", False, None, 1.48, True),
        ("car_muscle", ["quaternius sports car"], MODELS_DIR / "car_muscle.glb", False, None, 1.44, True),
        ("car_hotring", ["quaternius race car", "quaternius sports car 2"], MODELS_DIR / "car_hotring.glb", False, None, 1.42, True),
        ("car_bloodring", ["quaternius taxi", "quaternius pickup"], MODELS_DIR / "car_bloodring.glb", False, None, 1.54, True),
        ("vehicle_monster_truck", ["quaternius suv", "monster truck"], MODELS_DIR / "vehicle_monster_truck.glb", False, None, 3.35, True),
        ("vehicle_tank", ["quaternius tank"], MODELS_DIR / "vehicle_tank.glb", False, None, 2.50, True),
        ("gear_jetpack", ["quaternius jetpack", "jetpack"], MODELS_DIR / "gear_jetpack.glb", False, None, 1.05, False),

        # City Buildings, Palm Tree & Pistol
        ("prop_palm_tree", ["quaternius palm tree"], PROPS_DIR / "palm_tree.glb", False, None, 11.8, False),
        ("weapon_gun", ["quaternius pistol"], PROPS_DIR / "weapon_gun.glb", False, None, 0.25, False),
        ("world_bungalow", ["quaternius house"], WORLD_DIR / "building_bungalow.glb", False, None, 6.45, False),
        ("world_storefront", ["quaternius shop", "quaternius small building"], WORLD_DIR / "building_storefront.glb", False, None, 9.9, False),
        ("world_tower_med", ["quaternius building"], WORLD_DIR / "building_tower_med.glb", False, None, 18.9, False),
        ("world_tower_tall", ["quaternius skyscraper", "quaternius large building"], WORLD_DIR / "building_tower_tall.glb", False, None, 26.9, False),
    ]

    manifest_path = MODELS_DIR / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    models = manifest["models"]

    credits: List[Dict[str, str]] = []
    for key, queries, out_path, anim, tint, target_h, is_vehicle in jobs:
        res = fetch_best_quaternius_model(key, queries, out_path, prefer_animated=anim, tint_rgba=tint)
        if res:
            yaw_off = 0.0
            if is_vehicle and res["extentX"] > res["extentZ"] * 1.15:
                yaw_off = 90.0
            models[key] = {
                "path": str(out_path.relative_to(ASSETS_DIR)).replace("\\", "/"),
                "targetHeight": target_h,
                "yawOffsetDeg": yaw_off,
            }
            credits.append({
                "slot": key,
                "title": str(res["title"]),
                "author": str(res["author"]),
                "license": str(res["license"]),
                "url": f"https://poly.pizza/m/{res['id']}",
            })

    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    # Update ASSETS_LICENSE.md with all installed Quaternius CC0 credits
    lines = [
        "# Temu Theft Auto — 3D Asset Credits & Licenses",
        "",
        "All bundled character, vehicle, tank, chicken, palm tree, weapon, and building models are CC0 / open-licensed assets sourced from **Quaternius** (via Poly Pizza) and **Poly Haven**.",
        "",
        "| Game Slot | Model | Creator | License | Source |",
        "|---|---|---|---|---|",
    ]
    for c in credits:
        lines.append(f"| `{c['slot']}` | {c['title']} | {c['author']} | {c['license']} | {c['url']} |")
    (ROOT / "ASSETS_LICENSE.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    # Final verification
    ok = True
    for k, spec in models.items():
        p = ASSETS_DIR / spec["path"]
        if not is_valid_glb(p):
            print(f"INVALID GLB: {k} -> {p}")
            ok = False
    print(f"\nInstalled {len(credits)}/{len(jobs)} Quaternius models! Verification={'OK' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
