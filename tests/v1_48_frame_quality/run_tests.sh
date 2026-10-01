#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
K=app/src/main/java/com/rui/pvpgo/live
T=tests/v1_48_frame_quality
kotlinc "$K/CaptureFrameQualityPolicy.kt" "$T/CaptureFrameQualityPolicyTest.kt" -include-runtime -d "$OUT/quality.jar"
java -jar "$OUT/quality.jar"
kotlinc "$K/CaptureFrameQualityPolicy.kt" "$K/LiveSemanticAdmissionPolicy.kt" "$T/LiveSemanticAdmissionPolicyTest.kt" -include-runtime -d "$OUT/admission.jar"
java -jar "$OUT/admission.jar"
kotlinc "$K/CaptureFrameQualityPolicy.kt" "$K/LiveSemanticAdmissionPolicy.kt" "$K/LiveCaptureContracts.kt" "$K/LiveBattleCoordinator.kt" "$T/stubs/LiveEngineStubs.kt" "$T/LiveCoordinatorGateTest.kt" -include-runtime -d "$OUT/coordinator.jar"
java -jar "$OUT/coordinator.jar"
kotlinc "$K/CompanionQuickLogPolicy.kt" tests/v1_47_companion_policy/CompanionQuickLogPolicyTest.kt -include-runtime -d "$OUT/manual.jar"
java -jar "$OUT/manual.jar"
python "$T/verify_wiring.py"
