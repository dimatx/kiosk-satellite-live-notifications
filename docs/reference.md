# Configuration and reference

Start with the [README](../README.md) for installation and a quick overview.
This guide covers source choices, visibility rules, progress, lifecycle behavior
and development checks.

## Your first notification

1. Open **Live notifications** settings and add a notification. Pick a **Primary entity**:
   its friendly name, icon and state supply everything by default.
2. Leave **Use different sources** off. Choose **Show when**: `Available` shows
   while all required data is usable. Exact state comparisons or numeric thresholds
   can restrict visibility.
3. Under **User interface**, leave **Show live notifications** on and choose
   **Show on**: **Always**, **Screensaver only**, or **Dashboard only**.
   The default is **Always**. **Style > Native** uses KS's own card and theme.
4. Under **Placement**, enable **Preview sample notifications** and adjust the anchor,
   offsets and width. Samples are explicitly labeled `Preview` and do not read HA.
5. Turn preview off. Eligible notifications appear in your chosen display scope.
   No HA service calls or changes to your screensaver are needed.

Preview works without configured entities or matching conditions. It cycles sample
notifications at the selected dwell interval and stops when switched off, settings
close, the screen turns off, the plugin stops, or two minutes elapse. A saved preview
never resumes after restart. One sample includes a progress bar so you can check
its appearance too. Preview does not wake the display or start a screensaver.

## Find the right setting

| Group | What it controls |
| --- | --- |
| User interface | Global enable, display scope, general style, and shared text/icon size |
| Notifications | What each row says, when it is relevant, and optional progress |
| Screensaver | **Style on screensaver**: **Default**, **Native**, or **Glass** |
| Rotation | Shared reading time and motion |
| Placement | Shared position/width and sample preview |

**Native** uses the KS card surface, typeface and theme colors, consistent with
Navbar and Action Bar. **Glass** is the translucent dark chip with light text,
useful over photos. It does not blur or inspect the screen behind it.
**Style on screensaver > Default** follows the general **Style**; it does **not**
read the stock screensaver's appearance. Changing style does not duplicate or
change placement, size, motion or rotation.

**Upgrading an earlier developer build:** configurations without **Show on** now
default to **Always**, and the style defaults to **Native** with the screensaver
following it. Choose **Screensaver only** and **Glass** to retain the earlier
presentation. Existing disabled settings, explicit sizes, source picks, progress
choices and percentage-text preferences are retained.

## Behavior

- At most one eligible row is visible. With none, the chip disappears; with one,
  it stays still. Multiple eligible rows rotate in configured order.
- **Time per notification** defaults to 10 seconds (3–120 seconds). New eligible
  rows do not interrupt the current reading; clearing the current row advances
  promptly. Live name, icon and value changes do not restart its dwell.
- **Roller** is a clipped 580 ms cylinder transition with clear vertical travel and
  perspective inside a stationary chip, not a whole-card rotation.
  The **Motion** setting also controls the live-indicator pulse: **None** disables
  both animations and keeps an enabled light static. Android's disabled animations
  setting is respected too.
- The native surface, KS typeface, circular icon and two single-line,
  ellipsized text fields stay bounded. Adaptive width is 480 dp in landscape and
  320 dp in portrait, capped by the available viewport. Height is normally
  84 dp and grows only for text scaling, never because a notification is long.
  KS's **Scale UI** applies to the whole overlay.
- **Text and icon size** defaults to a neutral 100% for new configurations.
  Existing explicitly saved sizes are preserved. The title and status use At a
  Glance's 13/17 proportions and regular/semibold hierarchy. Set this percentage to the
  current stock **Row scaling** to match manually; the SDK cannot read that setting.
- Each row's **Progress bar** can be **Automatic**, **Off**, or **On**. Automatic
  recognizes HA `%` units or one explicit percentage in displayed text/attributes;
  Off keeps the bar hidden. On reveals optional progress entity/attribute picks
  for a custom source, read as 0–100 percent, so remaining-time text can have a
  separate completion bar. Empty progress overrides follow the displayed value source.
  Progress sources share subscriptions and count toward the same 16-entity limit.
  Values outside 0–100, unavailable data and ambiguous multiple percentages never produce a fill;
  ordinary numbers such as ETA minutes are not progress. The track's space stays
  reserved on non-percent entries so the chip and clock never jump during rotation.
- **Show percentage text** is on by default. Turn it off to let a valid progress
  bar replace its matching percentage digits while retaining useful activity or
  remaining-time text. With no valid bar (including Off), percent text is retained;
  an unrelated percentage is not removed when the bar uses an independent source.
  The option is hidden when **Progress bar** is **Off**, without clearing its saved
  preference.
- **Live indicator is off by default.** An optional per-row light has a softly
  feathered halo and one slow brightness pulse;
  the entity icon stays static. It only appears with valid subscribed sources,
  never for preview samples, and becomes static with **None** or disabled system
  animations. It stops when the row/view/screen disappears. It indicates current
  subscription availability, not a guarantee of how frequently an integration
  measures its physical appliance.
- **Show live notifications** off disables all live display scopes. Explicit
  sample preview remains separate. Live mode requires the screen to be on and
  Kiosk Satellite to be in the foreground. **Dashboard only** excludes the screensaver; it does not mean
  displaying over other apps, menus or camera screens.
- On the dashboard the overlay uses KS's ordinary layer, below native menus,
  cameras and voice surfaces. Screensaver mode uses its upper overlay layer.
  Switching layers retains live subscriptions and the current rotation; leaving
  the allowed scope, screen-off or backgrounding releases sources and timers.
  Resuming waits for fresh data. Stock covers/Lockdown follow KS's own layer rules.
- Preview never subscribes to HA. KS does not expose a general "covered by menu"
  event: feeds can remain live while a KS surface temporarily covers the ordinary
  layer. Activity backgrounding and screen-off are tracked separately.

### Optional sources and conditions

Each row has an optional **Notification label**. **Use different sources** reveals
**Status text source** and its attribute, **Icon source**, then **Visibility source**
and its attribute immediately before **Show when** / **Compare with**. Progress
controls follow together. Blank sources use the **Primary entity**, not each other.
Turning different sources off ignores their saved values without deleting them.

The current KS notification editor renders one ordered form: its list-row schema
does not support section headings or field groups (and rejects `group` on row
fields). This plugin keeps related controls adjacent and names their purpose rather
than declaring unsupported headings. True in-editor sections require host support;
the plugin does not replace the native settings system.

An **attribute** is an extra value attached to an entity in Home Assistant.
Leave each attribute field blank to use that source's state. If you need an
attribute, look at that entity under **Home Assistant > Developer tools > States**
and copy its name, for example `remaining` or `completion` if it provides one.
There is no automatic attribute picker in this version.

Value and condition sources can select an exact **top-level attribute name**.
Only scalar text, booleans and numbers are supported, not objects, lists, paths
or templates. Missing attributes fail closed. Displayed state numbers use the
device locale and the value entity's `unit_of_measurement`; attribute values do
not inherit that unit. Common binary states have readable labels such as Open,
Closed, Detected and Clear. Other integration state strings are shown as supplied
by HA; this is not HA's full frontend translation/formatting engine.

| Show when | Comparison |
| --- | --- |
| Available | Primary, value and condition data are available and usable |
| Equals / Does not equal | Exact, case-sensitive raw HA state or selected scalar attribute |
| Greater than / At least / Less than / At most | Finite plain decimal numbers; use `25.5`, not `25,5`, units or expressions |

`unknown`, `unavailable`, missing, connecting, disconnected, not-configured and
oversized data never match, **even Does not equal**. Booleans in attributes compare
as `true` or `false`. A disconnected icon source uses a generic icon while status
reports the problem; unavailable primary/value/condition sources hide that row.
Transport and configuration failures appear in the plugin's runtime status,
separately from ordinary nonmatching conditions.

### Practical source examples

These are illustrative mappings, not entities installed by this plugin. Use the
entities and attributes your own integrations provide.

| Notification | Status text | Visibility | Progress |
| --- | --- | --- | --- |
| Dryer | Primary entity reports `24 min remaining`; no override needed | Optionally choose its completion sensor as **Visibility source**, **Show when > Less than**, **Compare with > 100** | **Progress bar > On**, **Progress source** = the completion sensor, reporting 0–100 |
| Vacuum | Primary entity is the vacuum. Enable different sources and choose a status sensor reporting `Cleaning - bedroom` as **Status text source** | Keep the vacuum as the visibility source and compare its state with `cleaning` | Choose its separate 0–100 cleaning-progress sensor |
| Vehicle arrival | A status/ETA sensor reports `12 min` or `Arrived` | **Available** keeps the arrived state eligible while that source remains valid | Choose a separate trip-completion sensor; arrival can report exactly 100 |

Trip completion is **not** inferred from ETA minutes. The plugin does not invent
room names, compute routes or create helpers. **Automatic** progress recognizes a
valid percentage in the displayed value; use **On** when progress comes from a
different entity or a plain numeric attribute. These numbers always mean 0–100
percent, not an arbitrary minimum/maximum range.

Turn **Show percentage text** off to remove redundant percent digits only while a
matching valid bar is shown. Status such as `Cleaning - bedroom` and time remaining
stays readable. The live light is optional; leave it off for a quiet default.

### Placement is manual

Choose any of nine anchors, horizontal and vertical offsets, and adaptive or custom
width. Adaptive width responds to orientation; Custom keeps your explicit
220–640 dp width. Positive offsets move right/down; negative offsets move left/up. KS caps
the chip to available screen space and keeps it within a 12 dp edge inset.

**There is no automatic clock/widget avoidance.** The current SDK does not expose
stock clock bounds, and the clock can move independently. Check your screensaver's
different layouts using preview and leave adequate space. The plugin does not
inspect private view trees, alter stock screensaver settings, or reserve dashboard
content insets.

### Limits and permissions

Up to 32 notification rows can be configured, but the SDK allows **16 unique HA
entity subscriptions per plugin**. All active row sources count, including overrides;
duplicates share one subscription. Disabled rows do not count. An over-limit
configuration is rejected as a whole with an actionable status message, not silently
truncated. Correcting configuration errors recovers automatically; a runtime SDK
failure asks you to check compatibility and restart the plugin.

Only `overlay` and `host.read` are requested. There are no HA service calls,
credentials, write capability, content actions, dismissal, history or network
libraries in the implementation. KS owns HA authentication and reconnection.
The host provides plugin settings controls, entity pickers and row reordering.

## Build and validate

Install **Python 3**, **JDK 17+**, and an Android SDK with platform 35 and build-tools
35.0.0. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) and `JAVA_HOME` to installed versions.
No Gradle, external Java libraries or Python packages are needed.

```powershell
python tools\test.py
python tools\build.py --android-platform 35 --android-build-tools 35.0.0
python tools\verify_package.py
```

Outputs:

- `dist/ks-live-notifications-0.1.3.zip` — local developer installation package.
- `dist/kiosk-satellite-plugin.json` — byte-identical standalone manifest.
- `dist/ks-live-notifications-0.1.3.zip.sha256` — SHA-256 checksum.

The official SDK and build helpers are vendored at the revision recorded in
[NOTICE](../NOTICE), with their Apache-2.0 licensing intact. SDK classes are compile-time
only, never packaged. The implementation is original Java under `src/`.

The tests compile **all production sources** against the official SDK and Android
platform, then exercise eligibility, formatting, exact subscription limits, ordered
rotation, stale-read races, live updates, preview expiry, subscription changes,
cleanup and restart with a fake host and deterministic clock. They do **not** render
Android views or replace testing in KS. Package verification checks the checksum,
manifest identity, license, size limits and actual DEX class definitions.

**Build and test** CI produces the developer ZIP as an Actions artifact on pushes
and pull requests. The separate **Release** workflow runs only for an explicitly
published release or a retry against an existing release tag; it builds tagged
source and attaches the three required assets with GitHub Actions' bot identity.
No release or pull request is created by the build scripts.

Both workflows explicitly select build-tools 35.0.0, not just Android platform 35.
Installing that package alone does not stop a runner from selecting a newer
preinstalled DEX compiler. The pinned compiler matches the device-tested builds;
the verifier continues to reject classes outside the plugin's namespace.

## On-device acceptance checks

Before calling a release device-tested, install the ZIP on a KS test device and check:

- Live mode over more than one existing screensaver (including dim), without
  changing its settings; stock screen-off, cover and Lockdown behavior remains intact.
- Touches and Back go through normally; the chip neither wakes nor dismisses anything.
- Different screen sizes/orientations, KS UI scales, Android font sizes, light/dark
  KS themes, long labels/values, and the full 580 ms roller with animation disabled too.
- Two-minute preview expiry, stopping via its toggle/settings close/screen-off,
  accurate placement, and no implication of automatic collision avoidance.
- HA disconnection/reconnection, entity removal, unknown/unavailable/large data,
  conditions clearing, and live updates without flicker or dwell resets.

No physical-device or emulator rendering verification is implied by the JVM/build
checks. KS Remote Admin screenshots may omit native overlays; use a device screenshot
when verifying visuals.

Physical coverage is Android 8.1 (Lenovo StarView, 1280×800 landscape and
800×1280 portrait) and Android 17 (Fire Max 11, 2000×1200 landscape), on KS
2026.10.45. The Android 8.1 display
was checked with isolated simulated HA values, dashboard/screensaver transitions,
all three scopes, both styles, inheritance, screen-off and background/resume.
Both devices were checked with the washer icon, text and progress at 100%, 130%
and 180% plugin text/icon size using native-resolution ADB framebuffer captures.
Settled content matched pixel-for-pixel with motion off and after the roller,
including reuse of both content rows. KS supplies vector icon outlines drawn at
their final bounds; this plugin does not upscale cached icon bitmaps.
The vehicle, dryer and vacuum demo icons were also inspected on Android 17.
Single static notifications were checked across screensaver-to-dashboard changes
without relying on another value update or rotation to paint the first frame.

The portrait pass checked adaptive width, a custom width capped to the viewport,
long labels/status, 180% text/icon size, offsets, three-row rotation and progress.
Native styling was checked on the light-theme dashboard, and Glass over the
built-in Image URL screensaver using a public nature photograph, not household
photos. An isolated demo source was made unavailable and restored. A separate
device-local HA connection failure was induced by temporarily pointing only the
test kiosk at a verified closed loopback port, then restoring its connection:
stale content hid and fresh content returned without restarting the plugin.
Shared HA, credentials and network configuration were untouched. Exact host,
plugin and demo-helper settings were restored, preserving portrait and lights off.

Remote Admin's actual notification editor was checked for field order, plain
labels, conditional progress controls and canceled-draft preservation. The test
device's restricted menu did not expose native Settings; its editor rendering
remains an acceptance gap rather than a claimed pass. Native list-row headings
are unsupported in the verified host schema/renderer.

Core/controller tests also cover disconnect and lifecycle behavior. Additional
screensaver renderers, KS UI scales/Android font sizes, abrupt shared-server
outages and prolonged intermittent network loss remain broader coverage gaps.
These two devices do not establish sharpness on every Android version or GPU.
The earlier demo video predates the new light-off preference, display scopes and
Native/Glass style controls; it is not evidence of those newer changes.

See [CONTRIBUTING.md](../CONTRIBUTING.md) for development and the manual release
checklist, and [CHANGELOG.md](../CHANGELOG.md) for release changes.
