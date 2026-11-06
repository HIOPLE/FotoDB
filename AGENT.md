# Agent Instructions

## Preserve the installed app and its data when testing

- Do not uninstall the app, clear its data, or reset the device as part of testing or verification.
- Prefer local JVM unit tests (for example, `testDebugUnitTest`) when they can verify the change.
- Before running instrumentation tests, check whether the test setup or command would uninstall/reinstall the app, clear app data, or otherwise affect the user's installed app or data. Do not proceed if it would; choose a non-destructive alternative.
- Do not run `clean` or other tasks that remove user data or installed app state as a routine testing step.
- If a requested verification cannot be done without risking the installed app or its data, explain the risk and ask the user for permission before proceeding.
