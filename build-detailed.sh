#!/bin/bash

echo "========================================="
echo "BHATS Ingestion - Detailed Build Script"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Set error handling
set -e

PROJECT_ROOT="/Users/liqunchen/git/bhats-ingestion"

echo "Working directory: $PROJECT_ROOT"
echo ""

# Function to build a module
build_module() {
    MODULE_NAME=$1
    echo "========================================="
    echo "Building $MODULE_NAME..."
    echo "========================================="

    cd "$PROJECT_ROOT/$MODULE_NAME"

    # Check if pom.xml exists
    if [ ! -f "pom.xml" ]; then
        echo -e "${RED}ERROR: pom.xml not found in $MODULE_NAME${NC}"
        exit 1
    fi

    # Clean and install without tests
    echo "Running: mvn clean install -DskipTests"
    mvn clean install -DskipTests

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $MODULE_NAME built and installed successfully${NC}"

        # Check if JAR was created
        JAR_FILE=$(find target -name "*.jar" -not -name "*-original.jar" 2>/dev/null | head -1)
        if [ -n "$JAR_FILE" ]; then
            JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
            echo "   Created: $JAR_FILE ($JAR_SIZE)"
        fi
    else
        echo -e "${RED}❌ $MODULE_NAME build failed${NC}"
        exit 1
    fi

    echo ""
    cd "$PROJECT_ROOT"
}

# Step 1: Build bhats-common
build_module "bhats-common"

# Step 2: Build bhats-bhwrtam
build_module "bhats-bhwrtam"

# Step 3: Build bhats-bhpubwrt
build_module "bhats-bhpubwrt"

echo "========================================="
echo -e "${GREEN}✅ All modules built successfully!${NC}"
echo "========================================="
echo ""

# Show what was created
echo "Artifacts created:"
echo "  1. bhats-common/target/bhats-common-1.0-SNAPSHOT.jar"
echo "  2. bhats-bhwrtam/target/bhats-bhwrtam-1.0-SNAPSHOT.jar"
echo "  3. bhats-bhpubwrt/target/bhats-bhpubwrt-1.0-SNAPSHOT.jar"
echo ""

# Ask if user wants to run tests
echo "Would you like to run tests now? (y/n)"
read -t 5 -n 1 RESPONSE || RESPONSE="n"
echo ""

if [ "$RESPONSE" = "y" ] || [ "$RESPONSE" = "Y" ]; then
    echo "Running tests..."
    cd "$PROJECT_ROOT"
    mvn test
else
    echo "Skipping tests. You can run them later with: mvn test"
fi

echo ""
echo "Build complete!"

