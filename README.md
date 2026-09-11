# Justify Lasers

Configurable laser emitters, redstone receivers, and portable refocusing cubes, with optional energy-powered lasers for technical modpacks.

Author: Mr.Askick.

## Installation

Use exactly one JAR matching both Minecraft and the loader, on the client and server:

| Minecraft | Loader | Required dependencies | Energy interface |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | Fabric Loader 0.19.5+, Fabric API | Team Reborn Energy 3, bundled |
| 1.20.1 | Forge | Forge 47.4.0+ | Forge Energy |
| 1.21.1 | Fabric | Fabric Loader 0.19.5+, Fabric API | Team Reborn Energy 4, bundled |
| 1.21.1 | NeoForge | NeoForge 21.1.200+ | NeoForge Energy |

Minecraft 1.20.1 targets Java 17; 1.21.1 targets Java 21. Architectury API is not a runtime dependency. Mekanism and shaders are optional. Different loaders cannot be mixed on the same server merely by installing this mod.

**2.0.0-alpha.6 is a prerelease. Back up worlds before upgrading or changing loaders.** Registry IDs and saved settings are preserved, but this does not make another mod's world data portable between loaders. Minecraft worlds cannot safely be downgraded from 1.21.1 to 1.20.1.

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

## Energy-powered lasers

Without a supported technical mod, the original emitter and its recipe work as before. By default, installing a mod with one of the IDs `mekanism`, `techreborn`, `modern_industrialization`, `powah`, `thermal`, or `oritech` enables technical mode. The server's mode controls recipes and is synchronized to clients. The list is configurable; merely installing an energy API does not enable it.

In technical mode, the original block becomes **Creative Laser Emitter** and is no longer craftable. Previously placed emitters are not removed or converted. Their unlimited operation remains available for creative builds. The separate **Powered Laser Emitter** becomes craftable and appears in the Justify Lasers tab together with its parts.

1. Place the powered emitter and supply energy through any face. With Mekanism, connect a Universal Cable to an energy cube's configured output. The powered emitter does not use redstone as its power source.
2. Open **Modules** and insert a color crystal into the first slot. No crystal means no beam, even with a full buffer. Switching crystals changes the beam and model color. Crystals are not consumed.
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

Modules are reusable upgrades, not consumables. Only range and thickness modules stack; other slots hold one part. Different range tiers cannot share a stack. Only the matching part fits each slot; shift-click moves it to the correct slot. Settings are remembered when a module is removed, but its capability immediately becomes unavailable. Installing a module does not automatically turn on block destruction or entity damage. Without the drops module, mined blocks do not drop items or experience.

An unupgraded powered emitter has a range of 1 block and thickness of 0.10×. Range is `min(512, 1 + count × tier bonus)`; 64 tier-II modules reach the 512-block cap. Thickness uses the existing logarithmic scale, from 0.10× with no modules to 10× with 64. The range still applies to the whole refocused path and never loads chunks. These module rules replace the powered emitter's manual range/thickness sliders; the original/creative emitter keeps its sliders and defaults.

Upgrading from alpha.1 preserves the crystal, the four original module slots, energy, and switches. Existing powered emitters now need the new modules for mining, entity damage, range, and thickness. No upgrades are granted automatically.

The menu shows stored energy, capacity, cost per tick, and why the beam is stopped. Energy is charged once per server tick for the enabled settings, even if the beam currently hits no entity or mineable block. Faster mining and higher damage, hit rate, or knockback cost more. Ignition adds a cost only with its module and switch enabled. Each Range I or Thickness module adds 1 energy unit/tick; each Range II module adds 8. The two slots' costs add together, including the last Range II module at the 512-block cap. Shortening the beam with an obstacle does not reduce the installed-module cost. Color and lighting do not change consumption. Cubes share their source emitter's power budget; they do not charge again for each segment. A disabled emitter, one blocked by redstone, or one without a crystal consumes nothing. Insufficient energy stops the beam, mining, damage, and receiver activation.

Energy and installed parts survive world saves and chunk reloads. Breaking the emitter returns installed parts as items; stored energy is lost. When technical mode is disabled, existing powered emitters and parts stay registered but their recipes are hidden and the emitters stop operating.

All nine crystals have a 3D crystal-and-metal housing, visible in menus, either hand, item frames, and as dropped items. The colored crystal and indicator inserts have emissive shader materials; the housing remains metallic. They do not place light blocks or change the emitter's power cost. Existing crystal items keep their IDs, colors, recipes, and single-item stack limit.

Modules and the Control Circuit also have individual 3D models. Right-click a block with any crystal or component to place it as a decoration; sneak-right-click when placing against a block that opens a menu. Mining a decoration in survival returns the same reusable part without Silk Touch. Creative removal produces no drop. Decorations face the placing player, have solid collision, and do not run laser or module effects on their own.

Placed crystals retain their colored shader emission. Kappa with Iris/Oculus can show bloom and colored surface lighting from their materials; the result depends on the shader pack and its settings. This does not add ordinary Minecraft block light. Technical-mode recipe and creative-tab availability rules are unchanged.

### Recipes

Recipes use vanilla ingredients and appear in the recipe book after obtaining redstone or quartz. The powered emitter uses `IGI / DHD / IRI`: iron ingots, tinted glass, diamonds, a hopper, and a redstone block. Each crystal uses three quartz, a matching dye, and an amethyst shard: quartz in the top center and on either side of the central dye, with amethyst below the dye. Violet uses purple dye.

Each basic module uses `IRI / DCD / IRI`: four iron ingots, two redstone dust, two diamonds, and a central component. Components: cobweb (Silk Touch), hopper (Block Drops), flint and steel (Scorch Marks), blaze powder (Ignition), diamond pickaxe (Block Destruction), diamond sword (Entity Damage), ender pearl (Range I), and amethyst block (Thickness).

Craft a Control Circuit using `RGR / GQG / RGR`: four redstone dust, four gold nuggets, and central quartz. Surround it with eight Range I modules (`XXX / XCX / XXX`) to craft one Range II module.

### Server configuration

The first launch creates `config/justifylasers.json`. Edit it with the game/server stopped, then restart. Multiplayer clients do not determine server costs or availability.

```json
{
  "energyMode": "AUTO",
  "technicalMods": ["mekanism", "techreborn", "modern_industrialization", "powah", "thermal", "oritech"],
  "capacity": 2000000,
  "maxInput": 100000,
  "basePerTick": 80,
  "miningPerTick": 120,
  "miningSpeedMultiplier": 20.0,
  "damagePerHealthPoint": 40.0,
  "knockbackPerHit": 8.0,
  "ignitionPerHit": 10.0
}
```

`AUTO` detects the configured mod IDs. `ON` enables technical mode explicitly, including with an unlisted energy mod; `OFF` restores standalone mode. Adding an ID only changes detection: that mod must still supply the platform's energy interface. Mekanism cable transfer has been tested on Forge 1.20.1 and NeoForge 1.21.1; the other listed mods have not each been integration-tested.

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

## Scorch marks

Laser contact leaves a scorched spot on a block. Moving the impact point, for example by aiming a refocusing cube, draws a continuous burn across the surface. The fresh groove has a white-yellow center and orange edges surrounded by soot. It cools over roughly 2.5 seconds after contact ends; the dark scar remains for about two minutes and fades during the last 20 seconds. Width follows the emitter's beam thickness.

Marks follow the actual collision surface, including slabs and steps, and are clipped at block edges and gaps. Breaking or replacing the supporting block removes its marks. The receiver's front sensor is protected. This is a cosmetic effect: it works with block destruction disabled and does not change blocks, hardness, drops, entity damage, or Minecraft light levels. Hot marks use the emitter's shader-emission setting for lighting and reflections in compatible packs.

Scorch marks are temporary client-side effects, not saved world damage. Only impacts observed in loaded chunks are recorded; changing worlds or unloading their chunks clears them. Geometry is capped and old marks are discarded under heavy use. Neither tracing nor drawing marks loads extra chunks.

## Using the receiver

Find **Laser Receiver** beside the emitter in the **Justify Lasers** creative tab or the Redstone tab. It can also be crafted from five iron ingots, two redstone dust, one glass block, and one nether quartz:

```text
Iron      Glass   Iron
Redstone  Quartz  Redstone
Iron      Iron    Iron
```

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

Find the cube in the **Justify Lasers** or Redstone creative tab. Craft it with four iron ingots, three glass blocks, an amethyst shard, and redstone dust (`IGI / GAG / IRI`). Use the item on a block to place the entity.

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

The Kappa 5.3 scene has been checked with Iris 1.7.6 + Sodium 0.5.13 + Indium 1.0.36 on Fabric 1.20.1; Oculus 1.8.0 + Embeddium 0.3.31 on Forge 1.20.1; and Iris 1.8.8 + Sodium 0.6.13 on both Fabric and NeoForge 1.21.1. These versions describe the tested combinations, not mandatory shader dependencies or a guarantee for every pack.

## Development

Use JDK 21 or 22 to run the Gradle 8.14.3 wrapper (verified locally with Corretto 22.0.2). Point IntelliJ's Gradle JVM or `JAVA_HOME` at that JDK; Java 26 cannot run this wrapper. Compilation targets Java 17 for Minecraft 1.20.1 and Java 21 for 1.21.1.

```text
gradlew.bat build
gradlew.bat test
gradlew.bat runGameTest
gradlew.bat runClient
```

On Linux or macOS, use `./gradlew`. `build` compiles/remaps all four targets, runs the Fabric 1.20.1 regression suite, and collects the four distributable JARs in `build/libs`. The short `test`, `runGameTest`, `runClient`, and `runServer` commands target Fabric 1.20.1. Select another target explicitly, for example `gradlew.bat :neoforge-1.21.1:runClient` or `gradlew.bat :forge-1.20.1:build`.

Shared gameplay is in `src/main`, rendering/screens in `src/client`, Minecraft API bridges in `versions/<minecraft-version>`, and loader hooks in `platforms/<loader-version>`. Dependency versions live in the root and platform `gradle.properties` files. Unit tests are in `src/test`; the full world regression suite is in `src/gametest`. Test worlds and build outputs are isolated under each platform's `build` directory.

`gradle/laser-materials.gradle` generates LabPBR maps and lit texture variants. Edit source textures in `src/main/resources/assets/justifylasers/textures/block`, not generated copies. `gradle/laser-parts.gradle` generates part models, recipes, and recipe-book entries and translates resource paths/conditions for each loader and Minecraft version.

Crystal geometry is defined in `LaserCrystalModel`; its immutable mesh is built once and shared by all colors. Item transforms are in `assets/justifylasers/models/item/crystal.json`. `gradle/crystal-materials.gradle` generates the crystal, steel, and indicator materials; resource packs can replace those textures at their normal asset paths. No external rendering library is required.

For a local shader check, `runClient -PcompatibilityModsDir=<mods-directory>` adds Iris, Sodium and Indium from that directory. This development setup also expects Iris's nested ANTLR, GLSL Transformer and JCPP runtime JARs under `run/compat-libs`; they are not bundled with the mod.

Native-loader integration checks and client smoke tests are documented in [the test notes](docs/development/2.0.0-testing.md). Their fixtures and optional third-party mods are not included in release JARs.

Current results and remaining release checks are in [alpha.6 verification](docs/development/2.0.0-alpha.6-testing.md).

## Contributing

Bug reports and pull requests are welcome. For larger changes, open an issue first. Run `gradlew.bat build` (or `./gradlew build`) before submitting a pull request.

## License

Licensed under the [MIT License](LICENSE.txt). Third-party dependencies retain their own licenses.
