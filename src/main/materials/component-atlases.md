# Component texture sources

The original reference sheets supplied by Mr.Askick are private local art inputs. Gun, turret, crystal and module sheets are stored in the matching folders under the ignored `ref/private_material_sources/` directory. Receiver sheets remain in `ref/modules/Reciever/`. Neither directory is part of the public source tree or a production build's resource inputs.

Normal builds use finished atlases checked into `src/main/resources/assets/justifylasers/textures/component/`. No reference sheets are needed to build a public checkout. `verifyPublishedAssets` inspects distributable and source JARs for private inputs and obsolete textures.

`component-parts.json` and `turret-parts.json` identify selected panels, lenses, housings and light inserts in source-pixel coordinates. Crystal and turret layouts differ between colors, so each has its own crop plan and UV metadata. Captions, reference renders and layout dividers are not sampled by model surfaces.

The optional `generateComponentAtlases` task in `gradle/component-atlases.gradle` packs these components into power-of-two atlases with four-pixel extruded gutters. Rectangular crops retain their native aspect ratio and resolution; the long lens-barrel strip is uniformly downsampled. The cyan tip is rotated out of its diamond layout, and the orange tip is assembled from its labeled triangular piece.

Generated resources use `textures/component/<kind>/<variant>.png`, alongside UV-layout JSON and LabPBR `_n` / `_s` maps. Module atlases use `module/<item-id>/default`. Original sheets are not bundled in the mod JAR.

`generateEnergyReceiverMaterials` uses `receiver-parts.json` to select the receiver's chassis, sockets, corner caps and edge strips. The off sheet supplies neutral albedo. The white glow mask selects illuminated pixels; their intensity comes from the on sheet with its baked color removed. The renderer tints only this mask with the exact incoming beam RGB. A separate non-emissive indicator material respects the existing shader-emission setting.

Art tasks write to `build/generated/`, not the runtime source tree. After reviewing their output, use `java tools/OptimizeTextures.java <generated-texture-directory> <runtime-texture-directory>` to install the finished assets. The tool preserves dimensions and every decoded RGBA pixel, validates the result and only replaces an in-place PNG when its encoding is smaller. Do not copy reference sheets into resources.

Model faces select named components in local coordinates before rotation. Cylindrical sides use a continuous wrap; caps and crystal tips share planar UV fields. Small metal parts crop their material grain, while light strips retain their colored borders. These materials do not create Minecraft block light.

`generateOpticalComponentMaterials` crops cube, mirror, splitter and Configurator parts listed in `optical-parts.json`. The cube's continuous light channels have explicit geometry and a uniform neutral mask; on/off image differences must not be used to locate these thin rings. Its optical windows sample the final lit scene in the renderer, not a dark baked glass image.

The Configurator uses selected jaw, grip, control, core and trim components from `ref/modules/Configurator/UV_01.png`. Contiguous extruded jaw sections share one UV field. The reserved off-state sheet is not used: changing tool mode does not turn its lights off. None of these original reference sheets enter runtime or source JARs.

The nine module references cover destruction, damage, both range tiers, thickness, circuitry, silk touch, collection and scorch marks. Ignition shares thermal hardware while retaining its cooling fins. The target filter shares range/circuit hardware and retains its targeting reticle; no separate sheets were supplied for these two items.

Visual review: `:fabric-1.20.1:runClientSmoke -PclientSmoke -PsmokeTextures` captures all modules and all nine crystal, turret and gun colors from three angles. The weapon and optics fixtures additionally exercise the models in a world.

`generateSaberMaterials` uses `saber-parts.json` and the single/staff UV and white glow sheets in `ref/modules/LightSaber/new/`. It packs cropped grip, casing, control, shroud, vent and light components into separate 512×512 atlases. Only explicit light faces use the neutral glow material; their RGB comes from the active blade. The industrial machine meshes reuse these compact metal materials. Their recipe components explicitly register the same base texture with the block sprite atlas.

Ore blocks use the active resource pack's stone/deepslate sprites and modeled mineral inclusions. `generateIndustryMaterials` crops the authored Wolframite/Photonite facets from `ref/Blocks/Ores/`; the old combined stone/ore PNGs are excluded from builds. Each face has three elongated faceted minerals and nine smaller chips, with silhouette clearance between pieces. Four deterministic layouts avoid identical patterns; changing their geometry does not change their material artwork.

`industry-parts.json` also maps chamber bodies/casings from `ref/Blocks/`, five laser components from `ref/parts/` and tablet rails, case panels and indicator inserts from `ref/Tablet/`. These are selected component crops, not complete reference layouts. Red component accents are neutralized before packing. Thin beveled panels use cuts small enough to keep their cap polygons convex; chamber corners are owned once instead of duplicated by adjacent face assemblies. `ChamberSeamsTest` checks same-plane polygon overlap, including bevel triangles.

The tablet display is live geometry and text, not a screenshot of the reference. Its item preview uses the registered Minecraft renderer. Text retains the captured hand projection and, with shader packs, is composed after tone mapping so screen glyphs are not interpreted as PBR surface lighting. No additional world render or full-screen image buffer is needed.
