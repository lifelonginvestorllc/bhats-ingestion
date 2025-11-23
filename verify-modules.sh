#!/bin/bash

# Module Build Verification Script
# This script verifies that all 3 modules build successfully

echo "========================================="
echo "BHATS Ingestion - Module Build Verification"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: pom.xml not found. Please run this script from the project root.${NC}"
    exit 1
fi

echo "Step 1: Verifying module structure..."
echo "--------------------------------------"

# Check for modules
modules=("bhats-common" "bhats-bhpubwrt" "bhats-bhwrtam")
all_modules_exist=true

for module in "${modules[@]}"; do
    if [ -d "$module" ]; then
        print_status 0 "Module $module exists"
    else
        print_status 1 "Module $module NOT found"
        all_modules_exist=false
    fi
done

if [ "$all_modules_exist" = false ]; then
    echo -e "${RED}Some modules are missing!${NC}"
    exit 1
fi

echo ""
echo "Step 2: Verifying POM files..."
echo "--------------------------------------"

# Check POMs
poms=("pom.xml" "bhats-common/pom.xml" "bhats-bhpubwrt/pom.xml" "bhats-bhwrtam/pom.xml")
all_poms_exist=true

for pom in "${poms[@]}"; do
    if [ -f "$pom" ]; then
        print_status 0 "POM $pom exists"
    else
        print_status 1 "POM $pom NOT found"
        all_poms_exist=false
    fi
done

if [ "$all_poms_exist" = false ]; then
    echo -e "${RED}Some POM files are missing!${NC}"
    exit 1
fi

echo ""
echo "Step 3: Verifying source files..."
echo "--------------------------------------"

# Check key source files
check_file() {
    if [ -f "$1" ]; then
        print_status 0 "$1"
        return 0
    else
        print_status 1 "$1"
        return 1
    fi
}

files_ok=true

# Common module files
check_file "bhats-common/src/main/java/com/example/payload/common/TSValues.java" || files_ok=false
check_file "bhats-common/src/main/java/com/example/payload/common/PayloadStatus.java" || files_ok=false
check_file "bhats-common/src/main/java/com/example/payload/common/StatusPublisher.java" || files_ok=false

# bhpubwrt module files
check_file "bhats-bhpubwrt/src/main/java/com/example/payload/BhpubwrtApplication.java" || files_ok=false
check_file "bhats-bhpubwrt/src/main/java/com/example/payload/bhpubwrt/BhpubwrtProducer.java" || files_ok=false

# bhwrtam module files
check_file "bhats-bhwrtam/src/main/java/com/example/payload/BhwrtamApplication.java" || files_ok=false
check_file "bhats-bhwrtam/src/main/java/com/example/payload/bhwrtam/KafkaPayloadProcessor.java" || files_ok=false

if [ "$files_ok" = false ]; then
    echo -e "${RED}Some source files are missing!${NC}"
    exit 1
fi

echo ""
echo "Step 4: Building all modules (without tests)..."
echo "--------------------------------------"
echo -e "${YELLOW}This may take a minute...${NC}"
echo ""

# Build all modules
mvn clean install -DskipTests > /tmp/bhats-build.log 2>&1
BUILD_RESULT=$?

if [ $BUILD_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ All modules built successfully!${NC}"
    echo ""
    echo "Build artifacts created:"
    echo "  - bhats-common/target/bhats-common-1.0-SNAPSHOT.jar"
    echo "  - bhats-bhpubwrt/target/bhats-bhpubwrt-1.0-SNAPSHOT.jar"
    echo "  - bhats-bhwrtam/target/bhats-bhwrtam-1.0-SNAPSHOT.jar"
else
    echo -e "${RED}✗ Build failed!${NC}"
    echo ""
    echo "Last 30 lines of build log:"
    tail -30 /tmp/bhats-build.log
    exit 1
fi

echo ""
echo "Step 5: Verifying artifacts..."
echo "--------------------------------------"

artifacts=(
    "bhats-common/target/bhats-common-1.0-SNAPSHOT.jar"
    "bhats-bhpubwrt/target/bhats-bhpubwrt-1.0-SNAPSHOT.jar"
    "bhats-bhwrtam/target/bhats-bhwrtam-1.0-SNAPSHOT.jar"
)

all_artifacts_ok=true
for artifact in "${artifacts[@]}"; do
    if [ -f "$artifact" ]; then
        size=$(du -h "$artifact" | cut -f1)
        print_status 0 "$artifact ($size)"
    else
        print_status 1 "$artifact NOT found"
        all_artifacts_ok=false
    fi
done

if [ "$all_artifacts_ok" = false ]; then
    echo -e "${RED}Some artifacts were not created!${NC}"
    exit 1
fi

echo ""
echo "========================================="
echo -e "${GREEN}✓ All verifications passed!${NC}"
echo "========================================="
echo ""
echo "Summary:"
echo "  - 3 modules created and configured"
echo "  - All source files in place"
echo "  - All modules build successfully"
echo "  - All artifacts created"
echo ""
echo "Next steps:"
echo "  - Run tests: mvn test"
echo "  - Run bhpubwrt: cd bhats-bhpubwrt && mvn spring-boot:run"
echo "  - Run bhwrtam: cd bhats-bhwrtam && mvn spring-boot:run"
echo ""
echo "For more information, see README-MODULES.md"
echo ""

exit 0

