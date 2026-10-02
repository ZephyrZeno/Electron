$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskRun = Join-Path $taskRoot 'build/showcase-run'
New-Item -ItemType Directory -Path $taskRun -Force | Out-Null
# Never regenerate over chests/signs from a previous export: their destruction drops items.
# Keep the previous generated world for inspection; never touch an installed player save.
$taskWorld = Join-Path $taskRun 'Circuit_Foundry_Academy'
if (Test-Path -LiteralPath $taskWorld) {
    $taskResolvedWorld = (Resolve-Path -LiteralPath $taskWorld).Path
    $taskExpectedWorld = [IO.Path]::GetFullPath((Join-Path $taskRoot 'build/showcase-run/Circuit_Foundry_Academy'))
    if ($taskResolvedWorld -ne $taskExpectedWorld) { throw 'Refusing to move a world outside the generated export path.' }
    $taskArchive = Join-Path $taskRoot ('build/academy-history/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $taskArchive -Force | Out-Null
    Move-Item -LiteralPath $taskResolvedWorld -Destination $taskArchive
}
'eula=true' | Set-Content -LiteralPath (Join-Path $taskRun 'eula.txt')
@'
level-name=Circuit_Foundry_Academy
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
level-seed=20261003
gamemode=survival
difficulty=peaceful
spawn-protection=0
generate-structures=false
view-distance=6
simulation-distance=6
online-mode=false
server-ip=127.0.0.1
server-port=25585
max-players=1
motd=Electron offline academy map export
'@ | Set-Content -LiteralPath (Join-Path $taskRun 'server.properties')
'' | Set-Content -LiteralPath (Join-Path $taskRun 'academy-verification.txt')
Push-Location $taskRoot
try {
    & (Join-Path $PSScriptRoot 'gradle.ps1') runShowcase --offline --no-daemon
    if ($LASTEXITCODE -ne 0 -or (Get-Content -LiteralPath (Join-Path $taskRun 'academy-verification.txt') -Raw) -notmatch 'build_verified=true') { throw 'Map construction or electrical validation failed.' }
    & (Join-Path $PSScriptRoot 'gradle.ps1') runShowcase -PacademyVerify=true --offline --no-daemon
    if ($LASTEXITCODE -ne 0 -or (Get-Content -LiteralPath (Join-Path $taskRun 'academy-verification.txt') -Raw) -notmatch 'reload_verified=true') { throw 'Saved map reload validation failed.' }
    Write-Output 'Map verified. Run tools/package_academy.py with Pillow-enabled Python to package it.'
} finally { Pop-Location }
