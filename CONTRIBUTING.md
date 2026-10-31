# Development and release

## Local checks

Use Python 3, JDK 17+, Android platform 35 and build-tools 35.0.0. Set `JAVA_HOME`
and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to your installed toolchains.

```powershell
python tools\test.py
python tools\build.py --android-platform 35
python tools\verify_package.py
git diff --check
```

Tests compile the production sources against the official SDK, then exercise the
Android-independent model, real controller and package contracts. They do not
render Android Views. On Windows, two upstream asset tests can skip because of
filename/symlink restrictions; run CI on Linux before a release.

Keep the plugin ID and persisted setting keys/choices stable. Use only supported
KS manifest controls. Row editors currently allow at most 16 fields and no group
headings; adding a `group` to a row field is rejected, not a way to make sections.
Retain the SDK/tool attribution and Apache license when updating the pinned SDK.

Before sharing screenshots, crop/redact real household values, names, credentials
and unrelated UI. Do not commit device settings, helper exports, tokens, recordings
or private validation artifacts. Use simulated values for public documentation.

## Manual release checklist

This checklist is for a future, explicitly authorized release. Nothing in the
local build commands creates a tag, pushes, or publishes a release.

1. Review the [README acceptance checks](README.md#on-device-acceptance-checks).
   Record actual device coverage and remaining gaps. Check both native settings
   and Remote Admin, all three display scopes, both styles/inheritance, progress,
   screen-off/background resume, and reduced motion.
2. Update the manifest development version and changelog, run the checks above,
   and commit reviewed source/docs. Confirm that no private artifacts are staged.
   The release README is read from the tagged commit, not the latest branch.
3. After authorization, push the reviewed commit and create its version tag,
   for example `v0.1.3`. Publish a **stable** GitHub release from that tag and make
   it the latest release. KS's repository installer ignores drafts/prereleases.
4. The **Release** workflow tests the tagged source and builds with the tag's
   version (`v` is optional). It uses the built-in `${{ github.token }}` to attach
   `kiosk-satellite-plugin.json`, `ks-live-notifications-<version>.zip`, and
   `ks-live-notifications-<version>.zip.sha256` from the same build.
5. Confirm all three assets were uploaded by **github-actions[bot]**. KS rejects
   manually uploaded or personal-token assets. The standalone manifest must be
   byte-identical to the packaged one; the checksum names that exact ZIP.
   Do not upload `kiosk-plugin-sdk-1.jar`.
6. If the upload needs retrying, run **Release** with the existing release's tag
   selected, not a branch. Do not change published package bytes under the same
   version; release a new version for changes.
7. Verify the resulting repository-install preview, installation and compatible
   update on a test device. Community directory submission is a separate action,
   not an automatic consequence of creating the repository or release.

The package/installer contract and uploader requirement come from the official
[KS creating-plugins guide](https://github.com/jxlarrea/kiosk-satellite-plugins/blob/main/docs/creating-plugins.md#repository-and-release).
