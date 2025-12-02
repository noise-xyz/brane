#!/bin/bash
set -euo pipefail

# Configuration
PROJECT_NAME="brane-website"

# Check for Account ID
if [ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
  echo "Error: CLOUDFLARE_ACCOUNT_ID environment variable is not set."
  echo "Please export it or run: CLOUDFLARE_ACCOUNT_ID=<your-id> ./deploy.sh"
  exit 1
fi

echo "🚀 Starting deployment for $PROJECT_NAME..."

# 1. Build
echo "📦 Building project..."
./cloudflare-build.sh

# 2. Deploy
echo "☁️ Deploying to Cloudflare Pages..."
# We use --commit-dirty=true to allow deploying even if there are uncommitted changes (e.g. artifacts)
npx wrangler pages deploy website/out --project-name "$PROJECT_NAME" --commit-dirty=true

echo "✅ Deployment complete!"
