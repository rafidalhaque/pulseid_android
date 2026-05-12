# Project Instructions

## Release & Versioning
- **Semantic Versioning (SemVer):** This project strictly follows SemVer (`MAJOR.MINOR.PATCH`).
- **Version Updates:** After every logical set of changes (features, bug fixes, refactorings), you MUST update the `versionCode` (integer) and `versionName` (string) in `app/build.gradle.kts` before committing.
- **Automated Releases:** The GitHub Actions workflow (`release.yml`) uses the exact `versionName` from `app/build.gradle.kts` for the GitHub Release tag and the Telegram notification.
- **Uniqueness:** Every push to `master` must have a unique `versionName` in `app/build.gradle.kts` to avoid release conflicts.