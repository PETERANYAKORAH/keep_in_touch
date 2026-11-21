# KinKeep

Offline Android app that tracks your calls and texts, scores the health of each relationship, and reminds you to reach out, warmly and without guilt. No internet permission, no analytics, all data stays on the device.

## Get an APK without Android Studio (GitHub builds it for you)
1. Create a free account at github.com, then create a **new private repository** (name it anything).
2. Upload `KinKeep-source.zip` to the main page of the repository: *Add file, Upload files*, drop in the zip, *Commit changes*. (On a computer you can instead unzip it and upload the contents of the `KinKeep` folder; the zip route also works from a phone.)
3. Create the build recipe: *Add file, Create new file*. In the name box type `.github/workflows/build-apk.yml` exactly, paste the contents of `build-apk.yml` (also in the download), then *Commit changes*.
4. Open the **Actions** tab. The *Build APK* run starts by itself (or choose *Build APK, Run workflow*). It takes about 8 to 15 minutes the first time.
5. When it shows a green tick, open the run and scroll down to **Artifacts**. Download **KinKeep-debug-apk**, unzip it, and copy the `.apk` to your phone to install it (allow "install unknown apps" when asked).
6. If the run fails (a red cross), open it: the summary page has a box listing the errors. Copy that box and send it to Claude to fix.

The installed app is called KinKeep with the package name `app.kinkeep.direct`. The *release* artifact is the smaller, optimised build; until you add a signing key it is signed with a debug key, which is fine for installing but not for the Play Store.

### Optional: a real signing key and tagged releases
1. In a **private** repository run the workflow *Create signing key*, download its artifact, add the four values as repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; Settings, Secrets and variables, Actions), then delete the artifact and keep a private copy of the key.
2. Create a release: *Releases, Draft a new release*, make a new tag such as `v1.0.0`. The *Release* workflow builds a signed `KinKeep-direct.apk` and attaches it to that release.

See `docs/DISTRIBUTION.md` for why there are two versions (direct download and Google Play), and `docs/play/` for the store listing, data safety answers, privacy policy and release checklist.

## Status
- Phase 0 done: spec in `docs/PHASE0_SPEC.md`
- Phase 1 done: Gradle project, dark green and white theme, Hilt, Room schema
- Phase 7 done: GitHub Actions builds (APK, signed release, signing key generator, CI), direct-download and Google Play versions, signing and versioning, database schema frozen at version 1 with a migrations scaffold, privacy policy, Play listing and data safety documents, release checklist
- Phase 6 done: faster import for very large phones, instrumented test suite, scale tests, phone-maker battery guidance, stale-reminder warning, local privacy-safe error log, CI workflow, accessibility labels, missing build files added
- Phase 5 done: biometric app lock, encrypted notes, password-encrypted backup and restore, delete-all-data, 3-page onboarding, weekly goal with streaks, Sunday weekly summary, tab icons
- Phase 4 done: hourly reminder engine with notifications (Call, Snooze, Already spoke), learned best time per person, quiet hours, daily cap, missed-call and birthday reminders, 32 rotating warm messages, home-screen widget, Settings tab with battery guidance and a test notification
- Phase 3 done: dashboard (health ring, category counts, reach-out list, missed calls, 8-week activity chart), people list with search and a not-tracked list, contact detail (circle, cadence, pause, notes, manual log, history, call and message buttons)
- Phase 2 done: scoring engine, contact and call-log import, number matching, circle suggestions, 6-hourly background sync, and a first home screen listing tracked contacts by health score (most in need first)

## Run it
1. Open the `KinKeep` folder in Android Studio (latest stable).
2. Let Gradle sync. If a version is flagged as outdated, bump AGP, Kotlin, KSP, Compose BOM, Room and Hilt together.
3. Run the `app` configuration on a device or emulator (API 26+).
4. Run the unit tests: right-click `app/src/test` and choose Run Tests, or run `gradle wrapper` once and then `./gradlew :app:testFullDebugUnitTest`. See `docs/TESTING.md` for the full test and manual QA guide.

## Not yet verified
This code was written without a Gradle build environment, so it has not been compiled here. The scoring numbers in the tests were cross-checked against a Python mirror of the formula. Expect to fix small build issues on first sync.

## Next phases
6 to 7: Testing, OEM battery handling, release and Play Store assets

## How import works
- New contacts start as Ignored, then get a suggested circle: starred or 8+ contacts in 90 days = Close, 3+ = Friends, otherwise not tracked until you add them.
- Phone numbers match across formats (+234, 0803, 00234) using the last 9 digits.
- Rejected, blocked and voicemail entries are not counted. Texts are not read yet.
- Sync runs when you tap Import and every 6 hours in the background.

## Using the app (Phase 3)
- Dashboard: overall health ring, people per category, who to reach out to today (closest circles first, most overdue first), missed calls to return, and conversations per week.
- People: Tracked and Not tracked lists with search. Open anyone in Not tracked and pick a circle to start tracking them.
- Contact detail: Call and Message open your dialer or messaging app. "I spoke to them" logs WhatsApp, in-person or any conversation outside the call log. You can change the circle, adjust the reminder interval, pause one person, and keep notes.
- Notes are not encrypted yet (Phase 5). Manual logs count as full-weight contact.

## Reminders (Phase 4)
- A worker runs about once an hour. It reminds you about a person once the current hour reaches their target hour: the median hour you usually call them (after 3 or more calls), otherwise your default time. A late run still catches up later the same day.
- Never during quiet hours (default 22:00 to 08:00). At most 3 a day by default. The same person is not reminded again for 2 days. Muted and snoozed people are skipped.
- Order: missed calls first, then closest circles, then most overdue. Birthdays (read from your contacts) are sent once a year and do not use the daily cap.
- Notification buttons: Call, Snooze (until tomorrow), Already spoke. Tapping the notification opens that person.
- Snooze for a week, or clear a snooze, on the contact page.
- Timing is approximate. WorkManager can run late in battery saver or Doze, which is why Settings guides you to allow unrestricted battery use.
- The database schema changed again (version 2). Debug builds wipe local data on a schema change, so uninstall and reinstall if the app crashes after updating. Real migrations are needed before release.

## Not yet done
Daily digest mode, anniversaries, WhatsApp shortcuts, translations, performance work for very large contact lists, OEM battery testing, release signing and Play Store assets.

## Privacy and security (Phase 5)
- Notes (on people and on logged conversations) are encrypted with AES-256-GCM using a key held in the Android Keystore. The key never leaves the phone. If the key is lost (for example after "Delete all data"), old notes become unreadable by design.
- App lock uses fingerprint, face or your phone's PIN, pattern or password. It locks on launch and after 30 seconds in the background, and blocks screenshots and the recent-apps preview while enabled.
- Backups are one encrypted file (PBKDF2-HMAC-SHA256, 210,000 rounds, then AES-256-GCM) saved wherever you choose. There is no way to recover a lost password. Restoring replaces everything in KinKeep. Notes are stored inside the encrypted file in readable form, so a backup can be restored on a new phone.
- After a restore, tap Import on the People tab. Contacts are re-linked by phone number and calls are matched by time, so nothing is counted twice.
- Delete all data removes every record, note, setting, the encryption key and pending notifications. Your phone's own contacts and call history are never touched.
- Weekly goal: a week counts when you had a real conversation (call of a minute or more, text, or logged chat) with at least the goal number of tracked people, over rolling 7-day periods. A streak is consecutive weeks meeting the goal. The current week never breaks a streak until it ends.
- Database version is now 3 (calls are de-duplicated by time). Debug builds wipe data on a schema change, so reinstall if needed.

## Tests
69 unit tests and 31 instrumented tests cover scoring, phone matching, dashboard logic, reminder planning, wording rotation, streaks, weekly summary timing, note encryption and backup encryption. Run them with `./gradlew :app:testFullDebugUnitTest`.

## Reliability and performance (Phase 6)
- Import loads contacts, numbers and call data into memory maps once, then writes in a few batches, instead of querying per contact. A new index on call time speeds the dashboard query. Database version was reset to 1 for the first release (see Phase 7).
- The dashboard only ever scores the latest data: while an import is writing, intermediate updates are skipped.
- A JVM test scores 5,000 contacts with 50,000 calls, builds the dashboard and plans reminders, and fails if it takes over 10 seconds (it should take well under one).
- Settings detects Samsung, Xiaomi/Redmi/Poco, Oppo/Realme/OnePlus, Vivo/iQOO, Huawei/Honor and Tecno/Infinix/itel phones, shows steps for that phone, and opens its autostart screen when it exists (best effort, falls back to app settings).
- If reminders are on but have not checked in for 3 hours, Settings shows a warning pointing to those steps.
- Crashes and background errors are recorded in a small local log: error type and stack only, never messages, names, numbers or notes. Settings lets you share it (you choose where) or clear it.
- Added `gradle.properties` (AndroidX), Gradle wrapper settings, `.gitignore`, `.editorconfig` and R8 rules for the enums stored by name.
