# Play Console: Data safety answers (play version)

KinKeep has no internet permission and sends nothing off the phone, so under Google's definition (data transmitted off the device) it collects and shares nothing. Re-check these answers if you ever add a network feature, analytics or crash reporting.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing is transmitted) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: Settings, Delete all data (removes everything on the phone). Uninstalling also removes it. |
| Security practices | Notes are encrypted at rest with a key in the Android Keystore. Backups are encrypted with the user's password. |
| Independent security review | No |
| Privacy policy | Required. Host `docs/index.html` (steps in RELEASE_CHECKLIST.md). |

## Permissions to declare and explain
| Permission | Why |
|---|---|
| Read contacts | So the person can choose which of their own contacts to keep in touch with. Contacts stay on the device. |
| Post notifications | Reminders to reach out. |
| Biometric / screen lock (used through the system prompt) | Optional app lock. |
| Receive boot completed | Lets reminders resume after the phone restarts. |

There is no call log, SMS, location, microphone, camera or network permission.
