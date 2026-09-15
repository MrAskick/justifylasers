param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '../ref/sounds'),
    [string]$Ffmpeg = 'ffmpeg'
)
$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot '../src/main/resources/assets/justifylasers/sounds/saber'
$clips = [ordered]@{
    'catch' = 'saber_catch.mp3'
    'ignite' = 'once_on_off/saberon.mp3'
    'retract' = 'once_on_off/saberoff.mp3'
    'staff_ignite' = 'double_on_off/enemy_saber_on.mp3'
    'staff_retract' = 'double_on_off/enemy_saber_off.mp3'
}
foreach ($index in 1..5) { $clips["idle_$index"] = "saber_idle/saberhum$index.wav" }
foreach ($index in 1..9) { $clips["swing_$index"] = "saber_attack/saberhup$index.mp3" }
foreach ($index in 1..3) { $clips["fire_$index"] = "saber_fire/saberhitwall$index.mp3" }
foreach ($source in $clips.Values) {
    if (!(Test-Path -LiteralPath (Join-Path $SourceDirectory $source) -PathType Leaf)) { throw "Missing audio source: $source" }
}
New-Item -ItemType Directory -Force -Path $destination | Out-Null
foreach ($clip in $clips.GetEnumerator()) {
    $target = Join-Path $destination ($clip.Key + '.ogg')
    & $Ffmpeg -hide_banner -loglevel error -y -i (Join-Path $SourceDirectory $clip.Value) -map 0:a:0 -vn -sn -dn -map_metadata -1 -ac 1 -ar 44100 -c:a libvorbis -q:a 5 $target
    if ($LASTEXITCODE -ne 0) { throw "Could not convert $($clip.Value)" }
    Get-Item -LiteralPath $target | Select-Object Name,Length
}
