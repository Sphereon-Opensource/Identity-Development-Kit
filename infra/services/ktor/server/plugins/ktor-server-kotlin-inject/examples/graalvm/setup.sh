#!/bin/bash
# Setup script for GraalVM example
# Copies Gradle wrapper from project root

set -e

echo "Setting up GraalVM example..."

# Get project root (5 levels up from this script)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../../../../.." && pwd )"

echo "Project root: $PROJECT_ROOT"
echo "Example dir: $SCRIPT_DIR"

# Copy Gradle wrapper files
if [ -f "$PROJECT_ROOT/gradlew" ]; then
    cp "$PROJECT_ROOT/gradlew" "$SCRIPT_DIR/"
    chmod +x "$SCRIPT_DIR/gradlew"
    echo "✓ Copied gradlew"
else
    echo "✗ gradlew not found in project root"
    exit 1
fi

if [ -f "$PROJECT_ROOT/gradlew.bat" ]; then
    cp "$PROJECT_ROOT/gradlew.bat" "$SCRIPT_DIR/"
    echo "✓ Copied gradlew.bat"
fi

if [ -d "$PROJECT_ROOT/gradle" ]; then
    cp -r "$PROJECT_ROOT/gradle" "$SCRIPT_DIR/"
    echo "✓ Copied gradle directory"
else
    echo "✗ gradle directory not found in project root"
    exit 1
fi

echo ""
echo "Setup complete! You can now build with Docker:"
echo "  docker build -t graalvm-example ."
