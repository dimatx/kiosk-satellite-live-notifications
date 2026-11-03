# Changelog

## 0.1.6

- Horizontal and vertical placement offsets use exact signed numeric entry
  instead of wide sliders, preserving saved values and the full 1 dp range.
- On KS 2026.11.1+, Native and Glass follow the Clock screensaver's active Night
  mode foreground/background and Flip card colors. Normal palettes return on
  exit or full-screen Now Playing takeover, without changing saved styles.
- Respect Clock Night mode's Hide widgets preference: hide live notifications,
  stop preview, pause sources/rotation and resume with fresh data.
- Discover the supported appearance command/event before subscribing and reading;
  protect initial attachment from stale snapshots and retain older-host behavior.
  Regression coverage for lifecycle races, both materials and explicit API failures.
- Native Android acceptance with an isolated temporary plugin ID on KS 2026.11.3:
  Night/custom/Flip palettes, restoration, hidden widgets and precise 1 dp placement.
  Transitions used detached SDK fixtures; production profiles and settings were unchanged.

## 0.1.5

- Vacuums without an explicit Home Assistant icon now use a robot-vacuum icon
  instead of the generic information icon.
- Regression coverage for active-only vacuum notifications with independent
  current-room and cleaning-percentage sources.

## 0.1.4

- Original notification/status-bar icon in the Navigation bar and Action bar
  family: orange background, dark pill, teal icon circle and a bell/status motif.
- Icon included in the manifest, installation preview, plugin package and README.
- Package verification checks the declared icon's bytes, dimensions and path,
  while still rejecting undeclared assets and non-plugin DEX definitions.
- Notification runtime and settings are unchanged.

## 0.1.3

First stable release, with the same runtime as the verified 0.1.3-rc.1 candidate.

- Repository installation and update checks through KS's normal Add plugin flow.
- A concise setup-focused README, installation button and contextual screenshots.

## 0.1.3-rc.1

First public release candidate. Install its downloaded ZIP through Developer
Tools; KS's repository installation flow intentionally excludes prereleases.

- Original passive Home Assistant notification overlay with ordered rotation,
  explicit state/numeric visibility rules, bounded text and a clipped roller.
- Display scopes: Always, Screensaver only, and Dashboard only. Screen-off and
  Activity backgrounding stop active work; resuming waits for fresh values.
- Native KS theme/card appearance or the translucent dark Glass chip.
  Style on screensaver offers Default (inherit general Style), Native and Glass.
- Independent status, icon, visibility and progress sources, with shared entity
  subscriptions and explicit validation of the 16-entity limit.
- Optional 0–100 progress bars, redundant percentage-text suppression, and a live
  light that is off by default. No-motion disables the roller and light pulse.
- Shared manual placement, adaptive landscape/portrait width, text/icon scale,
  and a bounded sample preview including progress. No automatic clock avoidance.
- Purpose-oriented settings labels, adjacent related fields, plain attribute help,
  and compatible saved Off/On and Roller/None choices.
- Progress ambiguity is checked before display truncation; percentage suppression
  handles localized digits. Disabling motion cancels an active roller immediately.
- Repaint after the host sizes a wrapped native overlay, so a single static
  notification does not wait for a later value update or rotation to appear.
- Build/test workflows, verified developer packages, licensing and release docs.
- Device acceptance on Android 8.1 and 17, including native-resolution settled
  icon/text/progress checks. The Android 8.1 pass also covers portrait, light KS
  theme, an Image URL photo screensaver, isolated unavailable/recovery and a
  device-local HA connection failure/recovery. See the device coverage in
  `docs/reference.md` for the exact limits.
- Explicit platform and DEX compiler selection in both workflows keeps release
  builds on the verified Android 35/build-tools 35.0.0 toolchain, even when
  GitHub runners have newer compilers preinstalled.

Earlier 0.1.0–0.1.2 versions were local developer iterations, not public releases.
When updating those builds, missing display scope defaults to Always, general
style to Native, and screensaver style to Default. Existing explicit values remain
saved, including disabled notifications and opt-in indicator choices.
