$ErrorActionPreference = 'Stop'
$dir = Join-Path $PSScriptRoot '../app/libs'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$path = Join-Path $dir 'libmihomo-android-v0.3.12.aar'
if (!(Test-Path -LiteralPath $path)) {
    Invoke-WebRequest 'https://github.com/oviron/libmihomo-android/releases/download/v0.3.12/libmihomo-android-v0.3.12.aar' -OutFile $path
}
$expected = 'c5f857d00319a7690b1dbf083387f5617454ed5a21eb87488644de2c18b7e42f'
if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Native library SHA256 mismatch' }
Write-Host 'Verified libmihomo-android 0.3.12 / Mihomo 1.19.33'
