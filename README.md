# Answer Card Android

Kotlin answer-card generator and scanner. The Android app lives in `app`, while the reusable OMR recognition logic is split into `omr-core` so it can run on a normal desktop JVM without compiling the Android app.

## Requirements

- JDK 21 LTS is recommended for this repository.
- JDK 17 usually works for the desktop-only modules, but JDK 21 is the safer repo-wide choice with the current Gradle and Android plugin versions.
- Set `JAVA_HOME` to the installed JDK before running Gradle.

The checked-in `gradlew` may not have executable permissions on macOS. Use `sh gradlew ...`, or run `chmod +x gradlew` once and then use `./gradlew ...`.

## Modules

- `omr-core`: Android-free Kotlin/JVM OMR algorithm, template geometry, answer/admission readers, scoring, and frame data models.
- `omr-cli`: desktop command-line scanner that reads PNG/JPG images with JVM `ImageIO` and calls `AndroidOmrEngine`.
- `app`: Android app, CameraX integration, Compose UI, storage, sharing, and legacy scanner.

Keep `omr-core` free of Android, AndroidX, Compose, CameraX, and `android.graphics` dependencies.

## Desktop OMR CLI

Scan the sample WeChat image without compiling the Android app:

```bash
sh gradlew :omr-cli:run --args="微信图片_20260707164730_464_10.png --questions 16 --answers 1:A,2:B,6:C,11:D,16:A --score 2"
```

Useful options:

- `--questions N`: number of questions, default `16`.
- `--answers 1:A,2:B`: answer key. When provided, listed questions receive `--score`; unlisted questions use score `0`.
- `--score N`: score for each listed/default question, default `2`.
- `--debug`: print OMR diagnostics such as anchor detection and projection details.

## Tests

Run the Android-free OMR core tests:

```bash
sh gradlew :omr-core:test
```

Run only the desktop image-regression test:

```bash
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.DesktopWechatImageScanTest
```

Run the full project test suite, including app unit tests and Robolectric tests:

```bash
sh gradlew test
```

Build the Android debug APK:

```bash
sh gradlew assembleDebug
```
