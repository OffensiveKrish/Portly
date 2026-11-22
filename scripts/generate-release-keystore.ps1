# Portly — Production Release Keystore Generation Script (PowerShell)
# Generates a high-security RSA 4096-bit permanent production signing identity.
#
# IMPORTANT:
# 1. Store the generated keystore file securely in offline backups.
# 2. Never commit the keystore or passwords to Git.
# 3. Use the base64 output for GitHub Secret: ANDROID_KEYSTORE_BASE64

param(
    [string]$KeystorePath = "portly-production-release.keystore",
    [string]$Alias = "portly-release",
    [int]$ValidityYears = 30
)

$ValidityDays = $ValidityYears * 365

Write-Host "=== Portly Production Keystore Generator ===" -ForegroundColor Cyan
$Password = Read-Host "Enter Keystore & Key Password (secure passphrase)" -AsSecureString
$BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
$PlainPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)

if ([string]::IsNullOrWhiteSpace($PlainPass) -or $PlainPass.Length -lt 12) {
    Write-Error "Password must be at least 12 characters."
    exit 1
}

$DName = "CN=Portly Production Release, OU=Mobile, O=Portly Project, C=US"

Write-Host "Generating RSA 4096-bit PKCS12 Keystore..." -ForegroundColor Yellow

& keytool -genkeypair `
    -v `
    -keystore $KeystorePath `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -sigalg SHA256withRSA `
    -validity $ValidityDays `
    -dname $DName `
    -storetype PKCS12 `
    -storepass $PlainPass `
    -keypass $PlainPass

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nKeystore generated successfully at: $KeystorePath" -ForegroundColor Green
    
    # Print Certificate Fingerprint
    Write-Host "`n=== Certificate SHA-256 Fingerprint ===" -ForegroundColor Cyan
    & keytool -list -v -keystore $KeystorePath -storepass $PlainPass -alias $Alias | Select-String "SHA256:"
    
    # Generate Base64 for GitHub Actions Secret
    $Bytes = [System.IO.File]::ReadAllBytes($KeystorePath)
    $Base64 = [System.Convert]::ToBase64String($Bytes)
    $Base64File = "$KeystorePath.base64.txt"
    Set-Content -Path $Base64File -Value $Base64
    
    Write-Host "`n=== GitHub Actions Configuration ===" -ForegroundColor Cyan
    Write-Host "Add the following Repository Secrets in GitHub (Settings > Secrets and variables > Actions):"
    Write-Host "  1. ANDROID_KEYSTORE_BASE64   : (Contents of $Base64File)"
    Write-Host "  2. ANDROID_KEYSTORE_PASSWORD : [Your secure passphrase]"
    Write-Host "  3. ANDROID_KEY_ALIAS         : $Alias"
    Write-Host "  4. ANDROID_KEY_PASSWORD      : [Your secure passphrase]"
    Write-Host "`nWARNING: Keep $KeystorePath and $Base64File strictly private! Never commit them." -ForegroundColor Yellow
} else {
    Write-Error "Keystore generation failed."
}
