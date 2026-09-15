# Build and verification

Target: **2.0.0-alpha.21**, checked on 2026-09-15.

## Build from source

Use JDK 21 or 22 to run the Gradle wrapper. The compiled Minecraft 1.20.1 targets require Java 17; 1.21.1 targets require Java 21. The first build downloads dependencies.

```text
gradlew.bat build
gradlew.bat runGameTest
```

On Linux/macOS, use `./gradlew`. `build` compiles all six loader/version targets, runs unit tests, produces two universal JARs and audits both binary and source archives. `runGameTest` separately runs the Fabric 1.20.1 world regression suite. Outputs are in `build/libs`; test worlds and reports stay under `platforms/<target>/build`.

The public checkout contains the finished textures, models and OGG recordings. Normal builds do not need reference images, source MP3/WAV files, FFmpeg, an installed Minecraft instance or the optional material-authoring tasks. Test fixture sources are included for reproducibility but are excluded from production JARs.

## Completed checks

- A clean archive of the staged public source built successfully without private references, local authoring notes or pre-existing build outputs: all 88 Gradle tasks executed. Its two universal JARs match the previous alpha.21 build's contents apart from text line endings; compiled classes and binary assets are unchanged.
- All six Fabric/Forge/NeoForge build targets passed for Minecraft 1.20.1 and 1.21.1.
- 99 JUnit tests passed, covering geometry, resource consistency, settings, energy and related regressions.
- 184 Fabric 1.20.1 GameTests passed, including damage, optics, inventory/security and industrial behavior.
- All 14 distributable/source archives passed the private-asset and obsolete-texture audit. Original reference sheets and MP3/WAV inputs are not bundled.
- Both universal JARs passed loader metadata, optional-integration discovery, recipe consistency and test-fixture isolation checks.
- All 22 new saber recordings decoded successfully as mono 44.1 kHz OGG Vorbis.
- Native audio fixtures passed on Fabric 1.20.1 without shaders, Forge 1.20.1 with Oculus/Embeddium/Kappa, and NeoForge 1.21.1 with Iris/Sodium/Kappa. They exercise sound registration/decoding, both hands, activation, attacks, contact, resource reloads, muting and cleanup.
- Ore geometry was checked without shaders and with the Forge/NeoForge shader combinations above. Tests check face separation, silhouette clearance and neighbor culling.

These checks are not a guarantee for every shader pack, modpack or server workload. Shader loaders/packs are not bundled. Forge 1.21.1 and legacy NeoForge 1.20.1 have no verified shader combination in this release.

## Remaining alpha work

- Add the tablet discovery structure and complete survival access to the schematic catalog.
- Finish the Fuel Generator/Electric Smelter progression; their recipes remain disabled.
- Add and balance weapon energy consumption.
- Continue multiplayer, large-installation performance, long-session and third-party modpack testing.
- Perform subjective audio-mix and gameplay-balance testing beyond the automated fixtures.

Back up worlds before upgrading. The server and clients must use matching Minecraft, loader and mod versions. A universal JAR selects its native loader implementation; it does not enable cross-loader multiplayer or safe world downgrades.
