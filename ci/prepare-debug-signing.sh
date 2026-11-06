#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${PASSOLIBERO_SIGNING_PASSPHRASE:-}" ]]; then
  echo "::warning::Firma debug persistente non configurata: imposta il repository secret PASSOLIBERO_SIGNING_PASSPHRASE."
  echo "PASSOLIBERO_SIGNED=false" >> "$GITHUB_ENV"
  exit 0
fi

encrypted=".github/signing/debug-keystore.p12.enc"
fingerprint=".github/signing/debug-cert.sha256"
mkdir -p .github/signing
keystore="$RUNNER_TEMP/passolibero-debug.p12"

if [[ -f "$encrypted" ]]; then
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 250000 -in "$encrypted" -out "$keystore" -pass env:PASSOLIBERO_SIGNING_PASSPHRASE
elif [[ -f "$fingerprint" ]]; then
  echo "::error::Fingerprint presente senza chiave cifrata. Non generare una chiave diversa."
  exit 1
else
  keytool -genkeypair -noprompt -keystore "$keystore" -storetype PKCS12 -storepass android -keypass android -alias passolibero-debug -keyalg RSA -keysize 3072 -validity 36500 -dname "CN=PassoLibero Debug, OU=Development, O=PassoLibero, C=IT"
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 250000 -in "$keystore" -out "$encrypted" -pass env:PASSOLIBERO_SIGNING_PASSPHRASE
fi

chmod 600 "$keystore"
certificate="$(keytool -exportcert -keystore "$keystore" -storepass android -alias passolibero-debug | sha256sum | cut -d' ' -f1)"
if [[ -f "$fingerprint" ]]; then
  if [[ "$(cat "$fingerprint")" != "$certificate" ]]; then
    echo "::error::Certificato diverso dalla firma persistente."
    exit 1
  fi
else
  printf '%s\n' "$certificate" > "$fingerprint"
  git config user.name "github-actions[bot]"
  git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
  git add "$encrypted" "$fingerprint"
  git commit -m "Initialize encrypted stable debug signing certificate"
  git push origin "HEAD:$GITHUB_REF_NAME"
fi

echo "PASSOLIBERO_DEBUG_KEYSTORE=$keystore" >> "$GITHUB_ENV"
echo "PASSOLIBERO_SIGNED=true" >> "$GITHUB_ENV"
