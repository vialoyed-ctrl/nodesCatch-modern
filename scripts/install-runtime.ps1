param([string]$Destination = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Destination = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('nodesCatch-runtime-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
try {
    $core = Join-Path $Destination 'mihomo-nodes.exe'
    if (!(Test-Path -LiteralPath $core)) {
        if (![Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -eq 'ARM64') {
            throw 'This package requires Windows x64. Download a compatible Mihomo core manually.'
        }
        $archive = Join-Path $scratch 'mihomo.zip'
        Write-Host 'Downloading Mihomo v1.19.33 from the official GitHub release...'
        Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/MetaCubeX/mihomo/releases/download/v1.19.33/mihomo-windows-amd64-compatible-v1.19.33.zip' -OutFile $archive
        $expected = '7AAF636A4F2172DCD8EC34AC4F52797B059195E5AFD6409508C963E545F8864D'
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'Mihomo archive SHA256 mismatch.' }
        Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $scratch 'mihomo')
        $exe = @(Get-ChildItem (Join-Path $scratch 'mihomo') -Filter '*.exe' -Recurse)
        if ($exe.Count -ne 1) { throw 'Unexpected Mihomo archive layout.' }
        Copy-Item -LiteralPath $exe[0].FullName -Destination $core
    }
    # Needed by legacy converters and formats; modern native testing works without it.
    $converter = Join-Path $Destination 'subconverter'
    if (!(Test-Path -LiteralPath (Join-Path $converter 'subconverter.exe'))) {
        try {
            $archive = Join-Path $scratch 'upstream.zip'
            Write-Host 'Downloading the optional converter from the upstream nodesCatchNext release...'
            Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/AHackerX/nodesCatchNext/releases/download/V4.0.0/nodesCatchNext-v4.0.zip' -OutFile $archive
            $expected = '8ED285B37818A21090D329B70A7D5956C3E331A86DB5F625476325106ADA685B'
            if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'Upstream archive SHA256 mismatch.' }
            Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $scratch 'upstream')
            Copy-Item -LiteralPath (Join-Path $scratch 'upstream/subconverter') -Destination $converter -Recurse -Force
            $pref = Join-Path $converter 'pref.toml'
            $content = [IO.File]::ReadAllText($pref).Replace('listen = "0.0.0.0"', 'listen = "127.0.0.1"')
            [IO.File]::WriteAllText($pref, $content)
        } catch { Write-Warning ('Optional converter not installed: ' + $_.Exception.Message) }
    }
    $configDir = Join-Path $Destination 'config'
    New-Item -ItemType Directory -Path $configDir -Force | Out-Null
    $configPath = Join-Path $configDir 'config.yaml'
    if (!(Test-Path -LiteralPath $configPath)) {
        [IO.File]::WriteAllText($configPath, "mixed-port: 40000`nallow-lan: false`nbind-address: 127.0.0.1`n")
    }
    $userConfig = Join-Path $Destination 'nodeConfig.json'
    if (!(Test-Path -LiteralPath $userConfig)) {
        Copy-Item -LiteralPath (Join-Path $Destination 'nodeConfig.example.json') -Destination $userConfig
    }
    Write-Host 'Runtime ready. Existing nodes and settings were preserved.'
} finally {
    # Delete only the unique temporary directory created by this script.
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $resolved = [IO.Path]::GetFullPath($scratch)
    if ($resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf).StartsWith('nodesCatch-runtime-')) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
