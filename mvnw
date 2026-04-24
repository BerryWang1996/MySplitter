#!/bin/sh

set -e

MVNW_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$MVNW_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
MVNW_USER_HOME="$MVNW_DIR/.mvn/maven-user-home"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Maven wrapper jar not found: $WRAPPER_JAR" >&2
  exit 1
fi

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE=java
fi

exec "$JAVA_EXE" "-Dmaven.multiModuleProjectDirectory=$MVNW_DIR" "-Dmaven.user.home=$MVNW_USER_HOME" -classpath "$WRAPPER_JAR" $MAVEN_OPTS $JAVA_OPTS "$WRAPPER_LAUNCHER" "$@"
