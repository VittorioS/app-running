#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build-test
javac -d build-test app/src/main/java/it/example/passolibero/RunMath.java \
  app/src/main/java/it/example/passolibero/DistanceAccumulator.java \
  app/src/main/java/it/example/passolibero/BatteryPolicy.java \
  app/src/main/java/it/example/passolibero/GpsQualityPolicy.java \
  app/src/main/java/it/example/passolibero/MovementClassifier.java \
  app/src/main/java/it/example/passolibero/ActivityEvidence.java \
  app/src/main/java/it/example/passolibero/StepCounterEvidence.java \
  app/src/main/java/it/example/passolibero/MotionEvidencePolicy.java \
  app/src/main/java/it/example/passolibero/MotionTimeline.java \
  app/src/main/java/it/example/passolibero/InertialGaitDetector.java \
  app/src/main/java/it/example/passolibero/RunStats.java \
  app/src/main/java/it/example/passolibero/SessionTabStats.java \
  app/src/main/java/it/example/passolibero/RouteSegmenter.java \
  app/src/main/java/it/example/passolibero/AdaptiveProfile.java tests/CoreAlgorithmTest.java tests/MotionRegressionTest.java
java -cp build-test CoreAlgorithmTest
java -cp build-test MotionRegressionTest

# Timestamped, synthetic GPS/step traces: no device or real sensor required.
javac -cp build-test -d build-test tests/TrackingReplayTest.java
java -cp build-test TrackingReplayTest

# Pure, reference-equivalent recovery of the monotonic clock and unknown gaps.
javac -d build-test app/src/main/java/it/example/passolibero/domain/SessionRecoveryPolicy.java \
  tests/SessionRecoveryPolicyTest.java
java -cp build-test SessionRecoveryPolicyTest

# Pure foreground-service permission/type decisions, including Android 11 fallback.
javac -d build-test app/src/main/java/it/example/passolibero/domain/ForegroundStartPolicy.java \
  tests/ForegroundStartPolicyTest.java
java -cp build-test ForegroundStartPolicyTest

# Pure GNSS path throttling and distance gates with reference-equivalence probes.
javac -d build-test app/src/main/java/it/example/passolibero/domain/GpsFixPolicy.java \
  tests/GpsFixPolicyTest.java
java -cp build-test GpsFixPolicyTest
javac -cp build-test -d build-test tests/SessionTabStatsTest.java
java -cp build-test SessionTabStatsTest

javac -d build-test app/src/main/java/it/example/passolibero/WeatherRules.java tests/WeatherRulesTest.java
java -cp build-test WeatherRulesTest

javac -d build-test app/src/main/java/it/example/passolibero/WeatherSchedule.java tests/WeatherScheduleTest.java
java -cp build-test WeatherScheduleTest

javac -d build-test app/src/main/java/it/example/passolibero/UpdatePolicy.java tests/UpdatePolicyTest.java
java -cp build-test UpdatePolicyTest

javac -d build-test app/src/main/java/it/example/passolibero/UpdateMetadataReader.java tests/UpdateMetadataReaderTest.java
java -cp build-test UpdateMetadataReaderTest

javac -d build-test app/src/main/java/it/example/passolibero/RunPeakSpeedPolicy.java tests/RunPeakSpeedPolicyTest.java
java -cp build-test RunPeakSpeedPolicyTest

javac -d build-test app/src/main/java/it/example/passolibero/WeatherLocations.java tests/WeatherLocationsTest.java
java -cp build-test WeatherLocationsTest

javac -d build-test app/src/main/java/it/example/passolibero/SprintTrial.java tests/SprintTrialTest.java
java -cp build-test SprintTrialTest

javac -d build-test app/src/main/java/it/example/passolibero/SprintCountdownVoicePlan.java tests/SprintCountdownVoicePlanTest.java
java -cp build-test SprintCountdownVoicePlanTest

javac -cp build-test -d build-test app/src/main/java/it/example/passolibero/SessionMotionVoiceEvents.java tests/SessionMotionVoiceEventsTest.java
java -cp build-test SessionMotionVoiceEventsTest

javac -cp build-test -d build-test app/src/main/java/it/example/passolibero/SprintProgressVoicePlan.java tests/SprintProgressVoicePlanTest.java
java -cp build-test SprintProgressVoicePlanTest

javac -cp build-test -d build-test app/src/main/java/it/example/passolibero/RouteSections.java tests/RouteSectionsTest.java
java -cp build-test RouteSectionsTest
javac -cp build-test -d build-test app/src/main/java/it/example/passolibero/RouteSectionSelection.java tests/RouteSectionSelectionTest.java
java -cp build-test RouteSectionSelectionTest
