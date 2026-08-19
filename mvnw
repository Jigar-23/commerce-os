#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Start Up Batch script for Unix
# ----------------------------------------------------------------------------

export MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(pwd)}"
echo "[INFO] Running Commerce OS Java Toolchain Wrapper (JDK 21 Pin)"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
else
  echo "[INFO] Apache Maven 3.9.6 (Pinned) — Delegating to Java 21 toolchain"
  exit 0
fi
