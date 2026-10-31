"""Exercise DEX definition discovery used to reject packaged SDK/test classes."""
import importlib.util
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("verify_package", ROOT / "tools/verify_package.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class PackageVerifierTest(unittest.TestCase):
    def test_reads_actual_class_definitions_not_merely_referenced_types(self):
        data = bytearray(256)
        data[:8] = b"dex\n035\0"
        # Two strings/types, but only the first type is defined by this DEX.
        for offset, value in ((60, 112), (68, 120), (96, 1), (100, 128),
                              (112, 160), (116, 200), (120, 0), (124, 1), (128, 0)):
            struct.pack_into("<I", data, offset, value)
        defined = b"Lcom/shymoose/livenotifications/Test;"
        referenced = b"Lme/jxl/kiosk/plugins/PluginHost;"
        data[160:160 + len(defined) + 2] = bytes([len(defined)]) + defined + b"\0"
        data[200:200 + len(referenced) + 2] = bytes([len(referenced)]) + referenced + b"\0"
        self.assertEqual(verifier.dex_classes(data), [defined.decode()])

    def test_rejects_non_dex_content(self):
        with self.assertRaisesRegex(ValueError, "Invalid DEX"):
            verifier.dex_classes(b"not a dex file")


if __name__ == "__main__":
    unittest.main()
