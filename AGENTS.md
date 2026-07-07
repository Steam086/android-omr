# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin Android app for generating and scanning answer cards. Main source lives in `app/src/main/java/com/answercard/grader/`; unit tests mirror it under `app/src/test/java/com/answercard/grader/`. Key packages are `ui` for Jetpack Compose screens, `template` for card geometry/rendering/storage, `camera` for CameraX integration, `miniprogram` for the current OMR engine, `vision` for the legacy scanner, plus `grading`, `records`, `export`, and `speech`. Android resources are in `app/src/main/res/`. Design and implementation notes live in `docs/`. The root `desktop-reference-card.png` and local image captures are reference assets, not app resources.

## Build, Test, and Development Commands

- `./gradlew test` runs JVM unit tests, including Robolectric-enabled tests.
- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew check` runs the standard Gradle verification lifecycle.
- `./gradlew clean` removes generated build output when results look stale.

Run commands from the repository root. If Gradle needs dependencies, use the configured repositories in `settings.gradle.kts`.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and idiomatic data classes, immutable values, and small focused objects. Match existing package boundaries before adding new abstractions. Compose screen files use `PascalCase` names such as `ScanScreen.kt`; tests use the same subject plus `Test`, for example `AndroidOmrEngineTest.kt`. Keep comments brief and only where the image-processing or geometry logic is not obvious.

## Testing Guidelines

Tests use JUnit 4, Robolectric, and plain Kotlin assertions. Add focused tests beside the package being changed, especially for OMR geometry, mapping, thresholding, template JSON, and scoring behavior. Prefer synthetic frames or rendered-template fixtures over device-only checks when possible. Run `./gradlew test` before submitting.

## Commit & Pull Request Guidelines

This checkout does not include Git history, but project docs reference Conventional Commit-style examples such as `feat(omr): add android paper template layout`. Use short imperative messages with a scope: `fix(camera): handle rotated y-plane`. Pull requests should describe the user-visible change, list tests run, call out recognition or template compatibility risks, and include screenshots or sample scan images for UI/OMR changes.

## Architecture Notes

The active scanner path is CameraX `ImageAnalysis` -> `AndroidOmrImageAnalyzer` -> `AndroidOmrEngine`. Keep the legacy `vision` path intact unless a task explicitly removes it; it is useful for fallback and comparison.
