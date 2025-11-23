#!/bin/bash

echo "========================================="
echo "Building BHATS Ingestion - Multi-Module"
echo "========================================="
echo ""

# Build order matters!
# 1. bhats-common (no dependencies)
# 2. bhats-bhwrtam (depends on common)
# 3. bhats-bhpubwrt (depends on common + bhwrtam for tests)

echo "Step 1: Building bhats-common..."
cd bhats-common
mvn clean install -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ Failed to build bhats-common"
    exit 1
fi
echo "✅ bhats-common built successfully"
echo ""

echo "Step 2: Building bhats-bhwrtam..."
cd ../bhats-bhwrtam
mvn clean install -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ Failed to build bhats-bhwrtam"
    exit 1
fi
echo "✅ bhats-bhwrtam built successfully"
echo ""

echo "Step 3: Building bhats-bhpubwrt..."
cd ../bhats-bhpubwrt
mvn clean install -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ Failed to build bhats-bhpubwrt"
    exit 1
fi
echo "✅ bhats-bhpubwrt built successfully"
echo ""

echo "========================================="
echo "✅ All modules built successfully!"
echo "========================================="
echo ""
echo "Now running tests..."
cd ..
mvn test

exit 0

