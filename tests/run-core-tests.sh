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
javac -cp build-test -d build-test tests/SessionTabStatsTest.java
java -cp build-test SessionTabStatsTest
