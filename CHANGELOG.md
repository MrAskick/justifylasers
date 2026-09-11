# Changelog

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
