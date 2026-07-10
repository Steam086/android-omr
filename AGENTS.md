# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin Android app for generating and scanning answer cards, with the reusable scanner split into JVM modules. Android app source lives in `app/src/main/java/com/answercard/grader/`; unit tests mirror it under `app/src/test/java/com/answercard/grader/`. The pure OMR scanner and template geometry live in `omr-core/src/main/java/com/answercard/grader/`, and the desktop command-line runner lives in `omr-cli/src/main/java/com/answercard/grader/cli/`. Key Android app packages are `ui` for Jetpack Compose screens, `camera` for CameraX integration, `vision` for the legacy scanner, plus `records`, `export`, and `speech`. Android resources are in `app/src/main/res/`. Design and implementation notes live in `docs/`. The root `desktop-reference-card.png` and local image captures are reference assets, not app resources.

## Build, Test, and Development Commands

- Install JDK 21 LTS and set `JAVA_HOME` before running Gradle. JDK 17 is usually enough for the desktop-only modules, but JDK 21 is the preferred repo-wide version.
- `sh gradlew test` runs JVM unit tests, including Robolectric-enabled tests. Use `./gradlew` instead if the wrapper has executable permissions.
- `sh gradlew :omr-core:test` runs the Android-free OMR core tests.
- `sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.DesktopWechatImageScanTest` runs the desktop image-regression test for `微信图片_20260707164730_464_10.png`.
- `sh gradlew :omr-cli:run --args="../images/微信图片_20260707164730_464_10.png --questions 16 --answers 1:A,2:B,6:C,11:D,16:A --score 2 --legacy"` scans the legacy sample image without compiling the Android app.
- `sh gradlew assembleDebug` builds a debug APK.
- `sh gradlew check` runs the standard Gradle verification lifecycle.
- `sh gradlew clean` removes generated build output when results look stale.

Run commands from the repository root. If Gradle needs dependencies, use the configured repositories in `settings.gradle.kts`.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and idiomatic data classes, immutable values, and small focused objects. Match existing package boundaries before adding new abstractions. Compose screen files use `PascalCase` names such as `ScanScreen.kt`; tests use the same subject plus `Test`, for example `AndroidOmrEngineTest.kt`. Keep `omr-core` free of Android, AndroidX, Compose, CameraX, and `android.graphics` imports; use plain JVM APIs such as `ImageIO` only in desktop adapters/tests. Keep comments brief and only where the image-processing or geometry logic is not obvious.

## Testing Guidelines

Tests use JUnit 4, Robolectric, and plain Kotlin assertions. Add focused tests beside the package being changed, especially for OMR geometry, mapping, thresholding, template JSON, and scoring behavior. Prefer synthetic frames or rendered-template fixtures over device-only checks when possible. For Android-free scanner changes, run `sh gradlew :omr-core:test`; for app integration changes, run `sh gradlew test` before submitting.

## Commit & Pull Request Guidelines

This checkout does not include Git history, but project docs reference Conventional Commit-style examples such as `feat(omr): add android paper template layout`. Use short imperative messages with a scope: `fix(camera): handle rotated y-plane`. Pull requests should describe the user-visible change, list tests run, call out recognition or template compatibility risks, and include screenshots or sample scan images for UI/OMR changes.

## Architecture Notes

The active Android scanner path is CameraX `ImageAnalysis` -> `AndroidOmrImageAnalyzer` -> `AndroidOmrEngine`. `AndroidOmrEngine` and its geometry/readers are owned by `omr-core`; Android-specific frame plumbing stays in `app`. The desktop path is image file -> `ImageIO` grayscale frame -> `AndroidOmrEngine`. Keep the legacy `vision` path intact unless a task explicitly removes it; it is useful for fallback and comparison.
