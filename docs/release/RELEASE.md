# DaGoal 1.0.1 release candidate

Prepared 17 November 2026. Version code 2; minimum Android 10 (API 29), target API 36. This is a locally prepared release candidate. No GitHub release, policy hosting, or Play Console submission has been published.

## Distribution files

Run `python tools/prepare_release.py` from the repository root. It builds, lints, signs, verifies, and copies the distribution files to `build/outputs/distribution/`:

- `DaGoal-1.0.1.apk`: installable APK with the existing debug-to-release signing lineage.
- `DaGoal-1.0.1.aab`: signed bundle for store submission preparation.
- `SHA256SUMS.txt` and public signature verification reports.

Use this distribution APK for updates to the existing GitHub/debug build. The ordinary Gradle release APK has no migration lineage. The included lineage applies only to the original local debug certificate; installations signed with another developer's debug key require a separate migration plan. Do not uninstall an existing installation as an update workaround: uninstalling removes local data.

## Protect the permanent signing identity

The release keystore and signing configuration are in the Git-ignored `.release-private/` directory. Back up `dagoal-release.p12` and `signing.properties` together to secure storage outside this computer. The properties contain passwords. Never publish this directory, passwords, a keystore, or a test APK. Keep `docs/release/debug-to-release.lineage` with the source; it is public proof, not a private key.

Release certificate SHA-256: `18e3f5bb2a752b6fe682a368c0a310646aa8e7bd55ecfe41b7a4c2ab9e4e350c`.

Future builds must reuse this identity. Signing settings may instead be supplied through `DAGOAL_RELEASE_STORE_FILE`, `DAGOAL_RELEASE_STORE_PASSWORD`, `DAGOAL_RELEASE_KEY_ALIAS`, and `DAGOAL_RELEASE_KEY_PASSWORD`. Do not generate another key merely because credentials are missing.

## Changes included

- Fixed release lint errors in profiling back navigation and the package-scoped task-progress broadcast.
- Disabled cleartext HTTP; Firebase continues to use HTTPS.
- Added an offline privacy screen accessible from the welcome screen and Settings, using the existing design tokens. Public hosting copies are alongside this file.
- Account deletion waits for cloud-profile and authentication deletion before reporting success and clearing local data. Connection/authentication failures keep the session and show an error.
- Step tracking uses one sensor source, retains the day's reported count across counter resets, and handles missing activity permission before foreground startup.
- Daily reminder alarms are restored after reboot for an active guest or signed-in session; notification permission is checked before posting.

## Verification and limits

UI follow-up: moved the Settings privacy description below its title and made the shop refresh button grow with its text, with darker text and clearer cost/count labels. The APK/AAB were rebuilt and signatures verified again; release lint remains at zero errors. All eight existing responsive-layout cases passed after these changes, including gold, token, and maxed refresh states at normal and larger fonts. The earlier 92-case runs below preceded this UI-only follow-up.

- Signed release APK and AAB build successfully; APK and JAR signatures verified. APK signature verification covers API 29 through 36. Release lint has zero errors; existing warnings remain in the lint report.
- Android 12 physical phone: 92 regression cases passed before the final privacy system-bar contrast adjustment. Existing user, inventory, consumable, quest, reward receipt, recurring schedule, and shuffle-usage data were compared before and after the debug update and preserved. No AndroidRuntime or SQLiteLog errors were found in the checked startup log.
- Android 16 isolated emulator: all 92 regression cases passed on the final signed release APK. The suite covers account backup, scheduling, rewards, data integrity, database migrations, cosmetics, responsive layouts, session startup, quest dialogs, and release runtime behavior. An emulator System UI crash dialog blocked an earlier UI check; after dismissing that system dialog, the complete suite passed.
- Live Firebase check passed: temporary accounts could save and restore their own profile/schedules, other-account and unauthenticated access to that profile were denied, and temporary profiles/accounts were removed. This checks the exercised user-document paths, not every deployed rule or administrative setting.
- Actual debug-to-release APK update passed without uninstall: the same installation UID, private preference marker, and database gold value survived.
- Actual Android 16 reboot restored both daily reminder alarms without opening Dashboard. Delivery timing under every manufacturer's battery restrictions and physical sensor accuracy across every device remain unverified.

The emulator checks use isolated test fixtures; they are not a claim that every Android device or every account state has been tested. Do not run opt-in boot/migration probes against a person's installation.

## Before public store submission

1. Back up the permanent key and credentials securely.
2. Publish `privacy-policy.html` and `delete-account.html` at stable public HTTPS URLs and use those URLs in the store listing. They are prepared locally; repository Pages hosting is not enabled.
3. Confirm the listed project contact is monitored and complete owner declarations for target ages, Data Safety, foreground services, and any relevant health/activity features. The capstone evaluation sample (19–26) is not a minimum-age policy. See `PRIVACY-REFERENCE.md`.
4. Complete store/internal-track checks, including device-specific background behavior and the intended audience's account/deletion flows. The APK has migration proof for the prior GitHub build; store-managed signing has its own enrollment and upgrade path to review before publishing.
5. Review the release diff and lint warnings, then publish only the intended APK/AAB, checksums, and public policy files. No source commit or remote publishing was performed as part of this preparation.
