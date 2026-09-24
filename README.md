# IntroLeap

![IntroLeap banner](brand/export/introleap_banner_1280x720.png)

IntroLeap is a lightweight Android TV and Google TV utility that automatically
activates supported **Skip Intro** controls. It currently supports Disney+ only.

It does not modify streaming applications, require root access, or attempt to
bypass content restrictions. IntroLeap simply activates a skip control after the
streaming app makes that control available to the user.

## Contents

- [Motivation](#motivation)
- [Currently supported](#currently-supported)
- [How it works](#how-it-works)
- [Accessibility service](#why-an-accessibility-service-is-required)
- [Setup and troubleshooting](#setup-and-troubleshooting)
- [Privacy](#privacy)
- [Performance](#performance)
- [Other streaming services](#other-streaming-services)
- [Limitations](#limitations)
- [Building from source](#building-from-source)
- [Automated builds](#automated-builds)
- [Development transparency](#development-transparency)
- [Security and unofficial builds](#security-and-unofficial-builds)
- [License](#license)
- [Disclaimer](#disclaimer)

## Motivation

IntroLeap was initially created because some TV series have introductions that
are considerably louder than the rest of the episode.

Even when a streaming service provides a **Skip Intro** button, the viewer must
locate the remote and press it quickly. By the time that happens, the loudest part
of the introduction may already have played.

IntroLeap automates that single interaction.

## Currently supported

### Disney+

- Automatically skips intros

Disney+ integration is enabled by default and can be disabled with a single
switch in IntroLeap.

IntroLeap currently does not skip:

- Recaps
- Trailers
- Credits
- Next-episode screens
- Other playback elements

## How it works

IntroLeap uses an Android accessibility service to receive interface events from
supported streaming applications.

When Disney+ updates its playback interface, IntroLeap:

1. Confirms that Disney+ integration is enabled.
2. Examines the interface element associated with the event.
3. Searches for a supported and currently visible skip control.
4. Confirms that the control is enabled and clickable.
5. Performs the same click action that the user could perform with the remote.
6. Optionally displays a short confirmation after a successful skip.

The changed interface element is checked first. A bounded search of the accessible
interface tree is used only as a fallback. IntroLeap does not continuously analyze
the screen and does not use image recognition or OCR.

## Why an accessibility service is required

Android does not provide a standard API that lets one application press a button
inside another application.

An accessibility service is the standard non-root mechanism that can inspect
controls deliberately exposed by another application and activate those controls
on the user's behalf.

Android displays a broad warning when any accessibility service is enabled. That
warning describes the maximum capabilities available to accessibility services in
general; it does not mean that IntroLeap uses all of them.

IntroLeap limits its accessibility configuration to supported application
packages and only reacts to recognized skip controls.

## Setup and troubleshooting

### Enabling the service

1. Install IntroLeap.
2. Open it from the Android TV or Google TV applications list.
3. Select **Open accessibility settings**.
4. Find **IntroLeap** in the accessibility services list.
5. Enable the service and confirm Android's warning.
6. Return to IntroLeap.
7. Confirm that Disney+ is shown as available.

Disney+ integration is enabled by default. It can be disabled at any time from
the IntroLeap settings screen.

### If the accessibility settings do not open

Accessibility settings vary between manufacturers and Android TV versions.

If the IntroLeap shortcut does not open the correct screen:

1. Open the device's main **Settings** application.
2. Look for **Device Preferences**, **System**, or **Accessibility**.
3. Open **Accessibility services** or **Downloaded services**.
4. Select **IntroLeap**.
5. Enable the service manually.

The names and locations of these menus may differ between devices.

### If the service turns itself off

On Android 13 and later, applications installed outside an app store may be
prevented from using sensitive features until the user explicitly allows
restricted settings.

If IntroLeap disables itself immediately after being enabled:

1. Open the device's application settings.
2. Select **Apps** and then **IntroLeap**.
3. Open the overflow menu, usually represented by three dots.
4. Select **Allow restricted settings**.
5. Return to the accessibility settings and enable IntroLeap again.

The exact menu names and availability depend on the device manufacturer. Some
Android TV and Google TV firmwares may not display the **Allow restricted
settings** option.

When the option is unavailable, connect the device through ADB and check its
current state:

```bash
adb shell cmd appops get io.github.highwindbr.introleap ACCESS_RESTRICTED_SETTINGS
```

If the result is not `allow`, grant access with:

```bash
adb shell cmd appops set io.github.highwindbr.introleap ACCESS_RESTRICTED_SETTINGS allow
```

This command only removes Android's restricted-settings block for IntroLeap. It
does not enable the accessibility service automatically. After running it, return
to the device's accessibility settings and enable IntroLeap manually.

On devices that do not implement this restriction, the command may report that
the operation is unknown. In that case, no change is required.

Also look for battery, background-process, security, or application-management
restrictions imposed by the device manufacturer.

For additional ADB diagnostics, the following commands may help:

```bash
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility
adb shell dumpsys package io.github.highwindbr.introleap
```

The first command displays the accessibility services currently recorded as
enabled by Android. When active, IntroLeap should appear as:

```text
io.github.highwindbr.introleap/io.github.highwindbr.introleap.SkipService
```

## Privacy

IntroLeap operates entirely on the device and requires no internet, storage,
account, or root access.

The application contains no advertising, analytics, or telemetry components.
Screen, audio, and video capture are never used, and playback content is not
analyzed through OCR or image recognition. IntroLeap neither creates a viewing
history nor stores or transmits interface text.

Processing is limited to recognized accessible controls exposed by supported
applications that are enabled in IntroLeap. The optional skip confirmation is
generated locally and records no information about the content being watched.

## Performance

IntroLeap is event-driven and does not continuously poll the display. It examines
the interface element that changed and performs a limited accessibility-tree
search only when necessary.

## Other streaming services

Netflix, Prime Video, HBO Max, Globoplay, YouTube TV, and Apple TV were
investigated during development.

Some applications render their playback controls through custom interfaces that
do not expose visible skip buttons as actionable Android accessibility nodes.
Others could not be tested in a sufficiently reproducible playback state.

For that reason, services without a reliable accessibility implementation are not
currently included. They may be reconsidered if future versions expose suitable
accessibility controls.

For the tested versions, captured hierarchies, attempted approaches, and
service-specific conclusions, see
[`DEVELOPMENT.md`](DEVELOPMENT.md#other-services-investigated).

## Limitations

IntroLeap depends on the accessibility information exposed by the supported
streaming application. An application update may change button text, identifiers,
accessibility descriptions, interface hierarchy, or click behavior.

Such changes may temporarily prevent automatic skipping until IntroLeap is
updated. Blind coordinate taps are intentionally avoided because they can activate
the wrong control, interfere with navigation, or leave the player in an unexpected
focus state.

## Building from source

### Requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools
- Internet access during the first build so Gradle can download dependencies

The project includes the Gradle Wrapper, so a separate Gradle installation is not
required.

### Linux, macOS, or Termux

```bash
git clone https://github.com/HighwindBR/IntroLeap.git
cd IntroLeap
./gradlew :app:assembleDebug
```

### Windows

```bat
git clone https://github.com/HighwindBR/IntroLeap.git
cd IntroLeap
gradlew.bat :app:assembleDebug
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Installing the debug build through ADB

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation, open IntroLeap and enable its accessibility service manually.

## Automated builds

GitHub Actions builds a debug APK for every push and pull request. When a new
`versionName` reaches the `main` branch, the release workflow builds a signed APK,
creates the corresponding `v*` tag, and publishes the APK in the repository's
**Releases** section together with a SHA-256 checksum.

Automated releases use a stable CI signing key included in this repository. Its
purpose is to preserve Android update compatibility between releases, not to
provide exclusive proof of authorship. Because the key is public, obtain official
builds from this repository and verify the published checksum when appropriate.

Earlier test builds may use a different signing key. Android requires those builds
to be uninstalled before installing the first official release.

For implementation details, investigation results, release automation, and
guidance for extending IntroLeap, see [`DEVELOPMENT.md`](DEVELOPMENT.md).

## Development transparency

Research, implementation, debugging, documentation, and interface development
were carried out with assistance from OpenAI Codex under human direction.

Product decisions, physical-device testing, playback verification, and final
review were performed by the project maintainer.

Codex was used only as a development tool. IntroLeap includes no OpenAI model,
SDK, API integration, account requirement, cloud inference, or runtime service.
Codex is not present in the installed application and has no access to the device
or to what the user watches.

## Security and unofficial builds

### Security considerations

Only install APKs built from source or obtained from a release you trust.

Because accessibility services are security-sensitive, modified builds should be
reviewed carefully before installation. A fork could change the declared package
scope, add network access, or process unrelated interface content.

The official source is intended to remain small enough to be reasonably inspected.

### Forks and modified builds

The source code may be modified and redistributed under the terms of the GNU
General Public License v3.0.

To avoid confusion with the official project, forks and modified builds are
encouraged to use a different application name, icon, package name, and signing
key, and to state clearly that they are not official IntroLeap releases. These are
recommendations intended to help users identify the origin of an APK; they are not
additional licensing conditions.

Technical guidance for forks is available in
[`DEVELOPMENT.md`](DEVELOPMENT.md).

## License

IntroLeap is licensed under the GNU General Public License v3.0. See
[`LICENSE`](LICENSE) for the complete terms.

## Disclaimer

IntroLeap is an independent project and is not affiliated with, endorsed by, or
sponsored by Disney, Disney+, Google, Android TV, Google TV, or any other streaming
provider. All product names and trademarks belong to their respective owners.
