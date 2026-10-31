import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import verify_package


class PackageIconTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "assets").mkdir()
        (self.root / "LICENSE").write_bytes(b"test license")
        self.png = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 256, 256)
        (self.root / "assets" / "icon.png").write_bytes(self.png)
        self.manifest = {
            "id": "ks-live-notifications", "version": "0.1.4", "icon": "icon.png",
            "entryClass": "com.shymoose.livenotifications.LiveNotificationsPlugin"
        }

    def package(self, assets):
        raw = json.dumps(self.manifest).encode()
        (self.root / "kiosk-satellite-plugin.json").write_bytes(raw)
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("classes.dex", b"test DEX")
        path = self.root / "ks-live-notifications-0.1.4.zip"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("kiosk-satellite-plugin.json", raw)
            archive.writestr("plugin.jar", jar.getvalue())
            archive.writestr("LICENSE", b"test license")
            for name, content in assets.items():
                archive.writestr(name, content)
        path.with_suffix(".zip.sha256").write_text(
            hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name)
        return path

    def verify(self, assets):
        path = self.package(assets)
        with patch.object(verify_package, "ROOT", self.root), patch.object(
                verify_package, "dex_classes",
                return_value=["Lcom/shymoose/livenotifications/LiveNotificationsPlugin;"]):
            verify_package.verify(path)

    def test_declared_icon_is_verified(self):
        self.verify({"assets/icon.png": self.png})

    def test_no_icon_packages_remain_supported(self):
        del self.manifest["icon"]
        self.verify({})

    def test_missing_or_undeclared_assets_are_rejected(self):
        for assets in ({}, {"assets/icon.png": self.png, "assets/extra.png": self.png}):
            with self.subTest(assets=list(assets)), self.assertRaisesRegex(ValueError, "Unexpected package"):
                self.verify(assets)

    def test_changed_icon_bytes_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Packaged icon differs"):
            self.verify({"assets/icon.png": self.png + b"changed"})

    def test_wrong_dimensions_are_rejected(self):
        png = self.png[:16] + struct.pack(">II", 128, 256)
        (self.root / "assets" / "icon.png").write_bytes(png)
        with self.assertRaisesRegex(ValueError, "256x256"):
            self.verify({"assets/icon.png": png})

    def test_unsafe_icon_path_is_rejected(self):
        self.manifest["icon"] = "../icon.png"
        with self.assertRaisesRegex(ValueError, "icon path"):
            self.verify({"assets/icon.png": self.png})


if __name__ == "__main__":
    unittest.main()
