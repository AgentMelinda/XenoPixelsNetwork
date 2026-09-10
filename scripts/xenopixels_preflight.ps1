[CmdletBinding()]
param(
    [ValidateSet('Check', 'Fix')]
    [string]$Mode = 'Check',
    [Parameter(Position = 0)]
    [string[]]$PackPath = @('.')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$script:Issues = 0
$script:Changes = 0
$script:IsArchive = $false
$script:BackupStamp = Get-Date -Format 'yyyyMMdd-HHmmss'

function Write-Check([string]$Message) { Write-Host "[OK] $Message" -ForegroundColor Green }
function Write-Warn([string]$Message) { Write-Host "[WARN] $Message" -ForegroundColor Yellow; $script:Issues++ }
function Write-Change([string]$Message) { Write-Host "[FIX] $Message" -ForegroundColor Cyan; $script:Changes++ }

function Set-ConfigText([string]$Path, [string]$Content, [string]$Reason) {
    $old = [IO.File]::ReadAllText($Path)
    if ($old -ceq $Content) { return }
    if ($Mode -eq 'Check') {
        Write-Warn "$Reason ($Path)"
        return
    }
    if (-not $script:IsArchive) {
        $backup = "$Path.xenopixels-backup-$script:BackupStamp"
        Copy-Item -LiteralPath $Path -Destination $backup
    }
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
    Write-Change "$Reason ($Path)"
}

function Get-ConfigRoot([string]$Root) {
    if (Test-Path -LiteralPath (Join-Path $Root 'fml.toml')) { return $Root }
    $config = Join-Path $Root 'config'
    if (Test-Path -LiteralPath $config) { return $config }
    return $Root
}

function Remove-ScalableLuxOverride([string]$ConfigRoot) {
    $path = Join-Path $ConfigRoot 'fml.toml'
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Warn "fml.toml was not found under $ConfigRoot"
        return
    }
    $text = [IO.File]::ReadAllText($path)
    $pattern = '(?m)^[\t ]*sable[\t ]*=[\t ]*\[[\t ]*"-scalablelux"[\t ]*\][\t ]*(?:\r?\n|$)'
    $updated = [Text.RegularExpressions.Regex]::Replace($text, $pattern, '')
    if ($updated -ceq $text) {
        Write-Check 'obsolete sable = ["-scalablelux"] override is absent'
    } else {
        Set-ConfigText $path $updated 'removed exact obsolete scalablelux dependency override'
    }
}

function Test-OozaruTree([string]$ConfigRoot, [string]$Tree) {
    $path = Join-Path $ConfigRoot "$Tree/races/saiyan/forms/oozaru.json"
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Warn "missing duplicated DMZ config $Tree/races/saiyan/forms/oozaru.json"
        return
    }
    try {
        $json = [IO.File]::ReadAllText($path) | ConvertFrom-Json
    } catch {
        Write-Warn "invalid JSON in ${path}: $($_.Exception.Message)"
        return
    }
    $form = $json.forms.oozaru
    if ($null -eq $form) {
        Write-Warn "standard oozaru.oozaru form is missing in $path"
        return
    }
    $scale = @($form.modelScaling)
    if ($scale.Count -ne 3 -or @($scale | Where-Object { [Math]::Abs([double]$_ - 3.8) -gt 0.0001 }).Count -gt 0) {
        Write-Warn "standard Oozaru modelScaling must remain [3.8, 3.8, 3.8] in $path"
    }
    $sizeProperty = $form.PSObject.Properties["xenopixelsNpcDisplaySize"]
    $auraProperty = $form.PSObject.Properties["xenopixelsNpcAuraScale"]
    $size = if ($null -eq $sizeProperty) { 0 } else { [int]$sizeProperty.Value }
    $aura = if ($null -eq $auraProperty) { [double]::NaN } else { [double]$auraProperty.Value }
    if ($size -eq 30 -and [Math]::Abs($aura - 1.0) -lt 0.0001) {
        Write-Check "$Tree standard Oozaru uses NPC size 30 and aura multiplier 1.0"
        return
    }
    if ($Mode -eq 'Check') {
        Write-Warn "$Tree standard Oozaru needs xenopixelsNpcDisplaySize=30 and xenopixelsNpcAuraScale=1.0"
        return
    }
    $form | Add-Member -NotePropertyName xenopixelsNpcDisplaySize -NotePropertyValue 30 -Force
    $form | Add-Member -NotePropertyName xenopixelsNpcAuraScale -NotePropertyValue 1.0 -Force
    $content = ($json | ConvertTo-Json -Depth 100) + [Environment]::NewLine
    Set-ConfigText $path $content 'configured standard Oozaru NPC size/aura tuning'
}

function Find-Version([IO.FileInfo[]]$Jars, [string]$Pattern) {
    foreach ($jar in $Jars) {
        if ($jar.Name -match $Pattern) { return $Matches[1] }
    }
    return $null
}

function Compare-VersionPrefix([string]$Actual, [string[]]$Allowed) {
    foreach ($allowed in $Allowed) {
        if ($Actual -eq $allowed -or $Actual.StartsWith("$allowed-")) { return $true }
    }
    return $false
}

function Test-ModAlignment([string]$Root, [string]$ConfigRoot) {
    $mods = Join-Path $Root 'mods'
    if (-not (Test-Path -LiteralPath $mods)) {
        $parentMods = Join-Path (Split-Path $ConfigRoot -Parent) 'mods'
        if (Test-Path -LiteralPath $parentMods) { $mods = $parentMods }
    }
    if (-not (Test-Path -LiteralPath $mods)) {
        Write-Host '[INFO] mods directory unavailable; version alignment check skipped'
        return
    }
    [IO.FileInfo[]]$jars = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File)
    $checks = @(
        @{ Name='Create'; Pattern='(?i)^create-(?:neoforge-)?(?:1\.21\.1-)?(6\.0\.(?:10|11)(?:-295)?)'; Allowed=@('6.0.10','6.0.11-295') },
        @{ Name='DragonMineZ'; Pattern='(?i)dragonminez[^0-9]*(2\.1\.3(?:[-+._][^.]*)?)'; Allowed=@('2.1.3') },
        @{ Name='My NPCs'; Pattern='(?i)(?:my.?npcs|mynpcs)[^0-9]*(1\.5\.0(?:[-+._][^.]*)?)'; Allowed=@('1.5.0') },
        @{ Name='Sable'; Pattern='(?i)^sable[^0-9]*(2\.0\.3(?:[-+._][^.]*)?)'; Allowed=@('2.0.3') },
        @{ Name='Sable Companion'; Pattern='(?i)sable.?companion[^0-9]*(1\.6\.0(?:[-+._][^.]*)?)'; Allowed=@('1.6.0') }
    )
    foreach ($check in $checks) {
        $version = Find-Version $jars $check.Pattern
        if ($null -eq $version) {
            Write-Warn "$($check.Name) required version was not identifiable in $mods"
        } elseif (Compare-VersionPrefix $version $check.Allowed) {
            Write-Check "$($check.Name) version $version is aligned"
        } else {
            Write-Warn "$($check.Name) version $version is not aligned; expected $($check.Allowed -join ' or ')"
        }
    }
    Test-CreateAddons $jars
}

function Test-SimpleRange([string]$Version, [string]$Range) {
    if ([string]::IsNullOrWhiteSpace($Range) -or $Range -eq '*') { return $true }
    if ($Range -notmatch '^([\[\(])\s*([^,]*)\s*,\s*([^\]\)]*)\s*([\]\)])$') { return $true }
    $lowerInclusive = $Matches[1] -eq '['
    $lower = $Matches[2]
    $upper = $Matches[3]
    $upperInclusive = $Matches[4] -eq ']'
    try { $actualVersion = [version](($Version -split '-')[0]) } catch { return $true }
    if ($lower) {
        try { $lowerVersion = [version](($lower -split '-')[0]) } catch { $lowerVersion = $null }
        if ($null -ne $lowerVersion -and (($lowerInclusive -and $actualVersion -lt $lowerVersion) -or (-not $lowerInclusive -and $actualVersion -le $lowerVersion))) { return $false }
    }
    if ($upper) {
        try { $upperVersion = [version](($upper -split '-')[0]) } catch { $upperVersion = $null }
        if ($null -ne $upperVersion -and (($upperInclusive -and $actualVersion -gt $upperVersion) -or (-not $upperInclusive -and $actualVersion -ge $upperVersion))) { return $false }
    }
    return $true
}

function Test-CreateAddons([IO.FileInfo[]]$Jars) {
    $createJar = $Jars | Where-Object { $_.Name -match '(?i)^create-' } | Select-Object -First 1
    if ($null -eq $createJar -or $createJar.Name -notmatch '(6\.0\.(?:10|11)(?:-295)?)') { return }
    $createVersion = $Matches[1]
    foreach ($jar in $Jars) {
        if ($jar.FullName -eq $createJar.FullName) { continue }
        try {
            $archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
            $entry = $archive.GetEntry('META-INF/neoforge.mods.toml')
            if ($null -eq $entry) { $archive.Dispose(); continue }
            $reader = [IO.StreamReader]::new($entry.Open())
            $toml = $reader.ReadToEnd()
            $reader.Dispose(); $archive.Dispose()
            foreach ($block in [regex]::Matches($toml, '(?ms)\[\[dependencies\.[^\]]+\]\](.*?)(?=\[\[dependencies\.|\z)')) {
                $body = $block.Groups[1].Value
                if ($body -match '(?m)^\s*modId\s*=\s*"create"' -and $body -match '(?m)^\s*versionRange\s*=\s*"([^"]+)"') {
                    $range = $Matches[1]
                    if (-not (Test-SimpleRange $createVersion $range)) {
                        Write-Warn "Create addon $($jar.Name) declares incompatible Create range $range for $createVersion"
                    }
                }
            }
        } catch {
            Write-Warn "could not inspect Create addon metadata in $($jar.Name): $($_.Exception.Message)"
        }
    }
}

function Find-MalformedIdentifiers([string]$ConfigRoot) {
    $extensions = @('.json','.toml','.cfg','.properties','.yaml','.yml')
    $found = 0
    Get-ChildItem -LiteralPath $ConfigRoot -Recurse -File | Where-Object { $extensions -contains $_.Extension.ToLowerInvariant() } | ForEach-Object {
        $lineNumber = 0
        Get-Content -LiteralPath $_.FullName | ForEach-Object {
            $lineNumber++
            if ($_ -match '["'']([a-z0-9_.-]+:)["'']') {
                Write-Warn "malformed empty-path identifier '$($Matches[1])' at $($_.FullName):$lineNumber"
                $found++
            }
        }
    }
    if ($found -eq 0) { Write-Check 'no quoted namespace-only identifiers were found in config text' }
}

function Invoke-Pack([string]$InputPath) {
    $resolved = (Resolve-Path -LiteralPath $InputPath).Path
    $isZip = [IO.Path]::GetExtension($resolved).Equals('.zip', [StringComparison]::OrdinalIgnoreCase)
    $tempRoot = $null
    $workRoot = $resolved
    $script:IsArchive = $isZip
    if ($isZip) {
        $tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("xenopixels-preflight-" + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $tempRoot | Out-Null
        [IO.Compression.ZipFile]::ExtractToDirectory($resolved, $tempRoot)
        $workRoot = $tempRoot
    }
    try {
        Write-Host "`n=== $resolved ($Mode) ===" -ForegroundColor White
        $before = $script:Changes
        $configRoot = Get-ConfigRoot $workRoot
        Remove-ScalableLuxOverride $configRoot
        Test-OozaruTree $configRoot 'dragonminez'
        Test-OozaruTree $configRoot 'dragonminez1'
        Test-ModAlignment $workRoot $configRoot
        Find-MalformedIdentifiers $configRoot
        if ($isZip -and $Mode -eq 'Fix' -and $script:Changes -gt $before) {
            $backup = "$resolved.xenopixels-backup-$script:BackupStamp"
            Copy-Item -LiteralPath $resolved -Destination $backup
            $replacement = "$resolved.xenopixels-new"
            if (Test-Path -LiteralPath $replacement) { Remove-Item -LiteralPath $replacement -Force }
            [IO.Compression.ZipFile]::CreateFromDirectory($workRoot, $replacement, [IO.Compression.CompressionLevel]::Optimal, $false)
            Move-Item -LiteralPath $replacement -Destination $resolved -Force
            Write-Host "[BACKUP] $backup" -ForegroundColor DarkCyan
        }
    } finally {
        if ($null -ne $tempRoot) {
            $fullTemp = [IO.Path]::GetFullPath($tempRoot)
            $fullSystemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
            if ($fullTemp.StartsWith($fullSystemTemp, [StringComparison]::OrdinalIgnoreCase) -and
                    (Split-Path $fullTemp -Leaf).StartsWith('xenopixels-preflight-')) {
                Remove-Item -LiteralPath $fullTemp -Recurse -Force
            }
        }
    }
}

foreach ($path in $PackPath) { Invoke-Pack $path }
Write-Host "`nPreflight complete: $script:Changes change(s), $script:Issues unresolved warning(s)."
if ($Mode -eq 'Check' -and $script:Issues -gt 0) { exit 1 }
