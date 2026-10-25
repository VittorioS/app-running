#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build-test
javac -d build-test app/src/main/java/it/example/passolibero/RunMath.java \
  app/src/main/java/it/example/passolibero/MovementClassifier.java \
  app/src/main/java/it/example/passolibero/AdaptiveProfile.java tests/CoreAlgorithmTest.java
java -cp build-test CoreAlgorithmTest
