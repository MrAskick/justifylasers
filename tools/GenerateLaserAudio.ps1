param([string]$Ffmpeg = 'ffmpeg', [switch]$SabersOnly, [switch]$ClashOnly)
$ErrorActionPreference = 'Stop'
$soundDirectory = Join-Path $PSScriptRoot '../src/main/resources/assets/justifylasers/sounds'
New-Item -ItemType Directory -Force -Path $soundDirectory | Out-Null

# Original oscillator synthesis. Every idle partial completes whole cycles in four seconds.
$idle = '(0.27*sin(2*PI*110*t+0.6*sin(2*PI*0.5*t))+0.13*sin(2*PI*220*t+0.4*sin(2*PI*1.25*t))+0.08*sin(2*PI*440*t+1.6*sin(2*PI*33*t))+0.025*sin(2*PI*1760*t+3*sin(2*PI*71*t)))*(0.91+0.09*cos(2*PI*2*t))'
$start = '(0.28*sin(2*PI*(110*t+220*t*t))+0.13*sin(2*PI*(330*t+660*t*t)+sin(2*PI*49*t))+0.055*sin(2*PI*1540*t+4*sin(2*PI*93*t)))*min(1,t/0.015)*exp(-5*t)'
$stop = '(0.29*sin(2*PI*(330*t-260*t*t))+0.14*sin(2*PI*(660*t-520*t*t)+0.8*sin(2*PI*41*t)))*min(1,t/0.012)*exp(-10*t)'
$contact = '(0.12*sin(2*PI*880*t+8*sin(2*PI*313*t))+0.10*sin(2*PI*1760*t+6*sin(2*PI*467*t))+0.12*sin(2*PI*165*t))*min(1,t/0.005)*exp(-18*t)'
$sounds = @(
    @{Name='laser_idle'; Duration=4; Expression=$idle},
    @{Name='laser_start'; Duration=0.36; Expression=$start},
    @{Name='laser_stop'; Duration=0.40; Expression=$stop},
    @{Name='laser_contact'; Duration=0.22; Expression=$contact}
)
if ($SabersOnly -and !$ClashOnly) {
    & (Join-Path $PSScriptRoot 'ImportSaberAudio.ps1') -Ffmpeg $Ffmpeg
    $sounds = @()
}
$sounds += @(
    @{Name='saber_clash'; Duration=0.36; Expression='(0.25*sin(2*PI*(130*t-65*t*t))+0.13*sin(2*PI*1710*t+19*sin(2*PI*413*t))+0.10*sin(2*PI*2800*t+17*sin(2*PI*713*t)))*min(1,t/0.002)*exp(-16*t)+(0.04*sin(2*PI*740*t)+0.03*sin(2*PI*1493*t))*min(1,t/0.009)*exp(-9*t)'}
)
if ($ClashOnly) { $sounds = @($sounds | Where-Object { $_.Name -eq 'saber_clash' }) }
foreach ($sound in $sounds) {
    $source = "aevalsrc='$($sound.Expression)':s=48000:d=$($sound.Duration)"
    $destination = Join-Path $soundDirectory "$($sound.Name).ogg"
    & $Ffmpeg -hide_banner -loglevel error -y -f lavfi -i $source -ac 1 -c:a libvorbis -q:a 5 $destination
    if ($LASTEXITCODE -ne 0) { throw "Could not synthesize $($sound.Name)" }
    Get-Item -LiteralPath $destination | Select-Object Name,Length
}
