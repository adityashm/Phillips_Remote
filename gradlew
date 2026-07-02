#!/usr/bin/env sh
# Gradle start up script for POSIX
GRADLE_OPTS="${GRADLE_OPTS:-""} -Xdock:name=Gradle"
exec "$JAVACMD" "${JVM_OPTS[@]}" \
  -classpath "$CLASSPATH" \
  org.gradle.launcher.GradleMain \
  "$@"
