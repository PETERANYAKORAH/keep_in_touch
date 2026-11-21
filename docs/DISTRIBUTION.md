# How KinKeep is distributed

KinKeep builds in two versions from one code base.

| | Direct download (`full`) | Google Play (`play`) |
|---|---|---|
| Where | GitHub Releases, or any APK file | Google Play Store |
| Reads the call history | Yes (the main feature) | **No** |
| Conversations come from | Call history, plus "I spoke to them" | "I spoke to them" and the notification button |
| App id | `app.kinkeep.direct` | `app.kinkeep` |
| Build command | `gradle :app:assembleFullRelease` | `gradle :app:bundlePlayRelease` |

## Why two versions
Google Play only allows the call-history permission for apps that are the phone's default dialer (or assistant), with a few narrow exceptions. Google's published policy lists "contact prioritization" by an app that is not the default handler as a use that is not permitted. KinKeep is not a dialer, so a build that reads the call log would be rejected, and an app that declares the permission without qualifying risks removal. Do not submit the `full` version to Google Play, and do not submit a Permissions Declaration Form for it.

The `play` version keeps everything else: circles, health scores from the conversations you log, learned reminder times, birthdays, notifications, widget, streaks, weekly summary, encrypted notes, backup and app lock. Reminders and scores are only as accurate as the conversations people log, so it is a lighter product than the direct version.

Check Google's current policy before you publish: it can change. Search for "Google Play SMS and Call Log permissions policy".

## Installing the direct version
Download `KinKeep-direct.apk` from the GitHub release, open it on the phone, and allow "install from this source" when Android asks. Updates: install the newer APK over the old one (it must be signed with the same key, so keep the signing key safe).

Because there is no store, there is no automatic update. Tell users to watch the repository's Releases page.

## Residual detail for Play review
The `play` build contains the code that can read the call log (it is switched off by a build flag and the permission is not in its manifest). This is harmless, but if a reviewer ever asks, the answer is that the build cannot request or use the permission.
