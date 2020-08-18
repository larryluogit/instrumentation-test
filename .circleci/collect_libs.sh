#!/usr/bin/env bash

# Save all important libs into (project-root)/libs
# This folder will be saved by circleci and available after test runs.

set -x
set -e

LIBS_DIR=./libs/
mkdir -p $LIBS_DIR >/dev/null 2>&1

cp workspace/javaagent/build/libs/*.jar $LIBS_DIR/
cp workspace/javaagent-exporters/*/build/libs/*.jar $LIBS_DIR/
