#!/bin/bash
set -e

# 0. Install Java 21 (Amazon Corretto)
echo "Installing Java 21..."
curl -LO https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz
tar -xzf amazon-corretto-21-x64-linux-jdk.tar.gz
rm amazon-corretto-21-x64-linux-jdk.tar.gz
export JAVA_HOME=$(pwd)/$(ls -d amazon-corretto-21*)
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
