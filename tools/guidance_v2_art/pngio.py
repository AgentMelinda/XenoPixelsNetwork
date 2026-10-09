"""Stdlib PNG read/write. Matches the filter-0 RGBA writer used by tools/gen_*.py."""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

PNG_SIG = b"\x89PNG\r\n\x1a\n"
Pixel = tuple[int, int, int, int]


def clamp(v: int) -> int:
    return max(0, min(255, int(v)))


def write_png(path: Path, pixels: list[list[Pixel]]) -> None:
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = pixels[y][x]
            raw.extend((r, g, b, a))
    compressed = zlib.compress(bytes(raw), 9)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(
            ">I", zlib.crc32(tag + data) & 0xFFFFFFFF
        )

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = PNG_SIG + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def read_png(path: Path) -> list[list[Pixel]]:
    data = Path(path).read_bytes()
    if data[:8] != PNG_SIG:
        raise ValueError(f"not a PNG: {path}")
    pos = 8
    width = height = None
    bit_depth = color_type = interlace = None
    idat = bytearray()
    while pos + 12 <= len(data):
        length = struct.unpack_from(">I", data, pos)[0]
        tag = data[pos + 4 : pos + 8]
        chunk = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, bit_depth, color_type, _comp, _filter, interlace = struct.unpack(
                ">IIBBBBB", chunk
            )
        elif tag == b"IDAT":
            idat.extend(chunk)
        elif tag == b"IEND":
            break
    if width is None or height is None or bit_depth != 8 or interlace != 0:
        raise ValueError(f"unsupported PNG {path}: {width}x{height} d={bit_depth} i={interlace}")
    if color_type not in (2, 6):
        raise ValueError(f"unsupported color type {color_type} in {path}")
    bpp = 4 if color_type == 6 else 3
    raw = zlib.decompress(bytes(idat))
    stride = width * bpp
    rows: list[bytes] = []
    src = 0
    prev = bytes(stride)
    for _ in range(height):
        ftype = raw[src]
        scan = bytearray(raw[src + 1 : src + 1 + stride])
        src += 1 + stride
        _paeth_filter(ftype, scan, prev, bpp)
        prev = bytes(scan)
        rows.append(bytes(scan))
    pixels: list[list[Pixel]] = []
    for row in rows:
        line: list[Pixel] = []
        if bpp == 4:
            for x in range(width):
                i = x * 4
                line.append((row[i], row[i + 1], row[i + 2], row[i + 3]))
        else:
            for x in range(width):
                i = x * 3
                line.append((row[i], row[i + 1], row[i + 2], 255))
        pixels.append(line)
    return pixels


def _paeth_filter(ftype: int, scan: bytearray, prev: bytes, bpp: int) -> None:
    if ftype == 0:
        return
    for i, val in enumerate(scan):
        a = scan[i - bpp] if i >= bpp else 0
        b = prev[i]
        c = prev[i - bpp] if i >= bpp else 0
        if ftype == 1:
            pr = a
        elif ftype == 2:
            pr = b
        elif ftype == 3:
            pr = (a + b) // 2
        elif ftype == 4:
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
        else:
            raise ValueError(f"unknown PNG filter {ftype}")
        scan[i] = (val + pr) & 0xFF


def ihdr_size(path: Path) -> tuple[int, int]:
    data = Path(path).read_bytes()
    if data[:8] != PNG_SIG:
        raise ValueError("not a PNG")
    return struct.unpack_from(">II", data, 16)
