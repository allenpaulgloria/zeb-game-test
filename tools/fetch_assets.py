#!/usr/bin/env python3
"""
Sunset Grove — Trusted Free 3D PBR Asset & Audio Pipeline.

Downloads real external free CC0 / MIT 3D models & 1K PBR textures:
- Three.js official repo (MIT): Soldier.glb (rigged Mixamo humanoid with Idle/Walk/Run)
  and generates material-tinted variants for Street Pedestrians and Purple GTA Ballas Gang
  (plus supports zero-code Sketchfab overrides via tools/fetch_sketchfab.py).
- Poly Haven Public API (100% CC0): photorealistic street props (street_lamp_01,
  fire_hydrant, concrete_road_barrier, painted_wooden_bench, barrel_03) & 1K PBR textures.
- Generates crisp 16-bit PCM WAV SFX (engine, siren, crash, gunshot, bat hit, water splash,
  tank cannon, jetpack, cheat chime, WASTED/RESPECT+) AND a full 90s West Coast G-Funk
  audio-only intro & radio music theme (music_intro_theme.wav).
"""

from __future__ import annotations

import argparse
import io
import json
import math
import random
import struct
import sys
import urllib.request
import wave
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    from PIL import Image, ImageFilter
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
MODELS_DIR = ASSETS_DIR / "models"
PROPS_DIR = MODELS_DIR / "props"
WORLD_DIR = MODELS_DIR / "world"
OVERRIDES_DIR = MODELS_DIR / "overrides"
AUDIO_DIR = ASSETS_DIR / "audio"
CACHE_DIR = ROOT / "tools" / ".asset_cache"

USER_AGENT = "SunsetGroveAndroidGame/1.1 (https://github.com/allenpaulgloria/zeb-game-test)"


def http_get_bytes(url: str, timeout: int = 25) -> Optional[bytes]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read()
    except Exception as exc:
        print(f"  [warn] Failed to fetch {url}: {exc}")
        return None


def download_cached(url: str, cache_name: str, offline: bool = False) -> Optional[bytes]:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cache_path = CACHE_DIR / cache_name
    if cache_path.exists() and cache_path.stat().st_size > 128:
        return cache_path.read_bytes()
    if offline:
        return None
    print(f"  [fetch] {url}")
    data = http_get_bytes(url)
    if data:
        cache_path.write_bytes(data)
    return data


def tint_glb_materials(src_glb: bytes, out_path: Path, rgba: Tuple[float, float, float, float]) -> bool:
    """
    Create a tinted copy of a real external rigged .glb (e.g., Soldier.glb -> Purple GTA Ballas
    or Street Pedestrian) by adjusting pbrMetallicRoughness.baseColorFactor while preserving
    the full mesh, skeleton, normals, UVs, and animations untouched!
    """
    if len(src_glb) < 28:
        return False
    magic, version, _ = struct.unpack("<III", src_glb[:12])
    if magic != 0x46546C67 or version != 2:
        return False
    json_len, json_type = struct.unpack("<II", src_glb[12:20])
    if json_type != 0x4E4F534A:
        return False
    gltf = json.loads(src_glb[20 : 20 + json_len].decode("utf-8"))
    bin_offset = 20 + json_len
    bin_len, bin_type = struct.unpack("<II", src_glb[bin_offset : bin_offset + 8])
    bin_data = src_glb[bin_offset + 8 : bin_offset + 8 + bin_len]

    for mat in gltf.get("materials", []):
        pbr = mat.setdefault("pbrMetallicRoughness", {})
        pbr["baseColorFactor"] = list(rgba)

    write_glb_raw(out_path, gltf, bin_data)
    return True


def fetch_polyhaven_texture_diffuse(slug: str, offline: bool = False) -> Optional[bytes]:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cache_jpg = CACHE_DIR / f"ph_tex_{slug}_1k.jpg"
    if cache_jpg.exists() and cache_jpg.stat().st_size > 1000:
        return cache_jpg.read_bytes()
    if offline:
        return None

    meta_bytes = http_get_bytes(f"https://api.polyhaven.com/files/{slug}", timeout=15)
    if not meta_bytes:
        return None
    try:
        meta = json.loads(meta_bytes.decode("utf-8"))
        diff = meta.get("Diffuse") or meta.get("diff") or meta.get("col") or {}
        res_block = diff.get("1k") or diff.get("2k") or next(iter(diff.values()), {})
        fmt_block = res_block.get("jpg") or res_block.get("png") or next(iter(res_block.values()), {})
        url = fmt_block.get("url")
        if not url:
            return None
        print(f"  [polyhaven tex] {slug} -> {url}")
        img_bytes = http_get_bytes(url, timeout=25)
        if img_bytes:
            if HAS_PIL:
                im = Image.open(io.BytesIO(img_bytes)).convert("RGB")
                im = im.resize((512, 512), Image.Resampling.LANCZOS)
                out = io.BytesIO()
                im.save(out, format="JPEG", quality=88)
                img_bytes = out.getvalue()
            cache_jpg.write_bytes(img_bytes)
            return img_bytes
    except Exception as exc:
        print(f"  [warn] Poly Haven texture error for {slug}: {exc}")
    return None


def fetch_polyhaven_gltf_as_glb(slug: str, out_path: Path, offline: bool = False) -> Optional[Dict[str, str]]:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cached_glb = CACHE_DIR / f"ph_model_{slug}_1k.glb"
    if cached_glb.exists() and is_valid_glb(cached_glb):
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_bytes(cached_glb.read_bytes())
        return {"source": f"https://polyhaven.com/a/{slug}", "license": "CC0 1.0 (Poly Haven)", "origin": "external_polyhaven"}
    if offline:
        return None

    meta_bytes = http_get_bytes(f"https://api.polyhaven.com/files/{slug}", timeout=15)
    if not meta_bytes:
        return None
    try:
        meta = json.loads(meta_bytes.decode("utf-8"))
        gltf_block = meta.get("gltf") or {}
        res_block = gltf_block.get("1k") or gltf_block.get("2k")
        if not res_block:
            return None
        entry = res_block.get("gltf")
        if not entry or "url" not in entry:
            return None
        gltf_url = entry["url"]
        print(f"  [polyhaven model] {slug} -> {gltf_url}")
        gltf_raw = http_get_bytes(gltf_url, timeout=25)
        if not gltf_raw:
            return None
        gltf = json.loads(gltf_raw.decode("utf-8"))
        include_map: Dict[str, Any] = entry.get("include", {})
        bin_chunks = bytearray()

        def append_chunk(raw_bytes: bytes) -> Tuple[int, int]:
            while len(bin_chunks) % 4 != 0:
                bin_chunks.append(0)
            offset = len(bin_chunks)
            bin_chunks.extend(raw_bytes)
            return offset, len(raw_bytes)

        buffer_offsets: List[int] = []
        for buf in gltf.get("buffers", []):
            uri = buf.get("uri", "")
            bdata = http_get_bytes(include_map[uri]["url"], timeout=25) if uri in include_map else http_get_bytes(f"{gltf_url.rsplit('/', 1)[0]}/{uri}", timeout=25)
            if not bdata:
                return None
            off, _ = append_chunk(bdata)
            buffer_offsets.append(off)
            buf.pop("uri", None)

        for bv in gltf.get("bufferViews", []):
            bidx = bv.get("buffer", 0)
            bv["buffer"] = 0
            bv["byteOffset"] = bv.get("byteOffset", 0) + buffer_offsets[bidx]

        buffer_views = gltf.setdefault("bufferViews", [])
        for img in gltf.get("images", []):
            uri = img.get("uri")
            if not uri:
                continue
            idata = http_get_bytes(include_map[uri]["url"], timeout=25) if uri in include_map else http_get_bytes(f"{gltf_url.rsplit('/', 1)[0]}/{uri}", timeout=25)
            if not idata:
                return None
            mime = "image/png" if uri.lower().endswith(".png") else "image/jpeg"
            if HAS_PIL:
                try:
                    im = Image.open(io.BytesIO(idata))
                    if max(im.size) > 512:
                        im.thumbnail((512, 512), Image.Resampling.LANCZOS)
                    out_buf = io.BytesIO()
                    if im.mode in ("RGBA", "LA"):
                        im.save(out_buf, format="PNG", optimize=True)
                        mime = "image/png"
                    else:
                        im.convert("RGB").save(out_buf, format="JPEG", quality=85)
                        mime = "image/jpeg"
                    idata = out_buf.getvalue()
                except Exception:
                    pass
            off, length = append_chunk(idata)
            bv_idx = len(buffer_views)
            buffer_views.append({"buffer": 0, "byteOffset": off, "byteLength": length})
            img.pop("uri", None)
            img["bufferView"] = bv_idx
            img["mimeType"] = mime

        while len(bin_chunks) % 4 != 0:
            bin_chunks.append(0)
        gltf["buffers"] = [{"byteLength": len(bin_chunks)}]
        write_glb_raw(cached_glb, gltf, bytes(bin_chunks))
        write_glb_raw(out_path, gltf, bytes(bin_chunks))
        return {"source": f"https://polyhaven.com/a/{slug}", "license": "CC0 1.0 (Poly Haven)", "origin": "external_polyhaven"}
    except Exception as exc:
        print(f"  [warn] Poly Haven model packing failed for {slug}: {exc}")
        return None


def write_glb_raw(path: Path, gltf_json: Dict[str, Any], bin_data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    json_bytes = json.dumps(gltf_json, separators=(",", ":")).encode("utf-8")
    while len(json_bytes) % 4 != 0:
        json_bytes += b" "
    bin_padded = bytearray(bin_data)
    while len(bin_padded) % 4 != 0:
        bin_padded.append(0)
    total_length = 12 + 8 + len(json_bytes) + 8 + len(bin_padded)
    header = struct.pack("<III", 0x46546C67, 2, total_length)
    json_chunk = struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes
    bin_chunk = struct.pack("<II", len(bin_padded), 0x004E4942) + bytes(bin_padded)
    path.write_bytes(header + json_chunk + bin_chunk)


def is_valid_glb(path: Path) -> bool:
    if not path.exists() or path.stat().st_size < 32:
        return False
    data = path.read_bytes()[:12]
    magic, version, length = struct.unpack("<III", data)
    return magic == 0x46546C67 and version == 2 and length == path.stat().st_size


class MeshAccumulator:
    def __init__(self) -> None:
        self.positions: List[Tuple[float, float, float]] = []
        self.normals: List[Tuple[float, float, float]] = []
        self.uvs: List[Tuple[float, float]] = []
        self.indices: List[int] = []

    def add_quad(self, p0, p1, p2, p3, uv_scale=(1.0, 1.0)) -> None:
        ux, uy, uz = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
        vx, vy, vz = p3[0] - p0[0], p3[1] - p0[1], p3[2] - p0[2]
        nx = uy * vz - uz * vy
        ny = uz * vx - ux * vz
        nz = ux * vy - uy * vx
        mag = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        n = (nx / mag, ny / mag, nz / mag)
        base = len(self.positions)
        self.positions.extend([p0, p1, p2, p3])
        self.normals.extend([n, n, n, n])
        su, sv = uv_scale
        self.uvs.extend([(0.0, 0.0), (su, 0.0), (su, sv), (0.0, sv)])
        self.indices.extend([base, base + 1, base + 2, base, base + 2, base + 3])

    def add_box(self, cx, cy, cz, sx, sy, sz, uv_tile=1.0, taper_top_x=1.0, taper_top_z=1.0, shift_top_z=0.0) -> None:
        hx, hy, hz = sx * 0.5, sy * 0.5, sz * 0.5
        tx, tz = hx * taper_top_x, hz * taper_top_z
        b0 = (cx - hx, cy - hy, cz - hz); b1 = (cx + hx, cy - hy, cz - hz)
        b2 = (cx + hx, cy - hy, cz + hz); b3 = (cx - hx, cy - hy, cz + hz)
        t0 = (cx - tx, cy + hy, cz - tz + shift_top_z); t1 = (cx + tx, cy + hy, cz - tz + shift_top_z)
        t2 = (cx + tx, cy + hy, cz + tz + shift_top_z); t3 = (cx - tx, cy + hy, cz + tz + shift_top_z)
        u_x, u_y, u_z = max(1.0, sx / uv_tile), max(1.0, sy / uv_tile), max(1.0, sz / uv_tile)
        self.add_quad(b3, b2, t2, t3, (u_x, u_y))
        self.add_quad(b1, b0, t0, t1, (u_x, u_y))
        self.add_quad(b2, b1, t1, t2, (u_z, u_y))
        self.add_quad(b0, b3, t3, t0, (u_z, u_y))
        self.add_quad(t3, t2, t1, t0, (u_x, u_z))
        self.add_quad(b0, b1, b2, b3, (u_x, u_z))

    def add_cylinder(self, cx, cy, cz, rb, rt, length, axis="y", segments=18, cap=True) -> None:
        half = length * 0.5
        ring_b, ring_t = [], []
        for i in range(segments + 1):
            ang = 2.0 * math.pi * i / segments
            ca, sa = math.cos(ang), math.sin(ang)
            u = i / segments
            if axis == "y":
                pb, pt, n = (cx + rb * ca, cy - half, cz + rb * sa), (cx + rt * ca, cy + half, cz + rt * sa), (ca, 0.0, sa)
            elif axis == "x":
                pb, pt, n = (cx - half, cy + rb * ca, cz + rb * sa), (cx + half, cy + rt * ca, cz + rt * sa), (0.0, ca, sa)
            else:
                pb, pt, n = (cx + rb * ca, cy + rb * sa, cz - half), (cx + rt * ca, cy + rt * sa, cz + half), (ca, sa, 0.0)
            ring_b.append(len(self.positions)); self.positions.append(pb); self.normals.append(n); self.uvs.append((u, 0.0))
            ring_t.append(len(self.positions)); self.positions.append(pt); self.normals.append(n); self.uvs.append((u, 1.0))
        for i in range(segments):
            b0, b1, t0, t1 = ring_b[i], ring_b[i + 1], ring_t[i], ring_t[i + 1]
            if axis == "y":
                self.indices.extend([b0, t0, t1, b0, t1, b1])
            else:
                self.indices.extend([b0, b1, t1, b0, t1, t0])

    def add_sphere(self, cx, cy, cz, rx, ry, rz, rings=10, sectors=16) -> None:
        base = len(self.positions)
        for r in range(rings + 1):
            phi = math.pi * r / rings
            sp, cp = math.sin(phi), math.cos(phi)
            for s in range(sectors + 1):
                theta = 2.0 * math.pi * s / sectors
                st, ct = math.sin(theta), math.cos(theta)
                nx, ny, nz = sp * ct, cp, sp * st
                self.positions.append((cx + rx * nx, cy + ry * ny, cz + rz * nz))
                self.normals.append((nx, ny, nz))
                self.uvs.append((s / sectors, r / rings))
        stride = sectors + 1
        for r in range(rings):
            for s in range(sectors):
                a = base + r * stride + s
                b = a + stride
                self.indices.extend([a, a + 1, b, a + 1, b + 1, b])


class GlbSceneBuilder:
    def __init__(self) -> None:
        self.materials: List[Dict[str, Any]] = []
        self.images: List[Tuple[bytes, str]] = []
        self.nodes: List[Dict[str, Any]] = []
        self.node_meshes: List[Dict[int, MeshAccumulator]] = []

    def add_texture(self, img_bytes: bytes, mime: str = "image/jpeg") -> int:
        idx = len(self.images)
        self.images.append((img_bytes, mime))
        return idx

    def add_material(self, name, base_color=(1, 1, 1, 1), metallic=0.0, roughness=0.65, emissive=None, texture_idx=None, double_sided=False) -> int:
        pbr: Dict[str, Any] = {"baseColorFactor": list(base_color), "metallicFactor": metallic, "roughnessFactor": roughness}
        if texture_idx is not None:
            pbr["baseColorTexture"] = {"index": texture_idx}
        mat: Dict[str, Any] = {"name": name, "pbrMetallicRoughness": pbr}
        if emissive is not None:
            mat["emissiveFactor"] = list(emissive)
        if double_sided:
            mat["doubleSided"] = True
        idx = len(self.materials)
        self.materials.append(mat)
        return idx

    def add_node(self, name, translation=(0.0, 0.0, 0.0), parent=None) -> int:
        idx = len(self.nodes)
        node: Dict[str, Any] = {"name": name}
        if translation != (0.0, 0.0, 0.0):
            node["translation"] = list(translation)
        self.nodes.append(node)
        self.node_meshes.append({})
        if parent is not None:
            self.nodes[parent].setdefault("children", []).append(idx)
        return idx

    def mesh_for(self, node_idx: int, mat_idx: int) -> MeshAccumulator:
        bucket = self.node_meshes[node_idx]
        if mat_idx not in bucket:
            bucket[mat_idx] = MeshAccumulator()
        return bucket[mat_idx]

    def export(self, out_path: Path) -> None:
        bin_data = bytearray()
        buffer_views, accessors, meshes = [], [], []

        def push_bytes(raw: bytes, target: Optional[int] = None) -> int:
            while len(bin_data) % 4 != 0:
                bin_data.append(0)
            offset = len(bin_data)
            bin_data.extend(raw)
            bv: Dict[str, Any] = {"buffer": 0, "byteOffset": offset, "byteLength": len(raw)}
            if target is not None:
                bv["target"] = target
            idx = len(buffer_views)
            buffer_views.append(bv)
            return idx

        gltf_images, gltf_textures = [], []
        for img_bytes, mime in self.images:
            bv_idx = push_bytes(img_bytes)
            img_idx = len(gltf_images)
            gltf_images.append({"bufferView": bv_idx, "mimeType": mime})
            gltf_textures.append({"sampler": 0, "source": img_idx})

        for node_idx, mat_map in enumerate(self.node_meshes):
            if not mat_map:
                continue
            primitives = []
            for mat_idx, acc in mat_map.items():
                if not acc.indices:
                    continue
                pos_raw, norm_raw, uv_raw, idx_raw = bytearray(), bytearray(), bytearray(), bytearray()
                min_p, max_p = [1e9, 1e9, 1e9], [-1e9, -1e9, -1e9]
                for x, y, z in acc.positions:
                    pos_raw.extend(struct.pack("<fff", x, y, z))
                    min_p[0] = min(min_p[0], x); max_p[0] = max(max_p[0], x)
                    min_p[1] = min(min_p[1], y); max_p[1] = max(max_p[1], y)
                    min_p[2] = min(min_p[2], z); max_p[2] = max(max_p[2], z)
                for nx, ny, nz in acc.normals:
                    norm_raw.extend(struct.pack("<fff", nx, ny, nz))
                for u, v in acc.uvs:
                    uv_raw.extend(struct.pack("<ff", u, v))
                for i in acc.indices:
                    idx_raw.extend(struct.pack("<I", i))

                pos_bv = push_bytes(bytes(pos_raw), 34962)
                norm_bv = push_bytes(bytes(norm_raw), 34962)
                uv_bv = push_bytes(bytes(uv_raw), 34962)
                idx_bv = push_bytes(bytes(idx_raw), 34963)
                pos_acc = len(accessors); accessors.append({"bufferView": pos_bv, "componentType": 5126, "count": len(acc.positions), "type": "VEC3", "min": min_p, "max": max_p})
                norm_acc = len(accessors); accessors.append({"bufferView": norm_bv, "componentType": 5126, "count": len(acc.normals), "type": "VEC3"})
                uv_acc = len(accessors); accessors.append({"bufferView": uv_bv, "componentType": 5126, "count": len(acc.uvs), "type": "VEC2"})
                idx_acc = len(accessors); accessors.append({"bufferView": idx_bv, "componentType": 5125, "count": len(acc.indices), "type": "SCALAR"})
                primitives.append({"attributes": {"POSITION": pos_acc, "NORMAL": norm_acc, "TEXCOORD_0": uv_acc}, "indices": idx_acc, "material": mat_idx})

            mesh_idx = len(meshes)
            meshes.append({"name": self.nodes[node_idx]["name"], "primitives": primitives})
            self.nodes[node_idx]["mesh"] = mesh_idx

        child_set = {c for n in self.nodes for c in n.get("children", [])}
        roots = [i for i in range(len(self.nodes)) if i not in child_set]
        gltf: Dict[str, Any] = {
            "asset": {"version": "2.0", "generator": "SunsetGrove PBR Asset Pipeline"},
            "scene": 0, "scenes": [{"nodes": roots}], "nodes": self.nodes, "meshes": meshes,
            "materials": self.materials, "bufferViews": buffer_views, "accessors": accessors,
            "buffers": [{"byteLength": len(bin_data)}],
        }
        if gltf_images:
            gltf["samplers"] = [{"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 10497}]
            gltf["images"] = gltf_images
            gltf["textures"] = gltf_textures
        write_glb_raw(out_path, gltf, bytes(bin_data))


def build_fx_assets() -> None:
    # Bullet tracer for Purple Gang / Armed Carjackers / Player gunfights
    b = GlbSceneBuilder()
    m = b.add_material("BulletTracer", (1.0, 0.88, 0.25, 1.0), roughness=0.1, emissive=(1.0, 0.82, 0.15))
    r = b.add_node("BulletRoot")
    b.mesh_for(r, m).add_box(0.0, 0.0, 0.0, 0.09, 0.09, 0.85)
    b.export(PROPS_DIR / "bullet_tracer.glb")

    # Wooden pier slab for Verona Beach / Pacific Ocean
    b2 = GlbSceneBuilder()
    mw = b2.add_material("PierWood", (0.52, 0.36, 0.22, 1.0), roughness=0.88)
    r2 = b2.add_node("PierRoot")
    m2 = b2.mesh_for(r2, mw)
    m2.add_box(0.0, 1.2, 0.0, 48.0, 0.35, 9.0)
    for px in (-20, -10, 0, 10, 20):
        for pz in (-3.6, 3.6):
            m2.add_cylinder(px, 0.0, pz, 0.28, 0.28, 2.6, axis="y", segments=10)
    b2.export(WORLD_DIR / "beach_pier.glb")


def write_wav(path: Path, samples: List[float], sample_rate: int = 22050) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        raw = bytearray()
        for s in samples:
            clamped = max(-0.96, min(0.96, s))
            raw.extend(struct.pack("<h", int(clamped * 32767)))
        wf.writeframes(bytes(raw))


def generate_west_coast_theme_and_new_sfx() -> None:
    sr = 22050
    rng = random.Random(1992)

    # 1. Gunshot crisp crack
    n = int(sr * 0.22)
    gun = [
        math.exp(-18.0 * (i / sr)) * (0.72 * rng.uniform(-1, 1) + 0.38 * math.sin(2 * math.pi * 160 * (i / sr)))
        for i in range(n)
    ]
    write_wav(AUDIO_DIR / "sfx_gunshot.wav", gun, sr)

    # 2. Baseball bat thwack
    n = int(sr * 0.18)
    bat = [
        math.exp(-22.0 * (i / sr)) * (0.65 * math.sin(2 * math.pi * 240 * (i / sr)) + 0.35 * rng.uniform(-1, 1))
        for i in range(n)
    ]
    write_wav(AUDIO_DIR / "sfx_bat_hit.wav", bat, sr)

    # 3. Ocean swimming splash loop
    n = int(sr * 1.2)
    splash = []
    lp = 0.0
    for i in range(n):
        t = i / sr
        w = rng.uniform(-1, 1)
        lp = 0.80 * lp + 0.20 * w
        stroke_env = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(2 * math.pi * 1.66 * t))
        splash.append(0.32 * stroke_env * lp)
    write_wav(AUDIO_DIR / "sfx_swim.wav", splash, sr)

    # 4. Rich 90s West Coast G-Funk / San Andreas-Style Audio-Only Music Loop (16-bar / ~20.8s loop at 92 BPM!)
    bpm = 92.0
    beat_sec = 60.0 / bpm
    total_beats = 32  # 8 full bars seamless loop
    total_sec = total_beats * beat_sec
    total_samples = int(sr * total_sec)

    # Iconic West Coast minor groove (D minor / F / G / A vibe)
    bass_pattern = [
        (0.0, 73.42, 0.8), (1.5, 73.42, 0.5), (2.0, 87.31, 0.7), (3.0, 82.41, 0.8),
        (4.0, 65.41, 0.8), (5.5, 65.41, 0.5), (6.0, 73.42, 0.7), (7.0, 98.00, 0.8),
    ]
    # Classic high G-Funk synth whistle melody
    lead_pattern = [
        (0.0, 1174.66, 1.4), (1.5, 1046.50, 0.5), (2.0, 880.00, 1.5), (3.5, 1046.50, 0.5),
        (4.0, 1174.66, 1.0), (5.0, 1318.51, 0.9), (6.0, 1174.66, 1.8),
    ]
    chords = [
        (0.0, [293.66, 349.23, 440.00, 523.25]),  # Dm7
        (4.0, [261.63, 329.63, 392.00, 493.88]),  # Cmaj7
    ]

    music = [0.0] * total_samples
    for i in range(total_samples):
        t = i / sr
        beat = t / beat_sec
        bar_beat = beat % 8.0
        step_in_beat = beat % 1.0
        half_beat = beat % 0.5

        val = 0.0
        # Boom-bap Kick on 0, 2.5, 4, 6.5
        for kb in (0.0, 2.5, 4.0, 6.5):
            dt_k = bar_beat - kb
            if 0.0 <= dt_k < 0.35:
                sec_k = dt_k * beat_sec
                f_k = 135.0 * math.exp(-18.0 * sec_k) + 44.0
                val += 0.42 * math.exp(-10.0 * sec_k) * math.sin(2 * math.pi * f_k * sec_k)

        # Crisp West Coast Snare/Clap on 1, 3, 5, 7
        for sb in (1.0, 3.0, 5.0, 7.0):
            dt_s = bar_beat - sb
            if 0.0 <= dt_s < 0.28:
                sec_s = dt_s * beat_sec
                val += 0.24 * math.exp(-16.0 * sec_s) * (rng.uniform(-1, 1) * 0.75 + math.sin(2 * math.pi * 210 * sec_s) * 0.35)

        # 8th-note closed hi-hat
        sec_h = half_beat * beat_sec
        if sec_h < 0.05:
            val += 0.07 * math.exp(-55.0 * sec_h) * rng.uniform(-1, 1)

        # Deep Funk Bassline
        for b_start, freq, dur in bass_pattern:
            dt_b = bar_beat - b_start
            if 0.0 <= dt_b < dur:
                sec_b = dt_b * beat_sec
                env = min(1.0, sec_b * 40.0) * math.exp(-2.8 * sec_b)
                val += 0.28 * env * (math.sin(2 * math.pi * freq * t) + 0.35 * math.sin(4 * math.pi * freq * t))

        # Warm electric piano chords
        for c_start, notes in chords:
            dt_c = bar_beat - c_start
            if 0.0 <= dt_c < 3.8:
                sec_c = dt_c * beat_sec
                env = min(1.0, sec_c * 14.0) * math.exp(-0.75 * sec_c)
                chord_sum = sum(math.sin(2 * math.pi * fn * t) for fn in notes) / len(notes)
                val += 0.16 * env * chord_sum

        # Iconic G-Funk high sine whistle (bars 3-8)
        if beat >= 8.0:
            for l_start, freq, dur in lead_pattern:
                dt_l = bar_beat - l_start
                if 0.0 <= dt_l < dur:
                    sec_l = dt_l * beat_sec
                    env = min(1.0, sec_l * 18.0) * math.exp(-1.1 * sec_l)
                    vib = 1.0 + 0.008 * math.sin(2 * math.pi * 5.8 * t)
                    val += 0.13 * env * math.sin(2 * math.pi * freq * vib * t)

        music[i] = val

    write_wav(AUDIO_DIR / "music_intro_theme.wav", music, sr)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()

    if not args.verify:
        for d in (MODELS_DIR, PROPS_DIR, WORLD_DIR, OVERRIDES_DIR, AUDIO_DIR):
            d.mkdir(parents=True, exist_ok=True)

        # Ensure real rigged external Soldier.glb is cached and generate real rigged
        # Street Pedestrian & Purple GTA Ballas Gang variants (NO hand-made box people!)
        soldier_url = "https://raw.githubusercontent.com/mrdoob/three.js/master/examples/models/gltf/Soldier.glb"
        soldier_bytes = download_cached(soldier_url, "Soldier.glb", offline=args.offline)
        if soldier_bytes:
            (MODELS_DIR / "character_player.glb").write_bytes(soldier_bytes)
            # Street pedestrian: warm civilian street tone
            tint_glb_materials(soldier_bytes, MODELS_DIR / "character_pedestrian.glb", (0.86, 0.80, 0.72, 1.0))
            # Purple GTA Ballas Gang: unmistakable royal purple!
            tint_glb_materials(soldier_bytes, MODELS_DIR / "character_ballas.glb", (0.64, 0.16, 0.96, 1.0))

        # Fetch additional real Poly Haven CC0 street props (bench + barrel)
        fetch_polyhaven_gltf_as_glb("painted_wooden_bench", PROPS_DIR / "wooden_bench.glb", offline=args.offline)
        fetch_polyhaven_gltf_as_glb("barrel_03", PROPS_DIR / "street_barrel.glb", offline=args.offline)

        build_fx_assets()
        generate_west_coast_theme_and_new_sfx()

        # Update manifest.json with new models
        manifest_path = MODELS_DIR / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {"version": 1, "models": {}}
        models = manifest["models"]
        models["character_pedestrian"] = {"path": "models/character_pedestrian.glb", "targetHeight": 1.76, "yawOffsetDeg": 180.0}
        models["character_ballas"] = {"path": "models/character_ballas.glb", "targetHeight": 1.82, "yawOffsetDeg": 180.0}
        models["fx_bullet"] = {"path": "models/props/bullet_tracer.glb", "targetHeight": 0.14, "yawOffsetDeg": 0.0}
        models["world_beach_pier"] = {"path": "models/world/beach_pier.glb", "targetHeight": 2.6, "yawOffsetDeg": 0.0}
        if (PROPS_DIR / "wooden_bench.glb").exists():
            models["prop_bench"] = {"path": "models/props/wooden_bench.glb", "targetHeight": 0.95, "yawOffsetDeg": 0.0}
        if (PROPS_DIR / "street_barrel.glb").exists():
            models["prop_barrel"] = {"path": "models/props/street_barrel.glb", "targetHeight": 1.0, "yawOffsetDeg": 0.0}
        manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    # Verify all models & audio
    manifest = json.loads((MODELS_DIR / "manifest.json").read_text(encoding="utf-8"))
    ok = True
    for key, spec in manifest["models"].items():
        p = ASSETS_DIR / spec["path"]
        if not is_valid_glb(p):
            print(f"INVALID GLB: {key} -> {p}")
            ok = False
        else:
            print(f"  OK  {key:24s} ({p.stat().st_size // 1024:4d} KB)  {spec['path']}")

    required_audio = [
        "sfx_engine.wav", "sfx_siren.wav", "sfx_crash.wav", "sfx_tank_cannon.wav",
        "sfx_jetpack.wav", "sfx_cheat.wav", "sfx_car_door.wav", "sfx_wasted.wav",
        "sfx_respect.wav", "sfx_gunshot.wav", "sfx_bat_hit.wav", "sfx_swim.wav",
        "music_intro_theme.wav",
    ]
    for a in required_audio:
        p = AUDIO_DIR / a
        if not p.exists() or p.stat().st_size < 1000:
            print(f"INVALID AUDIO: {p}")
            ok = False
        else:
            print(f"  OK  audio/{a:22s} ({p.stat().st_size // 1024:4d} KB)")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
