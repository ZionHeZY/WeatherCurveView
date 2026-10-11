#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPOSITORY="$(cd "${1:-$ROOT/build/repository}" && pwd)"
VERSION="${2:-${VERSION:-0.2.0-SNAPSHOT}}"
MODE="${3:-}"
if [[ -z "$VERSION" || ( -n "$MODE" && "$MODE" != --connected ) ]]; then
    printf 'Usage: %s [local-maven-repository] [version] [--connected]\n' "$0" >&2
    exit 2
fi
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp "$ROOT/verification/consumer/"*.gradle.kts "$ROOT/verification/consumer/gradle.properties" "$WORK/"
cp -R "$ROOT/verification/consumer/src" "$WORK/"
GRADLE_ARGS=(--project-dir "$WORK" -PcurveRepository="$REPOSITORY" -PcurveVersion="$VERSION")
"$ROOT/gradlew" "${GRADLE_ARGS[@]}" assembleDebug assembleDebugAndroidTest
mkdir -p "$ROOT/build/consumer"
cp "$WORK/build/outputs/apk/debug/curve-consumer-debug.apk" "$ROOT/build/consumer/consumer-debug.apk"
cp "$WORK/build/outputs/apk/androidTest/debug/curve-consumer-debug-androidTest.apk" "$ROOT/build/consumer/consumer-debug-androidTest.apk"
if [[ "$MODE" == --connected ]]; then
    "$ROOT/gradlew" "${GRADLE_ARGS[@]}" connectedDebugAndroidTest
    cp -R "$WORK/build/reports/androidTests" "$ROOT/build/consumer/"
    cp -R "$WORK/build/outputs/androidTest-results" "$ROOT/build/consumer/"
fi
printf 'PASS: independent Kotlin/Java consumer compiled com.github.ZionHeZY.WeatherCurveView:curve-view:%s from %s\n' "$VERSION" "$REPOSITORY"
