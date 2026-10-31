"""Manifest and passive native overlay contracts; no Android runtime required."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / "kiosk-satellite-plugin.json").read_text(encoding="utf-8"))


class ContractsTest(unittest.TestCase):
    def test_least_privilege(self):
        self.assertEqual(MANIFEST["capabilities"], ["overlay", "host.read"])
        self.assertEqual(MANIFEST["commands"], [])
        self.assertEqual(MANIFEST["apiVersion"], 1)
        self.assertEqual(MANIFEST["minAppVersion"], "2026.10.42")
        self.assertLess((ROOT / "kiosk-satellite-plugin.json").stat().st_size, 32768)

    def test_settings_schema(self):
        def verify(fields):
            previous = set()
            self.assertLessEqual(len(fields), 50)
            for field in fields:
                self.assertNotIn(field["key"], previous)
                condition = field.get("visibleWhen")
                if condition:
                    self.assertIn(condition["key"], previous)
                    self.assertEqual(len(condition), 2)
                previous.add(field["key"])
                if field["type"] == "list":
                    self.assertLessEqual(len(field["fields"]), 16)
                    self.assertLessEqual(field["maxItems"], 32)
                    row_keys = {item["key"] for item in field["fields"]}
                    self.assertIn(field["row"]["title"], row_keys)
                    verify(field["fields"])
                if field["type"] == "select":
                    self.assertIn(field["default"], field["options"])
                if field["type"] == "number":
                    self.assertLessEqual(field["min"], field["default"])
                    self.assertLessEqual(field["default"], field["max"])
        verify(MANIFEST["settings"])

    def test_no_screensaver_control_or_touch_actions(self):
        source = (ROOT / "src/com/shymoose/livenotifications/LiveNotificationsPlugin.java").read_text()
        for disallowed in ("setContentInsets(", "publishScreensaver", "startScreensaver",
                           "stopScreensaver", "haCall(", "runAction(", "setOnClickListener"):
            self.assertNotIn(disallowed, source)
        self.assertIn(".onTop(true).touchable(false).closeOnBack(false)", source)
        chip = (ROOT / "src/com/shymoose/livenotifications/NotificationChip.java").read_text()
        self.assertIn("setSingleLine(true)", chip)
        self.assertIn("setEllipsize(TextUtils.TruncateAt.END)", chip)
        self.assertIn("ValueAnimator.areAnimatorsEnabled()", chip)
        self.assertIn("setDuration(580)", chip)
        self.assertIn("setClipToOutline(true)", chip)
        self.assertIn('ofFloat(outgoing, "rotationX", 0, 60)', chip)
        self.assertIn('ofFloat(current, "rotationX", -60, 0)', chip)
        self.assertIn("getHeight() * 0.70f", chip)
        self.assertIn("label.setTextSize(13 * scale)", chip)
        self.assertIn("value.setTextSize(17 * scale)", chip)
        self.assertIn("theme.typeface(600)", chip)
        self.assertIn("value == null ? INVISIBLE : VISIBLE", chip)
        self.assertIn("getWidth() * percent / 100f", chip)


if __name__ == "__main__":
    unittest.main()
