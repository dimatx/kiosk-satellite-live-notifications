#!/usr/bin/env python3
"""Verify the ZIP, sidecars and actual DEX class definitions without an Android device."""
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def dex_classes(data):
    require(data[:4] == b"dex\n" and data[7] == 0, "Invalid DEX header")

    def word(offset):
        return struct.unpack_from("<I", data, offset)[0]

    strings = word(60)
    types = word(68)
    size, offset = word(96), word(100)
    result = []
    for index in range(size):
        type_index = word(offset + index * 32)
        string_index = word(types + type_index * 4)
        cursor = word(strings + string_index * 4)
        # Skip DEX's ULEB128 UTF-16 length before its null-terminated descriptor.
        while data[cursor] & 0x80:
            cursor += 1
        cursor += 1
        result.append(data[cursor:data.index(0, cursor)].decode("ascii"))
    return result


def verify(path):
    sidecar = path.with_suffix(".zip.sha256").read_text(encoding="utf-8").strip()
    require(sidecar == hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name,
            "SHA-256 sidecar does not match package bytes")
    require(path.stat().st_size <= 4 * 1024 * 1024, "ZIP exceeds KS size limit")
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        require(sum(info.file_size for info in archive.infolist()) <= 4 * 1024 * 1024,
                "Expanded package exceeds KS size limit")
        manifest_bytes = archive.read("kiosk-satellite-plugin.json")
        require(manifest_bytes == (path.parent / "kiosk-satellite-plugin.json").read_bytes(),
                "Packaged and standalone manifests must be byte-identical")
        manifest = json.loads(manifest_bytes)
        expected = {"kiosk-satellite-plugin.json", "plugin.jar", "LICENSE"}
        icon = manifest.get("icon")
        if icon is not None:
            require(icon == "icon.png", "Unexpected plugin icon path")
            expected.add("assets/" + icon)
        require(names == expected, "Unexpected package contents")
        if icon is not None:
            png = archive.read("assets/" + icon)
            require(png == (ROOT / "assets" / icon).read_bytes(), "Packaged icon differs")
            require(len(png) >= 24 and png[:8] == b"\x89PNG\r\n\x1a\n"
                    and png[12:16] == b"IHDR" and struct.unpack(">II", png[16:24]) == (256, 256),
                    "Plugin icon must be a 256x256 PNG")
        require(path.name == f"{manifest['id']}-{manifest['version']}.zip", "Incorrect package filename")
        require(archive.read("LICENSE") == (ROOT / "LICENSE").read_bytes(), "Packaged license differs")
        with zipfile.ZipFile(io.BytesIO(archive.read("plugin.jar"))) as jar:
            names = jar.namelist()
            require(names and all(name.startswith("classes") and name.endswith(".dex") for name in names),
                    "plugin.jar must contain only DEX files")
            descriptors = [name for dex in names for name in dex_classes(jar.read(dex))]
            unexpected = [name for name in descriptors if not name.startswith("Lcom/shymoose/livenotifications/")]
            require(descriptors and not unexpected,
                    "SDK, test or unrelated implementation classes were packaged: "
                    + (", ".join(unexpected[:10]) if unexpected else "no class definitions"))
            entry = "L" + manifest["entryClass"].replace(".", "/") + ";"
            require(entry in descriptors, "Entrypoint is missing from DEX")
    print(f"Verified {path.name}: {path.stat().st_size:,} bytes, {len(descriptors)} plugin classes, "
          "matching manifests/checksum/license, no packaged SDK or tests.")


if __name__ == "__main__":
    if len(sys.argv) > 2:
        raise SystemExit("Usage: python tools/verify_package.py [path-to-plugin.zip]")
    if len(sys.argv) == 2:
        package = Path(sys.argv[1])
    else:
        manifest = json.loads((ROOT / "dist/kiosk-satellite-plugin.json").read_bytes())
        package = ROOT / "dist" / f"{manifest['id']}-{manifest['version']}.zip"
    verify(package)
