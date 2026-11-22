#!/usr/bin/env python3
"""
Sunset Grove — Trusted Free 3D PBR Asset & Audio Pipeline.

1. Downloads verified free CC0 / MIT 3D models and 1K PBR textures from trusted sources:
   - Three.js official repository (MIT): Soldier.glb (rigged animated 3rd-person humanoid)
   - Poly Haven Public API (100% CC0): photorealistic street props (street_lamp_01,
     fire_hydrant, concrete_road_barrier, painted_wooden_bench, barrel_03) and PBR
     material textures (asphalt, worn concrete, beach sand, stucco plaster, bark, metal).
2. Builds high-detail smooth-shaded PBR glTF 2.0 (.glb) models embedded with downloaded
   Poly Haven PBR textures for vehicles (Sedan, Muscle, Police Cruiser, Hotring Racer,
   Bloodring Banger, Rhino Army Tank with independent Turret node, Monster Truck),
   Jetpack, California Palm Trees, and 90s West Coast district buildings/terrain.
3. Generates crisp 16-bit PCM WAV sound effects for vehicles, tank cannon, explosions,
   jetpack thrusters, sirens, cheat chimes, and WASTED/BUSTED/RESPECT+ stings.
4. Writes app/src/main/assets/models/manifest.json and ASSETS_LICENSE.md.

Usage:
    python tools/fetch_assets.py            # Fetch external assets + build all GLBs & audio
    python tools/fetch_assets.py --offline  # Build using cached/procedural PBR textures only
    python tools/fetch_assets.py --verify   # Validate all GLB headers, manifest, and audio
"""

from __future__ import annotations

import argparse
import io
import json
import math
import os
import random
import struct
import sys
import urllib.error
import urllib.request
import wave
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    from PIL import Image, ImageDraw, ImageFilter
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

USER_AGENT = "SunsetGroveAndroidGame/1.0 (https://github.com/sunsetgrove-android)"


# ---------------------------------------------------------------------------
# HTTP Download Helpers (Poly Haven CC0 API & Three.js Official GitHub)
# ---------------------------------------------------------------------------

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


def fetch_polyhaven_texture_diffuse(slug: str, offline: bool = False) -> Optional[bytes]:
    """Fetch a 1K CC0 diffuse JPG/PNG from Poly Haven's public API."""
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
            # Resize to crisp 512x512 JPEG to keep APK size fast & lean while high-detail
            if HAS_PIL:
                im = Image.open(io.BytesIO(img_bytes)).convert("RGB")
                im = im.resize((512, 512), Image.Resampling.LANCZOS)
                out = io.BytesIO()
                im.save(out, format="JPEG", quality=88)
                img_bytes = out.getvalue()
            cache_jpg.write_bytes(img_bytes)
            return img_bytes
    except Exception as exc:
        print(f"  [warn] Poly Haven texture parse error for {slug}: {exc}")
    return None


def fetch_polyhaven_gltf_as_glb(slug: str, out_path: Path, offline: bool = False) -> Optional[Dict[str, str]]:
    """
    Download a Poly Haven 1K glTF 2.0 model and pack all external .bin + textures
    into a single self-contained binary .glb so Filament loads it cleanly.
    """
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cached_glb = CACHE_DIR / f"ph_model_{slug}_1k.glb"
    if cached_glb.exists() and is_valid_glb(cached_glb):
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_bytes(cached_glb.read_bytes())
        return {
            "source": f"https://polyhaven.com/a/{slug}",
            "license": "CC0 1.0 (Poly Haven)",
            "origin": "external_polyhaven",
        }
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

        # Resolve and pack buffers + images into a single GLB binary chunk
        bin_chunks = bytearray()

        def append_chunk(raw_bytes: bytes) -> Tuple[int, int]:
            while len(bin_chunks) % 4 != 0:
                bin_chunks.append(0)
            offset = len(bin_chunks)
            bin_chunks.extend(raw_bytes)
            return offset, len(raw_bytes)

        # Resolve main buffer(s)
        buffer_offsets: List[int] = []
        for buf in gltf.get("buffers", []):
            uri = buf.get("uri", "")
            if uri in include_map:
                bdata = http_get_bytes(include_map[uri]["url"], timeout=25)
            else:
                base = gltf_url.rsplit("/", 1)[0]
                bdata = http_get_bytes(f"{base}/{uri}", timeout=25)
            if not bdata:
                return None
            off, _ = append_chunk(bdata)
            buffer_offsets.append(off)
            buf.pop("uri", None)

        # Remap existing bufferViews into unified buffer 0
        for bv in gltf.get("bufferViews", []):
            bidx = bv.get("buffer", 0)
            bv["buffer"] = 0
            bv["byteOffset"] = bv.get("byteOffset", 0) + buffer_offsets[bidx]

        # Embed external images into bufferViews (downscaling huge textures to 512px)
        buffer_views = gltf.setdefault("bufferViews", [])
        for img in gltf.get("images", []):
            uri = img.get("uri")
            if not uri:
                continue
            if uri in include_map:
                idata = http_get_bytes(include_map[uri]["url"], timeout=25)
            else:
                base = gltf_url.rsplit("/", 1)[0]
                idata = http_get_bytes(f"{base}/{uri}", timeout=25)
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
            buffer_views.append({
                "buffer": 0,
                "byteOffset": off,
                "byteLength": length,
            })
            img.pop("uri", None)
            img["bufferView"] = bv_idx
            img["mimeType"] = mime

        while len(bin_chunks) % 4 != 0:
            bin_chunks.append(0)
        gltf["buffers"] = [{"byteLength": len(bin_chunks)}]

        write_glb_raw(cached_glb, gltf, bytes(bin_chunks))
        write_glb_raw(out_path, gltf, bytes(bin_chunks))
        return {
            "source": f"https://polyhaven.com/a/{slug}",
            "license": "CC0 1.0 (Poly Haven)",
            "origin": "external_polyhaven",
        }
    except Exception as exc:
        print(f"  [warn] Poly Haven model packing failed for {slug}: {exc}")
        return None


# ---------------------------------------------------------------------------
# Pure-Python glTF 2.0 (.glb) Builder (Smooth Normals + UVs + Embedded PBR)
# ---------------------------------------------------------------------------

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
    """Collects smooth-shaded triangles with positions, normals, and UVs per material."""

    def __init__(self) -> None:
        self.positions: List[Tuple[float, float, float]] = []
        self.normals: List[Tuple[float, float, float]] = []
        self.uvs: List[Tuple[float, float]] = []
        self.indices: List[int] = []

    def add_quad(
        self,
        p0: Tuple[float, float, float],
        p1: Tuple[float, float, float],
        p2: Tuple[float, float, float],
        p3: Tuple[float, float, float],
        uv_scale: Tuple[float, float] = (1.0, 1.0),
    ) -> None:
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

    def add_box(
        self,
        cx: float, cy: float, cz: float,
        sx: float, sy: float, sz: float,
        uv_tile: float = 1.0,
        taper_top_x: float = 1.0,
        taper_top_z: float = 1.0,
        shift_top_z: float = 0.0,
    ) -> None:
        hx, hy, hz = sx * 0.5, sy * 0.5, sz * 0.5
        tx, tz = hx * taper_top_x, hz * taper_top_z
        # Bottom corners
        b0 = (cx - hx, cy - hy, cz - hz)
        b1 = (cx + hx, cy - hy, cz - hz)
        b2 = (cx + hx, cy - hy, cz + hz)
        b3 = (cx - hx, cy - hy, cz + hz)
        # Top corners
        t0 = (cx - tx, cy + hy, cz - tz + shift_top_z)
        t1 = (cx + tx, cy + hy, cz - tz + shift_top_z)
        t2 = (cx + tx, cy + hy, cz + tz + shift_top_z)
        t3 = (cx - tx, cy + hy, cz + tz + shift_top_z)

        u_x = max(1.0, sx / uv_tile)
        u_y = max(1.0, sy / uv_tile)
        u_z = max(1.0, sz / uv_tile)
        # Front (+Z), Back (-Z), Right (+X), Left (-X), Top (+Y), Bottom (-Y)
        self.add_quad(b3, b2, t2, t3, (u_x, u_y))
        self.add_quad(b1, b0, t0, t1, (u_x, u_y))
        self.add_quad(b2, b1, t1, t2, (u_z, u_y))
        self.add_quad(b0, b3, t3, t0, (u_z, u_y))
        self.add_quad(t3, t2, t1, t0, (u_x, u_z))
        self.add_quad(b0, b1, b2, b3, (u_x, u_z))

    def add_cylinder(
        self,
        cx: float, cy: float, cz: float,
        radius_bottom: float, radius_top: float,
        length: float,
        axis: str = "y",
        segments: int = 20,
        cap: bool = True,
    ) -> None:
        """Smooth-shaded cylinder/frustum along x, y, or z axis."""
        half = length * 0.5
        ring_b: List[int] = []
        ring_t: List[int] = []

        for i in range(segments + 1):
            ang = 2.0 * math.pi * i / segments
            ca, sa = math.cos(ang), math.sin(ang)
            u = i / segments

            if axis == "y":
                pb = (cx + radius_bottom * ca, cy - half, cz + radius_bottom * sa)
                pt = (cx + radius_top * ca, cy + half, cz + radius_top * sa)
                n = (ca, 0.0, sa)
            elif axis == "x":
                pb = (cx - half, cy + radius_bottom * ca, cz + radius_bottom * sa)
                pt = (cx + half, cy + radius_top * ca, cz + radius_top * sa)
                n = (0.0, ca, sa)
            else:  # "z"
                pb = (cx + radius_bottom * ca, cy + radius_bottom * sa, cz - half)
                pt = (cx + radius_top * ca, cy + radius_top * sa, cz + half)
                n = (ca, sa, 0.0)

            ring_b.append(len(self.positions))
            self.positions.append(pb)
            self.normals.append(n)
            self.uvs.append((u, 0.0))

            ring_t.append(len(self.positions))
            self.positions.append(pt)
            self.normals.append(n)
            self.uvs.append((u, 1.0))

        for i in range(segments):
            b0, b1 = ring_b[i], ring_b[i + 1]
            t0, t1 = ring_t[i], ring_t[i + 1]
            if axis == "y":
                self.indices.extend([b0, t0, t1, b0, t1, b1])
            else:
                self.indices.extend([b0, b1, t1, b0, t1, t0])

        if cap:
            for end_idx, (rad, sign) in enumerate([(radius_bottom, -1.0), (radius_top, 1.0)]):
                if rad <= 1e-4:
                    continue
                if axis == "y":
                    center = (cx, cy + sign * half, cz)
                    cn = (0.0, sign, 0.0)
                elif axis == "x":
                    center = (cx + sign * half, cy, cz)
                    cn = (sign, 0.0, 0.0)
                else:
                    center = (cx, cy, cz + sign * half)
                    cn = (0.0, 0.0, sign)
                c_idx = len(self.positions)
                self.positions.append(center)
                self.normals.append(cn)
                self.uvs.append((0.5, 0.5))
                cap_ring = []
                for i in range(segments + 1):
                    ang = 2.0 * math.pi * i / segments
                    ca, sa = math.cos(ang), math.sin(ang)
                    if axis == "y":
                        p = (cx + rad * ca, cy + sign * half, cz + rad * sa)
                    elif axis == "x":
                        p = (cx + sign * half, cy + rad * ca, cz + rad * sa)
                    else:
                        p = (cx + rad * ca, cy + rad * sa, cz + sign * half)
                    cap_ring.append(len(self.positions))
                    self.positions.append(p)
                    self.normals.append(cn)
                    self.uvs.append((0.5 + 0.5 * ca, 0.5 + 0.5 * sa))
                for i in range(segments):
                    flip = (sign < 0) if axis != "y" else (sign > 0)
                    if flip:
                        self.indices.extend([c_idx, cap_ring[i + 1], cap_ring[i]])
                    else:
                        self.indices.extend([c_idx, cap_ring[i], cap_ring[i + 1]])

    def add_sphere(
        self,
        cx: float, cy: float, cz: float,
        rx: float, ry: float, rz: float,
        rings: int = 12, sectors: int = 18,
    ) -> None:
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
    """Builds multi-node, multi-material PBR glTF 2.0 (.glb) assets with embedded textures."""

    def __init__(self) -> None:
        self.materials: List[Dict[str, Any]] = []
        self.images: List[Tuple[bytes, str]] = []
        self.nodes: List[Dict[str, Any]] = []
        self.node_meshes: List[Dict[int, MeshAccumulator]] = []

    def add_texture(self, img_bytes: bytes, mime: str = "image/jpeg") -> int:
        idx = len(self.images)
        self.images.append((img_bytes, mime))
        return idx

    def add_material(
        self,
        name: str,
        base_color: Tuple[float, float, float, float] = (1.0, 1.0, 1.0, 1.0),
        metallic: float = 0.0,
        roughness: float = 0.65,
        emissive: Optional[Tuple[float, float, float]] = None,
        texture_idx: Optional[int] = None,
        double_sided: bool = False,
        alpha_blend: bool = False,
    ) -> int:
        pbr: Dict[str, Any] = {
            "baseColorFactor": list(base_color),
            "metallicFactor": metallic,
            "roughnessFactor": roughness,
        }
        if texture_idx is not None:
            pbr["baseColorTexture"] = {"index": texture_idx}
        mat: Dict[str, Any] = {"name": name, "pbrMetallicRoughness": pbr}
        if emissive is not None:
            mat["emissiveFactor"] = list(emissive)
        if double_sided:
            mat["doubleSided"] = True
        if alpha_blend:
            mat["alphaMode"] = "BLEND"
        idx = len(self.materials)
        self.materials.append(mat)
        return idx

    def add_node(
        self,
        name: str,
        translation: Tuple[float, float, float] = (0.0, 0.0, 0.0),
        parent: Optional[int] = None,
    ) -> int:
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
        buffer_views: List[Dict[str, Any]] = []
        accessors: List[Dict[str, Any]] = []
        meshes: List[Dict[str, Any]] = []

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

        # Pack images first
        gltf_images: List[Dict[str, Any]] = []
        gltf_textures: List[Dict[str, Any]] = []
        for img_bytes, mime in self.images:
            bv_idx = push_bytes(img_bytes)
            img_idx = len(gltf_images)
            gltf_images.append({"bufferView": bv_idx, "mimeType": mime})
            gltf_textures.append({"sampler": 0, "source": img_idx})

        # Pack node meshes
        for node_idx, mat_map in enumerate(self.node_meshes):
            if not mat_map:
                continue
            primitives: List[Dict[str, Any]] = []
            for mat_idx, acc in mat_map.items():
                if not acc.indices:
                    continue
                pos_raw = bytearray()
                min_p = [1e9, 1e9, 1e9]
                max_p = [-1e9, -1e9, -1e9]
                for x, y, z in acc.positions:
                    pos_raw.extend(struct.pack("<fff", x, y, z))
                    min_p[0] = min(min_p[0], x); max_p[0] = max(max_p[0], x)
                    min_p[1] = min(min_p[1], y); max_p[1] = max(max_p[1], y)
                    min_p[2] = min(min_p[2], z); max_p[2] = max(max_p[2], z)

                norm_raw = bytearray()
                for nx, ny, nz in acc.normals:
                    norm_raw.extend(struct.pack("<fff", nx, ny, nz))

                uv_raw = bytearray()
                for u, v in acc.uvs:
                    uv_raw.extend(struct.pack("<ff", u, v))

                idx_raw = bytearray()
                for i in acc.indices:
                    idx_raw.extend(struct.pack("<I", i))

                pos_bv = push_bytes(bytes(pos_raw), 34962)
                norm_bv = push_bytes(bytes(norm_raw), 34962)
                uv_bv = push_bytes(bytes(uv_raw), 34962)
                idx_bv = push_bytes(bytes(idx_raw), 34963)

                pos_acc = len(accessors)
                accessors.append({
                    "bufferView": pos_bv, "componentType": 5126,
                    "count": len(acc.positions), "type": "VEC3",
                    "min": min_p, "max": max_p,
                })
                norm_acc = len(accessors)
                accessors.append({
                    "bufferView": norm_bv, "componentType": 5126,
                    "count": len(acc.normals), "type": "VEC3",
                })
                uv_acc = len(accessors)
                accessors.append({
                    "bufferView": uv_bv, "componentType": 5126,
                    "count": len(acc.uvs), "type": "VEC2",
                })
                idx_acc = len(accessors)
                accessors.append({
                    "bufferView": idx_bv, "componentType": 5125,
                    "count": len(acc.indices), "type": "SCALAR",
                })

                primitives.append({
                    "attributes": {
                        "POSITION": pos_acc,
                        "NORMAL": norm_acc,
                        "TEXCOORD_0": uv_acc,
                    },
                    "indices": idx_acc,
                    "material": mat_idx,
                })

            mesh_idx = len(meshes)
            meshes.append({"name": self.nodes[node_idx]["name"], "primitives": primitives})
            self.nodes[node_idx]["mesh"] = mesh_idx

        # Find root nodes (not referenced as children)
        child_set = set()
        for n in self.nodes:
            for c in n.get("children", []):
                child_set.add(c)
        roots = [i for i in range(len(self.nodes)) if i not in child_set]

        gltf: Dict[str, Any] = {
            "asset": {"version": "2.0", "generator": "SunsetGrove PBR Asset Builder"},
            "scene": 0,
            "scenes": [{"nodes": roots}],
            "nodes": self.nodes,
            "meshes": meshes,
            "materials": self.materials,
            "bufferViews": buffer_views,
            "accessors": accessors,
            "buffers": [{"byteLength": len(bin_data)}],
        }
        if gltf_images:
            gltf["samplers"] = [{
                "magFilter": 9729,
                "minFilter": 9987,
                "wrapS": 10497,
                "wrapT": 10497,
            }]
            gltf["images"] = gltf_images
            gltf["textures"] = gltf_textures

        write_glb_raw(out_path, gltf, bytes(bin_data))


# ---------------------------------------------------------------------------
# Procedural High-Detail PBR Texture Fallback (when offline or API unavailable)
# ---------------------------------------------------------------------------

def make_procedural_texture(kind: str, size: int = 256) -> bytes:
    """Create crisp tileable PBR surface texture JPEG using PIL."""
    if not HAS_PIL:
        # Minimal valid 1x1 JPEG fallback if PIL missing
        return b""
    rng = random.Random(hash(kind) & 0xFFFFFFFF)
    im = Image.new("RGB", (size, size))
    px = im.load()

    palettes = {
        "asphalt": ((52, 54, 58), 16),
        "concrete": ((176, 170, 158), 18),
        "sand": ((224, 194, 138), 16),
        "grass": ((124, 134, 72), 20),
        "stucco_warm": ((228, 198, 156), 14),
        "stucco_coral": ((214, 148, 114), 14),
        "stucco_mint": ((168, 198, 178), 14),
        "brick_city": ((156, 136, 122), 18),
        "bark": ((112, 86, 62), 18),
        "metal_olive": ((78, 92, 62), 12),
        "metal_rust": ((132, 88, 62), 22),
        "ocean": ((34, 108, 146), 14),
    }
    base, var = palettes.get(kind, ((150, 150, 150), 15))
    for y in range(size):
        for x in range(size):
            n = rng.randint(-var, var)
            if kind == "bark":
                n += int(14 * math.sin(y * 0.25))
            elif kind == "asphalt":
                if (x in range(size // 2 - 3, size // 2 + 3)) and ((y // 32) % 2 == 0):
                    px[x, y] = (232, 194, 72)
                    continue
            elif kind == "brick_city":
                if y % 32 < 2 or (x + (16 if (y // 32) % 2 else 0)) % 64 < 2:
                    n -= 28
            px[x, y] = (
                max(0, min(255, base[0] + n)),
                max(0, min(255, base[1] + n)),
                max(0, min(255, base[2] + n)),
            )

    if kind in ("concrete", "stucco_warm", "stucco_coral", "stucco_mint", "sand"):
        im = im.filter(ImageFilter.GaussianBlur(radius=0.8))

    out = io.BytesIO()
    im.save(out, format="JPEG", quality=88)
    return out.getvalue()


# ---------------------------------------------------------------------------
# High-Detail PBR Vehicle, Tank, Monster Truck, Jetpack & World Builders
# ---------------------------------------------------------------------------

def add_detailed_wheel(
    builder: GlbSceneBuilder,
    node: int,
    mat_tire: int,
    mat_rim: int,
    x: float, y: float, z: float,
    radius: float, width: float,
) -> None:
    tire = builder.mesh_for(node, mat_tire)
    rim = builder.mesh_for(node, mat_rim)
    tire.add_cylinder(x, y, z, radius, radius, width, axis="x", segments=24)
    rim.add_cylinder(x, y, z, radius * 0.58, radius * 0.58, width * 1.03, axis="x", segments=18)
    rim.add_cylinder(x, y, z, radius * 0.22, radius * 0.22, width * 1.08, axis="x", segments=12)


def build_street_car(
    out_path: Path,
    body_color: Tuple[float, float, float],
    style: str = "sedan",
    metal_tex: Optional[bytes] = None,
) -> None:
    """
    Build detailed smooth-shaded 90s West Coast vehicles facing +Z:
    sedan, muscle, police, hotring, bloodring.
    """
    b = GlbSceneBuilder()
    tex_idx = b.add_texture(metal_tex) if (metal_tex and style == "bloodring") else None

    m_body = b.add_material(
        "CarBody", (*body_color, 1.0),
        metallic=0.68 if style != "bloodring" else 0.45,
        roughness=0.22 if style != "bloodring" else 0.62,
        texture_idx=tex_idx,
    )
    m_secondary = b.add_material("Secondary", (0.94, 0.94, 0.95, 1.0), metallic=0.4, roughness=0.25)
    m_glass = b.add_material("TintedGlass", (0.08, 0.11, 0.14, 1.0), metallic=0.9, roughness=0.08)
    m_chrome = b.add_material("Chrome", (0.88, 0.9, 0.94, 1.0), metallic=0.95, roughness=0.12)
    m_tire = b.add_material("TireRubber", (0.08, 0.08, 0.09, 1.0), metallic=0.0, roughness=0.88)
    m_trim = b.add_material("DarkTrim", (0.12, 0.12, 0.13, 1.0), metallic=0.2, roughness=0.65)
    m_head = b.add_material("Headlight", (1.0, 0.96, 0.84, 1.0), roughness=0.1, emissive=(1.0, 0.92, 0.7))
    m_tail = b.add_material("Taillight", (0.9, 0.08, 0.06, 1.0), roughness=0.15, emissive=(0.9, 0.08, 0.05))
    m_red_bar = b.add_material("SirenRed", (1.0, 0.08, 0.08, 1.0), roughness=0.1, emissive=(1.0, 0.1, 0.1))
    m_blue_bar = b.add_material("SirenBlue", (0.08, 0.35, 1.0, 1.0), roughness=0.1, emissive=(0.1, 0.45, 1.0))

    root = b.add_node("VehicleRoot")
    body = b.mesh_for(root, m_body)
    sec = b.mesh_for(root, m_secondary)
    glass = b.mesh_for(root, m_glass)
    chrome = b.mesh_for(root, m_chrome)
    trim = b.mesh_for(root, m_trim)
    head = b.mesh_for(root, m_head)
    tail = b.mesh_for(root, m_tail)

    length = 4.65 if style in ("muscle", "police", "hotring") else 4.45
    width = 1.92 if style in ("muscle", "hotring") else 1.84
    wheel_r = 0.36
    wheel_w = 0.28 if style in ("muscle", "hotring") else 0.24

    # Underbody chassis
    trim.add_box(0.0, 0.36, 0.0, width * 0.92, 0.22, length * 0.92)
    # Main lower body (smoothly tapered upper edge)
    body.add_box(0.0, 0.68, 0.0, width, 0.52, length, taper_top_x=0.95, taper_top_z=0.96)
    # Sculpted hood + trunk decks
    body.add_box(0.0, 0.95, length * 0.24, width * 0.92, 0.16, length * 0.36, taper_top_x=0.92, taper_top_z=0.92)
    body.add_box(0.0, 0.95, -length * 0.27, width * 0.92, 0.14, length * 0.28, taper_top_x=0.94, taper_top_z=0.94)

    # Police white doors + roof panel (classic 90s LAPD / LSPD black-and-white!)
    if style == "police":
        sec.add_box(0.0, 0.69, -0.05, width + 0.03, 0.46, 1.95, taper_top_x=0.96)
        sec.add_box(0.0, 1.44, -0.14, width * 0.72, 0.08, 1.72, taper_top_x=0.94)
        # Front heavy pushbar
        trim.add_box(0.0, 0.56, length * 0.52, 0.95, 0.34, 0.14)
        trim.add_box(-0.34, 0.68, length * 0.53, 0.08, 0.42, 0.14)
        trim.add_box(0.34, 0.68, length * 0.53, 0.08, 0.42, 0.14)
        # Roof red/blue lightbar
        trim.add_box(0.0, 1.50, -0.02, 1.12, 0.06, 0.24)
        b.mesh_for(root, m_red_bar).add_box(-0.30, 1.56, -0.02, 0.44, 0.11, 0.22)
        b.mesh_for(root, m_blue_bar).add_box(0.30, 1.56, -0.02, 0.44, 0.11, 0.22)

    # Cabin greenhouse + glass windows
    cabin_len = 2.05 if style == "sedan" else 1.85
    body.add_box(0.0, 1.22, -0.15, width * 0.84, 0.52, cabin_len, taper_top_x=0.84, taper_top_z=0.72, shift_top_z=-0.08)
    glass.add_box(0.0, 1.20, -0.15, width * 0.855, 0.40, cabin_len * 0.90, taper_top_x=0.86, taper_top_z=0.78, shift_top_z=-0.06)

    # Muscle / Hotring / Bloodring extras
    if style in ("muscle", "hotring"):
        # Hood intake scoop
        chrome.add_box(0.0, 1.07, 1.05, 0.56, 0.12, 0.68, taper_top_x=0.85)
        # Rear race spoiler
        body.add_box(0.0, 1.18, -length * 0.44, width * 0.94, 0.06, 0.28)
        trim.add_box(-0.62, 1.08, -length * 0.44, 0.08, 0.22, 0.18)
        trim.add_box(0.62, 1.08, -length * 0.44, 0.08, 0.22, 0.18)
        if style == "hotring":
            # Racing center stripe
            sec.add_box(0.0, 1.04, 1.15, 0.36, 0.03, 1.45)
            sec.add_box(0.0, 1.49, -0.18, 0.36, 0.03, 1.32)

    if style == "bloodring":
        # Heavy demolition bullbar & reinforced roof bars
        trim.add_box(0.0, 0.62, length * 0.53, width * 0.96, 0.32, 0.18)
        trim.add_box(0.0, 1.50, -0.15, width * 0.70, 0.07, 1.45)

    # Chrome bumpers, grille, side mirrors, exhausts
    chrome.add_box(0.0, 0.44, length * 0.50, width * 0.98, 0.15, 0.14)
    chrome.add_box(0.0, 0.44, -length * 0.50, width * 0.98, 0.15, 0.14)
    trim.add_box(0.0, 0.68, length * 0.495, width * 0.52, 0.22, 0.06)
    body.add_box(width * 0.51, 1.05, 0.50, 0.16, 0.10, 0.12)
    body.add_box(-width * 0.51, 1.05, 0.50, 0.16, 0.10, 0.12)
    chrome.add_cylinder(-0.55, 0.34, -length * 0.51, 0.045, 0.045, 0.22, axis="z", segments=10)
    chrome.add_cylinder(0.55, 0.34, -length * 0.51, 0.045, 0.045, 0.22, axis="z", segments=10)

    # Headlights & Taillights
    for sx in (-1.0, 1.0):
        head.add_box(sx * (width * 0.36), 0.72, length * 0.495, 0.34, 0.14, 0.06)
        tail.add_box(sx * (width * 0.36), 0.74, -length * 0.495, 0.38, 0.13, 0.06)

    # 4 smooth wheels with chrome rims
    wx = width * 0.48
    wz = length * 0.31
    for sx in (-1.0, 1.0):
        for sz in (-1.0, 1.0):
            add_detailed_wheel(b, root, m_tire, m_chrome, sx * wx, wheel_r, sz * wz, wheel_r, wheel_w)

    b.export(out_path)


def build_rhino_tank(out_path: Path, metal_tex: Optional[bytes] = None) -> None:
    """
    Build detailed armored Rhino Army Tank facing +Z with a separate 'Turret' child node
    so Filament rotates the turret + cannon barrel independently toward camera aim!
    """
    b = GlbSceneBuilder()
    tex_idx = b.add_texture(metal_tex) if metal_tex else None

    m_armor = b.add_material("TankArmor", (0.42, 0.48, 0.34, 1.0), metallic=0.45, roughness=0.58, texture_idx=tex_idx)
    m_dark = b.add_material("ArmorDark", (0.24, 0.28, 0.20, 1.0), metallic=0.5, roughness=0.65)
    m_track = b.add_material("Tracks", (0.11, 0.11, 0.12, 1.0), metallic=0.35, roughness=0.82)
    m_steel = b.add_material("GunSteel", (0.28, 0.31, 0.26, 1.0), metallic=0.78, roughness=0.36)
    m_head = b.add_material("TankLight", (1.0, 0.95, 0.78, 1.0), roughness=0.15, emissive=(1.0, 0.9, 0.65))
    m_tail = b.add_material("TankTail", (0.85, 0.08, 0.06, 1.0), roughness=0.2, emissive=(0.8, 0.08, 0.05))

    root = b.add_node("TankRoot")
    armor = b.mesh_for(root, m_armor)
    dark = b.mesh_for(root, m_dark)
    track = b.mesh_for(root, m_track)
    head = b.mesh_for(root, m_head)
    tail = b.mesh_for(root, m_tail)

    # Armored hull with sloped glacis & side skirts
    armor.add_box(0.0, 0.95, 0.0, 3.15, 0.85, 6.20, taper_top_x=0.92, taper_top_z=0.88)
    armor.add_box(0.0, 1.25, -1.65, 2.75, 0.38, 2.25, taper_top_x=0.95)
    # Rear engine grill slats
    dark.add_box(0.0, 1.46, -1.85, 2.15, 0.08, 1.55)
    # Armored side skirts over tracks
    for sx in (-1.0, 1.0):
        armor.add_box(sx * 1.62, 0.92, 0.0, 0.18, 0.62, 6.05)
        # Heavy track runs
        track.add_box(sx * 1.42, 0.56, 0.0, 0.56, 0.78, 5.95, taper_top_z=0.92)
        # 6 armored road wheels per side
        for wi in range(6):
            wz = -2.25 + wi * 0.90
            add_detailed_wheel(b, root, m_track, m_dark, sx * 1.42, 0.44, wz, 0.40, 0.58)
        head.add_box(sx * 1.05, 1.15, 2.95, 0.28, 0.18, 0.12)
        tail.add_box(sx * 1.08, 1.08, -3.02, 0.30, 0.14, 0.10)

    # Independent rotating Turret child node pivoted at top-center of hull!
    turret_node = b.add_node("Turret", translation=(0.0, 1.48, 0.15), parent=root)
    t_armor = b.mesh_for(turret_node, m_armor)
    t_dark = b.mesh_for(turret_node, m_dark)
    t_steel = b.mesh_for(turret_node, m_steel)

    # Angular modern Abrams/Rhino turret bustle
    t_armor.add_box(0.0, 0.42, -0.20, 2.45, 0.78, 3.10, taper_top_x=0.82, taper_top_z=0.78, shift_top_z=-0.10)
    # Turret cheeks & mantlet
    t_dark.add_box(0.0, 0.42, 1.32, 0.92, 0.46, 0.48)
    # Commander cupola + hatch + side storage boxes
    t_dark.add_cylinder(0.55, 0.88, -0.35, 0.38, 0.36, 0.22, axis="y", segments=16)
    t_dark.add_box(-1.26, 0.42, -0.45, 0.24, 0.42, 1.35)
    t_dark.add_box(1.26, 0.42, -0.45, 0.24, 0.42, 1.35)
    # Long 120mm main cannon barrel + fume extractor + muzzle brake extending along +Z
    t_steel.add_cylinder(0.0, 0.44, 3.35, 0.13, 0.11, 4.10, axis="z", segments=18)
    t_armor.add_cylinder(0.0, 0.44, 2.75, 0.19, 0.19, 0.82, axis="z", segments=18)
    t_dark.add_cylinder(0.0, 0.44, 5.42, 0.16, 0.16, 0.34, axis="z", segments=16)

    b.export(out_path)


def build_monster_truck(out_path: Path) -> None:
    """
    Build detailed lifted San Andreas 'Monster' Truck facing +Z with oversized off-road
    wheels, tubular suspension lift, pickup bed rollbar, and roof floodlights.
    """
    b = GlbSceneBuilder()
    m_body = b.add_material("MonsterPaint", (0.12, 0.36, 0.78, 1.0), metallic=0.72, roughness=0.22)
    m_stripe = b.add_material("RacingStripe", (0.94, 0.76, 0.16, 1.0), metallic=0.5, roughness=0.28)
    m_glass = b.add_material("Glass", (0.07, 0.10, 0.14, 1.0), metallic=0.9, roughness=0.08)
    m_chrome = b.add_material("Chrome", (0.90, 0.92, 0.95, 1.0), metallic=0.96, roughness=0.10)
    m_chassis = b.add_material("Chassis", (0.14, 0.14, 0.16, 1.0), metallic=0.45, roughness=0.55)
    m_shock = b.add_material("RedShocks", (0.88, 0.14, 0.12, 1.0), metallic=0.4, roughness=0.35)
    m_tire = b.add_material("GiantTire", (0.08, 0.08, 0.09, 1.0), metallic=0.0, roughness=0.90)
    m_head = b.add_material("FloodLights", (1.0, 0.96, 0.82, 1.0), roughness=0.1, emissive=(1.0, 0.95, 0.75))
    m_tail = b.add_material("Taillight", (0.9, 0.08, 0.06, 1.0), roughness=0.15, emissive=(0.9, 0.08, 0.05))

    root = b.add_node("MonsterRoot")
    body = b.mesh_for(root, m_body)
    stripe = b.mesh_for(root, m_stripe)
    glass = b.mesh_for(root, m_glass)
    chrome = b.mesh_for(root, m_chrome)
    chassis = b.mesh_for(root, m_chassis)
    shock = b.mesh_for(root, m_shock)
    head = b.mesh_for(root, m_head)
    tail = b.mesh_for(root, m_tail)

    # Heavy lifted ladder frame + solid axles + red coilover shocks
    chassis.add_box(0.0, 1.48, 0.0, 1.15, 0.28, 4.45)
    for sz in (-1.65, 1.65):
        chassis.add_cylinder(0.0, 0.88, sz, 0.12, 0.12, 2.75, axis="x", segments=12)
        chassis.add_sphere(0.0, 0.88, sz, 0.24, 0.24, 0.24, rings=8, sectors=10)
        for sx in (-0.82, 0.82):
            shock.add_cylinder(sx, 1.25, sz, 0.075, 0.075, 0.75, axis="y", segments=10)

    # High-mounted classic pickup truck body
    body.add_box(0.0, 1.98, 0.0, 2.18, 0.74, 4.95, taper_top_x=0.96)
    stripe.add_box(0.0, 1.98, 0.0, 2.21, 0.16, 4.97)
    # Pickup cab + tinted windows
    body.add_box(0.0, 2.66, 0.25, 1.94, 0.72, 1.72, taper_top_x=0.88, taper_top_z=0.84)
    glass.add_box(0.0, 2.64, 0.25, 1.96, 0.52, 1.54, taper_top_x=0.90, taper_top_z=0.86)
    # Open pickup bed recessed liner
    chassis.add_box(0.0, 2.26, -1.52, 1.82, 0.26, 1.65)

    # Chrome rollbar in bed + 4 roof floodlights + twin chrome exhaust stacks
    chrome.add_cylinder(-0.82, 2.76, -0.78, 0.06, 0.06, 0.92, axis="y", segments=10)
    chrome.add_cylinder(0.82, 2.76, -0.78, 0.06, 0.06, 0.92, axis="y", segments=10)
    chrome.add_cylinder(0.0, 3.18, -0.78, 0.06, 0.06, 1.72, axis="x", segments=10)
    for lx in (-0.54, -0.18, 0.18, 0.54):
        chrome.add_cylinder(lx, 3.30, -0.78, 0.11, 0.11, 0.14, axis="z", segments=12)
        head.add_cylinder(lx, 3.30, -0.70, 0.09, 0.09, 0.04, axis="z", segments=12)

    # Chrome front/rear bumpers & bullbar
    chrome.add_box(0.0, 1.70, 2.52, 2.24, 0.24, 0.20)
    chrome.add_box(0.0, 1.70, -2.52, 2.24, 0.24, 0.20)
    for sx in (-1.0, 1.0):
        head.add_box(sx * 0.76, 2.02, 2.49, 0.38, 0.20, 0.08)
        tail.add_box(sx * 0.84, 2.02, -2.49, 0.30, 0.26, 0.08)

    # 4 GIANT Monster Truck tires (radius 0.88m, width 0.72m!)
    for sx in (-1.0, 1.0):
        for sz in (-1.65, 1.65):
            add_detailed_wheel(b, root, m_tire, m_chrome, sx * 1.44, 0.88, sz, 0.88, 0.74)

    b.export(out_path)


def build_jetpack(out_path: Path) -> None:
    """
    Build detailed twin-thruster San Andreas Jetpack (YECGAA / ROCKETMAN)
    with brushed steel tanks, harness frame, control handles, and glowing plasma nozzles.
    """
    b = GlbSceneBuilder()
    m_steel = b.add_material("BrushedSteel", (0.76, 0.78, 0.82, 1.0), metallic=0.90, roughness=0.22)
    m_dark = b.add_material("HarnessDark", (0.14, 0.15, 0.17, 1.0), metallic=0.4, roughness=0.65)
    m_red = b.add_material("AccentRed", (0.85, 0.16, 0.12, 1.0), metallic=0.5, roughness=0.35)
    m_flame = b.add_material("ThrusterFlame", (1.0, 0.62, 0.18, 1.0), roughness=0.1, emissive=(1.0, 0.55, 0.15))

    root = b.add_node("JetpackRoot")
    steel = b.mesh_for(root, m_steel)
    dark = b.mesh_for(root, m_dark)
    red = b.mesh_for(root, m_red)
    flame = b.mesh_for(root, m_flame)

    # Backplate + harness cross-brace
    dark.add_box(0.0, 0.0, 0.0, 0.50, 0.56, 0.14)
    red.add_box(0.0, 0.16, -0.10, 0.44, 0.12, 0.08)

    # Twin pressurized fuel tanks + domes + flared thruster bells + glowing flame cones
    for sx in (-0.22, 0.22):
        steel.add_cylinder(sx, 0.02, -0.16, 0.12, 0.12, 0.62, axis="y", segments=18)
        steel.add_sphere(sx, 0.33, -0.16, 0.12, 0.10, 0.12, rings=8, sectors=14)
        dark.add_cylinder(sx, -0.34, -0.16, 0.15, 0.09, 0.18, axis="y", segments=16)
        flame.add_cylinder(sx, -0.54, -0.16, 0.03, 0.12, 0.28, axis="y", segments=14)
        # Forward control arms
        dark.add_box(sx * 1.45, -0.05, 0.22, 0.06, 0.06, 0.48)
        red.add_cylinder(sx * 1.45, -0.05, 0.46, 0.04, 0.04, 0.14, axis="y", segments=10)

    b.export(out_path)


def build_palm_tree(out_path: Path, bark_tex: Optional[bytes]) -> None:
    """Build iconic curved California Washingtonia palm tree with PBR bark & arched fronds."""
    b = GlbSceneBuilder()
    tex_idx = b.add_texture(bark_tex) if bark_tex else None
    m_bark = b.add_material("PalmBark", (0.76, 0.64, 0.52, 1.0), roughness=0.90, texture_idx=tex_idx)
    m_frond = b.add_material("PalmFrond", (0.22, 0.46, 0.18, 1.0), roughness=0.72, double_sided=True)

    root = b.add_node("PalmRoot")
    trunk = b.mesh_for(root, m_bark)
    fronds = b.mesh_for(root, m_frond)

    # Gently curved 9-segment trunk rising ~11.5m tall
    segs = 9
    total_h = 11.2
    prev_x = 0.0
    for i in range(segs):
        t0 = i / segs
        t1 = (i + 1) / segs
        y_mid = (t0 + t1) * 0.5 * total_h
        x_mid = math.sin(t0 * 1.4) * 0.85
        r_bot = 0.34 * (1.0 - 0.52 * t0)
        r_top = 0.34 * (1.0 - 0.52 * t1)
        trunk.add_cylinder(x_mid, y_mid, 0.0, r_bot, r_top, total_h / segs + 0.12, axis="y", segments=12)
        prev_x = x_mid

    # 12 arched drooping palm fronds radiating around crown
    crown_y = total_h - 0.15
    for k in range(12):
        ang = 2.0 * math.pi * k / 12.0 + (0.25 if k % 2 else 0.0)
        elev = 0.45 if k % 2 == 0 else 0.10
        ca, sa = math.cos(ang), math.sin(ang)
        px, pz = -sa, ca  # perpendicular width axis
        length = 3.6 if k % 2 == 0 else 4.1
        sub = 5
        for s in range(sub):
            f0, f1 = s / sub, (s + 1) / sub
            d0, d1 = f0 * length, f1 * length
            y0 = crown_y + elev * d0 - 0.14 * d0 * d0
            y1 = crown_y + elev * d1 - 0.14 * d1 * d1
            w0 = 0.42 * math.sin(f0 * math.pi * 0.85 + 0.15)
            w1 = 0.42 * math.sin(f1 * math.pi * 0.85 + 0.15)
            p0 = (prev_x + ca * d0 - px * w0, y0, sa * d0 - pz * w0)
            p1 = (prev_x + ca * d0 + px * w0, y0, sa * d0 + pz * w0)
            p2 = (prev_x + ca * d1 + px * w1, y1, sa * d1 + pz * w1)
            p3 = (prev_x + ca * d1 - px * w1, y1, sa * d1 - pz * w1)
            fronds.add_quad(p0, p1, p2, p3)

    b.export(out_path)


def build_california_bungalow(out_path: Path, stucco_tex: Optional[bytes]) -> None:
    """90s Grove Street single-story California bungalow with stucco walls, porch, & tile roof."""
    b = GlbSceneBuilder()
    tex_idx = b.add_texture(stucco_tex) if stucco_tex else None
    m_wall = b.add_material("Stucco", (0.94, 0.88, 0.78, 1.0), roughness=0.82, texture_idx=tex_idx)
    m_roof = b.add_material("TerracottaRoof", (0.64, 0.28, 0.18, 1.0), roughness=0.78)
    m_wood = b.add_material("PorchWood", (0.36, 0.24, 0.16, 1.0), roughness=0.82)
    m_win = b.add_material("WindowGlass", (0.14, 0.20, 0.26, 1.0), metallic=0.8, roughness=0.12)
    m_garage = b.add_material("GarageDoor", (0.82, 0.80, 0.76, 1.0), roughness=0.65)

    root = b.add_node("BungalowRoot")
    wall = b.mesh_for(root, m_wall)
    roof = b.mesh_for(root, m_roof)
    wood = b.mesh_for(root, m_wood)
    win = b.mesh_for(root, m_win)
    garage = b.mesh_for(root, m_garage)

    # Main structure (14m wide x 4.4m tall x 11m deep)
    wall.add_box(0.0, 2.2, 0.0, 14.0, 4.4, 11.0, uv_tile=3.0)
    # Hip roof
    roof.add_box(0.0, 5.4, 0.0, 15.2, 2.1, 12.2, taper_top_x=0.42, taper_top_z=0.35)
    # Front porch canopy & columns
    roof.add_box(-2.2, 3.6, 6.4, 6.2, 0.26, 2.6)
    wood.add_box(-2.2, 0.18, 6.4, 6.0, 0.36, 2.4)
    for cx in (-4.8, -2.2, 0.4):
        wood.add_box(cx, 1.9, 7.4, 0.22, 3.2, 0.22)
    # Front door + garage door + recessed windows
    wood.add_box(-2.2, 1.35, 5.54, 1.25, 2.35, 0.12)
    garage.add_box(3.8, 1.45, 5.54, 4.4, 2.7, 0.14)
    for wx in (-4.5, -0.2):
        win.add_box(wx, 2.25, 5.54, 1.65, 1.35, 0.10)
        win.add_box(wx, 2.25, -5.54, 1.85, 1.35, 0.10)

    b.export(out_path)


def build_downtown_tower(
    out_path: Path,
    facade_tex: Optional[bytes],
    width: float,
    height: float,
    depth: float,
    tint: Tuple[float, float, float],
) -> None:
    """Multi-story Downtown/Vinewood building with landable flat rooftop & parapet."""
    b = GlbSceneBuilder()
    tex_idx = b.add_texture(facade_tex) if facade_tex else None
    m_wall = b.add_material("Facade", (*tint, 1.0), roughness=0.78, texture_idx=tex_idx)
    m_roof = b.add_material("RoofTar", (0.28, 0.28, 0.30, 1.0), roughness=0.92)
    m_glass = b.add_material("TowerGlass", (0.12, 0.19, 0.28, 1.0), metallic=0.88, roughness=0.10)
    m_awning = b.add_material("Awning", (0.82, 0.26, 0.18, 1.0), roughness=0.6)

    root = b.add_node("TowerRoot")
    wall = b.mesh_for(root, m_wall)
    roof = b.mesh_for(root, m_roof)
    glass = b.mesh_for(root, m_glass)
    awning = b.mesh_for(root, m_awning)

    wall.add_box(0.0, height * 0.5, 0.0, width, height, depth, uv_tile=4.0)
    # Flat tar rooftop pad + perimeter parapet lip (for Jetpack landings!)
    roof.add_box(0.0, height + 0.05, 0.0, width - 0.6, 0.12, depth - 0.6)
    wall.add_box(0.0, height + 0.45, depth * 0.5 - 0.25, width, 0.9, 0.5)
    wall.add_box(0.0, height + 0.45, -depth * 0.5 + 0.25, width, 0.9, 0.5)
    wall.add_box(width * 0.5 - 0.25, height + 0.45, 0.0, 0.5, 0.9, depth)
    wall.add_box(-width * 0.5 + 0.25, height + 0.45, 0.0, 0.5, 0.9, depth)
    # Rooftop AC unit
    roof.add_box(width * 0.2, height + 0.75, -depth * 0.2, 2.4, 1.3, 2.0)

    # Ribbon glass windows per floor
    floors = max(2, int(height // 4.2))
    for f in range(floors):
        fy = 2.6 + f * 3.8
        if fy + 1.2 >= height:
            break
        glass.add_box(0.0, fy, depth * 0.5 + 0.04, width * 0.84, 1.65, 0.08)
        glass.add_box(0.0, fy, -depth * 0.5 - 0.04, width * 0.84, 1.65, 0.08)
        glass.add_box(width * 0.5 + 0.04, fy, 0.0, 0.08, 1.65, depth * 0.84)
        glass.add_box(-width * 0.5 - 0.04, fy, 0.0, 0.08, 1.65, depth * 0.84)

    # Storefront awning at ground level
    awning.add_box(0.0, 3.6, depth * 0.5 + 1.0, width * 0.78, 0.28, 2.0)
    b.export(out_path)


def build_pbr_slab(
    out_path: Path,
    sx: float, sy: float, sz: float,
    tex_bytes: Optional[bytes],
    color: Tuple[float, float, float] = (1.0, 1.0, 1.0),
    roughness: float = 0.88,
    uv_tile: float = 8.0,
) -> None:
    b = GlbSceneBuilder()
    tex_idx = b.add_texture(tex_bytes) if tex_bytes else None
    mat = b.add_material("Surface", (*color, 1.0), roughness=roughness, texture_idx=tex_idx)
    root = b.add_node("SlabRoot")
    b.mesh_for(root, mat).add_box(0.0, sy * 0.5, 0.0, sx, sy, sz, uv_tile=uv_tile)
    b.export(out_path)


def build_stunt_ramp(out_path: Path, concrete_tex: Optional[bytes]) -> None:
    b = GlbSceneBuilder()
    tex_idx = b.add_texture(concrete_tex) if concrete_tex else None
    m_conc = b.add_material("Concrete", (0.85, 0.82, 0.76, 1.0), roughness=0.85, texture_idx=tex_idx)
    m_stripe = b.add_material("Hazard", (0.96, 0.72, 0.10, 1.0), roughness=0.5)
    root = b.add_node("RampRoot")
    m = b.mesh_for(root, m_conc)
    w, l, h = 6.0, 11.0, 2.6
    # Sloped wedge facing +Z
    p_bl = (-w / 2, 0.0, -l / 2); p_br = (w / 2, 0.0, -l / 2)
    p_fl = (-w / 2, 0.0, l / 2);  p_fr = (w / 2, 0.0, l / 2)
    p_tl = (-w / 2, h, l / 2);    p_tr = (w / 2, h, l / 2)
    m.add_quad(p_bl, p_br, p_tr, p_tl, (2.0, 3.0))
    m.add_quad(p_fr, p_fl, p_tl, p_tr, (2.0, 1.0))
    b.mesh_for(root, m_stripe).add_box(0.0, h - 0.12, l / 2 - 0.2, w + 0.1, 0.24, 0.4)
    b.export(out_path)


def build_fallback_prop(out_path: Path, kind: str) -> None:
    """Detailed PBR prop fallback if a Poly Haven prop fetch is unavailable offline."""
    b = GlbSceneBuilder()
    root = b.add_node(f"Prop_{kind}")
    if kind == "street_lamp":
        m_pole = b.add_material("Pole", (0.34, 0.36, 0.38, 1.0), metallic=0.8, roughness=0.35)
        m_glow = b.add_material("Bulb", (1.0, 0.86, 0.56, 1.0), roughness=0.1, emissive=(1.0, 0.82, 0.45))
        p = b.mesh_for(root, m_pole)
        p.add_cylinder(0.0, 3.6, 0.0, 0.12, 0.08, 7.2, axis="y", segments=12)
        p.add_box(0.0, 7.1, 0.9, 0.12, 0.12, 1.9)
        b.mesh_for(root, m_glow).add_box(0.0, 6.98, 1.7, 0.36, 0.14, 0.52)
    elif kind == "fire_hydrant":
        m_red = b.add_material("HydrantRed", (0.82, 0.14, 0.12, 1.0), metallic=0.5, roughness=0.4)
        m = b.mesh_for(root, m_red)
        m.add_cylinder(0.0, 0.42, 0.0, 0.16, 0.14, 0.84, axis="y", segments=14)
        m.add_sphere(0.0, 0.86, 0.0, 0.15, 0.12, 0.15)
        m.add_cylinder(0.0, 0.62, 0.0, 0.07, 0.07, 0.42, axis="x", segments=10)
    elif kind == "concrete_barrier":
        m_c = b.add_material("Concrete", (0.72, 0.70, 0.66, 1.0), roughness=0.92)
        b.mesh_for(root, m_c).add_box(0.0, 0.48, 0.0, 2.4, 0.96, 0.68, taper_top_z=0.45)
    elif kind == "explosion_fx":
        m_exp = b.add_material("Blast", (1.0, 0.52, 0.08, 1.0), roughness=0.2, emissive=(1.0, 0.48, 0.06))
        b.mesh_for(root, m_exp).add_sphere(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, rings=12, sectors=16)
    elif kind == "tank_shell":
        m_sh = b.add_material("Shell", (1.0, 0.85, 0.25, 1.0), roughness=0.15, emissive=(1.0, 0.72, 0.15))
        b.mesh_for(root, m_sh).add_sphere(0.0, 0.0, 0.0, 0.18, 0.18, 0.42, rings=8, sectors=12)
    elif kind == "player_fallback":
        # Stylized realistic-proportion humanoid fallback if Soldier.glb offline
        m_skin = b.add_material("Skin", (0.56, 0.36, 0.25, 1.0), roughness=0.65)
        m_shirt = b.add_material("GroveTank", (0.94, 0.94, 0.94, 1.0), roughness=0.78)
        m_jeans = b.add_material("Jeans", (0.16, 0.24, 0.44, 1.0), roughness=0.85)
        m_shoes = b.add_material("Sneakers", (0.12, 0.12, 0.12, 1.0), roughness=0.7)
        b.mesh_for(root, m_jeans).add_cylinder(-0.12, 0.46, 0.0, 0.09, 0.10, 0.84, axis="y", segments=12)
        b.mesh_for(root, m_jeans).add_cylinder(0.12, 0.46, 0.0, 0.09, 0.10, 0.84, axis="y", segments=12)
        b.mesh_for(root, m_shoes).add_box(-0.12, 0.06, 0.05, 0.14, 0.12, 0.26)
        b.mesh_for(root, m_shoes).add_box(0.12, 0.06, 0.05, 0.14, 0.12, 0.26)
        b.mesh_for(root, m_shirt).add_box(0.0, 1.18, 0.0, 0.42, 0.62, 0.24, taper_top_x=1.08)
        b.mesh_for(root, m_skin).add_cylinder(-0.28, 1.14, 0.0, 0.065, 0.075, 0.60, axis="y", segments=10)
        b.mesh_for(root, m_skin).add_cylinder(0.28, 1.14, 0.0, 0.065, 0.075, 0.60, axis="y", segments=10)
        b.mesh_for(root, m_skin).add_sphere(0.0, 1.66, 0.0, 0.13, 0.15, 0.14, rings=12, sectors=16)
    b.export(out_path)


# ---------------------------------------------------------------------------
# Crisp 16-bit PCM WAV Sound Effects Generator (CC0 Self-Contained)
# ---------------------------------------------------------------------------

def write_wav(path: Path, samples: List[float], sample_rate: int = 22050) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        raw = bytearray()
        for s in samples:
            clamped = max(-0.98, min(0.98, s))
            raw.extend(struct.pack("<h", int(clamped * 32767)))
        wf.writeframes(bytes(raw))


def generate_audio_pack() -> None:
    sr = 22050
    rng = random.Random(42)

    # 1. Engine hum loop (rich saw+harmonics)
    n = int(sr * 1.0)
    eng = [
        0.35 * math.sin(2 * math.pi * 68 * i / sr)
        + 0.22 * math.sin(2 * math.pi * 136 * i / sr)
        + 0.12 * math.sin(2 * math.pi * 204 * i / sr)
        for i in range(n)
    ]
    write_wav(AUDIO_DIR / "sfx_engine.wav", eng, sr)

    # 2. Police siren wail loop (classic LA wail 650Hz <-> 1350Hz)
    n = int(sr * 1.6)
    phase = 0.0
    siren = []
    for i in range(n):
        t = i / sr
        freq = 960.0 + 360.0 * math.sin(2 * math.pi * (t / 1.6))
        phase += 2 * math.pi * freq / sr
        siren.append(0.32 * math.sin(phase) + 0.12 * math.sin(2 * phase))
    write_wav(AUDIO_DIR / "sfx_siren.wav", siren, sr)

    # 3. Crash / metal thud
    n = int(sr * 0.45)
    crash = [
        math.exp(-7.5 * (i / sr))
        * (0.55 * rng.uniform(-1, 1) + 0.45 * math.sin(2 * math.pi * 92 * (i / sr)))
        for i in range(n)
    ]
    write_wav(AUDIO_DIR / "sfx_crash.wav", crash, sr)

    # 4. Rhino Tank cannon fire + heavy explosion boom
    n = int(sr * 0.95)
    boom = []
    for i in range(n):
        t = i / sr
        env = math.exp(-3.4 * t)
        sub = math.sin(2 * math.pi * (110 * math.exp(-4.0 * t) + 32) * t)
        noise = rng.uniform(-1, 1)
        boom.append(env * (0.62 * sub + 0.45 * noise))
    write_wav(AUDIO_DIR / "sfx_tank_cannon.wav", boom, sr)

    # 5. Jetpack rocket thruster loop
    n = int(sr * 1.0)
    jet = []
    prev = 0.0
    for i in range(n):
        w = rng.uniform(-1, 1)
        prev = 0.72 * prev + 0.28 * w
        jet.append(0.45 * prev + 0.15 * math.sin(2 * math.pi * 115 * i / sr))
    write_wav(AUDIO_DIR / "sfx_jetpack.wav", jet, sr)

    # 6. Classic San Andreas "Cheat Activated" crisp two-tone chime
    n = int(sr * 0.36)
    chime = []
    for i in range(n):
        t = i / sr
        freq = 587.33 if t < 0.14 else 880.0
        env = math.exp(-6.0 * (t if t < 0.14 else t - 0.14))
        chime.append(0.45 * env * (math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(4 * math.pi * freq * t)))
    write_wav(AUDIO_DIR / "sfx_cheat.wav", chime, sr)

    # 7. Car door slam
    n = int(sr * 0.22)
    door = [
        math.exp(-16.0 * (i / sr)) * (0.6 * math.sin(2 * math.pi * 125 * i / sr) + 0.35 * rng.uniform(-1, 1))
        for i in range(n)
    ]
    write_wav(AUDIO_DIR / "sfx_car_door.wav", door, sr)

    # 8. WASTED / BUSTED low dramatic bass sting
    n = int(sr * 1.4)
    sting = []
    for i in range(n):
        t = i / sr
        env = math.exp(-1.9 * t)
        f = 72.0 - 20.0 * (t / 1.4)
        sting.append(0.55 * env * (math.sin(2 * math.pi * f * t) + 0.4 * math.sin(2 * math.pi * f * 1.5 * t)))
    write_wav(AUDIO_DIR / "sfx_wasted.wav", sting, sr)

    # 9. MISSION PASSED / RESPECT+ warm major chord sting
    n = int(sr * 1.3)
    respect = []
    for i in range(n):
        t = i / sr
        env = min(1.0, t * 14.0) * math.exp(-2.1 * t)
        val = (
            math.sin(2 * math.pi * 261.63 * t)
            + 0.8 * math.sin(2 * math.pi * 329.63 * t)
            + 0.8 * math.sin(2 * math.pi * 392.00 * t)
            + 0.6 * math.sin(2 * math.pi * 523.25 * t)
        )
        respect.append(0.22 * env * val)
    write_wav(AUDIO_DIR / "sfx_respect.wav", respect, sr)


# ---------------------------------------------------------------------------
# Main Orchestrator
# ---------------------------------------------------------------------------

def run_build(offline: bool) -> None:
    for d in (MODELS_DIR, PROPS_DIR, WORLD_DIR, OVERRIDES_DIR, AUDIO_DIR):
        d.mkdir(parents=True, exist_ok=True)
    (OVERRIDES_DIR / "README.txt").write_text(
        "Drop any Sketchfab CC-BY or Mixamo .glb here with the matching filename\n"
        "(e.g., character_player.glb, car_police.glb, vehicle_tank.glb,\n"
        "vehicle_monster_truck.glb, gear_jetpack.glb) to override the default model\n"
        "automatically with zero code changes.\n",
        encoding="utf-8",
    )

    licenses: List[Dict[str, str]] = []

    print("[1/5] Fetching trusted external rigged 3D character (Three.js Soldier.glb)...")
    soldier_url = "https://raw.githubusercontent.com/mrdoob/three.js/master/examples/models/gltf/Soldier.glb"
    soldier_path = MODELS_DIR / "character_player.glb"
    soldier_bytes = download_cached(soldier_url, "Soldier.glb", offline=offline)
    if soldier_bytes and len(soldier_bytes) > 10000:
        soldier_path.write_bytes(soldier_bytes)
        licenses.append({
            "asset": "models/character_player.glb",
            "source": soldier_url,
            "license": "MIT (mrdoob/three.js, rigged Mixamo humanoid)",
            "origin": "external_threejs",
        })
    else:
        print("  [fallback] Building local humanoid character_player.glb")
        build_fallback_prop(soldier_path, "player_fallback")
        licenses.append({
            "asset": "models/character_player.glb",
            "source": "Procedural fallback (run tools/fetch_assets.py online for Soldier.glb)",
            "license": "CC0",
            "origin": "fallback",
        })

    print("[2/5] Fetching Poly Haven CC0 photorealistic PBR textures...")
    tex_slugs = {
        "asphalt": "asphalt_02",
        "concrete": "concrete_floor_worn_001",
        "sand": "aerial_beach_01",
        "grass": "dry_ground_01",
        "stucco_warm": "plastered_wall",
        "brick_city": "red_brick",
        "bark": "bark_brown_02",
        "metal_rust": "rusty_metal_02",
    }
    textures: Dict[str, bytes] = {}
    for key, slug in tex_slugs.items():
        data = fetch_polyhaven_texture_diffuse(slug, offline=offline)
        if data:
            textures[key] = data
            licenses.append({
                "asset": f"texture:{key}",
                "source": f"https://polyhaven.com/a/{slug}",
                "license": "CC0 1.0 (Poly Haven)",
                "origin": "external_polyhaven",
            })
        else:
            textures[key] = make_procedural_texture(key)
    textures["ocean"] = make_procedural_texture("ocean")

    print("[3/5] Fetching Poly Haven CC0 3D street props...")
    ph_props = [
        ("street_lamp_01", PROPS_DIR / "street_lamp.glb", "street_lamp"),
        ("fire_hydrant", PROPS_DIR / "fire_hydrant.glb", "fire_hydrant"),
        ("concrete_road_barrier", PROPS_DIR / "concrete_barrier.glb", "concrete_barrier"),
    ]
    for slug, path, fallback_kind in ph_props:
        info = fetch_polyhaven_gltf_as_glb(slug, path, offline=offline)
        if info:
            licenses.append({"asset": str(path.relative_to(ASSETS_DIR)), **info})
        else:
            build_fallback_prop(path, fallback_kind)
            licenses.append({
                "asset": str(path.relative_to(ASSETS_DIR)),
                "source": f"Fallback for Poly Haven {slug}",
                "license": "CC0",
                "origin": "fallback",
            })

    print("[4/5] Building high-detail PBR vehicles (Cars, Police, Rhino Tank, Monster Truck, Jetpack) & West Coast District...")
    build_street_car(MODELS_DIR / "car_sedan.glb", (0.78, 0.20, 0.16), style="sedan")
    build_street_car(MODELS_DIR / "car_muscle.glb", (0.14, 0.56, 0.24), style="muscle")  # Grove green muscle!
    build_street_car(MODELS_DIR / "car_police.glb", (0.09, 0.10, 0.12), style="police")
    build_street_car(MODELS_DIR / "car_hotring.glb", (0.95, 0.42, 0.08), style="hotring")
    build_street_car(MODELS_DIR / "car_bloodring.glb", (0.62, 0.38, 0.24), style="bloodring", metal_tex=textures.get("metal_rust"))
    build_rhino_tank(MODELS_DIR / "vehicle_tank.glb", metal_tex=textures.get("metal_rust"))
    build_monster_truck(MODELS_DIR / "vehicle_monster_truck.glb")
    build_jetpack(MODELS_DIR / "gear_jetpack.glb")

    build_palm_tree(PROPS_DIR / "palm_tree.glb", textures.get("bark"))
    build_fallback_prop(PROPS_DIR / "explosion_fx.glb", "explosion_fx")
    build_fallback_prop(PROPS_DIR / "tank_shell.glb", "tank_shell")

    # World terrain & buildings using Poly Haven PBR textures
    build_pbr_slab(WORLD_DIR / "ground_grass.glb", 460.0, 0.2, 460.0, textures.get("grass"), (0.88, 0.88, 0.78), 0.94, uv_tile=14.0)
    build_pbr_slab(WORLD_DIR / "ground_beach.glb", 95.0, 0.24, 420.0, textures.get("sand"), (1.0, 0.96, 0.88), 0.92, uv_tile=12.0)
    build_pbr_slab(WORLD_DIR / "ground_ocean.glb", 140.0, 0.12, 460.0, textures.get("ocean"), (0.9, 0.96, 1.0), 0.18, uv_tile=18.0)
    build_pbr_slab(WORLD_DIR / "road_strip.glb", 14.0, 0.26, 20.0, textures.get("asphalt"), (0.95, 0.95, 0.95), 0.85, uv_tile=7.0)
    build_pbr_slab(WORLD_DIR / "sidewalk_strip.glb", 4.0, 0.36, 20.0, textures.get("concrete"), (0.96, 0.94, 0.90), 0.90, uv_tile=4.0)
    build_california_bungalow(WORLD_DIR / "building_bungalow.glb", textures.get("stucco_warm"))
    build_downtown_tower(WORLD_DIR / "building_storefront.glb", textures.get("stucco_warm"), 18.0, 9.0, 14.0, (0.96, 0.84, 0.72))
    build_downtown_tower(WORLD_DIR / "building_tower_med.glb", textures.get("brick_city"), 20.0, 18.0, 20.0, (0.92, 0.88, 0.84))
    build_downtown_tower(WORLD_DIR / "building_tower_tall.glb", textures.get("concrete"), 22.0, 26.0, 22.0, (0.86, 0.88, 0.92))
    build_stunt_ramp(WORLD_DIR / "stunt_ramp.glb", textures.get("concrete"))

    print("[5/5] Generating audio SFX pack & writing manifest.json + ASSETS_LICENSE.md...")
    generate_audio_pack()

    manifest = {
        "version": 1,
        "models": {
            "character_player": {"path": "models/character_player.glb", "targetHeight": 1.80, "yawOffsetDeg": 180.0},
            "car_sedan": {"path": "models/car_sedan.glb", "targetHeight": 1.48, "yawOffsetDeg": 0.0},
            "car_muscle": {"path": "models/car_muscle.glb", "targetHeight": 1.46, "yawOffsetDeg": 0.0},
            "car_police": {"path": "models/car_police.glb", "targetHeight": 1.62, "yawOffsetDeg": 0.0},
            "car_hotring": {"path": "models/car_hotring.glb", "targetHeight": 1.44, "yawOffsetDeg": 0.0},
            "car_bloodring": {"path": "models/car_bloodring.glb", "targetHeight": 1.54, "yawOffsetDeg": 0.0},
            "vehicle_tank": {"path": "models/vehicle_tank.glb", "targetHeight": 2.48, "yawOffsetDeg": 0.0},
            "vehicle_monster_truck": {"path": "models/vehicle_monster_truck.glb", "targetHeight": 3.40, "yawOffsetDeg": 0.0},
            "gear_jetpack": {"path": "models/gear_jetpack.glb", "targetHeight": 1.05, "yawOffsetDeg": 0.0},
            "prop_palm_tree": {"path": "models/props/palm_tree.glb", "targetHeight": 11.8, "yawOffsetDeg": 0.0},
            "prop_street_lamp": {"path": "models/props/street_lamp.glb", "targetHeight": 7.2, "yawOffsetDeg": 0.0},
            "prop_fire_hydrant": {"path": "models/props/fire_hydrant.glb", "targetHeight": 0.95, "yawOffsetDeg": 0.0},
            "prop_concrete_barrier": {"path": "models/props/concrete_barrier.glb", "targetHeight": 0.96, "yawOffsetDeg": 0.0},
            "fx_explosion": {"path": "models/props/explosion_fx.glb", "targetHeight": 2.0, "yawOffsetDeg": 0.0},
            "fx_tank_shell": {"path": "models/props/tank_shell.glb", "targetHeight": 0.45, "yawOffsetDeg": 0.0},
            "world_ground_grass": {"path": "models/world/ground_grass.glb", "targetHeight": 0.2, "yawOffsetDeg": 0.0},
            "world_ground_beach": {"path": "models/world/ground_beach.glb", "targetHeight": 0.24, "yawOffsetDeg": 0.0},
            "world_ground_ocean": {"path": "models/world/ground_ocean.glb", "targetHeight": 0.12, "yawOffsetDeg": 0.0},
            "world_road_strip": {"path": "models/world/road_strip.glb", "targetHeight": 0.26, "yawOffsetDeg": 0.0},
            "world_sidewalk_strip": {"path": "models/world/sidewalk_strip.glb", "targetHeight": 0.36, "yawOffsetDeg": 0.0},
            "world_bungalow": {"path": "models/world/building_bungalow.glb", "targetHeight": 6.45, "yawOffsetDeg": 0.0},
            "world_storefront": {"path": "models/world/building_storefront.glb", "targetHeight": 9.9, "yawOffsetDeg": 0.0},
            "world_tower_med": {"path": "models/world/building_tower_med.glb", "targetHeight": 18.9, "yawOffsetDeg": 0.0},
            "world_tower_tall": {"path": "models/world/building_tower_tall.glb", "targetHeight": 26.9, "yawOffsetDeg": 0.0},
            "world_stunt_ramp": {"path": "models/world/stunt_ramp.glb", "targetHeight": 2.6, "yawOffsetDeg": 0.0},
        },
    }
    (MODELS_DIR / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    # Write ASSETS_LICENSE.md
    lines = [
        "# Sunset Grove — Third-Party 3D Assets & Licenses",
        "",
        "All external assets are sourced from free, trusted CC0 / MIT repositories.",
        "",
        "## Bundled Asset Sources",
        "",
        "| Asset | License | Source |",
        "|---|---|---|",
    ]
    for item in licenses:
        lines.append(f"| `{item['asset']}` | {item['license']} | {item['source']} |")
    lines.extend([
        "",
        "## Zero-Code Custom Model Overrides (Sketchfab CC-BY / Adobe Mixamo)",
        "",
        "To swap in any higher-detail realistic `.glb` model from **Sketchfab** (CC0/CC-BY) or **Adobe Mixamo**:",
        "1. Download the model in `.glb` format.",
        "2. Place it inside `app/src/main/assets/models/overrides/` using the target filename:",
        "   - `character_player.glb` — Rigged third-person protagonist (Idle / Walk / Run animations)",
        "   - `car_police.glb` — 1994 Caprice / LAPD-style cruiser",
        "   - `car_muscle.glb` / `car_sedan.glb` — 90s West Coast muscle car / lowrider",
        "   - `vehicle_tank.glb` — Realistic military tank (name turret node `Turret` for rotation)",
        "   - `vehicle_monster_truck.glb` — Lifted Monster Truck",
        "   - `gear_jetpack.glb` — Twin-thruster Jetpack",
        "3. Rebuild the APK (`./gradlew assembleDebug`). `GltfModelManager` automatically detects overrides and normalizes scale & ground contact.",
        "",
    ])
    (ROOT / "ASSETS_LICENSE.md").write_text("\n".join(lines), encoding="utf-8")
    print("Done! All assets generated and manifest written.")


def run_verify() -> int:
    manifest_path = MODELS_DIR / "manifest.json"
    if not manifest_path.exists():
        print("ERROR: manifest.json missing!")
        return 1
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
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
        "sfx_jetpack.wav", "sfx_cheat.wav", "sfx_car_door.wav", "sfx_wasted.wav", "sfx_respect.wav",
    ]
    for a in required_audio:
        p = AUDIO_DIR / a
        if not p.exists() or p.stat().st_size < 1000:
            print(f"INVALID AUDIO: {p}")
            ok = False
        else:
            print(f"  OK  audio/{a:20s} ({p.stat().st_size // 1024:4d} KB)")

    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--offline", action="store_true", help="Skip network downloads and use cached/procedural PBR")
    parser.add_argument("--verify", action="store_true", help="Validate all GLB & audio files")
    args = parser.parse_args()

    if args.verify:
        return run_verify()
    run_build(offline=args.offline)
    return run_verify()


if __name__ == "__main__":
    sys.exit(main())
