# Changelog

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
