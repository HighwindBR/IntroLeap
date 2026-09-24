# IntroLeap — Development Notes

This document describes IntroLeap's internal architecture, the accessibility-based
implementation used for Disney+, the experiments performed with other streaming
applications, and the main considerations for developers interested in forking or
extending the project.

It complements the user-facing [`README.md`](README.md).

## Project scope

IntroLeap is deliberately limited to interactions that can be implemented through
Android's public accessibility APIs in a reasonably reliable, lightweight, and
privacy-preserving way.

The project does not currently use:

- Root access, Shizuku, LSPosed, or framework hooks
- Modification of streaming applications
- Screen capture, OCR, or image matching
- Fixed-coordinate or blind directional input
- Network services, analytics, or telemetry

Disney+ is currently the only supported streaming application.

## Architecture

IntroLeap has two mostly independent components.

### Configuration interface

The settings interface is implemented with Kotlin, Jetpack Compose, Compose for
TV, and Material 3 for TV. It displays the service status, opens Android's
accessibility settings, detects whether Disney+ is installed, and stores the
user's choices.

Settings are stored in the private `SharedPreferences` file
`introleap_settings`. The relevant keys are:

```text
disney_enabled
disney_intro
show_skip_toast
```

Disney+ automation is disabled by default. The intro option defaults to enabled
but has no effect until Disney+ itself is enabled. The Compose activity does not
participate in playback detection.

### Accessibility service

Playback detection and activation are implemented by `SkipService.java`, which
extends Android's standard `AccessibilityService`.

The service is protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`, so
Android controls whether it may run. Its declared package scope and runtime checks
are both currently restricted to:

```text
com.disney.disneyplus
```

This double restriction reduces unnecessary events and prevents rules intended
for Disney+ from being applied to another application.

## Disney+ implementation

### Why Disney+ can be supported

The tested Android TV version of Disney+ exposes its **Skip Intro** control through
the Android accessibility tree. The visible control is represented by one or more
`AccessibilityNodeInfo` objects containing enough information to detect its
label, determine whether it is visible, locate a clickable node, and perform
`ACTION_CLICK`.

A button being visible on the television is not sufficient. It must also be
represented as a meaningful and actionable accessibility node.

### Accessibility configuration

The service requests these event types:

```text
typeWindowStateChanged
typeWindowsChanged
typeWindowContentChanged
typeViewFocused
typeViewTextChanged
```

Streaming players may create, update, focus, or rename controls at different
stages, so several event types are useful. `notificationTimeout` is zero to avoid
adding an artificial delivery delay.

The enabled flags are:

```text
flagReportViewIds
flagRetrieveInteractiveWindows
flagIncludeNotImportantViews
```

These flags allow reported view identifiers, interactive-window retrieval, and
inspection of nodes the target application may not classify as important. The
current Disney+ detector does not depend on a resource ID: it classifies the
button by accessible text or content description. `flagReportViewIds` keeps IDs
available for diagnostics and possible complementary matching in the future.

### Event-driven, source-first detection

IntroLeap does not continuously poll the screen. Every search starts in response
to an accessibility event from a supported package enabled by the user.

The processing order is:

1. Read and validate the event package.
2. Confirm that the user enabled that application.
3. Search the subtree rooted at `event.getSource()`.
4. If necessary, search `getRootInActiveWindow()` as a fallback.
5. Classify visible nodes.
6. Resolve a safe clickable node.
7. Perform `AccessibilityNodeInfo.ACTION_CLICK`.
8. Optionally display a localized confirmation.

The local search is limited to 80 nodes. It is normally faster because the event
source is often the new button, one of its children, or a nearby container. The
fallback traversal is limited to 900 nodes so a missing or unusual hierarchy
cannot cause an unbounded search.

Both searches use a breadth-first traversal backed by `ArrayDeque` and stop as
soon as an action succeeds.

### Label classification

A node is considered only when `isVisibleToUser()` returns true. IntroLeap joins
`getText()` and `getContentDescription()`, then normalizes the resulting string by:

- Applying Unicode canonical decomposition
- Removing diacritical marks
- Converting to lowercase with `Locale.ROOT`
- Collapsing repeated whitespace
- Removing leading and trailing whitespace

The current intro labels are:

```text
pular abertura
pular introducao
skip intro
skip opening
saltar intro
saltar introduccion
omitir intro
omitir introduccion
```

Matching uses specific substrings rather than exact equality. This tolerates
icons, instructions, or adjacent text without accepting unsafe generic labels
such as `Skip`, `Pular`, `Next`, or `Próximo`.

### Resolving the clickable node

The node containing the label is not necessarily the node that accepts clicks. A
typical hierarchy can place a text node inside a clickable container.

IntroLeap therefore:

1. Searches the matched node and up to 24 descendants for a visible, enabled,
   clickable node.
2. If necessary, walks upward through at most six parents.
3. Requires the selected node to remain visible, enabled, and clickable.
4. Calls `performAction(AccessibilityNodeInfo.ACTION_CLICK)`.
5. Tries the original matched node once if the resolved node differs and fails.

No display coordinates are involved.

### Cooldown and confirmation

A successful action is recorded under `package name + skip type`, such as:

```text
com.disney.disneyplus:intro
```

`SystemClock.elapsedRealtime()` is used for a five-second in-memory cooldown. This
prevents multiple events caused by one interface update from activating the same
control repeatedly. The timestamp is stored only after `ACTION_CLICK` succeeds.

If confirmations are enabled, a successful skip displays a localized Android
toast. No title, episode, timestamp, interface text, or viewing history is stored.

## What Disney+ support depends on

The implementation depends on the following remaining true:

1. Disney+ continues using `com.disney.disneyplus`.
2. The player continues exposing the control to Android accessibility.
3. A relevant node contains recognizable text or a content description.
4. The button, an ancestor, or a descendant remains clickable through
   `ACTION_CLICK`.
5. Android delivers a configured event when the control appears or changes.
6. The control is not moved entirely into a custom-rendered surface invisible to
   accessibility.

A visual redesign alone does not necessarily break IntroLeap. Color, shape, size,
or screen position may change while the same accessible node remains available.
Changes to labels, IDs, hierarchy, event behavior, or click handling matter more.

## Possible improvements to the Disney+ detector

### Resource-ID matching

If a stable ID becomes available, a detector can inspect
`getViewIdResourceName()` before or alongside label matching. IDs may be faster
and language-independent, but they are not automatically more durable: they can
change between builds, be obfuscated or removed, identify only a text child, or be
reused for several actions.

A robust order would be:

1. Known ID plus structural validation
2. Known text or content description
3. A limited contextual fallback

### Structured application rules

Future forks could represent each integration as a rule set containing package
names, IDs, labels, rejected labels, expected classes, click requirements, skip
types, cooldowns, and traversal limits. Package-specific rules should never be
applied globally.

### Diagnostic builds

A developer-only mode could log event type, package, class, view ID, text, content
description, state, bounds, hierarchy, match result, and click result. Such logs
should remain disabled in releases and should never be transmitted automatically.

Developers should also respect the lifecycle of `AccessibilityNodeInfo`: do not
retain nodes beyond event processing, and avoid large or long-lived collections.

## Application versions and rendering implementations

The results below apply to the versions that were actually tested. Streaming
applications can replace their TV player implementations without changing their
package name. Different versions may use native Android views, Compose, web-based
interfaces, custom rendering engines, surface-based rendering, proprietary
cross-platform frameworks, or different accessibility bridges.

Those differences can completely change the automation strategy. One version may
expose a button with text, an ID, and a click action, while another draws an
identical-looking control inside a custom surface and exposes only the whole player
as a generic view.

A negative result for one version does not prove that every earlier or later
version is incompatible. Support confirmed with one version should not be assumed
to work permanently either.

When Android reports more than one version entry for the same package, it does not
normally mean both player implementations are active simultaneously. It may show a
factory version retained in the system image together with an installed update.
The implementation actually selected and executed by Android must be tested.

Every investigation should record package name, active version and version code,
Android version, device model, interface language, installation source, and the
exact player state being inspected.

## Investigation methodology

Other services were evaluated through combinations of visual inspection,
`uiautomator dump`, ADB package/version inspection, accessibility-tree inspection,
APK extraction, static APK inspection, label and resource-ID hypotheses, focus
experiments, coordinate input, and comparisons between countdown and enabled
states.

The principal runtime capture was:

```bash
adb shell uiautomator dump --compressed /sdcard/window.xml
adb pull /sdcard/window.xml
```

It must be collected while the target control is visibly present. If that control
does not appear as a meaningful node, an ordinary accessibility service is
unlikely to activate it reliably. Its absence does not prove that every possible
technique is impossible; it is evidence that IntroLeap's straightforward approach
is unavailable.

## HBO Max

Tested package and version:

```text
com.wbd.stream
7.12.0.68
versionCode 1827120068
```

The application was identified and tested as HBO Max. Package identifiers may
remain unchanged despite commercial branding or regional presentation, so runtime
matching should use the package while documentation uses the product name shown to
users.

The target Portuguese control was `Ignorar abertura`. Text, content-description,
tree, ID, APK, hierarchy, and coordinate-based approaches were investigated.

The visible button was not exposed as a useful actionable node in the inspected
hierarchy. IntroLeap could not obtain a reliable label, confirmed stable ID, or
safe `ACTION_CLICK` target. Static APK resources can reveal implementation hints,
but cannot make a runtime player publish its rendered controls to accessibility.

A fixed-coordinate ADB tap was also tested. It disrupted the expected focus state:
the skip button lost its highlight and normal player controls became unavailable
until playback was exited. This demonstrated why fixed coordinates are unsafe
across resolution, density, layout, focus, and application changes.

HBO Max support was therefore removed. A future integration would likely require
application-specific reverse engineering, an internal player command, or visual
analysis rather than Disney+'s node-based method.

## Globoplay

Tested package:

```text
com.globo.globotv
```

The intended investigation covered label, content description, ID, hierarchy, and
click behavior while an intro control was visible. A title or state that
consistently displayed the control could not be found during testing, so a properly
timed capture was not obtained.

Globoplay is therefore unverified, not proven incompatible. Support should wait
for a reproducible title and playback state.

## Prime Video

Tested package and reported versions:

```text
com.amazon.amazonvideo.livingroom
6.24.7+v16.0.0.332-allAbis — versionCode 606024070
6.18.12+v15.3.0.298-armv7a — versionCode 606018121
```

The targets were intro, recap, and the official advertisement-skip button after
Prime Video made it available. Mandatory advertising time was never intended to
be bypassed.

Separate `prime-intro.xml`, `prime-recap.xml`, and `prime-ad-countdown.xml`
captures were made. They were extremely small and effectively represented the
same limited hierarchy despite different visible player states. The relevant
controls were not represented as ordinary actionable accessibility nodes.

The service could not reliably distinguish intro from recap, countdown from an
enabled skip-ad action, or visible player text from unrelated nodes. Prime Video
was placed on standby and later removed.

## Netflix

Tested package and reported versions:

```text
com.netflix.ninja
13.1.5 build 26083
11.0.1 build 19770
```

Repeated captures exposed only a minimal hierarchy containing generic player
views such as:

```text
com.netflix.ninja:id/player0
com.netflix.ninja:id/gibbon
```

No accessible child represented the visible intro button. The `gibbon` ID marks
the broader custom interface surface, not the skip action. The button consequently
had no separately usable text, description, bounds, clickable state, or ID.

Clicking the entire surface would not identify the intended control and could
interfere with player navigation. Netflix cannot currently use IntroLeap's
straightforward node-based method.

## YouTube for Android TV

Tested package and versions:

```text
com.google.android.youtube.tv
5.30.340 — versionCode 530340320
7.24.300 — versionCode 724300320
5.02.301 — versionCode 502301320 (older factory or retained entry)
```

These entries do not imply that three implementations were running at once. An
older entry may be the factory version retained in the system image while the
installed update is the code Android actually executes.

The 5.x and 7.x generations may use different player UI, rendering,
focus-management, and accessibility implementations. Observations from 5.30.340
therefore cannot automatically be applied to 7.24.300. The update justified a
new investigation because nodes, labels, IDs, focus behavior, advertisement
controls, and countdown states could all change.

The targets were `Skip ad`, `Next ad`, `Pular anúncio`, `Próximo anúncio`, and
localized equivalents. YouTube may show a countdown, an enabled skip action, a
next-ad action, or a control combining text, an icon, and time.

Generic matches such as `Skip`, `Pular`, `Next`, or `Próximo` would be unsafe
because they may refer to the next video, playlist item, chapter, or navigation
action. Complete phrases or corroborating structural signals are required.

Text comparison itself is inexpensive. The larger costs come from large tree
traversals, event floods, screenshots, OCR, and continuous polling. The obstacle
was reliable exposure and classification, not string-matching performance.

The investigation did not establish a safe rule for all relevant advertisement
states, particularly a reproducible and distinct `Next ad` node. YouTube was not
included. It remains more promising than a player exposed only as one custom
surface, but needs new captures for the active version before implementation.

## Apple TV

Tested or inspected package:

```text
com.apple.atve.androidtv.appletv
```

Only intro skipping was considered. No active subscription was available, so the
skip state, hierarchy, label, ID, and click behavior could not be reproduced or
validated. Apple TV was removed rather than presenting an unverified experimental
option. This testing limitation is not proof that its player is inaccessible.

## Approaches intentionally rejected

### Fixed-coordinate and blind remote input

Coordinates depend on resolution, overscan, density, aspect ratio, layout, focus,
and version. Simulated DPAD navigation also assumes a predictable focus order and
can reveal, dismiss, or activate the wrong overlay while the viewer uses the
remote. Neither method reliably proves that the intended action is available.

### OCR and image recognition

Visual detection could find controls missing from accessibility, but would require
screen-capture permission, periodic acquisition, language-aware recognition,
scaling and theme tolerance, more CPU and memory, broader privacy disclosures, and
additional false-positive protection.

### APK modification

Patching a streaming APK would break its original signature, complicate updates,
potentially interfere with DRM, and require per-version maintenance.

### Root, hooks, and internal APIs

Root, LSPosed, framework hooks, or reverse-engineered player methods could reach
internal actions, but would abandon IntroLeap's stock-device scope and introduce
version-specific maintenance. These remain possible research directions for
forks, not current project goals.

## Adding another streaming service

Before implementing support, obtain:

1. The exact package, active version, and version code.
2. A reproducible title and playback timestamp.
3. A dump while the control is visible.
4. Separate dumps before and after it becomes clickable, if applicable.
5. A dump after it disappears.
6. Captures for every intended language.
7. Proof that the relevant node accepts `ACTION_CLICK`.
8. Evidence that the rule cannot match another player action.

Useful commands include:

```bash
adb shell dumpsys package PACKAGE_NAME | grep -E "versionName|versionCode"
adb shell pm path PACKAGE_NAME
adb shell uiautomator dump --compressed /sdcard/window.xml
adb pull /sdcard/window.xml
```

On Windows Command Prompt:

```bat
adb shell dumpsys package PACKAGE_NAME | findstr "versionName versionCode"
adb shell uiautomator dump --compressed /sdcard/window.xml
adb pull /sdcard/window.xml "%USERPROFILE%\Downloads\window.xml"
```

Inspect `package`, `class`, `text`, `content-desc`, `resource-id`, `clickable`,
`enabled`, `focusable`, `focused`, and `bounds`. Prefer a stable ID with context,
then specific labels, hierarchy, state, a safe clickable relative, and finally a
bounded fallback traversal.

## Validation checklist

Before merging another integration, verify that:

- The application is disabled by default.
- Enabling it does not enable unrelated actions.
- Events from other packages are ignored.
- Hidden and disabled nodes are rejected.
- Generic `Skip` and `Next` controls cannot match.
- The first actionable appearance is detected promptly.
- Repeated events cause only one activation.
- The cooldown does not block a legitimate later action.
- Remote input cannot cause an unintended click.
- Language changes do not create false positives.
- Restarting the target and accessibility service remains safe.
- No network permission or telemetry is introduced unintentionally.
- Any privacy-sensitive technique is prominently disclosed.

## Current assessment

| Service | Assessment |
|---|---|
| Disney+ | Confirmed accessible and implemented |
| HBO Max | Visible control, but no reliable actionable accessibility node |
| Prime Video | Relevant player states not exposed in the captured hierarchy |
| Netflix | Player represented mainly by custom `player0` and `gibbon` surfaces |
| YouTube TV | Different tested generations may use different player implementations; the updated version remained insufficiently confirmed |
| Globoplay | Inconclusive because a reproducible skip-button state was not found |
| Apple TV | Unverified because subscribed playback could not be tested |

The current scope is based on technical evidence rather than the popularity of a
service. Forks are encouraged to preserve the event-driven architecture and add an
integration only when its controls can be identified and activated without screen
capture, blind input, privileged access, or invasive instrumentation.

## Product names and package identifiers

Commercial names and package identifiers serve different purposes. This document
refers to the tested service as HBO Max while retaining its Android package
`com.wbd.stream`. Runtime matching should use package identifiers; user-facing
documentation should use the product name presented in the tested market.

A branding change does not necessarily change the package or player. Conversely,
an application can replace its player and accessibility behavior while keeping
both the same name and package.
