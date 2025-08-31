#!/bin/bash

# Build script for native executable
set -e

echo "🔨 Building Forge CLI Native Executable"
echo "========================================"

# Check if GraalVM is available
if ! command -v native-image &> /dev/null; then
    echo "❌ GraalVM native-image not found!"
    echo ""
    echo "To build native executable, you need GraalVM with native-image:"
    echo ""
    echo "1. Install GraalVM:"
    echo "   - Download from: https://github.com/graalvm/graalvm-ce-builds/releases"
    echo "   - Or use SDKMAN: sdk install java 21.0.1-graalce"
    echo "   - Or use mise: mise install java graalvm-21"
    echo ""
    echo "2. Install native-image component:"
    echo "   gu install native-image"
    echo ""
    echo "3. Set JAVA_HOME to GraalVM installation"
    echo ""
    exit 1
fi

echo "✅ GraalVM native-image found"
echo "Java version: $(java -version 2>&1 | head -1)"
echo ""

# Clean and compile
echo "🧹 Cleaning previous build..."
mvn clean

echo "📦 Building JAR with dependencies..."
mvn package -DskipTests

echo "🚀 Building native executable..."
mvn package -Pnative -DskipTests

if [ -f "target/forge" ]; then
    echo ""
    echo "✅ Native executable built successfully!"
    echo "📍 Location: $(pwd)/target/forge"
    echo ""
    echo "Test the executable:"
    echo "  ./target/forge --help"
    echo ""
    
    # Test the executable
    echo "🧪 Testing executable..."
    ./target/forge --help
else
    echo "❌ Native executable not found at target/forge"
    exit 1
fi