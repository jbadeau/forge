#!/bin/bash
# Script to start forge daemon with all plugins loaded

FORGE_DIR="/Users/jbadeau/git/forge-github"
PLUGIN_CLASSPATH="$FORGE_DIR/forge/target/forge.jar"

# Add all plugin and executor JARs to classpath
for jar in "$FORGE_DIR"/plugins/*/target/*-1.0.0-SNAPSHOT.jar "$FORGE_DIR"/executors/*/target/*-1.0.0-SNAPSHOT.jar; do
  if [[ -f "$jar" ]]; then
    PLUGIN_CLASSPATH="$PLUGIN_CLASSPATH:$jar"
  fi
done

# Start daemon with full classpath
java -cp "$PLUGIN_CLASSPATH" com.forge.daemon.ForgeDaemonMainKt