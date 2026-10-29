param(
    [string]$BinaryDirectory = (Join-Path $PSScriptRoot '../bin/Release/net48'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../dist'),
    [string]$Version = '4.1.0'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$package = Join-Path $OutputDirectory ('nodesCatch-modern-v' + $Version + '-windows-x64')
if (Test-Path -LiteralPath $package) { throw "Package directory already exists: $package" }
New-Item -ItemType Directory -Path $package -Force | Out-Null
# Explicit allowlist: never copy runtime directories, nodeConfig.json or logs.
foreach ($file in 'nodesCatchNext.exe', 'Newtonsoft.Json.dll') {
    Copy-Item -LiteralPath (Join-Path $BinaryDirectory $file) -Destination $package
}
foreach ($file in 'LICENSE', 'README.md', 'THIRD_PARTY_NOTICES.md') {
    Copy-Item -LiteralPath (Join-Path $root $file) -Destination $package
}
Copy-Item -LiteralPath (Join-Path $root 'app.ico') -Destination $package
foreach ($file in 'Start.cmd', 'nodeConfig.example.json', 'LICENSE.Newtonsoft.Json.txt') {
    Copy-Item -LiteralPath (Join-Path $root ('release/' + $file)) -Destination $package
}
New-Item -ItemType Directory -Path (Join-Path $package 'scripts') | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'install-runtime.ps1') -Destination (Join-Path $package 'scripts')
$files = @(Get-ChildItem -LiteralPath $package -Recurse -File)
if ($files.Name -contains 'nodeConfig.json' -or $files.Extension -contains '.log') { throw 'Private runtime state detected in package.' }
$example = Get-Content -LiteralPath (Join-Path $package 'nodeConfig.example.json') -Raw | ConvertFrom-Json
if ($example.vmess.Count -ne 0 -or $example.subItem.Count -ne 0) { throw 'Example configuration must be empty.' }
$zip = $package + '.zip'
Compress-Archive -Path (Join-Path $package '*') -DestinationPath $zip -Force
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'SHA256SUMS.txt'), $hash + '  ' + (Split-Path $zip -Leaf) + "`n")
Write-Host "Created $zip"
