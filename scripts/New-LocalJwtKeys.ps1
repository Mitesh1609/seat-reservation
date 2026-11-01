# Run once from the project root in PowerShell 7. Never replace keys at each startup.
$ErrorActionPreference = 'Stop'
$keyDirectory = Join-Path $PSScriptRoot '..\.local\jwt'
$privateFile = Join-Path $keyDirectory 'private.pem'
$publicFile = Join-Path $keyDirectory 'public.pem'
if ((Test-Path -LiteralPath $privateFile) -or (Test-Path -LiteralPath $publicFile)) {
    throw 'JWT keys already exist. Existing keys will not be overwritten.'
}
New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
$rsa = [System.Security.Cryptography.RSA]::Create(3072)
try {
    [System.IO.File]::WriteAllText($privateFile, $rsa.ExportPkcs8PrivateKeyPem())
    [System.IO.File]::WriteAllText($publicFile, $rsa.ExportSubjectPublicKeyInfoPem())
} finally {
    $rsa.Dispose()
}
Write-Output 'Set these environment variables in your application run configuration:'
Write-Output "JWT_PRIVATE_KEY=$([System.Uri]::new([System.IO.Path]::GetFullPath($privateFile)).AbsoluteUri)"
Write-Output "JWT_PUBLIC_KEY=$([System.Uri]::new([System.IO.Path]::GetFullPath($publicFile)).AbsoluteUri)"
