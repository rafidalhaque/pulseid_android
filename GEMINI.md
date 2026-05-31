# Project Instructions

## Pre-Push Checklist
Before every push to `master`, you MUST perform the following 4 things in order:
1. **Check Build:** Ensure the application builds successfully (`./gradlew assembleDebug`).
2. **Bump Version:** Update `versionCode` (integer) and `versionName` (string) in `app/build.gradle.kts`.
3. **Update Changelog:** Add a detailed entry for the new version in `changelog.txt`.
4. **Commit:** Commit all changes with a descriptive message.

## Release & Versioning
- **Semantic Versioning (SemVer):** This project strictly follows SemVer (`MAJOR.MINOR.PATCH`).
- **Uniqueness:** Every push to `master` must have a unique `versionName` in `app/build.gradle.kts` to avoid release conflicts.
- **Automated Releases:** The GitHub Actions workflow (`release.yml`) uses the exact `versionName` from `app/build.gradle.kts` for the GitHub Release tag and the Telegram notification.