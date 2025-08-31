#!/bin/bash

# Test the daemon + JSON-RPC approach

echo "Starting forge daemon..."

# Start daemon in background
java -jar forge-daemon/target/forge-daemon.jar &
DAEMON_PID=$!

# Give daemon time to start
sleep 1

# Test ping
echo '{"jsonrpc":"2.0","id":1,"method":"ping"}' | nc localhost 8080

# Test show projects (assuming we're in forge-demo)
echo '{"jsonrpc":"2.0","id":2,"method":"show/projects","params":{"workspaceRoot":"/Users/jbadeau/git/forge-demo"}}' | nc localhost 8080

# Kill daemon
kill $DAEMON_PID

echo "Test complete"