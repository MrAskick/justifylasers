# Justify Lasers

Configurable laser emitters, redstone receivers, and portable refocusing cubes for Minecraft 1.20.1 with Fabric. Requires Fabric Loader and Fabric API.

Author: Mr.Askick.

## Using the emitter

Find **Laser Emitter** in the **Justify Lasers** creative tab or the Redstone tab. Place it facing any of the six directions, then right-click to open its controls.

- Enable or disable the beam.
- Ignore redstone, require a signal, or run only without a signal.
- Choose one of nine colors and adjust beam thickness from 0.10× to 10.00×.
- Set the beam range from 1 to 512 blocks, in one-block steps.
- Toggle shader emission and Minecraft block lighting independently.
- Enable block destruction and entity damage separately.

New emitters start enabled, with both lighting options on. Block destruction and entity damage are off by default. Settings are saved per emitter and synchronized with the server. The interface supports English and Russian.

Beam range defaults to 64 blocks, including in existing saves. The beam stops at block collision shapes and unloaded chunk boundaries; it never forces chunks to load. Long beams therefore require the corresponding chunks to be loaded on the server and visible to the client. Its entity hit radius scales with its thickness. Block destruction takes time based on hardness; unbreakable blocks stop the beam.

The small button next to **Entity damage** opens per-emitter damage settings: 0.1–20 HP per hit, 0–10× knockback, 1–20 hits per second at 20 TPS, and optional entity ignition. Defaults remain 0.5 HP, 1× gentle knockback, and 20 hits/s (10 HP/s before damage reduction), with ignition off. Zero knockback disables the push without disabling damage. Settings are saved with each emitter; existing worlds keep the previous defaults. Arrow keys adjust sliders one step at a time, and **Reset defaults** restores all four damage settings.

Armor, armor toughness, Protection enchantments, Resistance, absorption and shields use Minecraft's normal damage rules. The beam applies a capped push along its direction and respects knockback resistance. With ignition enabled, successful, unblocked hits set entities on fire for up to four seconds; this adds normal fire damage and respects fire immunity and fire protection. Ignition requires **Entity damage** to be enabled. Hit frequency is distributed across game ticks, including rates that do not divide evenly into 20; lower server TPS slows it proportionally.

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

Iris is optional. Shader compatibility has been checked with Iris 1.7.6 and Kappa 5.3. Lighting and reflections depend on the active pack and its settings. Without shaders, the beam uses the vanilla rendering path.

## Development

Build with a JDK supported by the Gradle wrapper. The mod targets Java 17; dependency versions are in `gradle.properties`.

```text
gradlew.bat build
gradlew.bat test
gradlew.bat runGameTest
gradlew.bat runClient
```

On Linux or macOS, use `./gradlew`. The distributable JAR is written to `build/libs`.

Main/server code is in `src/main`, client rendering and screens in `src/client`, unit tests in `src/test`, and world integration tests in `src/gametest`. The build runs both test suites; game tests use an isolated world under `build/run/gameTest`. `gradle/laser-materials.gradle` generates the LabPBR maps and lit texture variants under `build/generated/laserMaterials`; edit the source textures in `src/main/resources/assets/justifylasers/textures/block`, not the generated copies.

For a local shader check, `runClient -PcompatibilityModsDir=<mods-directory>` adds Iris, Sodium and Indium from that directory. This development setup also expects Iris's nested ANTLR, GLSL Transformer and JCPP runtime JARs under `run/compat-libs`; they are not bundled with the mod.

## Contributing

Bug reports and pull requests are welcome. For larger changes, open an issue first. Run `gradlew.bat build` (or `./gradlew build`) before submitting a pull request.

## License

Licensed under the [MIT License](LICENSE.txt). Third-party dependencies retain their own licenses.
