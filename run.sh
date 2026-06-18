#!/bin/bash
# run.sh — compile & launch the Banking In-Memory Store demo

echo "Compiling..."
mkdir -p out
find src -name "*.java" | xargs javac --release 17 -d out

echo "Starting app..."
java -cp out banking.Main
