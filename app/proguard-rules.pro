# Room and Hilt ship their own consumer rules.
# Enums are persisted and backed up by name, so keep their constants un-renamed.
-keepclassmembers enum app.kinkeep.domain.** { *; }
