#!/bin/bash
set -euo pipefail

# 0. Install Java 21 (Amazon Corretto)
echo "Installing Java 21..."
curl -LO https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz
tar -xzf amazon-corretto-21-x64-linux-jdk.tar.gz
rm amazon-corretto-21-x64-linux-jdk.tar.gz

shopt -s nullglob
jdk_dirs=(amazon-corretto-21*)
if (( ${#jdk_dirs[@]} != 1 )); then
  echo "Error: Expected 1 JDK directory matching 'amazon-corretto-21*', but found ${#jdk_dirs[@]}." >&2
  exit 1
fi
export JAVA_HOME="$PWD/${jdk_dirs[0]}"
export PATH=$JAVA_HOME/bin:$PATH
java -version

# 1. Generate Javadocs
echo "Generating Javadocs..."
./gradlew allJavadoc

# 2. Prepare destination
mkdir -p website/public/javadoc

# 3. Copy Javadocs
echo "Copying Javadocs..."
cp -r build/docs/javadoc/* website/public/javadoc/

# 4. Build Next.js
echo "Building Website..."
cd website
npm install
npm run build
