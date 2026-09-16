# Build and verification

Target: **2.0.0-alpha.26**. Publication build checked on 2026-09-16; native graphics checks on 2026-09-15.

## Build from source

Use JDK 21 or 22 to run the Gradle wrapper. The compiled Minecraft 1.20.1 targets require Java 17; 1.21.1 targets require Java 21. The first build downloads dependencies.

```text
gradlew.bat build
gradlew.bat runGameTest
```

On Linux/macOS, use `./gradlew`. `build` compiles all six loader/version targets, runs unit tests and the Fabric 1.20.1 world regression suite, produces two universal JARs and audits both binary and source archives. `runGameTest` can also run the world suite on its own. Outputs are in `build/libs`; test worlds and reports stay under `platforms/<target>/build`.

The public checkout contains the finished textures, models and OGG recordings. Normal builds do not need reference images, source MP3/WAV files, FFmpeg, an installed Minecraft instance or the optional material-authoring tasks. Test fixture sources are included for reproducibility but are excluded from production JARs.

## Completed checks

- A clean export of the staged public source built successfully without private reference sheets or pre-existing build outputs: all 94 Gradle tasks executed.
- All six Fabric/Forge/NeoForge build targets passed for Minecraft 1.20.1 and 1.21.1.
- 122 JUnit tests passed, covering geometry, continuous dish UVs, tracking transforms, thermal arithmetic, resource consistency, settings and energy.
- 223 Fabric 1.20.1 GameTests passed, including damage, optics, inventory/security and industrial behavior. Generator checks cover real fuel consumption, lava warm-up, peak output, cooling, fuel changes, fractional FE and save/load. Solar checks cover all four output directions, adjacent optics, shared power and access settings.
- All 14 distributable/source archives passed the private-asset and obsolete-texture audit. Original reference sheets and MP3/WAV inputs are not bundled.
- Both universal JARs passed loader metadata, optional-integration discovery, recipe consistency and test-fixture isolation checks.
- The final universal JARs passed the Alpha26 native client fixture on Fabric 1.20.1 with Iris/Sodium/Indium/Kappa, Forge 1.20.1 with Oculus/Embeddium/Kappa, and NeoForge 1.21.1 with Iris/Sodium/Kappa. Forge and NeoForge also had JEI installed; Fabric exercised the optional-integration fallback without JEI.
- The same fixture passed in the Fabric 1.20.1 development client without shaders or JEI. Its screenshots were also inspected for panel alignment, tracking and generator display layout.
- Those fixtures check day/night generation, optical growing, tablet charging, selected output routing and synchronized English/Russian interfaces. Captured views were inspected for dish tracking, panel alignment, the four nozzles, generator details and the revised JEI construction layout.

The native fixture loads a hot generator to check its 1200 °C / 95% / 128 FE/t display; the GameTests independently reach that state through 5900 actual combustion ticks. A hot screenshot alone is not the thermal regression test.

Earlier alpha.21 verification included decoding all 22 saber OGG recordings, native audio fixtures and shader/no-shader ore checks. Those dedicated audio runs were not repeated for alpha.26.

These checks are not a guarantee for every shader pack, modpack or server workload. Shader loaders/packs are not bundled. Fabric/Forge 1.21.1 and legacy NeoForge 1.20.1 were compiled and archive-checked but did not receive the Alpha26 native client run. GameTests use Fabric 1.20.1, not a separate server run for every loader.

## Remaining alpha work

- Add the dedicated tablet discovery structure. Survival currently starts with a tablet schematic from existing structure loot; a charged tablet can copy that schematic.
- Continue balancing thermal fuel generation and the FE-to-LM survival progression. The Electric Smelter has been removed.
- Add and balance weapon energy consumption.
- Continue multiplayer, large-installation performance, long-session and third-party modpack testing.
- Perform subjective audio-mix and gameplay-balance testing beyond the automated fixtures.

Back up worlds before upgrading. Alpha.25 large concentrators need three additional lower resonators, facing outward; see the [solar guide](solar-concentrator.ru.md). The server and clients must use matching Minecraft, loader and mod versions. A universal JAR selects its native loader implementation; it does not enable cross-loader multiplayer or safe world downgrades.
