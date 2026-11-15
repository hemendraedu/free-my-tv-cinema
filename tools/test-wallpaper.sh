#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/wallpaper-tests
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -encoding UTF-8 -d out/wallpaper-tests app/src/io/freemytv/WallpaperFifo.java tests/WallpaperFifoTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp out/wallpaper-tests io.freemytv.WallpaperFifoTest
