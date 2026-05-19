#!/bin/bash

set -e

# Required parameters
OWNER="phylaxsystems"
REPO="credible-layer-besu-plugin"
GROUP_ID="net.phylax.credible"
ARTIFACT_ID="credible-layer-besu-plugin"
VERSION="0.2.3-patch-besu26.4.0"

OUTPUT_LOC="./linea-besu/besu/plugins/$ARTIFACT_ID-$VERSION.jar"
AUTH_TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"

mkdir -p "$(dirname "$OUTPUT_LOC")"

if [ -z "$AUTH_TOKEN" ]; then
    echo "❌ Missing GitHub token. Set GITHUB_TOKEN or GH_TOKEN."
    exit 1
fi

# Download using curl
response=$(curl -s -w "%{http_code}" -L -H "Authorization: token $AUTH_TOKEN" \
     -H "Accept: application/octet-stream" \
     "https://maven.pkg.github.com/$OWNER/$REPO/$GROUP_ID/$ARTIFACT_ID/$VERSION/$ARTIFACT_ID-$VERSION.jar" \
     -o "$OUTPUT_LOC")

http_code=${response: -3}

if [ "$http_code" -eq 200 ]; then
    echo "✅ Download successful!"
else
    echo "❌ Download failed with HTTP code: $http_code"
    echo "💡 Check:"
    echo "   - Token has 'read:packages' scope"
    echo "   - You have access to the repository"
    echo "   - Package coordinates are correct"
    exit 1
fi
