<#
.SYNOPSIS
    Drops the extra mods needed to test Xeno flight against Create and Create: Aeronautics into
    the dev run folder.

.DESCRIPTION
    Create is only a compileOnly dependency in build.gradle, so a plain `runClient` has no Create,
    no Aeronautics and no sails to compare against. NeoForge's dev run does scan <runDir>/mods,
    so copying the real jars there is enough — and it is what the live server actually runs, which
    makes a dev test meaningful rather than approximate.

    Create jar-in-jars Flywheel, Ponder and Registrate, and the Aeronautics bundle jar-in-jars
    Aeronautics, Offroad and Simulated, so these two files pull in the whole set.

    `run/` is gitignored, so this has to be re-run on a fresh clone or after cleaning the run
    directory. It copies rather than links so a Gradle clean cannot reach back into the launcher
    instance.

.PARAMETER Source
    Folder holding the mod jars. Defaults to the CurseForge instance this was set up from.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\setup_dev_mods.ps1
#>
param(
    [string]$Source = "$env:USERPROFILE\curseforge\minecraft\Instances\XenoPixels - DMZ\mods"
)

$ErrorActionPreference = 'Stop'

# Keep these pinned to what the live server runs. Create's compile version in build.gradle may be
# newer; the runtime one is what a test is actually played against.
$wanted = @(
    'create-1.21.1-6.0.10.jar',
    'create-aeronautics-bundled-1.21.1-1.3.0.jar',
    'CustomNPCs-Unofficial-NeoForge-1.21.1.20251230.jar'
)

$runMods = Join-Path $PSScriptRoot '..\run\mods'
New-Item -ItemType Directory -Force -Path $runMods | Out-Null

if (-not (Test-Path $Source)) {
    Write-Error "Mod source folder not found: $Source`nPass -Source with the folder holding the jars."
}

foreach ($name in $wanted) {
    $from = Join-Path $Source $name
    if (-not (Test-Path $from)) {
        Write-Warning "Missing $name in $Source - skipped."
        continue
    }
    Copy-Item -Path $from -Destination $runMods -Force
    Write-Host "Copied $name"
}

Write-Host ""
Write-Host "Dev mods ready in run\mods. Verify with:  .\gradlew.bat runGameTestServer"
Write-Host "The mod list in the log should name Create, Create Aeronautics, Create Simulated,"
Write-Host "Flywheel and Ponder."
