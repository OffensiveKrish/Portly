#!/usr/bin/env bash
# Portly — Production Release Keystore Generation Script (Bash)
# Generates a high-security RSA 4096-bit permanent production signing identity.

set -euo pipefail

KEYSTORE_PATH="${1:-portly-production-release.keystore}"
ALIAS="${2:-portly-release}"
VALIDITY_DAYS=10950 # 30 years

echo "=== Portly Production Keystore Generator ==="
read -rsp "Enter Keystore & Key Password (min 12 chars): " PASSWORD
echo ""

if [ ${#PASSWORD} -lt 12 ]; then
    echo "Error: Password must be at least 12 characters." >&2
    exit 1
fi

DNAME="CN=Portly Production Release, OU=Mobile, O=Portly Project, C=US"

echo "Generating RSA 4096-bit PKCS12 Keystore..."

keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -sigalg SHA256withRSA \
    -validity "$VALIDITY_DAYS" \
    -dname "$DNAME" \
    -storetype PKCS12 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD"

echo "Keystore created at: $KEYSTORE_PATH"
echo "=== Certificate SHA-256 Fingerprint ==="
keytool -list -v -keystore "$KEYSTORE_PATH" -storepass "$PASSWORD" -alias "$ALIAS" | grep "SHA256:"

# Export Base64
base64 -w 0 "$KEYSTORE_PATH" > "${KEYSTORE_PATH}.base64.txt" 2>/dev/null || base64 "$KEYSTORE_PATH" > "${KEYSTORE_PATH}.base64.txt"
echo "Base64 encoded keystore saved to: ${KEYSTORE_PATH}.base64.txt"
echo "Set GitHub Action secrets: ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD"
