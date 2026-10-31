# Changelog

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
