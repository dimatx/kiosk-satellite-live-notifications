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
        groups = [field["group"] for field in MANIFEST["settings"]]
        self.assertEqual(list(dict.fromkeys(groups)),
                         ["User interface", "Notifications", "Screensaver", "Rotation", "Placement"])
        self.assertEqual(sum(a != b for a, b in zip(groups, groups[1:])), 4)
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

    def test_offsets_use_exact_signed_numeric_entry(self):
        settings = {field["key"]: field for field in MANIFEST["settings"]}
        for key, default in (("offsetX", 0), ("offsetY", -32)):
            field = settings[key]
            self.assertEqual(field["type"], "number")
            self.assertEqual(field["input"], "field")
            self.assertEqual(field["default"], default)
            self.assertEqual((field["min"], field["max"], field["step"]), (-2048, 2048, 1))
            self.assertEqual(field["unit"], "dp")
            self.assertIn("whole number", field["description"])
        self.assertEqual([field["key"] for field in MANIFEST["settings"]
                          if field.get("input") == "field"], ["offsetX", "offsetY"])

    def test_no_screensaver_control_or_touch_actions(self):
        source = (ROOT / "src/com/shymoose/livenotifications/LiveNotificationsPlugin.java").read_text()
        for disallowed in ("setContentInsets(", "publishScreensaver", "startScreensaver",
                           "stopScreensaver", "haCall(", "runAction(", "setOnClickListener"):
            self.assertNotIn(disallowed, source)
        self.assertIn(".onTop(onTop).touchable(false).closeOnBack(false)", source)
        self.assertIn("config.allows(screensaver)", source)
        chip = (ROOT / "src/com/shymoose/livenotifications/NotificationChip.java").read_text()
        self.assertIn("theme.card()", chip)
        self.assertIn('theme.color("onSurface")', chip)
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

    def test_simple_settings_preserve_saved_choices(self):
        settings = {field["key"]: field for field in MANIFEST["settings"]}
        fields = {field["key"]: field for field in settings["entries"]["fields"]}
        self.assertEqual(settings["textScale"]["default"], 100)
        self.assertEqual(settings["motion"]["title"], "Motion")
        self.assertEqual(settings["motion"]["options"], ["Roller", "None"])
        self.assertIn("light stays static", settings["motion"]["description"])
        self.assertEqual(fields["progressMode"]["options"], ["Automatic", "Off", "On"])
        self.assertIn("custom progress source", fields["progressMode"]["description"])
        self.assertEqual(fields["showPercentage"]["visibleWhen"],
                         {"key": "progressMode", "notEquals": "Off"})
        self.assertTrue(fields["showPercentage"]["default"])
        self.assertFalse(fields["advanced"]["default"])
        self.assertFalse(fields["liveIndicator"]["default"])
        self.assertEqual(settings["displayScope"]["default"], "Always")
        self.assertEqual(settings["displayScope"]["options"],
                         ["Always", "Screensaver only", "Dashboard only"])
        self.assertEqual(settings["style"]["group"], "User interface")
        self.assertEqual(settings["style"]["title"], "Style")
        self.assertEqual(settings["style"]["default"], "Native")
        self.assertEqual(settings["style"]["options"], ["Native", "Glass"])
        self.assertEqual(settings["screensaverStyle"]["group"], "Screensaver")
        self.assertEqual(settings["screensaverStyle"]["title"], "Style on screensaver")
        self.assertEqual(settings["screensaverStyle"]["options"],
                         ["Default", "Native", "Glass"])
        self.assertEqual(settings["screensaverStyle"]["default"], "Default")
        for key in ("progressEntity", "progressAttribute"):
            self.assertEqual(fields[key]["visibleWhen"], {"key": "progressMode", "equals": "On"})
        for key in ("iconEntity", "valueEntity", "valueAttribute", "conditionEntity", "conditionAttribute"):
            self.assertEqual(fields[key]["visibleWhen"], {"key": "advanced", "equals": True})

    def test_wrapped_overlay_repaint_lifecycle(self):
        source = (ROOT / "src/com/shymoose/livenotifications/NotificationChip.java").read_text()
        self.assertIn("redrawAfterLayout = this::postInvalidateOnAnimation", source)
        self.assertIn("addOnGlobalLayoutListener(redrawAfterLayout)", source)
        self.assertIn("removeOnGlobalLayoutListener(redrawAfterLayout)", source)

    def test_appearance_uses_discovery_and_shared_palette_without_new_settings(self):
        source = (ROOT / "src/com/shymoose/livenotifications/LiveNotificationsPlugin.java").read_text()
        self.assertIn('executeCommand("getHostApi"', source)
        self.assertIn('api.get("commands")', source)
        self.assertIn('api.get("events")', source)
        self.assertIn('host.subscribe("screensaver.appearance");\n                        readAppearance();', source)
        self.assertIn("revision != appearanceRevision", source)
        self.assertIn("request != appearanceRead", source)
        self.assertIn("currentSession != session", source)
        self.assertIn("screenKnown && saverKnown", source)
        chip = (ROOT / "src/com/shymoose/livenotifications/NotificationChip.java").read_text()
        self.assertIn("colors = new Colors(theme, darkSurface, appearance)", chip)
        self.assertIn("label.setTextColor(colors.label)", chip)
        self.assertIn("value.setTextColor(colors.value)", chip)
        self.assertIn("drawable.setTint(colors.icon)", chip)
        self.assertIn("progress.colors(colors.track, colors.fill)", chip)
        self.assertIn("indicator).setColor(colors.light)", chip)
        self.assertIn("outgoing.bind(outgoing.bound)", chip)
        self.assertFalse(any(field["key"].startswith("night") for field in MANIFEST["settings"]))

    def test_notification_editor_order_and_plain_language(self):
        entries = next(field for field in MANIFEST["settings"] if field["key"] == "entries")
        fields = entries["fields"]
        self.assertEqual([field["key"] for field in fields], [
            "enabled", "entity", "label", "advanced",
            "valueEntity", "valueAttribute", "iconEntity",
            "conditionEntity", "conditionAttribute", "rule", "operand",
            "progressMode", "progressEntity", "progressAttribute", "showPercentage",
            "liveIndicator",
        ])
        by_key = {field["key"]: field for field in fields}
        for key, title in (
            ("entity", "Primary entity"), ("label", "Notification label"),
            ("valueEntity", "Status text source"), ("valueAttribute", "Status text attribute"),
            ("iconEntity", "Icon source"), ("conditionEntity", "Visibility source"),
            ("conditionAttribute", "Visibility attribute"), ("progressEntity", "Progress source"),
        ):
            self.assertEqual(by_key[key]["title"], title)
        allowed_types = {"boolean", "entity", "string", "select"}
        for field in fields:
            self.assertIn(field["type"], allowed_types)
            self.assertNotIn("group", field)
            copy = field["title"] + " " + field.get("description", "")
            for jargon in ("scalar", "top-level", "operand", "subscription"):
                self.assertNotIn(jargon, copy.lower())
        for key in ("valueAttribute", "conditionAttribute", "progressAttribute"):
            help_text = by_key[key]["description"]
            self.assertIn("Leave blank", help_text)
            self.assertIn("state", help_text)
            self.assertIn("attribute", help_text)
        self.assertIn("0 to 100", by_key["progressEntity"]["description"])


if __name__ == "__main__":
    unittest.main()
