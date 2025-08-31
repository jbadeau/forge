#!/bin/bash

# Test the daemon with JSON-RPC over stdin/stdout

echo "Testing forge daemon with JSON-RPC..."
cd /Users/jbadeau/git/forge-demo

# Test ping request
echo '{"jsonrpc":"2.0","id":1,"method":"ping"}' | java -jar ../forge-github/forge-daemon/target/forge-daemon.jar

echo "---"

# Test show projects
echo '{"jsonrpc":"2.0","id":2,"method":"show/projects","params":{"workspaceRoot":"/Users/jbadeau/git/forge-demo","format":"text"}}' | java -jar ../forge-github/forge-daemon/target/forge-daemon.jar