# Expense Tracker (Android)

Kotlin + Jetpack Compose + Room. Single module `:app`, package
`io.github.abhimanuearya1.expensetracker`.

## Building and testing

The Android SDK and Google's Maven repository are usually not reachable from a
Claude Code cloud session (`dl.google.com` is blocked), so `./gradlew` cannot
build the app there. Use GitHub Actions as the build machine:

- Every push runs `.github/workflows/build-apk.yml`: unit tests, `assembleRelease`,
  and the instrumented tests on an API 34 emulator. Read the results with the
  GitHub MCP tools (`actions_list`, `get_job_logs`).
- Dispatch the workflow with input `publish=true` to have it commit the tested
  APK to `apk/ExpenseTracker.apk` and emulator screenshots to `docs/screenshots/`.
  Pull afterwards, then look at the PNGs to check the UI.

What can run locally without the Android SDK: the unit tests in
`app/src/test`, which only touch `core/`. Compile `core/` and the tests with a
throw-away Kotlin/JVM Gradle project whose source sets point at those two
directories (JUnit 4 from Maven Central).

## Conventions

- `core/` must stay free of Android imports so it remains testable on a plain JVM.
- "Classification" on screen is `Category` in code.
- Amounts are `Long` hundredths (`amountCents`); dates are `LocalDate.toEpochDay()`.
- UI strings are English and written in the Kotlin code.
- Icons are vector paths in `ui/AppIcons.kt`; do not add an icon library.
- Database changes need a higher `version` in `AppDatabase` plus a Room
  `Migration`. Never use destructive migration: it erases the user's expenses.
- Keep the application id and `signing/expense-tracker.keystore` unchanged, or
  installed copies can no longer be updated in place.
- On-device tests find nodes by `testTag`; keep the tags when changing screens.
