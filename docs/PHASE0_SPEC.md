# KinKeep: Phase 0 Spec

## Vision
Help people who struggle to pick up the phone stay close to the people who matter, privately, offline, and without guilt.

## Platform decision
Android first (Kotlin, Compose, Material 3). iOS cannot read the call log, so an iOS version would use manual logging only.
Play Store: Google Play only allows the call-log permission for the default dialer or assistant, and lists contact prioritization by other apps as not permitted. KinKeep therefore ships two versions: `full` (reads call history, direct download) and `play` (manual logging, no call-log permission). See docs/DISTRIBUTION.md.

## Design system
| Token | Hex | Use |
|---|---|---|
| Green Deep | #0B3D2E | primary, top bar, buttons |
| Green 700 | #1B5E46 | Thriving, dark containers |
| Green 500 | #2E7D5B | Healthy, secondary |
| Green 300 | #6FB594 | dark-theme secondary |
| Green 200 | #A8D5BE | dark-theme primary |
| Green 50 | #E8F3ED | light containers |
| White | #FFFFFF | background, text on green |
| Night | #0A1F17 / #10291F | dark theme background / surface |
| Amber | #B45F06 | Needs Attention |
| Rust | #8A3B12 | Fading |
| Slate | #5E7268 | Dormant |

Every status uses colour plus an icon and a text label (never colour alone).

## Data model
- contacts: id, androidContactId, displayName, circle, cadenceOverrideDays, birthdayMonthDay, notes, muted, createdAt
- phone_numbers: contactId, normalizedNumber (unique)
- interactions: contactId, timestamp, type (CALL/SMS/MANUAL), direction (INCOMING/OUTGOING/MISSED), durationSec, callLogId (unique, dedupe), note

## Circles and default cadence
Inner Circle 7 days, Close Friends and Family 14, Friends 30, Acquaintances 90, Ignored (not tracked). Per-contact override allowed.

## Relationship Health Score (0-100)
score = 0.50 recency + 0.25 frequency + 0.15 quality + 0.10 reciprocity − missed-call penalty

- Recency: 100 at 0 days, 75 when exactly due, linearly to 0 at 3x the cadence.
- Frequency: weighted contacts in the last 90 days / (90 / cadence), capped at 100%.
- Quality: call minutes in 90 days / (expected contacts x 5 min), capped.
- Reciprocity: 100 when outgoing share is 50%, 0 when entirely one-sided.
- Penalty: 10 points per unreturned missed call in the last 30 days (max 20).
- Weights: manual log 1.0, call of 60s or more 1.0, shorter call 0.3, SMS 0.4, missed 0. Meaningful contact means weight of at least 0.4.

Categories: Thriving 80+, Healthy 60-79, Needs Attention 40-59, Fading 20-39, Dormant below 20.

## Reminder logic (Phase 4)
1. A daily worker rescoring contacts.
2. Candidates are contacts at or past due, ranked by circle priority then days overdue.
3. Max N reminders per day (default 3), quiet hours respected, muted contacts skipped.
4. Time of day = the contact's typical outgoing-call hour (median), else the user default.
5. Snooze: later today, tomorrow, next week. "Already spoke" logs a manual interaction.
6. Copy: 30+ warm templates, rotated so no message repeats within 14 days.

## Screens
Onboarding, Permissions rationale, Dashboard, Contacts (filter by circle and category), Contact detail, Quick log, Reminders and quiet hours, Settings, Backup and restore.

## Privacy
No INTERNET permission. allowBackup off. Notes encrypted at rest. Biometric lock. Encrypted local export. Full data wipe in Settings.

## Build status
All planned phases (0 to 7) are done. Remaining work is on the person publishing: your own signing key, privacy policy hosting, screenshots from a real phone, and the Play Console steps in docs/play/RELEASE_CHECKLIST.md.
