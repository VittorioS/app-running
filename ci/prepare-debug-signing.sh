#!/usr/bin/env bash
# Only the trusted main-only release workflow may call this script.
set -euo pipefail
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${GITHUB_ENV:?GITHUB_ENV is required}"

if [[ -z "${PASSOLIBERO_SIGNING_PASSPHRASE:-}" ]]; then
  echo "::error::Missing PASSOLIBERO_SIGNING_PASSPHRASE in protected apk-release environment."
  exit 1
fi
encrypted=".github/signing/debug-keystore.p12.enc"
fingerprint=".github/signing/debug-cert.sha256"
if [[ ! -s "$encrypted" || ! -s "$fingerprint" ]]; then
  echo "::error::Encrypted signing key and certificate fingerprint are required."
  exit 1
fi
keystore="$RUNNER_TEMP/passolibero-debug.p12"
umask 077
openssl enc -d -aes-256-cbc -pbkdf2 -iter 250000 \
  -in "$encrypted" -out "$keystore" -pass env:PASSOLIBERO_SIGNING_PASSPHRASE
actual="$(keytool -exportcert -keystore "$keystore" -storetype PKCS12 \
  -storepass android -alias passolibero-debug | sha256sum | cut -d ' ' -f1)"
expected="$(tr -d '\r\n' < "$fingerprint" | tr '[:upper:]' '[:lower:]')"
if [[ ! "$expected" =~ ^[[:xdigit:]]{64}$ || "$actual" != "$expected" ]]; then
  echo "::error::Signing certificate mismatch. Do not rotate the signing key."
  rm -f "$keystore"
  exit 1
fi
chmod 600 "$keystore"
echo "PASSOLIBERO_DEBUG_KEYSTORE=$keystore" >> "$GITHUB_ENV"
echo "Verified existing signing key without modifying repository files."
