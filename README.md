# Justify Lasers

Configurable laser emitters, optical components, portable refocusing cubes, laser guns, automated turrets and laser blades, with an ore-to-laser industrial progression and standalone FE and photon-powered progression; native energy integration with technical modpacks remains optional.

Author: Mr.Askick.

[Changes since alpha.21](docs/releases/2.0.0-alpha.26.md) · [Подробное руководство на русском](docs/guide.ru.md) · [Build and verification](docs/verification.md)

## Installation

Install one universal JAR for your Minecraft version: `JustifyLasers-1.20.1-<version>-universal.jar` or `JustifyLasers-1.21.1-<version>-universal.jar`. Each supports Fabric, Forge and NeoForge. Loader-specific JARs are also built; use either the universal JAR or the matching loader-specific JAR, never both. Use the same mod version on the client and server.

| Minecraft | Loader | Required dependencies | Energy interface |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | Fabric Loader 0.19.5+, Fabric API | Team Reborn Energy 3, bundled |
| 1.20.1 | Forge | Forge 47.4.0+ | Forge Energy |
| 1.20.1 | NeoForge (legacy) | NeoForge 47.1.106, Java 17 | Forge-compatible Energy |
| 1.21.1 | Fabric | Fabric Loader 0.19.5+, Fabric API | Team Reborn Energy 4, bundled |
| 1.21.1 | Forge | Forge 52.1.0+ | Forge Energy |
| 1.21.1 | NeoForge | NeoForge 21.1.200+ | NeoForge Energy |

Minecraft 1.20.1 targets Java 17; 1.21.1 targets Java 21. Architectury API is not a runtime dependency. Mekanism and shaders are optional. Different loaders cannot be mixed on the same server merely by installing this mod.

NeoForge 1.20.1 is a legacy target and shares the Forge implementation. Prefer Forge for a new 1.20.1 modpack; contemporary NeoForge support is on 1.21.1. A universal JAR does not bundle a loader, Fabric API, shaders or technical mods, and it cannot make incompatible third-party mods run together.

**2.0.0-alpha.26 is a prerelease. Back up worlds before upgrading or changing loaders. Empty Electric Smelters before upgrading: that machine is removed. Rebuild large solar collectors using the new layout.** Other registry IDs and saved settings are preserved, but this does not make another mod's world data portable between loaders. Minecraft worlds cannot safely be downgraded from 1.21.1 to 1.20.1.

## Beam Combiner and recipe navigation

The **Beam Combiner** merges up to five incident beams into one output, retaining **95% of their total luminous flux** per combiner. The Energy Receiver converts that combined flux with its usual efficiency. The Configurator cycles each face through input, output and disabled; selecting another output returns the previous output to input. Incoming range is not renewed, and loops cannot repeat a source's energy budget. Output color is flux-weighted; mirrors, splitters, crystals and refocusing cubes remain compatible. `beamCombinerEfficiency` controls the loss (default `0.95`). The combiner requires its Assembly Chamber schematic.

With **JEI** installed, click a machine's processing arrow to open all its recipes. The smaller **Help** button below the grower's water tank opens **Multiblock Construction**, which shows both 2×2×2 chambers and the 3×3×3 + 4 Solar Concentrator, with a 3D preview, selectable layers, top views and exact component counts. Find it through the machine's recipes/usages or the construction button in chamber/concentrator screens. JEI remains optional.

## Solar progression

All content is available without installing another technical mod. The early route is ores → workbench Fuel Generator and Assembly Chamber → looted/traded Tablet Schematic → assembled and charged tablet → Small Solar Concentrator → LM-powered crystal growth → laser devices and large solar installation. Diamonds, quartz and an Eye of Ender are needed along the way; raw photonite must be grown and mounted before emitter assembly.

The new **Small Solar Concentrator** fits one block, tracks the sun and supplies up to **16 klm**, with a 32-block beam. Its tablet schematic assembles three Solar Absorbers, one Electric Motor and three Iron Ingots, costing 56,320 FE over 44 working seconds. The motor has a temporary model and uses a workbench recipe: `ICI / CRC / ICI` (iron, copper, redstone). Each absorber still costs 26,400 FE to assemble.

**Crystal Growth Chambers consume only light**, not FE. Aim at either lower lens on any horizontal face. A batch requires 12 klm continuously for 600 working ticks, plus raw photonite, two quartz and 1000 mB water. Inputs from multiple beams sum; excess light is not stored or used to accelerate the recipe. Night or insufficient light pauses progress. The generator is not a substitute for an optical input.

The **large 3×3×3 + 4 collector** now has a dish model. Its bottom is nine small concentrators, its middle has four housings, four controllers and an energy core, and its top is eight small concentrators around absorbing glass. Attach one outward-facing Optical Resonator to the center of each of the four **bottom-layer** sides. The dish tracks the sun in world coordinates and uses a continuous panel grid; leave clearance above the body for its moving rim. Incorporated small collectors stop generating individually. Break the structure and they can work separately again. A structure cannot absorb another owner's private small collector.

Use the Configurator's Rotate mode on an exterior horizontal bottom face to choose the single laser output. Both sizes have emitter-style GUIs with live luminous flux, status, power, redstone and privacy. The large collector peaks at **480 klm**, 30× one small collector and about 1.76× the combined flux of its 17 small components. It retains a 256-block beam, 3× width and up to 0.5 HP/tick damage with armor/resistance support. Small beam damage is proportionally weaker.

Sun height smoothly controls output; shading, night and weather matter. A default Energy Receiver converts a small source to up to **12 FE/tick**, a large one to **384 FE/tick**. The large collector exceeds a base emitter (80 klm), but not a maximally upgraded one (656 klm). FE conversion is a gameplay ratio, not a physical equivalence. Mirrors, crystals, cubes, splitters and combiners remain compatible; an optical share cannot be consumed twice. Creative beams have no spendable FE or LM.

**Laser-Absorbing Glass returns to the workbench**, with the same four glass, two Wolframite Ingots and one grown Photonite Crystal; it no longer has a schematic. It passes sunlight but stops lasers and cannot be laser-mined. Soft incoming sunlight shafts can be disabled under J → Effects without stopping generation.

Alpha.26 makes generator output thermal: lava reaches 1200 °C / 95% and a net 128 FE/tick; sticks cap at 300 °C. Heating takes 0.2 °C/tick, cooling is three times faster. The detailed casing has live instruments. Fuel type, residual heat and fractional FE survive reload. Stock alpha.25 output migrates from 32 to a 128 FE/tick peak, with a `justifylasers.pre-alpha26.json.bak` backup; custom rates remain. Add the three missing lower resonators to old alpha.25 collectors. Earlier layouts additionally need 17 small collectors instead of absorbers. The Electric Smelter remains removed.

[Recipes, layer diagrams, balance and migration details (Russian)](docs/solar-concentrator.ru.md).

## Client settings

Press **J** in a world to open the client settings panel; rebind it under **Controls → Justify Lasers**. Changes apply immediately and are saved to `config/justifylasers-client.json` when the menu closes. They affect only your rendering and audio, not the server's damage, energy or optical simulation.

- **Optics:** toggle cube lenses, set their magnification from 1× to 3× (default **1.8×**), toggle the Laser Gun's scope lens, and limit lens distance or the number of visible lens cubes. Disabling the scope lens retains normal aiming and its 1.5× camera focus.
- **Effects:** toggle surface scorch marks, adjust their rendering distance, disable idle/walking weapon sway, and toggle saber impact sparks. Disabling marks clears existing local decals; it does not stop block destruction. Attack animations and gun recoil remain enabled when sway is off.
- **Audio:** set a local laser/saber volume multiplier and the maximum number of sound voices. The lower of the local and common voice limits applies.

For busy installations, reduce lens distance/count and scorch distance first. Turning cube lenses off skips their scene-copy and optical passes entirely. The cube lens samples the finished frame, so it works with shader-processed colors but cannot show off-screen objects or add detail beyond the source image. **Reset all** restores every client preference.

## LightSaber and Light Staff

LightSaber and Light Staff work in **either hand**. With a single weapon, **V** ignites/retracts the blade (rebindable); **right-click** holds a frontal guard and ignites an inactive blade. **Sneak + right-click** cycles the nine colors. **Left-click** attacks; holding it repeats cuts within server timing and stamina limits. Move sideways for horizontal cuts, forward with a strafe for diagonals, or stand still for the alternating overhead/rising combo.

The one-handed **LightSaber** deals 6 HP before reductions, with 3 windup, 4 active and 5 recovery ticks. The **Light Staff** deals 6.5 HP, with 4/5/6 ticks and a wider three-cut combo whose final cut uses the opposite blade. A swing can hit each target once, up to two targets for the single blade or four for the staff; additional targets take 75% damage. Armor, toughness, Resistance, absorption, shields and attack-damage modifiers apply. Damage follows the server's moving blade during its active phase, stops at walls and cannot be accelerated by packet spam.

Guard covers a 110° frontal cone, never the rear. Raising it within 3 ticks of contact parries, briefly deflects the attacker and opens a faster, 15%-stronger counter. Blade collisions interrupt cuts with recoil, colored sparks and a dedicated contact sound. The default 100-point stamina pool pays 12 per single-blade attack or 20 per staff attack, 18 per normal block and 4 per parry. Holding guard also drains stamina; exhaustion breaks guard. Short stagger immunity prevents repeated full-duration recoil locks. A small HUD shows stamina and attack phase separately for each equipped saber. Damage, costs and timings are configurable in `config/justifylasers.json`.

First-person rendering follows the native hand depth pass, hides with F1, remains stable at vertical camera angles and uses the Laser Gun's movement inertia. Both views share combat poses; the staff grip stays within reach of both hands. Existing weapon IDs are unchanged.

Both weapons retain nine-color modeled hilts, rounded white-core blades, shader emission, hum/swing sounds and surface scorch trails. Marks are cosmetic. Weapons currently use **no energy, ammunition or durability**, but now require the corresponding **Assembly Schematic** and an Assembly Chamber; old workbench recipes are removed.

### Dual wielding

With a Laser Gun, LightSaber or Light Staff in **each hand**, **Use / right-click controls the physical right hand** and **Attack / left-click controls the physical left hand**, including Minecraft's left-handed setting. Both buttons can be held together. Guns, sabers and mixed pairs have independent triggers, attacks, colors and server validation. **V** toggles both held sabers.

Dual wielding replaces aiming and guarding with the second weapon's attack; the guns no longer converge into a shared aiming pose. Temporarily leave one weapon equipped to change its color with sneak + Use. A single weapon retains the controls below.

## Laser Gun and turret

Hold **Attack / left-click** to fire the Laser Gun, **Use / right-click** to aim through its sight, and **sneak + Use** to cycle its nine colors. These follow Minecraft's rebound Attack/Use keys. The sight has a transparent lens and its own reticle. Aiming smoothly focuses the world camera by 1.5×; the lens adds magnification to reach 2.5× relative to the unaimed view. It samples the finished world frame after shader processing, not a second world camera, so the lens cannot add detail beyond that frame's resolution. Walking bob and movement sway stop while aiming, but firing recoil remains. Normal handling includes two-handed support, equip motion, spring-damped look inertia, strafing/landing movement and sprint lowering; third-person aiming follows the player's view. It works without another gun mod.

Gun impacts leave the same cooling surface marks as emitters. Sweeping the beam draws a trail; hitting an entity stops it from marking the wall behind that entity. These marks are visual only and do not break or permanently alter blocks. Gun, crystal and turret materials use separate nine-color atlases with LabPBR emission maps. The turret stand has a tiered base and angled rotating supports.

The server applies damage, armor/Resistance/shield checks and directional knockback. Defaults are 64 blocks, 0.5 HP per hit, 20 hits/s and 1× knockback. Shots stop at the first eligible living entity, block collision or unloaded chunk; they do not load chunks. Weapon beams currently travel straight: they do not mine blocks or use the emitter's optical redirect network.

Place a **Laser Turret Stand**, then right-click it with a Laser Gun to mount the weapon. Right-click again to open its inventory and controls. The stand tracks targets with a rotating mount and fires only when aligned and unobstructed. A friendly entity in front blocks the shot. The default range is 32 blocks; without a filter, only hostile mobs are targeted.

Install a **Target Filter Module** in its second slot to select hostile mobs, passive creatures and players independently, exclude the owner, or toggle up to 32 named-player exclusions. Enter an online player's name and press **± Player** to add them; entering the saved name again removes the exclusion, including offline. Only the owner or a creative player can change settings. The power switch, weapon color, inventory, owner and filter persist through saves. Breaking the stand drops its installed gun and filter once.

**Guns and turrets are currently unlimited-energy prototypes.** They need neither charge nor ammunition. This does not change powered-emitter energy costs. Guns, stands and Target Filters now require their Assembly Chamber schematics; their old workbench recipes have been removed.

## Using the emitter

Find **Laser Emitter** in the **Justify Lasers** creative tab or the Redstone tab. Place it facing any of the six directions, then right-click to open its controls.

- Enable or disable the beam.
- Ignore redstone, require a signal, or run only without a signal.
- Choose one of nine colors and adjust beam thickness from 0.10× to 10.00×.
- Set the beam range from 1 to 512 blocks, in one-block steps.
- Toggle shader emission and Minecraft block lighting independently.
- Enable block destruction and entity damage separately.

New emitters start enabled, with both lighting options on. Block destruction and entity damage are off by default. Settings are saved per emitter and synchronized with the server. The interface supports English and Russian.

Beam range defaults to 64 blocks, including in existing saves. The beam stops at block collision shapes and unloaded chunk boundaries; it never forces chunks to load. Long beams therefore require the corresponding chunks to be loaded on the server and visible to the client. Its entity hit radius scales with its thickness. Unbreakable blocks stop the beam, regardless of mining speed.

The small button next to **Block destruction** opens per-emitter mining settings:

- **Mining speed:** the minimum is the original hardness-based mining time and remains the default. Increasing the slider shortens that time; the maximum mines one breakable block per game tick, or 20 blocks/s at 20 TPS. A single emitter cannot mine multiple blocks in the same world tick, including through refocusing cubes. The menu shows the resulting mining time for stone.
- **Silk Touch:** use vanilla Silk Touch loot where supported, including stone, glass, and ores. Off by default; requires block drops to be enabled.
- **Block drops:** enabled by default. Disabling removes items and experience from directly mined blocks, including container contents. With drops enabled, chest contents spill normally and shulker boxes retain their contents without duplication. Normal neighbor updates still apply to attached blocks.
- **Scorch marks:** enabled by default and independent of block destruction. Disabling clears this emitter's existing scorch marks and stops it leaving new ones, including through cubes. Marks from other emitters are unaffected.

Mining settings are saved and synchronized per emitter. Existing saves keep the previous mining speed, normal drops, and scorch marks. **Reset defaults** in this submenu resets only these four settings; it does not change the destruction switch or damage settings. Receiver input lenses remain protected from mining.

The small button next to **Entity damage** opens per-emitter damage settings: 0.1–20 HP per hit, 0–10× knockback, 1–20 hits per second at 20 TPS, and optional entity ignition. Defaults remain 0.5 HP, 1× gentle knockback, and 20 hits/s (10 HP/s before damage reduction), with ignition off. Zero knockback disables the push without disabling damage. Settings are saved with each emitter; existing worlds keep the previous defaults. Arrow keys adjust sliders one step at a time, and **Reset defaults** restores all four damage settings.

Armor, armor toughness, Protection enchantments, Resistance, absorption and shields use Minecraft's normal damage rules. The beam applies a capped push along its direction and respects knockback resistance. With ignition enabled, successful, unblocked hits set entities on fire for up to four seconds; this adds normal fire damage and respects fire immunity and fire protection. Ignition requires **Entity damage** to be enabled. Hit frequency is distributed across game ticks, including rates that do not divide evenly into 20; lower server TPS slows it proportionally.

## From ores to the first laser

Industrial progression is enabled by default without requiring Mekanism. **Wolframite Ore** uses diamond-sized veins with a height distribution shifted up 32 blocks: it occurs from the bottom of the Overworld up to Y 48, with its height curve peaking at Y -32. **Photonite Ore** uses the deeper diamond height curve, reaching Y 16, with fewer vein attempts. Wolframite is somewhat more common than diamonds; Photonite is slightly rarer. Both have deepslate variants, require an iron-tier pickaxe and support Fortune/Silk Touch. Ore blocks use the current resource pack's stone/deepslate with protruding 3D mineral inclusions and LabPBR emission maps. New ores appear only in newly generated chunks.

A normal furnace smelts one Raw Wolframite into one **Wolframite Ingot** in **150 seconds** (3,000 ticks at 20 TPS). New component recipes use this ingot; legacy Heat-Resistant Alloy items remain registered. The Fuel Generator now has a workbench recipe: a Blast Furnace above an Electric Motor, with Wolframite Ingots to its left, right and below. Place it against a formed Assembly Chamber for cable-free power. Electric Smelters are removed.

| Machine | Inputs and result | Default energy / time |
| --- | --- | --- |
| Fuel Generator | Furnace fuel; charging slot for tablet; returns empty fuel containers | Produces 32 units/tick; vanilla fuel duration |
| Crystal Growth Chamber | Raw Photonite + 2 quartz + 1,000 mB water → Photonite Crystal | Continuous 12 klm for 600 working ticks; no FE |
| Assembly Chamber | Reusable schematic + its materials → selected device/module | Per schematic: 24–464/tick for 120–1680 ticks |

Build each chamber from **eight matching casings in a solid 2×2×2 arrangement**. The last placed casing forms the multiblock; right-click can also form an existing arrangement. Every member opens the controller's inventory. The grower's shared tank holds **8,000 mB** by default, accepts water buckets or native fluid pipes and consumes water progressively. Its visible water level follows the tank. Removing a casing stops processing; controller contents remain owned by that controller rather than being duplicated across members.

The grower produces a bare **Photonite Crystal**, not a laser-ready item. Craft a **Crystal Mount**, then combine it with the grown crystal to obtain a **White Laser Crystal**. The mount can also be placed as decoration. Dye the mounted crystal as before; emitter assembly accepts any of the nine colors and installs the supplied crystal in the finished emitter.

Connect cables and pipes to any formed casing, on any face. Assembly chambers share a 100,000-unit FE buffer by default; growers reject FE and need light instead. Items can be inserted and results extracted from all six sides; fluid ports accept water only. Native Fabric Transfer and Forge/NeoForge item/fluid/energy interfaces allow automation without a hard dependency on an automation mod. The right-hand tabs control privacy and redstone: ignore (default), require power, or require no power. A signal on any casing controls the whole structure. Private chambers restrict access and breaking to the owner/admin and disable item/fluid automation. Full output, missing power or a disabled redstone condition pauses processing. Inventory, water, energy, ownership and progress persist across saves. Unloaded structures do not force-load missing chunks.

The grower enlarges and rotates its crystal amid brief violet/blue electrical arcs. The assembly chamber moves two articulated manipulators around the selected output, with active emissive details. Completing a powered emitter produces a brief calibration beam; this beam is visual only and cannot damage entities or blocks.

Insert a matching **Assembly Schematic** before loading its ingredients. Schematics are reusable and show a cached, flat 2D thumbnail of the resulting item on their card. Empty ingredient slots display dimmed previews, cycling through allowed alternatives such as crystal colors; previews are not real items. The chamber has **30 assembly recipes**, including five foundational components: **Optical Resonator**, **Reinforced Laser Housing**, **Energy Core**, **Focusing Lens Assembly** and **Beam Controller**. These have their own schematics and distinct recipes, and are used in appropriate combinations by emitters, weapons, optics and modules. Casings, mounts and basic electronics still use workbench recipes. The obsolete Laser Chassis and Optical Assembly are replaced by Reinforced Laser Housing and Focusing Lens Assembly; old stacks convert on entering a player's inventory, retaining quantity and custom data. **JEI** shows machine recipes, ingredient counts, schematics, water, duration and energy. Processing recipes use the data-pack type `justifylasers:industrial`; global costs, buffers, water requirements and timings are configurable. Every assembly schematic has its own duration and power draw. See [assembly costs](docs/assembly-costs.md) for the defaults and global scaling.

### Extraterrestrial Tablet

Use the tablet to interact directly with its handheld display. Choose a design on the left; drag the actual 3D item preview on the right to rotate it. Mouse wheel/page buttons browse the catalog; Enter records the selected schematic. The display shows world time and actual battery percentage. Escape or the inventory key closes it; F1 hides the tablet with the hand view.

New tablets are discharged. Their capacity is **50,000 energy units**, accepting up to **256 per transfer** in a compatible item charger. The Fuel Generator has a dedicated charging slot, taking priority over adjacent consumers. Alternatively, sneak-use a powered machine to transfer its stored energy into the tablet. Its open interface uses **1 unit per tick** (20/second), with no screen drain after closing; recording costs **1,000 units** and consumes one **Blank Schematic** from the inventory. Craft blank cards with `PPP / IRL / PPP`: paper, an iron ingot, redstone and lapis lazuli. Recording is validated on the server and does not consume resources when the card, charge, recipe or inventory space is missing. Both blank and recorded cards face the player when held in either hand.

Find the **Tablet Schematic** in dungeon (20%), shipwreck treasure (25%), mineshaft (15%), desert pyramid (15%), jungle temple (20%), stronghold library (35%) or ancient city (25%) chests. These are independent chances per newly generated chest loot, not replacements for vanilla treasure. Assemble a tablet with one each of **Control Circuit, Eye of Ender, Wolframite Ingot and Gold Ingot**: 30 seconds at 144 energy/tick, 86,400 total. The schematic is reusable and can also be recorded by a charged tablet, allowing copies to be traded on finite multiplayer worlds. A first tablet/schematic still has to survive somewhere; there is no recovery recipe if every copy is destroyed.

Control Circuits now require diamonds instead of gold nuggets; Crystal Mounts require iron ingots instead of nuggets. The Fuel Generator is craftable; the Electric Smelter is removed.

When upgrading an existing workshop, empty the old grower/assembler before forming its larger casing structure. Registry IDs are retained; old single-block chambers cannot process until formed.

## Energy-powered lasers

All machines, parts and recipes are available regardless of installed technical mods. Legacy `energyMode`, `industrialProgression` and `technicalMods` settings no longer control availability. External mods may still provide compatible FE power and automation.

The original block is always **Creative Laser Emitter** and is no longer craftable. Previously placed emitters are not removed or converted. Their unlimited operation remains available for creative builds. The separate **Powered Laser Emitter** is made in the Assembly Chamber and appears in the Justify Lasers tab together with its parts and machines.

1. Place the powered emitter and supply energy through any face. With Mekanism, connect a Universal Cable to an energy cube's configured output. The powered emitter does not use redstone as its power source.
2. Open **Modules** to inspect or replace the installed color crystal. Emitters made in the Assembly Chamber already contain the crystal used to build them; other emitters need one in the first slot. No crystal means no beam, even with a full buffer. Switching crystals changes the beam and model color. Crystals are not consumed during operation.
3. Install the modules in their labeled slots. The settings buttons next to the mining and damage slots unlock only when their respective module is installed. Then enable the capability and adjust its settings.

The powered emitter has a dedicated translucent blue control-panel interface, with a transparent exterior around its shaped frame. Text, items, and the 3D preview remain opaque and readable. Its main page shows a draggable 3D emitter preview with the crystal's beam color, operating cost, stored energy/capacity, and status. The preview displays the selected color even when the real emitter is off. The player inventory stays visible when switching pages. **Block light** and **Shader glow** are independent switches; they retain the original Minecraft lighting and shader-emission behavior.

**Redstone control** cycles between ignoring the signal (default), requiring a signal, and requiring no signal. Redstone is a control input, not an energy source. A blocked emitter consumes no energy.

**Security** shows the owner and switches between Public (default) and Private access. Only the owner or an operator with permission level 2 can change access. Private access checks opening the menu, settings packets, slot clicks, shift-clicks, player mining, and sided inventory automation. Energy cables can still supply it. Existing ownerless emitters are claimed on their first non-spectator interaction. This is machine access control, not a land-claim system: explosions, commands, and non-player world edits are not protected.

| Module | Unlocks |
| --- | --- |
| Silk Touch | Vanilla Silk Touch loot; also requires the Block Drops module and its switch |
| Block Drops | Items and experience from directly mined blocks |
| Scorch Marks | Cosmetic surface burns, including refocused trails |
| Ignition | Setting damaged entities on fire |
| Block Destruction | Mining and its settings; off until enabled in the submenu |
| Entity Damage | Entity damage and its settings; off until enabled in the submenu |
| Range I / II | +1 / +8 blocks and +1 / +8 energy units per tick per module; up to 64 in the range slot |
| Thickness | +1 energy unit per tick per module; up to 64, reaching 10× at a full stack |
| Target Filter | Hostile/passive/player targeting, owner exclusion and up to 32 named-player exclusions |

Modules are reusable upgrades, not consumables. Only range and thickness modules stack; other slots hold one part. Different range tiers cannot share a stack. Only the matching part fits each slot; shift-click moves it to the correct slot. Settings are remembered when a module is removed, but its capability immediately becomes unavailable. Installing a module does not automatically turn on block destruction or entity damage. Without the drops module, mined blocks do not drop items or experience.

An unupgraded powered emitter has a range of 1 block and thickness of 0.10×. Range is `min(512, 1 + count × tier bonus)`; 64 tier-II modules reach the 512-block cap. Thickness uses the existing logarithmic scale, from 0.10× with no modules to 10× with 64. The range still applies to the whole refocused path and never loads chunks. These module rules replace the powered emitter's manual range/thickness sliders; the original/creative emitter keeps its sliders and defaults.

Upgrading from alpha.1 preserves the crystal, the four original module slots, energy, and switches. Existing powered emitters now need the new modules for mining, entity damage, range, and thickness. No upgrades are granted automatically.

The menu shows stored energy, capacity, cost per tick, and why the beam is stopped. Energy is charged once per server tick for the enabled settings, even if the beam currently hits no entity or mineable block. Faster mining and higher damage, hit rate, or knockback cost more. Ignition adds a cost only with its module and switch enabled. Each Range I or Thickness module adds 1 energy unit/tick; each Range II module adds 8. The two slots' costs add together, including the last Range II module at the 512-block cap. Shortening the beam with an obstacle does not reduce the installed-module cost. Color and lighting do not change consumption. Cubes share their source emitter's power budget; they do not charge again for each segment. A disabled emitter, one blocked by redstone, or one without a crystal consumes nothing. Insufficient energy stops the beam, mining, damage, and receiver activation.

Energy and installed parts survive world saves and chunk reloads. Breaking the emitter returns installed parts as items; stored energy is lost. Powered emitters and their recipes are available without external technical mods.

All nine crystals have a 3D crystal-and-metal housing, visible in menus, either hand, item frames, and as dropped items. The colored crystal and indicator inserts have emissive shader materials; the housing remains metallic. They do not place light blocks or change the emitter's power cost. Existing crystal items keep their IDs, colors and single-item stack limit, and work without being regrown.

Modules and the Control Circuit also have individual 3D models. Right-click a block with a laser crystal, module or Control Circuit to place it; sneak-right-click when placing against a block that opens a menu. Mining a decoration in survival returns the same reusable part without Silk Touch. Creative removal produces no drop. Modules remain decorations; placed crystals also act as optical color filters. Crystal tips face away from their mounting surface, including walls and ceilings. All five assembly components are also placeable blocks and retain their assembly uses.

Placed crystals have translucent colored facets and retain their shader emission; their metal frame remains opaque. Kappa with Iris/Oculus can show bloom and colored surface lighting from their materials; the result depends on the shader pack and its settings. This does not add ordinary Minecraft block light. Crystals, powered emitters, energy receivers and all upgrade modules are available without technical mods.

### Recipes

The powered emitter has no workbench recipe: use the Assembly Chamber and a grown, mounted crystal. Combine a grown Photonite Crystal with a Crystal Mount to make a white laser crystal; dye it for the chosen color. Violet uses purple dye.

Modules require their Assembly Schematics. Their ingredients reflect their purpose: lenses for range/thickness, Beam Controllers for targeting/mining/damage, and specialized materials for Silk Touch, drops, scorch marks and ignition. See JEI for the exact ingredient counts and current data-pack recipes; the old workbench module recipes are no longer used.

Craft a Control Circuit using `RDR / DQD / RDR`: four redstone dust, four diamonds, and central quartz. The Advanced Range schematic combines eight Range I modules with one Control Circuit in the Assembly Chamber.

### Server configuration

The first launch creates `config/justifylasers.json`. Edit it with the game/server stopped, then restart. Multiplayer clients do not determine server costs or availability.

```json
{
  "machineCapacity": 100000,
  "machineTransfer": 512,
  "generatorPerTick": 128,
  "generatorHeatPerTick": 2,
  "smallSolarPeakFlux": 16000,
  "smallSolarBeamRange": 32,
  "solarPeakFlux": 480000,
  "crystalGrowthFlux": 12000,
  "assemblyPerTick": 160,
  "crystalGrowthTicks": 600,
  "laserAssemblyTicks": 400,
  "capacity": 2000000,
  "maxInput": 100000,
  "basePerTick": 80,
  "miningPerTick": 120,
  "miningSpeedMultiplier": 20.0,
  "damagePerHealthPoint": 40.0,
  "knockbackPerHit": 8.0,
  "ignitionPerHit": 10.0,
  "energyTransmissionEfficiency": 0.8,
  "laserVolume": 0.65,
  "maxLaserSoundSources": 8,
  "laserGunRange": 64,
  "laserGunDamage": 0.5,
  "laserGunHitsPerSecond": 20,
  "laserGunKnockback": 1.0,
  "turretRange": 32
}
```

Other mods must implement the platform's energy interface for power transfer; no direct Mekanism dependency is required. Alpha.26 migrates the former stock generator/solar values once, making a config backup first. Missing new keys use defaults; explicit nonstock values are retained.

Weapon limits: range 1–512 blocks, damage 0–100 HP, 1–20 hits/s and 0–10× knockback. Turrets use the same damage settings with their separate range of 1–128 blocks. Missing weapon keys in an existing config use the defaults above; the existing file is not rewritten. Add the keys to change these values. Weapon and emitter sounds share the configured voice/volume budget.

Amounts use the native platform's energy units (FE on Forge/NeoForge, E on Fabric). `maxInput` limits each transfer call, not the total across all cables in a tick. Operating cost is rounded up to a whole unit:

```text
basePerTick
+ rangeModuleCount * rangeTierBonus + thicknessModuleCount
+ [when mining] miningPerTick * miningSpeedMultiplier^(speedSlider / 100)
+ [when damage is enabled] hitsPerSecond / 20 *
    (damageHP * damagePerHealthPoint + knockbackMultiplier * knockbackPerHit
     + [when ignition is enabled and installed] ignitionPerHit)
```

With defaults and no range/thickness upgrades, a visual-only beam uses 80 units/tick. Mining uses 200 at minimum speed and 2,480 at maximum speed. Enabling default damage adds 28 units/tick. A full stack of Range II and a full stack of Thickness add 576 units/tick, making a visual-only beam use 656. Upgrade surcharges are fixed; existing configurable base/mining/damage rates are unchanged. Capacity, input limit, and base cost must be positive; other costs must be finite and non-negative, and the mining multiplier must be at least 1. Invalid configuration stops startup with the file path and does not overwrite the file.

## Optical components

- **Crystals:** a beam passing through a placed crystal changes color only after the crystal. Right-click switches replacement/additive mixing; red plus blue gives magenta. The mode is saved in the placed block.
- **Laser Mirror:** mount on a floor, wall or ceiling. Right-click rotates yaw by 15°; sneak-right-click changes tilt. The actual mirror normal determines reflection. Use the configurator for precise point-to-point aiming.
- **Beam Splitter:** a six-armed cross with a port on every face, including top and bottom. Each port can be an input, output or disabled. New splitters have one input and five outputs. Incoming power is shared equally among enabled outputs: five outputs each receive 1/5, three receive 1/3, and a single output receives the full branch. With no outputs the beam stops. Damage, knockback, new burning time, mining progress and transferable energy are divided, not duplicated. Branches keep their remaining range; the emitter still mines at most one block per world tick.
- **Laser Energy Receiver:** each face can accept a laser, export energy, or be disabled. Aim a powered or solar beam into an input lens. It converts the branch's luminous flux using `floor(lm / lumensPerEnergyUnit × energyTransmissionEfficiency)`; default efficiency is 80%. Emitter mining/damage costs do not increase that flux. Right-click displays incoming lumens and actual energy input. Only energy-output faces connect to extraction; changing a connected face immediately closes its old output. Obstacles or an inactive source stop new transfer; the buffer remains available through enabled outputs. Creative beams color the receiver but produce no energy. Efficiency must be strictly between 0 and 1. Automatic output is capped at `maxInput` per tick across all output faces.

Use the configurator's **Rotate / configure ports** mode on a face to cycle **Input → Output → Disabled**; sneak-right-click cycles backwards. Inputs show a lens, splitter outputs show outward markings, receiver outputs show energy contacts, and disabled faces have a shutter. Port settings survive saves and synchronize to clients. Existing alpha.7 splitters retain their previous three-output layout on first load; enable their other faces explicitly if desired.

Splitter and energy-receiver accents follow the incoming beam, including mixed colors. With simultaneous inputs, the strongest incoming branch determines the displayed color; ties have a stable source order. Without a valid input beam, the models turn gray and stop glowing, even if a receiver still stores energy. Their shader emission follows the incoming emitter's emission setting; they do not add Minecraft block light.

Optical paths include travel inside components, stop at unloaded chunks, and are limited to 16 redirects and 128 segments. A repeated optical component terminates that branch to prevent loops. Exact mixed colors propagate through mirrors, splitters and cube cores; the redstone receiver uses the nearest of its nine selectable colors.

Mirrors, splitters and energy receivers require their Assembly Schematics and an Assembly Chamber. Their former workbench recipes are removed. See JEI for materials and [assembly costs](docs/assembly-costs.md) for processing costs.

## Configurator and target filter

Make the **Laser Configurator** in the Assembly Chamber using its schematic, a Wolframite Ingot, quartz, redstone and a Control Circuit. Right-click air to cycle its modes:

| Mode | Right-click a block |
| --- | --- |
| Rotate / configure ports | Cycle the clicked splitter/energy-receiver port; sneak to reverse. Other emitters/redstone receivers still rotate toward the clicked face |
| Copy settings | Store an emitter's settings and select its trajectory preview |
| Paste settings | Apply supported settings to another accessible emitter |
| Aim mirror | Select a mirror being hit by a beam, then select a destination point; sneak-click selects another mirror |

Holding the tool previews the emitter under the crosshair, or the copied emitter within 64 blocks in the same dimension. Preview is depth-tested and has no gameplay effects. Copy/paste never transfers energy, inventory, module stacks, ownership or privacy. Uninstalled modules remain locked and private emitters reject unauthorized use.

Install **Target Filter** in the powered emitter's tenth slot and open its settings button. Select hostile mobs, passive creatures and players independently; owner exclusion is a separate switch. Enter an online player's name to exclude their UUID. Re-enter the saved name to remove it, even offline. The list is capped at 32 and survives saves; changing a player's name does not remove their exclusion. Hostile classification follows the entity type's monster spawn group. Filtering also prevents ignition and knockback, but does not boost damage or reduce energy costs. Removing the module restores unfiltered targeting.

## Sound and optional tooltips

The laser has its own start, seamless four-second idle loop, stop and surface-contact sounds. Thickness lowers the pitch and increases presence. Set `laserVolume` from 0 to 1 in the local `config/justifylasers.json` (0 mutes lasers), and `maxLaserSoundSources` from 1 to 64. The limit includes loops and transient effects; nearby sources are prioritized and mixed more quietly when several play. The regular Blocks/Master sound sliders still apply. These two settings are client-local, not overridden by a multiplayer server. Existing configuration files use the new defaults if keys are absent.

Sabers use 22 recordings in `assets/justifylasers/sounds/saber/`: equip, five idle variants, nine swings, three surface-contact variants, and separate ignition/retraction for the single blade and staff. Each hand has its own activation state and hum; drawing a weapon plays its equip sound, while changing color does not. Surface audio still works when cosmetic marks are disabled. Sustained contact is rate-limited, and one-shot sounds retain their natural tails.

The four original laser recordings and `saber_clash.ogg` remain directly under `assets/justifylasers/sounds/`. Resource packs can override the files and events in `sounds.json`. All shipped recordings are OGG Vorbis; normal builds need neither FFmpeg nor private MP3/WAV inputs. The optional `tools/GenerateLaserAudio.ps1` and `tools/ImportSaberAudio.ps1` handle audio authoring. The client menu's volume multiplier applies on top of `laserVolume`.

**Jade** optionally shows stored energy, operating cost, range, status/stop reason and receiver input. Private emitter details are hidden by this mod's provider. **JEI** optionally provides localized information pages for crystals, modules, optical blocks and the configurator. Neither is required or bundled. Their APIs are compile-only; use `-PtooltipMods` to add the pinned Jade/JEI versions to development runs.

## Scorch marks

Laser contact leaves a scorched spot on a block. Moving the impact point, for example by aiming a refocusing cube, draws a continuous burn across the surface. The fresh groove has a white-yellow center and orange edges surrounded by soot. It cools over roughly 2.5 seconds after contact ends; the dark scar remains for about two minutes and fades during the last 20 seconds. Width follows the emitter's beam thickness.

Marks follow the actual collision surface, including slabs and steps, and are clipped at block edges and gaps. Breaking or replacing the supporting block removes its marks. The receiver's front sensor is protected. This is a cosmetic effect: it works with block destruction disabled and does not change blocks, hardness, drops, entity damage, or Minecraft light levels. Hot marks use the emitter's shader-emission setting for lighting and reflections in compatible packs.

Scorch marks are temporary client-side effects, not saved world damage. Only impacts observed in loaded chunks are recorded; changing worlds or unloading their chunks clears them. Geometry is capped and old marks are discarded under heavy use. Neither tracing nor drawing marks loads extra chunks.

## Using the receiver

Find **Laser Receiver** beside the emitter in the **Justify Lasers** creative tab or the Redstone tab. Production requires its Assembly Schematic; JEI lists the current materials and energy cost.

Aim an emitter at the receiver's front sensor, within the beam's configured range. The receiver accepts a beam only from that face and outputs redstone on the other five faces, including through adjacent solid blocks. Obstructions stop reception. The front sensor withstands beams with block destruction enabled.

Right-click the receiver to configure:

- A master switch, which always turns off the output when disabled.
- Output signal strength from 0 to 15.
- Inversion: output when no matching beam is detected instead of while one is present.
- A color filter: accept any color or one specific laser color.
- Shader emission for the colored indicators, independent of redstone output and without Minecraft block light.

New receivers are enabled, accept any color, and output strength 15 without inversion. The model follows the incoming laser's color, even when it does not match the filter, and retains the last received color after the beam stops. The GUI displays the incoming color and actual output strength. All receiver settings are saved per block and synchronized with the server. Reception is independent of the emitter's entity damage and hit rate.

## Using the refocusing cube

The **Refocusing Cube** is a physical entity, not a block or an inventory-only effect. It has gravity and solid collision, can be pushed by players and mobs, and continues to interact with lasers while carried. Its five input lenses redirect a beam through the single output lens, marked with a double brass rim. The hollow metal frame, lens indicators, and illuminated core follow the incoming color.

Find the cube in the **Justify Lasers** or Redstone creative tab. Production requires its Assembly Schematic, a Reinforced Laser Housing, six Focusing Lens Assemblies, a laser crystal and an Optical Resonator. Use the resulting item on a block to place the entity.

| Control | Action |
| --- | --- |
| Right-click the cube | Carry it in front of you; click again to release |
| Look around while carrying | Aim the output, including vertically |
| Attack the cube | Kick it; sprint for a stronger kick |
| Sneak + right-click | Rotate an unheld cube by 90 degrees and set it upright |
| Sneak + attack | Collect it into your inventory; creative mode removes it |

Carrying respects walls and other entities. Rotation follows your view around the cube's center and stops at obstacles. After release, the cube smoothly settles onto the nearest flat face while preserving its horizontal heading. This also works on slabs; no external physics library is required. Only one player can carry a particular cube, and each player can carry only one cube. Releasing, losing the carrier, or reloading the world leaves it as a free physical object. Its position, orientation, and last color are saved.

When illuminated, the center contains a luminous sphere with a bright white core, a soft color-matched halo, and rotating plasma filaments. Its shader emission follows the incoming emitter's emission setting without changing Minecraft block light.

Refocused beams retain the emitter's color, width, damage, hit frequency, knockback, ignition, block destruction, and shader-emission settings. They can activate receivers and pass through chains of cubes. The entire optical path shares the emitter's configured range, including travel inside the cubes. Cycles stop at a previously visited cube, with an additional limit of 16 refocuses. With simultaneous inputs, a fixed emitter-position ordering selects one outgoing beam without color flicker; other incoming rays stop at the cube. A beam hitting the output face stops without being redirected.

## Lighting and shaders

Minecraft lighting controls the emitter's block light (level 15). Shader emission controls the beam's emissive material and the lit model textures; it does not toggle Minecraft lighting or hide the beam. Model textures follow the selected laser color.

For the strongest visual effect, use a compatible shader pack such as Kappa. Lighting and reflections depend on the active pack and its settings; they are not hardware ray tracing supplied by this mod. Without shaders, the beam uses the vanilla rendering path.

The Kappa 5.3 scene has been checked with Iris 1.7.6 + Sodium 0.5.13 + Indium 1.0.36 on Fabric 1.20.1; Oculus 1.8.0 + Embeddium 0.3.31 on Forge 1.20.1; and Iris 1.8.8 + Sodium 0.6.13 on both Fabric and NeoForge 1.21.1. These versions describe the tested combinations, not mandatory shader dependencies or a guarantee for every pack. Forge 1.21.1 and legacy NeoForge 1.20.1 have no verified shader combination here; do not use a shader-loader JAR meant for another platform or Minecraft version.

## Development

Use JDK 21 or 22 to run the Gradle 8.14.3 wrapper (verified locally with Corretto 22.0.2). Point IntelliJ's Gradle JVM or `JAVA_HOME` at that JDK; Java 26 cannot run this wrapper. Compilation targets Java 17 for Minecraft 1.20.1 and Java 21 for 1.21.1.

```text
gradlew.bat build
gradlew.bat test
gradlew.bat runGameTest
gradlew.bat runClient
```

On Linux or macOS, use `./gradlew`. `build` compiles/remaps all six targets, runs the Fabric 1.20.1 regression suite, collects six loader-specific JARs, and merges two universal JARs in `build/libs`. `mergeJars` rebuilds both universal JARs without running the full test suite. Forgix combines the loader implementations and deduplicates shared assets; each loader still uses its own networking and energy adapter. The short `test`, `runGameTest`, `runClient`, and `runServer` commands target Fabric 1.20.1. Select another target explicitly, for example `gradlew.bat :neoforge-1.21.1:runClient` or `gradlew.bat :forge-1.21.1:build`.

Legacy NeoForge 1.20.1 runtime checks need Java 17; pass `-PtestJavaHome=<jdk-17-directory>` while running Gradle with JDK 21/22. The Forge 1.21.1 development runtime uses `gradle/forge-modern-runtime.gradle` to separate Loom's merged Minecraft/Forge modules. This does not modify the distributed mod JAR or the user's Forge installation.

Shared gameplay is in `src/main`, rendering/screens in `src/client`, Minecraft API bridges in `versions/<minecraft-version>`, and loader hooks in `platforms/<loader-version>`. Dependency versions live in the root and platform `gradle.properties` files. Unit tests are in `src/test`; the full world regression suite is in `src/gametest`. Test worlds and build outputs are isolated under each platform's `build` directory.

`gradle/laser-materials.gradle` generates LabPBR maps and lit texture variants. Edit source textures in `src/main/resources/assets/justifylasers/textures/block`, not generated copies. `gradle/laser-parts.gradle` generates part models, recipes, and recipe-book entries and translates resource paths/conditions for each loader and Minecraft version.

Crystal geometry is defined in `LaserCrystalModel`; its immutable mesh is built once and shared by all colors. Item transforms are in `assets/justifylasers/models/item/crystal.json`. `gradle/crystal-materials.gradle` generates the crystal, steel, and indicator materials; resource packs can replace those textures at their normal asset paths. No external rendering library is required.

For a local shader check, `runClient -PcompatibilityModsDir=<mods-directory>` adds Iris, Sodium and Indium from that directory. This development setup also expects Iris's nested ANTLR, GLSL Transformer and JCPP runtime JARs under `run/compat-libs`; they are not bundled with the mod.

Native-loader integration checks and client smoke tests are documented in [the test notes](docs/development/2.0.0-testing.md). Their fixtures and optional third-party mods are not included in release JARs.

Current results, reproducible commands and remaining release checks are in [build and verification](docs/verification.md). Finished runtime assets are included; private reference sheets, local authoring records, installed-game launch helpers, worlds and build outputs are not part of the public source snapshot.

## Contributing

Bug reports and pull requests are welcome. For larger changes, open an issue first. Run `gradlew.bat build` (or `./gradlew build`) before submitting a pull request.

## License

Licensed under the [MIT License](LICENSE.txt). Third-party dependencies retain their own licenses.
