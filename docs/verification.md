# Build and verification

Target: **2.0.0-alpha.37**. Build and regression checks on 2026-09-19.

## Build from source

Use JDK 21 or 22 to run the Gradle wrapper. The compiled Minecraft 1.20.1 targets require Java 17; 1.21.1 targets require Java 21. The first build downloads dependencies.

```text
gradlew.bat build
gradlew.bat runGameTest
```

On Linux/macOS, use `./gradlew`. `build` compiles all six loader/version targets, runs unit tests and the Fabric 1.20.1 world regression suite, produces two universal JARs and audits both binary and source archives. `runGameTest` can also run the world suite on its own. Outputs are in `build/libs`; test worlds and reports stay under `platforms/<target>/build`.

The public checkout contains the finished textures, models and OGG recordings. Normal builds do not need reference images, source MP3/WAV files, FFmpeg, an installed Minecraft instance or the optional material-authoring tasks. Test fixture sources are included for reproducibility but are excluded from production JARs.

## Alpha.37 checks

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
