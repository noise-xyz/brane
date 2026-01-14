#!/bin/bash
set -euo pipefail

# Ensure we are in the project root
cd "$(dirname "$0")/.."

# 0. Install Java 21 (Amazon Corretto) - Only on Cloudflare Pages
if [ "${CF_PAGES:-0}" -eq 1 ]; then
  echo "Detected Cloudflare Pages environment. Installing Java 21..."
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
else
  echo "Running locally. Assuming Java 21 is installed."
fi

java -version

# 1. Generate Javadocs
echo "Generating Javadocs..."
./gradlew allJavadoc

# 2. Prepare destination
mkdir -p website/docs/public/javadoc

# 3. Copy Javadocs
echo "Copying Javadocs..."
cp -r build/docs/javadoc/* website/docs/public/javadoc/

# 4. Build vocs docs
echo "Building Website..."
cd website
npm install
npm run build
