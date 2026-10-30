#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build-test
javac -d build-test app/src/main/java/it/example/passolibero/RunMath.java \
  app/src/main/java/it/example/passolibero/DistanceAccumulator.java \
  app/src/main/java/it/example/passolibero/BatteryPolicy.java \
  app/src/main/java/it/example/passolibero/GpsQualityPolicy.java \
  app/src/main/java/it/example/passolibero/MovementClassifier.java \
  app/src/main/java/it/example/passolibero/MotionEvidencePolicy.java \
  app/src/main/java/it/example/passolibero/RunStats.java \
  app/src/main/java/it/example/passolibero/RouteSegmenter.java \
  app/src/main/java/it/example/passolibero/AdaptiveProfile.java tests/CoreAlgorithmTest.java
java -cp build-test CoreAlgorithmTest
