# Build and verification

Target: **2.0.0-alpha.49**. Build and regression checks on 2026-09-22.

## Build from source

Use JDK 21 or 22 to run the Gradle wrapper. The compiled Minecraft 1.20.1 targets require Java 17; 1.21.1 targets require Java 21. The first build downloads dependencies.

```text
gradlew.bat build
gradlew.bat runGameTest
```

On Linux/macOS, use `./gradlew`. `build` compiles all six loader/version targets, runs unit tests and the Fabric 1.20.1 world regression suite, produces two universal JARs and audits both binary and source archives. `runGameTest` can also run the world suite on its own. Outputs are in `build/libs`; test worlds and reports stay under `platforms/<target>/build`.

The public checkout contains the finished textures, models and OGG recordings. Normal builds do not need reference images, source MP3/WAV files, FFmpeg, an installed Minecraft instance or the optional material-authoring tasks. Test fixture sources are included for reproducibility but are excluded from production JARs.

## Alpha.49 mirror temporal stability

- Fixed self-occlusion between the TAA-jittered mirror pane and the unjittered reflection aperture. The comparison accounts for surface depth slope and shader render scaling; reflected object depth and GPU rendering are preserved.
- Full shader captures now resolve depth into the same viewport as their upscaled color image.
- Consecutive-frame checks pass with Oculus/Kappa, Iris/Kappa, CPU/GPU model rendering, oblique views, foreground blocks and resource reloads. The final Oculus/Kappa + seven Pixlli packs run at `ResolutionScale=0.5` has zero mismatched control pixels across 288 sampled frames. A separate vanilla Forge 1.21.1 run also passes.
- Final Fabric 1.21.1 + Iris/Kappa checks also pass parallax, reflected depth, occlusion, resize, reload and mirror controls. All six targets build; **230 JUnit tests and 326 GameTests pass**, and all 14 distributable/source archives pass their audits.
- [Reproduction, scope and evidence (Russian)](mirror-flicker.ru.md). Tests use isolated copies; installed saves, mods and shaders are unchanged.

## Alpha.48 shared GPU geometry and shader fixes

- All six targets build, with **230 JUnit tests and 326 GameTests passing**. Both universal JARs and 14 distributable/source archives pass their metadata and private-asset checks.
- GPU geometry is shared by solid machinery/optics, opaque printed models, bridge materials and late light effects. The client GPU tab exposes three compatibility toggles, a **16–512 MiB** residency budget and **1–32 MiB/frame** uploads. Transparent print faces retain Minecraft sorting. Cache misses use the CPU renderer, without hiding objects.
- Native pixel/reload/depth tests pass on Forge 1.20.1, Fabric 1.21.1, Forge 1.21.1 and NeoForge 1.21.1. Model lighting/transforms are compared on both Forge versions; effect checks cover beams, sabers, cube cores, sunlight, grouped scorch marks and bridge profiles. Minimum-budget eviction and upload fallback also pass.
- Mirror parallax, composite occlusion, resize, reload and controls pass with vanilla rendering and Kappa, including full shader captures. Continuous opaque mirror UVs remove false recursive-looking refraction when custom reflections are off.
- Reproduced the sky specks in the actual Oculus/Kappa + Pixlli scene. Isolated their source to Mekanism's energy-core queue during the shadow pass; the optional queue guard removes them in both CPU and GPU paths. The original shaderpack was not edited.
- Advanced nutrient growth now consumes one bucket per intermediate crystal; cutting yields **3/4/5/6** resources at **55/30/12/3%**. End-to-end world tests cover all four crystal types, all outcomes, exact resource use, inventory capacity and save/reload. Basic water growth is unchanged.
- JEI uses the sanitized user-supplied single-block house preview; its in-game appearance and the GPU settings screen were inspected with the full resource-pack set.
- Matched 1920×1080, 12-chunk runs at `-385 -60 -21 / 45 / 15`, with Kappa and all seven Pixlli packs, measured **9.13 → 76.45 FPS**. Same loaded scene; no quality reduction. Conditions, percentiles and limits: [alpha.48 report](prompt11-gpu.ru.md).

## Alpha.47 GPU light bridges

- All six loader/version targets build. **229 JUnit tests and 325 Fabric 1.20.1 GameTests pass**, with no skipped unit tests. All 14 distributable/source archives pass the private-asset audit.
- Shared static VBOs and GPU animation replace per-frame bridge mesh generation. Mesh density, collision, power, field colors and the existing late depth/lighting path are unchanged.
- Tests cover descriptor bounds, partial/maximum spans, all existing transform orientations and camera-relative precision near the world border. Native pixel comparisons exercise the actual shader, occlusion, shared meshes, bounded eviction and resource reload.
- Both the pixel/cache fixture and the bridge walking/power-off fixture pass on Forge 1.20.1 with the installed technical modpack, Fabric 1.21.1 + Iris/Kappa, Forge 1.21.1, and NeoForge 1.21.1 + Iris/Kappa. The 288 GPU/CPU image comparisons preserve the reference appearance; maximum mean visible-pixel channel error is 0.0035/255.
- An additional Forge 1.20.1 + Oculus/Kappa bridge physics run passes. Shader screenshots retain the colored field and bright edges against the sky.
- The restored-map benchmark uses 12 chunks and 1920×1080: alpha.46 measured **14.15 / 14.74 FPS**, alpha.47 **230.83 / 237.47 FPS**, with 18 active fields in all four runs. Earlier runs against the accidentally modified map are excluded. Evidence and limitations: [GPU bridge report](bridge-gpu.ru.md).

## Alpha.46 bridges and cultivation

- **226 JUnit tests and 325 Fabric 1.20.1 GameTests pass**, with no skipped unit tests. Added checks cover the printed-block vision/collision predicates, exact bridge coordinates across every mount/roll/rotation and join, real adjacent placement and shared optical power, growth economics, original basic yields, updated crafting, 72 chemical recoloring combinations and fluid registration.
- All six loader/version targets compile. Both universal JARs pass recipe/metadata checks; the 14 binary/source archives pass the private-asset audit. The retired Spectrum Module schematic retains its item/model registration for existing saves, but is absent from assembly recipes and the tablet catalog.
- The Prompt10 native-client fixture passes on **Forge 1.20.1 with the installed Mekanism/AE2/JEI/Embeddium pack**, **Fabric 1.21.1 + Iris/Kappa**, **Forge 1.21.1 without shaders**, and **NeoForge 1.21.1 + Iris/Kappa**. It checks straight/corner alignment, powered tunnel surfaces, bounded break debris, seed-stage rendering, fluid translations and all 13 synthesizer recipes in JEI. The Mekanism run verifies the actual filled photopolymer tank ingredient and its stored fluid data.
- Screenshots of both tunnel angles, seed stages, Mining Module materials and recipes were inspected. Frame-edge views retain the oriented emitters. Material tests check every UV region, non-overlap, edge extrusion and matching emission/normal maps.
- On fresh copies of the supplied Forge 1.20.1 world, at `-405 -60 18 / 50 / 15`, a fixed **1440×900**, shaders-off comparison measured **16.71 → 35.74 FPS**. No visual detail or effect settings were reduced. Method, percentiles and evidence paths are in the [performance report](client-performance.ru.md#alpha46-участок-со-световыми-мостами). This is not a general FPS guarantee or a benchmark of the other loaders.
- Logs: `build/prompt10/logs/`; native profiles: `build/prompt10/clients/` and `build/client-performance/clients/prompt10-*`. Tests never modify the installed world, modpack or configuration. Nothing was published. Dedicated-server integration, long-session soak and arbitrary resource-pack tests were not repeated in this pass.

Progression and balance changes: [alpha.46 notes](prompt10-fixes.ru.md), [advanced crystals](advanced-crystals.ru.md).

## Alpha.45 client performance

- All six loader/version targets build successfully. Both universal JARs pass the metadata, recipe, fixture-isolation and asset checks.
- **220 JUnit tests and 320 Fabric 1.20.1 GameTests pass**, with no skipped unit tests. Added coverage for cached quad geometry, UVs, normals, clipped printing layers, complete-vertex submission and mirror collision-cache invalidation.
- A real-world **Forge 1.20.1** comparison against the original alpha.44, using the installed modpack, fixed 1920×1080 resolution and the same camera/item, measured **8.26 FPS before** and **52.61 / 45.98 FPS after** in two fresh runs. Shaders were off; mirrors and model detail were not reduced. Methodology, frame-time percentiles and local evidence paths are in the [performance report](client-performance.ru.md).
- The final universal JARs pass the full Printing/WorldEdit native-client fixture on **Forge 1.20.1 + Oculus/Kappa**, **Fabric 1.21.1 + Iris/Kappa**, and **Forge 1.21.1 without shaders**. These check import, palette/textures, multipart output, construction layers, draft persistence, holograms and resource reload. Captured model/editor views were inspected.
- **NeoForge 1.21.1 + Iris/Kappa** passes the full Prompt5/Tablet chain with shader-rendered reflections enabled, including depth, parallax, occlusion, resizing, mirror controls and resource reload. Reflection screenshots were inspected.
- These are measurements for one scene, not an FPS guarantee for other loaders, shader packs or worlds. The existing alpha.44 dedicated-server integration matrix was not repeated for this client-focused change; the normal world suite includes the new mirror-cache regression.

## Alpha.44 release-audit fixes

- **216 JUnit tests and 319 Fabric 1.20.1 GameTests pass**, with no skipped unit tests. Eight security/upload regressions now run in the normal GameTest suite, not an optional audit source set. They cover private emitters and encoders, all eight chamber parts and all solar structure members, inventory preservation under every drop/silk/smelting combination, public mining, overlapping uploads, cooldown recovery and an immediate card write after autosaving.
- Native server integration checks pass on **all six Fabric/Forge/NeoForge targets for 1.20.1 and 1.21.1**. The expanded checks exercise the mining guard, private multiblock members, encoder packets and public mining through each platform. Existing inventory/fluid/FE, persistence, stale-packet and cached-privacy checks still pass.
- The Forge 1.20.1 integration run with **Mekanism 10.4.16.80, AE2 15.4.10 and GuideME 20.1.15** passes, including the actual installed AE2 Storage Bus, simulation, transfer and revocation through an already connected bus.
- Full **Fabric 1.20.1 + Lithium 0.11.4 + Iris/Kappa** LightBridge smoke passes: straight/diagonal walking, disable/re-enable, rejoining on the bridge, corners, modules and the radial menu. Exact-union tests compare 48 straight/corner orientation cases and reversed box ordering. In this run the recipes-to-integrated-server interval was **4 seconds**, compared with approximately **64 seconds** in the original audit. This is a startup observation on the same PC, not an isolated CPU benchmark or an FPS claim.
- **Forge 1.20.1 + Oculus/Kappa** passes the complete Optics/CubeLens chain: 38 particle sprites, optical routing, mixed color, filter packets, JEI, audio limits, six ports, lens magnification and occlusion.
- **NeoForge 1.21.1 + Iris/Kappa**, with shader rendering inside the reflection explicitly enabled, passes the full Prompt5 → Tablet chain, including mirror depth/parallax, controls, resource reload and Russian guide text. **Fabric 1.21.1 + Iris/Sodium/Kappa** passes Prompt8 → Tablet in English. These chains previously stopped on the missing printing guide entries.
- Full Printing tests pass on **Fabric 1.20.1/1.21.1, Forge 1.21.1 and NeoForge 1.21.1**: real schematic writes, multipart VOX, JSON, WorldEdit imports, skips, textures, palette, draft persistence and resource reload. The final Fabric 1.20.1, Forge 1.21.1 and NeoForge 1.21.1 runs also explicitly autosave, edit again and close during the cooldown: the editor waits, saves the last edit and restores it after reopening. Final Fabric 1.20.1/1.21.1 and NeoForge logs show the block atlas at **mip level 4**, including after resource reload; Forge logs also retain level 4. The NeoForge 1.20.1 graphical client was not separately launched.
- All six targets and both universal JARs build; the private-asset audit passes for all **14 binary/source archives**, checking against **145 private sheets**. No reference directories, tools or smoke helpers are packaged. New unit coverage checks block/item/particle sprite dimensions; the white print material remains white and the grown-crystal swatches retain their exact pixels.
- Logs: `build/release-fixes/logs/`; captures and copied test worlds: `build/release-fixes/clients/`. Previous alpha.43 universal JARs are preserved in `build/release-fixes/baseline/`. Installed mods, configuration and user worlds were not changed; nothing was published.

Scope remains limited: no arbitrary modpack compatibility guarantee, third-party claim integration, remote multiplayer/latency testing, long-session soak or acceptance test of the user's own world. Those checks are still needed before declaring stable 2.0.0. Recipes, balance, storage format and the loader protocol were not changed by these fixes.

## Alpha.43 unsupported schematic blocks

- **210 JUnit tests and 311 Fabric 1.20.1 GameTests pass.** New unit cases cover counted skips for missing blocks, incompatible states, unsupported models and missing textures, resolving each used state only once, excluding air and unused palette entries, empty results, and preserving gaps and coordinates in both scales. Unexpected failures and malformed files still surface as errors.
- All six loader/version targets build. Both universal JARs pass metadata, recipe and fixture-isolation checks; all 14 binary/source archives pass the private-asset audit.
- The alpha.43 universal JARs pass the expanded printing fixture on **Fabric 1.21.1 with Iris/Sodium/Kappa** and **Forge 1.20.1 with Oculus/Embeddium/Kappa**, both with JEI. The miniature fixture skips two chests, water, an invalid log state and a missing mod block; the full-size fixture skips a chest and water. Both still write real schematic items through the client/server menu, retain the original bounds and leave the skipped positions empty.
- Native assertions check the skipped-block count after autosave, both printing scales, preserved textures and resource reload. Inspected Russian and English editor captures: the persistent warning does not overlap the status or inventory. Existing VOX, draft, printing and particle regressions also pass.
- Checks use only isolated profiles under `build/prompt8-clients`. Installed mods, configuration and saves are unchanged. The other four targets are compiled/archive-checked, not separately launched; no dedicated-server, long-session or arbitrary-modpack testing was performed. Nothing was published. Storage format and Forge/NeoForge protocol **12** are unchanged.

## Alpha.42 WorldEdit schematic import

- **207 JUnit tests and 311 Fabric 1.20.1 GameTests pass.** New tests cover Sponge versions 1–3, compressed/uncompressed NBT, sparse palettes and VarInts, coordinates and empty margins, both import scales, multipart round trips, invalid states, malformed data and decompression limits. Entity and container data never enter a printable design.
- World tests verify that source block states survive item/block save and reload, a full-size bottom slab keeps its half-height collision, and an interrupted print retains its materials and exact paid FE/LM/polymer costs.
- All six loader/version targets compile. Both universal JARs pass metadata, recipe and fixture-isolation checks; all 14 binary/source archives pass the private-asset audit. Final packaging reruns unit tests and archive checks after the world suite; later runtime changes only refine client rendering/caches and interface wording.
- The expanded printing fixture passes on **Forge 1.20.1 with Oculus/Embeddium/Kappa** and **Fabric 1.21.1 with Iris/Sodium/Kappa**, both with JEI, using the alpha.42 universal JARs. It imports generated Sponge v3 files through the asynchronous encoder, writes schematics through the real client/server menu, places both scales and reloads resources. Existing VOX, paid printing, draft and particle checks remain enabled.
- Native assertions check rotated log end faces, furnace facing, slab geometry, grass tint, modded block textures, normalized sprite UVs and cache invalidation after resource reload. English/Russian editor and in-world captures are inspected, including original blocks alongside full-size prints.
- Tests use isolated profiles under `build/prompt8-clients`; installed mods, configuration and saves are unchanged. Nothing was published. Client/server must update together: Forge/NeoForge protocol is **12**.
- The native source files are generated fixtures, not an export supplied by the user. Automation does not click the operating-system file dialog. The other four targets are compiled/archive-checked, not separately launched in this update. Dedicated servers, long sessions, arbitrary modpacks and custom block-entity renderers are not covered by this pass.

Supported models, scale limits and import workflow: [photopolymer printing](photopolymer-printing.ru.md#кодировщик-импорт-worldedit).

## Alpha.41 multipart printing and crystal yields

- **200 JUnit tests and 309 Fabric 1.20.1 GameTests pass.** Added coverage includes lossless multipart splitting, bounded compressed storage, 512-part round trips, per-part editing, material replacement, rotated placement and occupied-target rejection, output extraction, interrupted multipart printing, large NBT and client-packet filtering.
- Crystal tests check the exact yield distributions, reserving room for three outputs, a single roll at completion, failed-growth resource costs and seed wear, and save/reload. Grower probabilities are 15/55/20/10% for 0/1/2/3 outputs; cutter probabilities are 50/35/15% for 1/2/3. The grower assumption resolving the requested 115% total is recorded in [advanced crystals](advanced-crystals.ru.md).
- All six loader/version targets compile. Both universal JARs pass metadata, recipe and fixture-isolation checks; all 14 binary/source archives pass the private-asset audit. Mesh tests check printer cap UV proportions; every photopolymer texture pixel has alpha 204 (80% opacity).
- The printing fixture passes on **Forge 1.20.1 with Oculus/Embeddium/Kappa** and **Fabric 1.21.1 with Iris/Sodium/Kappa**, both with JEI. It writes a multipart VOX schematic through the actual client/server menu, restores the draft, prints with a real beam, checks the collector's beam color and reloads resources. English/Russian editor, palette, texture-mode and world captures are inspected.
- A deliberately waiting import worker leaves the client and integrated server ticking; completion returns to the editor. Breaking a 2,048-voxel checkerboard print stays within the fixture's particle budget instead of emitting particles for every collision box.
- Native checks run the universal JARs in isolated profiles under `build/prompt8-clients`. Installed game mods, configuration and saves are unchanged. Nothing was published. Client/server must update together: Forge/NeoForge protocol is **11**.
- The operating-system file dialog itself is not clicked by automation; the waiting-worker check exercises the same asynchronous import path. The other four targets are compiled/archive-checked, not separately launched in this update. Long-session, dedicated-server and arbitrary-modpack checks remain outside this pass.

Operating instructions, format limits and multipart assembly: [photopolymer printing](photopolymer-printing.ru.md).

## Alpha.40 VOX and printer refinements

- **192 JUnit tests and 304 Fabric 1.20.1 GameTests pass.** New coverage includes VOX palettes, Z-up conversion, scene transforms/hidden layers, malformed chunks and cycles, 4096-cell storage, square/round brushes, draft persistence and concurrent edits, hologram FE costs, single/continuous printing, layer clipping and fluid opacity/flow. A full-tank job may exceed the vat capacity and continue after refilling; draft metadata has a separate bounded allowance around maximum-size designs.
- All six loader/version targets compile. Both universal JARs pass metadata, recipes and fixture-isolation checks; all 14 binary/source archives pass the private-asset audit. The finished OpenComputers-derived printer/collector textures are included with their CC0 notice, not the private reference sheets or authoring inputs.
- The printing fixture passes on **Forge 1.20.1 with Oculus/Embeddium/Kappa** and **Fabric 1.21.1 with Iris/Sodium/Kappa**, both with JEI. It imports a generated multicolor VOX file, checks that typing E leaves the editor open, writes a schematic, closes/reopens the draft, verifies the powered hologram, completes a real-beam print and reloads resources. English/Russian screenshots are inspected; the editor status now has its own full-width row.
- The expanded fixture also places the colored VOX model in the world and verifies the collector's client-side glow color against actual red and cyan beams. World captures show the rotating hologram, recolored collector and printer materials after resource reload.
- Final packaging reruns the full world suite, unit tests and archive checks after the tank-capacity and maximum-draft-size corrections. These last changes do not alter the native-tested rendering or GUI.
- The other four targets are compiled/archive-checked, not separately launched for this update. The native fixture calls the same file import path as the VOX picker but does not interact with the operating system's file dialog. Long-session, dedicated-server and arbitrary-modpack tests remain outside this pass.
- Checks use isolated profiles under `build/prompt8-clients`. Installed game mods, configuration and saves are unchanged. Nothing was published. Client/server must update together: Forge/NeoForge protocol is now **10**.

Operating instructions, file limits and draft recovery: [photopolymer printing](photopolymer-printing.ru.md).

## Alpha.39 photopolymer printing

- **182 JUnit tests and 299 Fabric 1.20.1 GameTests pass.** New checks cover JSON and texture-reference validation, rotations/UVs, outward face winding, voxel merging, overlapping-volume costs, violet spectrum gates, exact FE/resin costs, interrupted jobs and changed schematics, packet ordering/replays, privacy, and printed-block placement/picking/drops/save data.
- Native inventory tests exercise all 48 printer member/face combinations, model-data-preserving extraction, private access and fluid transaction rollback. Mesh seam tests include the encoder and printer frame, gantry, head and bed.
- All six loader/version targets compile. Both universal JARs pass metadata, recipe and fixture-isolation checks; all 14 binary/source archives pass the private-asset audit. No OpenComputers source or reference artwork is bundled.
- The printing fixture passes on **Forge 1.20.1 with Oculus/Embeddium/Kappa** and **Fabric 1.21.1 with Iris/Sodium/Kappa**, both with JEI. It verifies real-beam printing, server-side schematic recording, mod texture discovery, parent-model import, voxel painting/undo, machine menus, JEI previews and resource reload. Native screenshots were inspected; a clipped JEI preview and an overlong search placeholder were corrected.
- The final packaging reruns unit tests and archive checks after the world suite. The last change only shortens the English/Russian search placeholder. The other four loader/version combinations are compiled and archive-checked, not separately launched for this feature. Dedicated-server, long-session and arbitrary-modpack testing remain outside this pass.
- Native fixtures use project-owned profiles under `build/prompt8-clients`. Installed game mods, configuration and saves are unchanged. No release was published by this task.

Operating instructions and format limits: [photopolymer printing](photopolymer-printing.ru.md).

## Alpha.38 amplifier and photonite corrections

- **171 JUnit tests and 293 Fabric 1.20.1 GameTests pass.** Added coverage for single-item placement, full-stack insertion, Shift-click remainders, mixed-tier rejection and save/load. Counts of 1, 2 and 64 produce the exact additional LM and charge the full FE cost, including retained V–VI stacks. The registered crafting recipe rejects upgrades to disabled tiers.
- Photonite growth accepts purple `BC09F5` and rejects the old golden spectrum in the loaded machine recipe. The seed's render palette is also checked as violet.
- All six targets compile; both universal JARs and all 14 binary/source archives pass verification. After the world suite, `build -x runGameTest` reran unit tests and archive checks to include the final tooltip wording.
- The updated universal JARs pass the Prompt8 native fixture on Forge 1.20.1 with Oculus/Embeddium/Kappa and Fabric 1.21.1 with Iris/Sodium/Kappa, both with JEI. The catalog contains exactly I–IV and three upgrade recipes. Seed/icon screenshots were inspected; photonite is purple. Full fixture runs also pass native inventory, tablet and resource-reload checks. The other loader combinations below were tested before these corrections, not relaunched for this follow-up.

## Initial alpha.38 checks (before these corrections)

- All six loader/version targets compile. Both universal JARs pass metadata, recipe and test-fixture isolation checks. All 14 binary/source archives pass the private-asset audit against 145 reference images, including the new amplifier and loot references.
- **169 JUnit tests and 291 Fabric 1.20.1 GameTests pass.** Coverage includes all configured schematics and crafting-grid clearing, typed seed recipes and legacy migration, paid growth persistence, exact amplifier flux/FE costs and retired tiers, empty custom-data normalization, and exact-filter extraction with transaction rollback through all 48 cutter member/face combinations. Private and dismantled inventories remain protected.
- Mesh/resource checks cover cutter rail and moving-carriage intersections, Spectrum Module outward normals and GUI bounds, collector geometry/particles, six distinct 512px amplifier icons with clean alpha and transparent margins, and English/Russian localization parity. Supplied tier I–IV image hashes remain unchanged.
- The Prompt8 native fixture passes on **Fabric 1.20.1** without shaders, using the development client. The **final universal JARs** pass on Forge 1.20.1 / Oculus / Embeddium / Kappa 5.3 Caves, Fabric 1.21.1 / Iris 1.8.8 / Sodium 0.6.13 / Kappa, NeoForge 1.21.1 / Iris 1.8.8 / Sodium 0.6.13 / Kappa, and Forge 1.21.1 without shaders. All four universal runs include JEI; the 1.20.1 production client uses Java 17 and the 1.21.1 clients use Java 21.
- Native checks exercise cutter item-transfer APIs on every member and face, live growth, machine menus, resource reload, the new models/icons, minimum-flow text, and cube-core on/off captures without changing beam routing. They verify all configured schematic variants in JEI, four seed types, six amplifier types and five upgrade recipes. Tablet checks include the default Help tab, translated item coverage, scrolling, tab switching, projected mouse input, recording, search, charging, F1 and resource reload. English/Russian and shader/non-shader captures were inspected.
- These tests exposed a missing delegation in the universal JEI entry point: subtype and extra-ingredient registration were not forwarded. Both callbacks are now forwarded, and archive verification checks that the bridge covers every plugin callback. This is checked with the final universal artifacts, not only loader-specific development classes.
- Tests run in project-owned profiles under `build/prompt8-clients` and platform build directories. An older reused fixture had a stale industrial block entity over air; fresh isolated fixtures did not reproduce it. Installed game mods, configuration and saves are unchanged. Legacy NeoForge 1.20.1 is compiled/archive-checked, not separately launched. Shader checks cover the named combinations, not every pack. AE2/Mekanism support is verified through native inventory contracts and exact filters, not every third-party pipe setup.

Final packaging: `gradlew.bat build -x runGameTest` after the completed 291-test world run. The later changes were icon margins, JEI text contrast and universal JEI callback forwarding; unit tests and archive checks were rerun, followed by the native universal tests above.

## Previous alpha.37 checks

- All six loader/version targets build; 166 unit tests and 285 Fabric 1.20.1 GameTests pass. Both universal JARs pass metadata/recipe/fixture checks and all 14 binary/source archives pass the private-asset audit.
- Bridge footsteps now use a constant pitch of 1.0, with the tested walking/sprint cadence unchanged. The machine status is localized as “Waiting for fluid” / “Ожидание жидкости”. No other runtime behavior changed from alpha.36.
- Local `tools/` files are removed from the release tree and ignored, but retained locally. Private references, publishing inputs, local profiles and build outputs are excluded. The upstream `media/` tree, including its newly added logo, is preserved without edits.
- Exported the candidate Git index to a clean source directory with no `ref/`, `tools/` or local caches. `build -x runGameTest` succeeded there for all six targets, including unit tests and universal/private-asset checks. The full world suite was run in the original workspace against the same runtime sources.
- The detailed native rendering/JEI checks below were performed for alpha.36; they are not presented as fresh alpha.37 native runs.

## Previous alpha.36 checks

- All six loader/version targets build. Both universal JARs pass metadata, recipe and fixture-isolation checks; the private-asset audit passes for all 14 binary/source archives. Final packaging used `build -x runGameTest` after the separately completed world suite.
- 166 JUnit tests and 285 Fabric 1.20.1 GameTests pass. New coverage includes hue-range boundaries, seed wear/yield distributions, paused paid growth across removal/save/reload, vanilla item data and legacy migration, real workbench amplifier upgrades, exact paid 1 Tlm output, expanded inventory persistence and Configurator copying of six optic ports. Crafting compression/unpacking retains artificial origin. World nutrient flow reaches seven blocks and immersion is recognized as water.
- The Prompt7 native fixture passes on Fabric 1.20.1 with Iris/Sodium/Indium/Kappa, Forge 1.20.1 with Oculus/Embeddium/Kappa and JEI, Forge 1.21.1 with JEI without shaders, and NeoForge 1.21.1 with Iris/Sodium/Kappa and JEI. It covers world entry, working crystal production, synchronized machine/spectrum menus, resource reload, a paid tier-15 amplifier and its new slot, grown-crystal models and the display toggle. The 1.20.1 clients use Java 17; 1.21.1 uses Java 21.
- Inspected native model/menu captures, including floor/wall crystals and their silhouettes directly against Kappa's sky. Forge 1.20.1 and NeoForge 1.21.1 JEI fixtures open all 14 amplifier upgrades. A final resource-only adjustment turns the amplifier's front panel toward the GUI camera; the final universal JAR then passed the expanded NeoForge/Kappa fixture and its Russian menu/recipe captures were rechecked. Gameplay code is unchanged since the 285-test run.
- Finished nutrient-bucket and grown-crystal textures use the supplied ready-to-use assets. The imagegen skill supplied new material swatches for the Spectrum Module and Laser Cutter; individual swatches were packed into runtime atlases with UVs, normals and emissive masks. Original design sheets and the authoring sheet are not packaged.

All native checks use isolated profiles under `build/production-tests`; installed mods, configs and saves are untouched. Fabric 1.21.1 and legacy NeoForge 1.20.1 are compiled/archive-checked, not separately launched in this pass. Shader checks cover the named packs/stacks, not every rendering mod. Gem origin is item data rather than world-block provenance: placing and mining a vanilla storage block, or an external recipe that discards item data, can remove it. This is not a complete anti-exploit system.

## Previous alpha.35 checks

- All six loader/version targets compile; both universal distributions pass metadata, recipe and fixture-isolation checks. The archive audit checks 14 binary/source archives against private reference sheets.
- 160 unit tests cover bridge step cadence/mounting, mirror settings and shader wrapping, machine mesh seams, recipe resources, seed-yield probabilities and spectrum matching, alongside existing regressions.
- 277 Fabric game tests pass. New cases cover all four nutrient recipes and exact FE/fluid costs, four-stage seed wear, spectral/minimum-flow gates, pause/reload behavior, FE+LM cutting, fluid-transfer transaction rollback, new schematics and emitter spectrum persistence/security. The legacy photonite growth tests remain unchanged.
- Native shader-mirror checks pass on Fabric 1.20.1 / Iris 1.7.6 / Sodium 0.5.13 / Indium and Forge 1.20.1 / Oculus 1.8.0 / Embeddium 0.3.31 with Kappa 5.3 Caves. NeoForge 1.21.1 / Iris 1.8.8 / Sodium 0.6.13 also passed. They verify actual GPU color/depth, parallax, occlusion, angled views, window resize and resource reload. Oculus additionally asserts that a shader pipeline was created, rather than accepting vanilla fallback.
- The final 1.21.1 universal JAR passes the new-production native fixture on Forge 52.1.16 with JEI and on NeoForge 21.1.249 with JEI, Iris/Sodium and Kappa. Both runs check world entry, formed machines, actual inline spectrum delivery, world fluids, four synchronized menus, hex input and resource reload. The JEI checks find all four mixture recipes, five growth recipes and four cutting recipes. English and Russian menu/recipe captures were inspected. Tests run only under `build/production-tests`; installed game mods, configs and saves are not edited.
- Mirror materials use new imagegen-authored coarse material swatches, compiled into the existing UV/normal/specular atlas layout. Only compiled runtime textures enter the mod; the swatch sheet and original reference artwork do not.

Shader reflections remain experimental, off by default, and non-recursive. The tested combinations do not establish compatibility with every shader pack or other rendering mod. AE2/Mekanism integrations use native loader capabilities; the tests exercise those contracts, not every third-party pipe configuration.

## Alpha.34 checks

- Reproduced alpha.33's world-entry hang on native Forge 52.1.16 / Minecraft 1.21.1 with JEI, without shaders or resource packs. Two thread dumps showed the render thread spinning in `Frustum.offsetToFullyIncludeCameraCube`; the integrated server continued ticking. This was a renderer regression, not the earlier OptiFine incompatibility.
- The native regression guard fails on the original alpha.33 JAR before the bad main-camera frustum enters that loop. With alpha.34 it verifies the original frustum on every normal world frame, with reflections both off and on. Scripted-camera measurements ignore physical desktop mouse movement; deliberate mirror-drag inputs still reach the controller.
- The final 1.21.1 universal JAR passed the Prompt5 fixture on Forge 52.1.16 with Java 21.0.7 and JEI 19.56.0.441: world entry, reflected GPU depth and parallax, opaque occlusion, resize/reload, aiming and the tablet. Near/far lateral displacement was approximately 0.01950 / 0.00836 of the image width.
- World-entry/frustum, reflection depth, reload and aiming checks also passed with Kappa on Fabric 1.20.1 (Loader 0.19.5, Iris/Sodium/Indium), Forge 1.20.1 (47.4.6, Oculus/Embeddium) and NeoForge 1.21.1 (21.1.249, Iris/Sodium). The 1.20.1 clients used Java 17 and the 1.21.1 clients Java 21. Fabric 1.21.1 and legacy NeoForge 1.20.1 were compiled/archive-checked, not separately launched in this hotfix pass.
- All six targets built; 152 JUnit tests and 270 Fabric 1.20.1 GameTests passed. Both universal JARs passed metadata/recipe/fixture-isolation checks and all 14 binary/source archives passed the private-asset audit. No gameplay, registry or save-format changes.
- All native checks use isolated profiles under `build/production-tests`. Installed game mods, resource packs, settings and saves were not changed.

## Previous alpha.33 checks

- All six loader/version targets built successfully. Both universal JARs passed metadata and recipe checks; all 14 binary/source archives passed the private-asset audit. Reference sheets and source WAVs are not bundled. The mirror atlas contains extracted, repacked material regions rather than a reference sheet.
- 152 JUnit tests and 270 Fabric 1.20.1 GameTests passed. New coverage includes reflected camera projection, depth reconstruction and near/far parallax, the clipped rectangular aperture and open U-shaped stand, localized tablet search retaining recipe indices, mirror link persistence without a beam, manual-aim validation, camera-pitch-independent bridge placement and neighbour inheritance, and solar assembly with independently rotated components.
- Forge/NeoForge packet protocol is now 7. The new mirror packet validates held tool/mode/slot, range, line of sight, permissions, charge and finite/rate-limited angles. Client and server must update together.
- The supplied OptiFine log was diagnosed, not reproduced in its original modpack. See [the compatibility analysis](optifine-crash.ru.md); no missing-method exception is suppressed by the mod.
- The Prompt5 native client fixture passed on Fabric 1.20.1 (Iris 1.7.6 / Sodium / Indium / Kappa), Forge 1.20.1 (Oculus 1.8 / Embeddium 0.3.31 / Kappa) and NeoForge 1.21.1 (Iris 1.8.8 / Sodium / Kappa). It checks GPU color/depth and near/far parallax, opaque-world occlusion, resource reload, server packet synchronization, held-mouse mirror aiming without camera movement, and tablet search/selection/recording. The Fabric profile also passed with shaders disabled. English and Russian captures of the tablet, chamber lids and joined corner emitters were inspected.
- Near-target lateral displacement was about 0.0195 of the captured view width, versus 0.0084–0.0090 for the far target. This is a measured second-camera view, not an image sliding on the glass. The native fixture also asserts that an opaque block completely hides the reflected target.
- Tests use isolated profiles under `build/production-tests`; installed game mods/configs/saves were not changed. Embeddium emits its standard mixin-taint warning for the optional terrain-attribute compatibility hook. The hook changes the fallback shader bindings only during isolated Oculus mirror captures.

The three other loader/version combinations were compiled and archive-checked, not separately run as native clients. Shader-disabled checks still included the installed Iris/Sodium stack; they are not a separate no-mods client run.

Mirror architecture, shader-lighting limitations and future extension boundaries are documented in [planar reflections](planar-reflections.md). These tests do not replace long-session, dedicated-server or arbitrary-modpack testing.

## Previous alpha.32 checks

- All six loader/version targets built successfully. The two universal JARs passed metadata, recipe and fixture-isolation checks; all 14 binary/source archives passed the private-asset audit. New meshes and the radial interface reuse existing materials or code-native geometry, not reference sheets.
- 143 JUnit tests and 267 Fabric 1.20.1 GameTests passed. Added coverage includes all eight roll positions across mounting/output axes, grid-centred optical intake after rotation, thin diagonal collision, corner-to-floor/wall joins, conserved shared LM, both corner collision sheets, movement along the inclined field, packet round trips, mode/slot validation, charge simulation and privacy, generator charging, block rotations/mirrors, and the distinct Thickness II mesh.
- Existing mounting and `rolled` states remain readable; the additional rotation defaults to zero. Bridge packets use a new identifier and Forge/NeoForge network protocol 6. Update both client and server together.
- The solar-damage fixture now has an optically shielded lane. Diagnostics identified a different fixture's redirected ray crossing its targets and adding damage. The turret fixture clears leftover entities from its reused plot and pins its test target against gravity. Production damage and turret behavior were not changed for these test-isolation fixes.
- Native client fixtures passed on Fabric 1.20.1 with Iris/Sodium/Indium/Lithium 0.11.4 (Kappa and shaders disabled), Forge 1.20.1 with Oculus/Embeddium/Kappa, and NeoForge 1.21.1 with Iris/Sodium/Lithium 0.15.4/Kappa. Forge and NeoForge also included JEI. These runs check a four-corner tunnel, synchronized inclined-field collision and every-tick walking, mode release/packet synchronization, native tool-energy simulation and charging, plus the existing logout/rejoin and inventory regressions.
- Inspected English/Russian radial menus, the corner tunnel, diagonal projection, selected-mode tool tint and both thickness models. The radial fixture uses the production renderer and release handler with a simulated held key and cursor, not physical keyboard automation. A final layout-only adjustment reserves space above the hotbar on smaller windows; all six targets and 143 unit tests were rebuilt afterward. The final packaged Fabric 1.20.1 JAR then passed the complete Kappa/Lithium client fixture again, and its smaller-window menu capture was rechecked. Forge/NeoForge client runs preceded that final viewport-only adjustment. Gameplay code was unchanged since the 267-test world run.

Testing used isolated profiles under `build/production-tests`, not the user's installed mods, configs or saves. Fabric/Forge 1.21.1 and legacy NeoForge 1.20.1 were compiled and archive-checked, not separately run as native clients. High-latency dedicated servers and large, long-running bridge networks still require broader testing. Diagonal straight sections deliberately remain single-width projections; cardinal rows retain their three-emitter limit.

One initial Forge client run stopped at the older grower synchronization assertion: the client reported zero LM with the previous mixed color. A repeat with expanded diagnostics passed that assertion and the complete fixture, without changing grower runtime code. The initial intermittent result has not been reproduced; it is not treated as a proven grower fix.

## Previous alpha.31 checks

- All six loader/version targets built successfully. Both universal JARs passed metadata/recipe/fixture checks; all 14 binary/source archives passed the private-asset audit. Reference sheets are not runtime resources, including the new motor material source.
- 138 JUnit tests and 262 Fabric 1.20.1 GameTests passed. Coverage includes paid Mining costs/work inside and outside the emitter, exhausted internal LM, fixed healing across overlapping sources, Thickness II equivalence, one-time lava migration, Tlm slider migration, distance loss across optical segments and server-policy serialization/reset. Mounted bridge tests now exercise both rolls in all six output directions.
- Confirmed the reported `Index -2147483648` crash in Lithium's terrain-only `collectAll` indexing contract. The bridge mixin now drains the actual iterator without that final terrain-index swap whenever synthetic surfaces participate. Native regression cases cover bridge-only and mixed terrain queries, prefetch and partially consumed iterators.
- The final universal JARs passed the expanded LightBridge client fixture on Fabric 1.20.1 with Lithium 0.11.4/Iris/Sodium/Indium/Kappa, Forge 1.20.1 with Oculus/Embeddium/Kappa, and NeoForge 1.21.1 with Lithium 0.15.4/Iris/Sodium/Kappa. Forge and NeoForge also included JEI. The connected-client checks retain every-tick walking, power loss, lift/descent, logout/rejoin, nested storage and synchronized GUI assertions.
- Native item API probes passed on all six faces: simulated/aborted and committed insertion/extraction, private-emitter rejection, upgraded modules, turret equipment, generator fuel and grower ingredients/output. Machine inputs remain protected from output extraction. These probes use Fabric Transfer API and Forge/NeoForge Item Handler, not a direct dependency on AE2 or Mekanism.
- Inspected English/Russian emitter and turret menus, the motor/collector models, and upward/downward bridge projections in both rolls. The final vertical scene is captured with a flying camera; an earlier capture was discarded because its survival-mode camera fell away from the test objects.
- The saber packet-rate test now clears leftover non-player entities from reused GameTest plots, matching the existing cube-test isolation. Those bodies could intercept the first two blade contacts; the production combat implementation was not changed.

Native fixtures ran only in isolated profiles under `build/production-tests`. The user's installed mods, configs and saves were not modified. The other three loader/version targets were compiled and archive-checked, not separately launched. Full AE2/Mekanism transport networks, Kilt cross-loader behavior, dedicated-server latency and long-running modpacks remain outside this test pass. The native shader fixtures do not constitute exhaustive shader-pack coverage.

## Previous alpha.30 checks

- All six loader/version targets built successfully. Both universal JARs passed publication checks; all 14 binary/source archives passed the private-asset audit.
- 137 JUnit tests passed. New scalar-budget regressions cover eight sources sharing one module charge, sequential costs, independent opposing inputs and depleted upstream rays. The explicit 4.6 Mlm minus 608 klm case delivers 3.992 Mlm. Creative flux is bounded and monotonic, with a 1 Mlm default.
- 255 Fabric 1.20.1 GameTests passed. Added cases exercise actual combiners and receivers, additive healing/lift/descent, nested Mining/Damage upgrades, furnace loot and container data, old-slot migration, creative LM and packet validation, and the bridge login checkpoint with expiry and dimension checks.
- The LightBridge native fixture passed with Fabric 1.20.1 (Lithium/Iris/Sodium/Indium/Kappa), Forge 1.20.1 (Oculus/Embeddium/Kappa) and NeoForge 1.21.1 (Lithium/Iris/Sodium/Kappa). Forge and NeoForge included JEI. It checks connected-player movement, loss of power, nested inventories, synchronized menus, lift/lower support, full logout/rejoin and the creative LM readout. English and Russian captures were inspected.
- The fixture also passed in the Fabric 1.20.1 development client without shaders, Lithium or JEI. That faster startup exposed a first-tick race: restoring support in the first server tick was too late for the client. Support is now restored and sent during player spawn, with the tick hook retained as a fallback. The client checks its height on every tick after rejoining.
- Grower captures were inspected with and without Kappa: the mixed-color rays originate from mounted focusing heads and converge on the crystal. Mining upgrade slots, its nine-slot storage, Damage Ignition and the creative slider were inspected in game.
- Existing storage indices and item IDs are retained. Conflicting old Mining items remain extractable instead of being overwritten. New collection geometry reuses a finished atlas; reference sheets are not bundled. All native fixtures use isolated profiles under `build/production-tests`, not the user's mods, configs or saves.

The world regression suite runs on Fabric 1.20.1. Fabric/Forge 1.21.1 and legacy NeoForge 1.20.1 were compiled and archive-checked, not separately run as native clients. These checks do not replace high-latency dedicated-server, long-session or large-installation testing.

## Previous alpha.29 checks

- All six loader/version targets built successfully. Both universal JARs passed publication checks; all 14 binary/source archives passed the private-asset audit.
- 132 JUnit tests passed, including the floor/wall/ceiling bridge bases and flush casing bounds, packet round trips, current module geometry, atlas UVs and resources.
- 246 Fabric 1.20.1 GameTests passed. Added coverage for installed-mode priority (including OFF), downstream-only world changes, insufficient LM, all-axis module interception, the total 512-block range cap, priority through mirror/splitter/combiner, heal/lift/lower behavior, filter persistence, menu packet validation, exact loot collection and overflow, container item data and six-direction grouped bridges.
- The solar cube fixture now traces its own source in isolation. Long beams from other concurrently registered solar fixtures could otherwise claim the cube's single input first, making that unrelated test order-dependent.
- Existing NBT slot indices remain unchanged; old Scorch items survive loading and can be extracted. New module textures reuse neutral regions of the existing atlas instead of duplicating reference sheets.

- The final universal JARs passed the extended LightBridge native fixture on Fabric 1.20.1 (Lithium 0.11.4, Iris/Sodium/Indium/Kappa), Forge 1.20.1 (Oculus/Embeddium/Kappa) and NeoForge 1.21.1 (Lithium 0.15.4, Iris/Sodium/Kappa). Forge and NeoForge included JEI. In addition to bridge walking and loss of power, the fixture checks synchronized Mining/Storage/effect/filter pages, placed-module screens, actual chest-content collection and lift/lower motion of a connected survival player beyond the flight-check interval.
- Inspected native captures of the flush bridge body, inset field, grower rays, new module models and menus. Fabric ran with Russian text; Forge/NeoForge ran with English text. Test profiles are isolated under `build/production-tests`; the user's installed mods, configs and saves were not modified.
- The same extended fixture passed in the Fabric 1.20.1 development client without shaders, Lithium or JEI. Its grower-ray and world-module captures were inspected separately.

Fabric/Forge 1.21.1 and legacy NeoForge 1.20.1 were compiled and archive-checked, not separately run as native clients. These checks do not replace high-latency dedicated-server or long-session modpack testing. The registry selector is not a guarantee for every third-party entity's behavior.

## Previous alpha.28 checks

- All six loader/version targets built successfully. Both universal JARs passed metadata/recipe/fixture-isolation checks; all 14 binary/source archives passed the private-asset audit.
- 131 JUnit tests passed. Additional checks cover the inset bridge bounds and 256-block default, current light balance and preservation of sub-1e-4 optical segments during beam coalescing.
- 234 Fabric 1.20.1 GameTests passed. New cases cover directly touching combiners in all six directions, a touching chain with a turn and a third input, total LM/FE accounting, growth rates above/below 480 klm, fractional save/load, weak sunlight, mixed colors, water costs, output blocking and one-batch-per-tick limits.
- Reproduced alpha.27's client movement failure with Lithium 0.11.4 in the isolated Fabric 1.20.1 profile. Its optimized collision sweeper bypasses the ordinary world hook. The optional compatibility mixin adds bridge shapes to that sweeper without disabling Lithium optimizations.
- The final universal JARs passed the expanded LightBridge native fixture on Fabric 1.20.1 (Lithium 0.11.4, Iris/Sodium/Indium/Kappa), Forge 1.20.1 (Oculus/Embeddium/Kappa) and NeoForge 1.21.1 (Lithium 0.15.4, Iris/Sodium/Kappa). Forge and NeoForge included JEI. Checks include every-tick movement, sustained server support, power loss, synchronized widths, atlas particles, directly touching combiners feeding a grower and live color/speed changes when one source is removed.
- Captured native views were inspected for the slim bridge body, inset field, mixed/red grower illumination and the new speed readout. Tests used isolated profiles, not the user's worlds or installed mod/config files.
- The same expanded fixture passed in the Fabric 1.20.1 development client without shaders, Lithium or JEI; its bridge/grower screenshots were inspected separately.

The three other loader/version targets were compiled and archive-checked, not separately run as native clients. These fixtures do not replace long-session, high-latency dedicated-server or full-modpack testing.

## Previous alpha.27 checks

- All six loader/version targets built successfully with `gradlew.bat build --offline`.
- 129 JUnit tests passed. New checks cover bridge geometry, power/area arithmetic, packet round trips, outward-facing ports and coplanar model seams.
- 230 Fabric 1.20.1 GameTests passed. Bridge regressions cover live LM budgets, solar day/night input, color, grouping and the three-section limit, obstacle shapes, normal entity movement, jumping, edge sneaking, immediate removal and absence of saved ghost power or support blocks/entities.
- All 14 binary/source archives passed the private-asset audit. Both universal JARs passed metadata, recipe, optional-integration and test-fixture isolation checks. The Light Bridge material-authoring task is optional; builds use finished atlases, not reference sheets.
- The final universal JARs passed the LightBridge native client fixture on Fabric 1.20.1 with Iris/Sodium/Indium/Kappa, Forge 1.20.1 with Oculus/Embeddium/Kappa, and NeoForge 1.21.1 with Iris/Sodium/Kappa. Forge and NeoForge also had JEI installed; Fabric ran without it.
- Native fixtures use a real connected client and integrated server to check synchronized widths, walking, continued support beyond the server flight-check interval and falling after power loss. Captured 1-/2-/3-wide views and underside views against the sky were inspected.
- The same LightBridge fixture also passed in the Fabric 1.20.1 development client without shaders or JEI; its final screenshots were inspected separately.

The collision hook uses the vanilla block-collision iterator on `World`. Forge 47's bundled Mixin cannot inject into the inherited interface default; delegating through `CollisionView.super` also fails on that older Mixin runtime. The native Forge fixture checks the working direct-iterator implementation, not just compilation.

Fabric/Forge 1.21.1 and legacy NeoForge 1.20.1 were compiled and archive-checked but did not receive this native client run. The world regression suite runs on Fabric 1.20.1; it is not a separate dedicated-server run for every loader. Long-session, high-latency multiplayer and large bridge installations still need broader testing.

## Previous alpha.26 checks

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
