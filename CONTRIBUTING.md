# Contributing

Use JDK 17 and Android SDK 35. Set `ANDROID_HOME` or create an untracked `local.properties` with your SDK path. Run commands from the repository root using the checked-in Gradle wrapper. For instrumentation, connect one API 24+ device/emulator; the configured CI matrix uses API 26 and 35.

## Local checks

1. Build the sample and test APK, lint both modules, and publish a local verification artifact:

   ```sh
   ./gradlew :sample:assembleDebug :sample:assembleDebugAndroidTest \
     :sample:lintDebug :curve-view:lintRelease \
     :curve-view:publishReleasePublicationToVerificationRepository \
     -Pversion=0.2.0-SNAPSHOT
   ```

2. Run sample instrumentation with animation disabled and enabled. Connect exactly one device; this Bash block restores its original animator setting on exit:

   ```sh
   (
     original_scale="$(adb shell settings get global animator_duration_scale | tr -d '\r')"
     restore_scale() {
       if [ "$original_scale" = null ]; then
         adb shell settings delete global animator_duration_scale
       else
         adb shell settings put global animator_duration_scale "$original_scale"
       fi
     }
     trap restore_scale EXIT
     set -e
     adb shell settings put global animator_duration_scale 0
     ./gradlew :sample:connectedDebugAndroidTest
     adb shell settings put global animator_duration_scale 1
     ./gradlew :sample:connectedDebugAndroidTest
   )
   ```

3. Verify the published artifact from an isolated consumer:

   ```sh
   ./scripts/verify-consumer.sh "$PWD/build/repository" 0.2.0-SNAPSHOT --connected
   ```

   Omit `--connected` for compile-only verification without a device. The script copies the checked-in `verification/consumer` project to a temporary directory and consumes the AAR/POM from the given Maven repository; it does not depend on the SDK source module. It compiles Java/Kotlin callers and, with a device, checks XML styling and the rendered marker. Generated consumer APKs/reports are copied under `build/consumer`; sample reports are under `sample/build/reports`.

These are verification instructions, not a claim that a particular revision or remote CI run passed. The workflow is in [.github/workflows/android.yml](.github/workflows/android.yml).

CI explicitly requests `platform-tools` from `setup-android`: its default also requests the retired `tools` package, which fails SDK setup. The compile SDK/build tools and API 26/35 emulator images are installed by their dedicated steps.

## Changes and regression tests

Keep changes focused. Preserve the public `dev.hezy.curveview.CustomCurveView` package/class, Java overloads and setters, Kotlin APIs, XML attributes, units and callback semantics unless an intentional compatibility change is discussed and documented. Check both the sample and independent consumer when changing public APIs, resources or publication metadata. Update [README.md](README.md) for user-visible contracts and unreleased migration changes.

Reuse [the existing sample instrumentation tests](sample/src/androidTest/java/dev/hezy/customcurveview/CurveBehaviorTest.kt) for SDK or sample behavior regressions. Use [verification/consumer](verification/consumer) for published-artifact integration regressions. Add the smallest assertion that fails for the actual defect: exercise selection, input, state, XML inflation or rendered output rather than testing constants or duplicating implementation calculations. Do not introduce a test framework or dependency for a problem the existing setup can cover. Document device/API and commands exercised in the pull request; do not report unrun checks as passing.

## Bug reports and pull requests

Open a GitHub Issue with a minimal reproducible dataset and integration snippet, expected versus actual behavior, device/model and Android API, consumed revision/version, and relevant logs or a screenshot. Include labels/icons, style/range settings, RTL/font scale and animation settings when they affect the defect. Remove private data and credentials.

For a pull request, describe the behavior change, compatibility impact and concrete verification results. Keep unrelated formatting, toolchain upgrades and generated build output out of the diff. Contributions are under the repository's [MIT License](LICENSE).

## Source map

- [curve-view](curve-view): SDK; `CustomCurveView.kt` implements drawing/selection and `res/values/attrs.xml` defines XML options.
- [sample](sample): runnable showcase and behavior instrumentation tests.
- [verification/consumer](verification/consumer) and [scripts/verify-consumer.sh](scripts/verify-consumer.sh): standalone published-AAR integration fixture and runner, not a root Gradle submodule.
- [.github/workflows/android.yml](.github/workflows/android.yml): configured build/lint and emulator checks.
