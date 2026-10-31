# Live notifications

A quiet, native notification chip over your **existing Kiosk Satellite screensaver**.
One Home Assistant notification at a time, rotating in the order you configure.
No replacement clock, new screensaver mode, buttons, tap actions, or touch interception.

**V1 developer build:** no release has been published yet. Requires Android 8.0+
(API 26) and Kiosk Satellite **2026.10.42+** with native overlay support.

## Set up

1. Install a developer ZIP using **Plugin Manager > Developer Tools > Install from ZIP**,
   confirm that you trust it, and enable the plugin.
2. Open **Live notifications** settings and add a notification. Pick one HA entity:
   its friendly name, icon and state supply everything by default.
3. Choose **Show when**. `Available` shows while all required data is usable;
   exact state comparisons or numeric thresholds can restrict visibility.
4. Under **Placement**, enable **Preview sample notifications** and adjust the anchor,
   offsets and width. Samples are explicitly labeled `Preview` and do not read HA.
5. Turn preview off and let your existing screensaver start normally.

Preview works without configured entities or matching conditions. It cycles sample
notifications at the selected dwell interval and stops when switched off, settings
close, the screen turns off, the plugin stops, or two minutes elapse. A saved preview
never resumes after restart. Preview does not wake the display or start a screensaver.

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
- The translucent dark surface, KS typeface, circular icon and two single-line,
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
  Values outside
  0–100, unavailable data and ambiguous multiple percentages never produce a fill;
  ordinary numbers such as ETA minutes are not progress. The track's space stays
  reserved on non-percent entries so the chip and clock never jump during rotation.
- **Show percentage text** is on by default. Turn it off to let a valid progress
  bar replace its matching percentage digits while retaining useful activity or
  remaining-time text. With no valid bar (including Off), percent text is retained;
  an unrelated percentage is not removed when the bar uses an independent source.
  The option is hidden when **Progress bar** is **Off**, without clearing its saved
  preference.
- An optional per-row **Live indicator** adds a small white light with a softly
  feathered halo and one slow brightness pulse;
  the entity icon stays static. It only appears with valid subscribed sources,
  never for preview samples, and becomes static with **None** or disabled system
  animations. It stops when the row/view/screen disappears. It indicates current
  subscription availability, not a guarantee of how frequently an integration
  measures its physical appliance.
- Live mode shows only while KS reports an active screensaver and screen. It works
  with stock or plugin screensavers, including dim mode; stock black covers and
  Lockdown Mode can still cover it according to KS's layering rules.
- Entity subscriptions pause outside live mode, during preview, or with the screen
  off. Resuming waits for fresh snapshots rather than showing cached values.

### Optional sources and conditions

Each row has an optional custom label. **Override sources** reveals icon, value
and condition entity pickers. Empty overrides use the **primary entity**, not each
other. Turning overrides off ignores their saved values.

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
python tools\build.py --android-platform 35
python tools\verify_package.py
```

Outputs:

- `dist/ks-live-notifications-0.1.2.zip` — local developer installation package.
- `dist/kiosk-satellite-plugin.json` — byte-identical standalone manifest.
- `dist/ks-live-notifications-0.1.2.zip.sha256` — SHA-256 checksum.

The official SDK and build helpers are vendored at the revision recorded in
[NOTICE](NOTICE), with their Apache-2.0 licensing intact. SDK classes are compile-time
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

## Credits and license

Conceptually inspired by [jtscott/ks-live-activities](https://github.com/jtscott/ks-live-activities).
**This is an independent, ground-up implementation.** No code from that project
was retrieved, copied, adapted, forked or reused.

Built against the official [Kiosk Satellite plugin SDK](https://github.com/jxlarrea/kiosk-satellite-plugins).
Licensed under [Apache-2.0](LICENSE); see [NOTICE](NOTICE) for SDK/tooling attribution.
