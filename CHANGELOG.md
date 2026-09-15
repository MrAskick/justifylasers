# Changelog

For the public update from alpha.6, see the [short alpha.21 release notes](docs/releases/2.0.0-alpha.21.md). The entries below retain the individual development changes.

## 2.0.0-alpha.21

- Replaced saber audio with 22 recorded OGG sounds: equip, five idle variants, nine swings, three surface contacts, and separate single/staff ignition and retraction.
- Fixed independent hand audio, clipped sound tails and repeated ignition during sound reloads. Surface audio works with scorch marks disabled and is rate-limited during sustained contact.
- Retained existing blade-clash sounds and weapon mechanics; removed four unused synthesized saber recordings.

## 2.0.0-alpha.20

- Rebuilt ore inclusions as separated, faceted minerals with larger crystals and smaller chips; removed overlapping stacked cubes.
- Added full-silhouette clearance and neighbor-face culling. Ore textures, generation, hardness and loot are unchanged.

## 2.0.0-alpha.19

- Increased chamber water opacity to about 70%; corrected stretched panel UVs and full-multiblock render bounds.
- Rebuilt the tablet frame; fixed preview face orientation and rotation, formalized Russian prompts, and made screen power drain active only while its interface is open.
- Replaced schematic models with framed cards carrying cached, flat item thumbnails.
- Added independent dual-wield gun/saber controls: right-click for the physical right hand, left-click for the left; disabled shared dual-gun aiming.
- Randomized ore inclusion placement and rotation without changing density, vein generation or loot.
- Corrected Crystal Mount breaking particles to use its own frame material.
- Replaced legacy Laser Chassis/Optical Assembly with the new housing/lens components, preserving old stacks. Assigned distinct time and energy costs to all 26 assembly recipes.

## 2.0.0-alpha.18

- Enlarged and doubled ore inclusions. Added diamond-style small, buried and large veins: more accessible Wolframite, rarer Photonite; retained Fortune and Silk Touch.
- Fixed overlapping chamber/casing geometry and material mapping. Turned assembly arms inward and increased their movement.
- Added chamber security/redstone tabs, all-face item/fluid access, cycling ingredient ghosts and centered titles. Matched Crystal Mount collision to laser crystals.
- Added five modeled laser components and their schematics; updated device/module assembly recipes to use them. Schematics now display their output item.
- Added craftable Blank Schematics and the rechargeable Extraterrestrial Tablet: interactive handheld display, rotatable 3D previews, world clock, battery meter and server-validated schematic recording.
- Updated English/Russian guides. Tablet discovery structures are not included yet; existing generator/smelter WIP restrictions remain.

## 2.0.0-alpha.17

- Fixed LightSaber slot-switch crashes and third-person grips. Added server-timed directional fencing, short combos, frontal guards, timed parries, stamina, guard breaks and blade clashes with colored sparks and sound.
- Rebuilt ores with resource-pack stone/deepslate and protruding 3D mineral inclusions; added black Wolframite ingots and volumetric raw minerals.
- Replaced single-block growth/assembly machines with 2×2×2 casing multiblocks, animated interiors and emitter-style interfaces. Added a visible, consumable water tank and native item/fluid/energy automation.
- Added bare Photonite Crystals and placeable Crystal Mounts. Mount a grown crystal before using or dyeing it.
- Moved 21 advanced-device/module recipes into schematic-driven assembly and added real JEI recipe categories. Schematics are reusable and Creative-only for now.
- Moved Wolframite smelting to a normal furnace (150 seconds). Removed Fuel Generator/Electric Smelter recipes and marked both WIP; preserved existing items and machines.

## 2.0.0-alpha.16

- Fixed first-person saber visibility with F1, camera-pole flips, yaw-dependent rolling and clipping into nearby walls. Added Laser Gun-style movement inertia.
- Added two alternating LightSaber cuts and a three-hit Light Staff combo using the opposite blade, with smooth recovery between inputs.
- Rebuilt both hilts with angular geometry, component-mapped textures and blade-colored emissive details. Renamed Laser Saber to LightSaber; kept its item ID.
- Added Wolframite and Photonic Crystal ores, a Fuel Generator, Electric Smelter, Crystal Growth Chamber and animated Assembly Chamber.
- Added heat-resistant alloys, chassis and optics. Raw crystals must be grown; powered emitters are assembled with a preinstalled crystal, not crafted at a workbench.
- Enabled standalone industrial progression by default, with configurable machine costs/times, native energy transfer, inventory automation, recipe unlocks and English/Russian guides. Existing blocks and parts remain valid; legacy mode is configurable.

## 2.0.0-alpha.15

- Added craftable Laser Saber and double-ended Light Staff: nine colors, retractable blades, fast melee sweeps, impact sparks, surface scorch trails and dedicated sounds. No energy required yet.
- Added server-validated melee with attack-rate limits, armor/resistance checks, wall occlusion and modest knockback. The staff trades attack speed for a wider sweep.
- Added a rebindable client-settings key (J), with cube/scope lens toggles and adjustable 1–3× cube magnification; changed the default to 1.8×.
- Added local controls for lens distance/count, scorch visibility/distance, weapon sway, saber sparks, sound volume and voice limits.
- Added component-mapped hilt models and rounded, white-core blades with shader emission; updated English/Russian controls and JEI guides.
- Preserved existing emitter energy costs, optical routing, inventories and gun/turret mechanics. Private reference sheets are excluded from release archives.

## 2.0.0-alpha.14

- Fixed removed Laser Guns remaining visible on turret stands; inventory updates now clear empty slots.
- Replaced patchy cube emission with continuous color-matched rings and guides; removed the dark shader-glass cover.
- Added depth-tested 1.28x convex cube lenses, preserving the white-hot core and foreground occlusion with shaders.
- Fixed remaining splitter corner seams and matched the redstone receiver's GUI scale to the emitter.
- Rebuilt the Configurator with an open fork, suspended core and component-mapped materials from the new artwork.
- Added inventory-sync, emission-mask, seam and paired-image optical regression checks. Recipes, energy and beam mechanics are unchanged.

## 2.0.0-alpha.13

- Fixed overlapping module panels and flickering corner caps; removed hidden coplanar faces.
- Added block-specific breaking-particle textures for crystals, modules and optical hardware; emitter particles follow their selected panel texture.
- Restored shader-compatible scope magnification: 1.5× camera focus and 2.5× total magnification through the lens. Fixed the lens pass interfering with vignette blending.
- Removed walking bob and movement sway while aiming; retained firing recoil and normal hip-fire handling.
- Rebuilt the refocusing cube, mirror and six-port splitter with component-mapped textures, neutral idle materials and beam-colored emission.
- Preserved cube physics, port configuration, optical routing, saved settings and energy costs.

## 2.0.0-alpha.12

- Removed 49 unused textures and losslessly optimized the remaining PNGs, reducing universal JARs by about 6 MB.
- Separated private reference sheets from public sources and added build-time archive checks to prevent accidental packaging.
- Fixed shader-washed scope colors with a dedicated final-image lens pass; retained magnification, aiming and weapon handling.
- Rebuilt the energy receiver with mapped chassis panels, raised details, configurable sockets and neutral beam-colored emission. It turns gray and stops glowing without an incoming beam.
- Preserved port settings, energy conversion, inventories and gameplay balance.

## 2.0.0-alpha.11

- Rebuilt gun, turret, crystal and module textures from individually selected reference components.
- Corrected per-face UVs, cylindrical wraps, crystal tips and color-specific layouts; removed repeated panel projections.
- Preserved shader emission, transparent crystal rendering and all gameplay mechanics.

## 2.0.0-alpha.10

- Fixed translucent crystals disappearing against the sky with shaders, and scorch marks drawing over nearer beams without shaders.
- Added cooling surface scorch trails to the Laser Gun; walls behind hit entities remain untouched.
- Added aim-down-sights, a transparent 2.5× screen-space lens, a dedicated reticle and smoother look/movement inertia.
- Changed gun controls to hold Attack for fire, hold Use for aiming, and sneak + Use for color selection. Turret mounting is unchanged.
- Replaced gun/crystal/turret textures with the supplied nine-color artwork and matching LabPBR maps; rebuilt the turret's base and angled support.
- Added validated server-side fire input with a lost-input timeout; retained damage, energy, inventory and optical settings.
- Updated English/Russian weapon guides.

## 2.0.0-alpha.9

- Added a craftable Laser Gun with nine colors, continuous fire, two-handed first/third-person handling, recoil and configurable damage/range.
- Added a rotating turret stand with a gun slot, Target Filter slot, owner-only settings and named-player exclusions. Guns and turrets require no charge yet.
- Made the Target Filter craftable without technical mods; added English/Russian weapon controls and guides.
- Reworked metal materials and UVs, slimmed the wrench head, made crystals translucent, and fitted item models to GUI slots. Mirror items now use the same round mesh as placed mirrors.
- Fixed mirror rear geometry/halo clipping and beams drawing over nearer custom models without shaders.
- Restored white beam/cube cores on Forge/Oculus by repairing universal-JAR manifest wrapping and mixin discovery.
- Isolated Forge/NeoForge classes, mixins and optional Jade/JEI discovery in universal JARs to prevent cross-loader crashes.
- Fixed a rare server crash when optical components register or disappear during the network's tick update.
- Preserved emitter/receiver models, saved settings, optical mechanics and powered-emitter energy costs.

## 2.0.0-alpha.8

- Added Forge 1.21.1 and legacy NeoForge 1.20.1 targets, plus one Fabric/Forge/NeoForge universal JAR per Minecraft version.
- Replaced the configurator with a modeled sci-fi wrench.
- Rebuilt the splitter as a six-port cross. Configure each face as input, output or disabled; enabled outputs share incoming power.
- Rebuilt the energy receiver with configurable laser-input, energy-output and disabled faces. Closing a port also stops extraction through existing cable connections.
- Added beam-colored accents and shader emission to both optical models; without an input beam they turn gray and stop glowing.
- Preserved existing splitter layouts and saved settings; updated English/Russian hints and guides.
- Fixed the Forge 1.21.1 world-render transform and a remapped Fabric 1.21.1 GUI-opening method conflict.

## 2.0.0-alpha.7

- Added crystal beam recoloring, additive mixing, and floor/wall/ceiling mounting.
- Added adjustable mirrors, three-way beam splitters, and energy receivers with conversion loss; split branches share power and creative beams produce no FE.
- Added a crafted configurator for rotation, settings-only copy/paste, mirror aiming, and trajectory previews.
- Added the Target Filter module and settings for mob categories, players, owner exclusion, and named-player exclusions.
- Replaced beacon sounds with laser start/loop/stop/contact audio, thickness-dependent pitch, separate volume, and a source limit.
- Added optional Jade status overlays and detailed English/Russian JEI guides. Crystal recipes now work without technical mods.
- Preserved existing module slot IDs, emitter settings, energy costs, cube controls, and beam rendering materials.

## 2.0.0-alpha.6

Changes since 1.4.0:

- Added Forge 1.20.1 and Fabric/NeoForge 1.21.1 support.
- Added energy-powered emitters, configurable consumption, and Mekanism cable support.
- Added crafted color crystals and modules for range, thickness, mining, damage, drops, Silk Touch, scorch marks, and ignition.
- Added a translucent powered-emitter GUI with a 3D preview, energy display, security, redstone controls, and redesigned buttons.
- Added placeable 3D crystals and modules, with colored shader emission from crystals.
- Added mining settings: speed up to one block/tick, Silk Touch, drops, and scorch visibility.
- Removed the unlimited emitter's survival recipe in technical mode; it remains available in creative.

## 2.0.0-alpha.5

- Fit all inventory rows and the hotbar inside the powered emitter's frame, keeping the supplied translucent GUI artwork and existing controls.
- Replace the flat module and control-circuit icons with ten distinct 3D models: drill, damage core, range units, focusing lens, circuit board, Silk Touch cage, collector, and thermal capsules.
- Make all nine crystals and ten components placeable decorations. Placement faces the player; survival mining returns the same part, and creative removal creates no extra drops.
- Reuse the crystals' colored shader-emission materials for placed models. Decorations have depth-tested geometry and collision, without adding Minecraft block light or energy consumption.
- Keep existing item IDs, translations, recipes, stack limits, module slots, saved emitter settings, and energy balance.
- Add placement, loot, collision, geometry, GUI-boundary, and cross-loader client regression checks.

## 2.0.0-alpha.4

- Replace the nine flat crystal items with a shared 3D model: an eight-sided crystal, pointed crown, beveled metal base, four retaining brackets, and color-matched light inserts.
- Add individual crystal materials and shader-compatible emission maps. Keep the metal frame neutral and render the model in inventory slots, both hands, item frames, and dropped-item form.
- Preserve crystal IDs, recipes, colors, stack limits, saved items, and emitter behavior. No gameplay or energy-balance changes.
- Add geometry/resource regression tests and client checks for all colors, resource reloads, and world item rendering.

## 2.0.0-alpha.3

- Use the supplied alpha-channel GUI texture, remove the black exterior, and make powered-emitter panels translucent without fading text, items, or the 3D preview.
- Draw the powered GUI background once on Minecraft 1.21.1, avoiding doubled opacity. Keep the existing layout and controls.
- Add an operating surcharge per installed upgrade: +1 energy unit/tick for each Range I or Thickness module, +8 for each Range II module. Add both slots' costs to the existing consumption, including at maximum range; removing upgrades removes their surcharge.
- Synchronize the full operating cost to the GUI and update English/Russian module hints. Keep creative emitters free and inactive/blocked emitters uncharged.
- Add regression coverage for genuine texture alpha, stacked-module charges, save/load, live menu synchronization, insufficient power, and overflow safety.

## 2.0.0-alpha.2

- Replace the powered emitter's interface with a blue high-tech control panel, a rotatable 3D color preview, energy gauge, operating cost, and status. Keep the player inventory visible across subpages.
- Add owner-based Public/Private access control without a Mekanism dependency. Enforce access on the server for controls, installed parts, player mining, and sided automation; allow energy input in private mode.
- Add redstone control to the powered emitter: ignore (default), require signal, or invert. Do not consume energy while blocked by redstone.
- Expand the module inventory to nine slots. Require dedicated Block Destruction and Entity Damage modules to unlock those capabilities and their settings.
- Add stackable range and thickness modules. Range I adds one block per module; eight around a new Control Circuit craft Range II, adding eight each. Cap range at 512 blocks and thickness at 10× with a full stack.
- Start unupgraded powered emitters at 1 block / 0.10×. Preserve old crystals, modules, energy, and switches when loading alpha.1 saves; the new capability/geometry modules must be installed separately.
- Restyle mining and damage settings, retain their energy scaling, and update English/Russian localization. Keep the original/creative emitter interface and world beam rendering unchanged.

## 2.0.0-alpha.1

- Add separate builds for Fabric and Forge on Minecraft 1.20.1, and Fabric and NeoForge on Minecraft 1.21.1.
- Add optional technical mode, detected from installed energy mods or selected in the server configuration. In this mode the original emitter becomes the Creative Laser Emitter and loses its survival recipe; existing blocks and settings remain intact.
- Add the Powered Laser Emitter, accepting Team Reborn Energy on Fabric and native energy capabilities on Forge/NeoForge, including Mekanism Universal Cables.
- Require one of nine crafted color crystals to operate the powered emitter. The crystal sets the beam and model color; it is reusable and does not wear out.
- Add crafted modules for Silk Touch, block drops, scorch marks, and entity ignition. Install or remove them in the new Energy & modules menu; unavailable options are locked on the server as well as in the GUI.
- Show stored energy, capacity, operating cost, and missing requirements in English and Russian. Save installed parts and energy with the block; return the installed parts when it is broken.
- Make capacity, input limits, technical-mod detection, and operating costs configurable. Mining speed, damage per hit, hit frequency, knockback, and ignition increase energy demand. Stop the beam and its gameplay effects when power runs out.
- Retain the standalone gameplay without technical mods, including redstone controls, receivers, portable cubes, mining settings, and independently configurable shader/Minecraft lighting.
- Port the existing emissive materials and depth-tested white core/halo rendering to the new loaders and Minecraft version; check Kappa with Iris and Oculus.
- Add energy, native-loader, Mekanism cable, GUI, and network regression checks. This is a prerelease; back up worlds before upgrading.

## 1.5.0

- Add a settings button beside Block destruction, opening a dedicated mining submenu.
- Configure mining speed from the original hardness-based timing to one block per world tick; keep the original speed as the default.
- Add optional Silk Touch and block-drop controls using vanilla loot. Suppress items, experience, and container contents when drops are disabled.
- Add a per-emitter scorch-mark toggle, independent of block destruction. Turning it off clears only that emitter's marks, including refocused trails.
- Save and synchronize all four settings, preserve defaults in existing worlds, and add a separate mining-settings reset.
- Keep unbreakable blocks and receiver input lenses protected at every speed.
- Add English/Russian interface text and regression tests for mining timing, loot, containers, synchronization, and scorch visibility.

## 1.4.0

- Add surface scorch marks: incandescent grooves with soft, irregular soot edges, continuous trails from moving impacts, and spots from stationary beams.
- Cool fresh marks over 2.5 seconds and retain the dark scar for about two minutes with a gradual fade.
- Clip marks to block collision faces, including slabs and steps; remove marks when their supporting blocks change and protect receiver input lenses.
- Render scorch marks with depth-tested occlusion in vanilla and Iris; use the incoming emitter's shader-emission setting for hot material lighting and reflections.
- Keep marks cosmetic and client-side, with bounded geometry, chunk/world cleanup, and no changes to damage, mining, lighting levels, or the existing beam and cube profiles.
- Add regression tests for surface clipping, winding, cooling, persistence limits, refocused trails, and cache eviction.

## 1.3.0

- Add a per-emitter range slider from 1 to 512 blocks; retain the 64-block default for existing saves.
- Apply the configured range to the full refocused path, rendering, damage, mining, and receiver detection. Stop at unloaded chunks without loading them.
- Render long beams independently of the source chunk's visibility and subdivide long vanilla beams for correct fog interpolation.
- Replace the cube's internal beam spokes with a luminous sphere, a white-hot center, a soft colored halo, and rotating plasma filaments.
- Preserve shader lighting and reflections through the existing emissive-material and depth-tested halo passes.
- Rotate carried cubes around their center with collision checks; smoothly settle released cubes onto a flat face without changing their horizontal heading or controls.
- Extend English/Russian GUI text and regression tests for range, networking, saving, and cube settling.

## 1.2.0

- Add a portable Refocusing Cube with gravity, solid entity collision, body pushing, kicking, and server-controlled carrying.
- Accept laser input on five faces and redirect it through one marked output, following the cube's yaw and pitch.
- Preserve beam color, width, damage settings, ignition, mining, and shader emission through cube chains; allow redirected beams to activate receivers.
- Share the 64-block range across the complete optical path, stop closed loops, and select one stable output for competing inputs.
- Add a hollow industrial model with six lenses, color-changing indicators, and an illuminated core; render it consistently in the world and inventory.
- Save cube orientation and color, release missing carriers safely, and add a recipe and English/Russian control hints.
- Reuse the approved beam rendering profile for all segments and add optical, physics, and interaction regression tests.

## 1.1.0

- Add optional entity ignition to the damage settings panel, off by default. Resetting damage settings also disables ignition.
- Add Laser Receiver: a thin, six-directional sensor panel that accepts a laser from the front and outputs redstone on its other five faces.
- Configure receiver signal strength (0–15), inversion, color filtering, a master switch, and shader emission.
- Match the receiver's indicator textures to the incoming laser color and reuse the emitter's LabPBR materials.
- Protect the receiver's front from laser block destruction; detect reception independently of entity damage and hit rate.
- Add a crafting recipe, creative-tab entries, English and Russian interface text, and regression tests for ignition and redstone behavior.

## 1.0.2

- Add a damage settings panel next to the Entity damage toggle.
- Configure damage per hit (0.1–20 HP), knockback (0–10×), and hit rate (1–20 hits/s at 20 TPS) independently for each emitter.
- Save settings with the emitter and preserve the previous defaults in existing worlds.
- Show base damage per second, support precise keyboard adjustments, and add a reset-to-defaults button.
- Send slider values through a validated packet without vanilla's one-byte button ID limit.

## 1.0.1

- Apply 0.5 HP of laser damage every game tick, respecting armor and other vanilla defenses.
- Add gentle, capped knockback along the beam direction, respecting knockback resistance.
- Remove ignition from beam hits so fire does not add damage after contact.
- Detect hits when the beam starts inside an entity's hitbox.
- Add English and Russian laser death messages.
