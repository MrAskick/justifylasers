# Justify Lasers

A configurable laser emitter for Minecraft 1.20.1 with Fabric. Requires Fabric Loader and Fabric API.

Author: Mr.Askick.

## Using the emitter

Find **Laser Emitter** in the **Justify Lasers** creative tab or the Redstone tab. Place it facing any of the six directions, then right-click to open its controls.

- Enable or disable the beam.
- Ignore redstone, require a signal, or run only without a signal.
- Choose one of nine colors and adjust beam thickness from 0.10× to 10.00×.
- Toggle shader emission and Minecraft block lighting independently.
- Enable block destruction and entity damage separately.

New emitters start enabled, with both lighting options on. Block destruction and entity damage are off by default. Settings are saved per emitter and synchronized with the server. The interface supports English and Russian.

The beam reaches up to 64 blocks and stops at block collision shapes. Its entity hit radius scales with its thickness. Block destruction takes time based on hardness; unbreakable blocks stop the beam.

## Lighting and shaders

Minecraft lighting controls the emitter's block light (level 15). Shader emission controls the beam's emissive material and the lit model textures; it does not toggle Minecraft lighting or hide the beam. Model textures follow the selected laser color.

Iris is optional. Shader compatibility has been checked with Iris 1.7.6 and Kappa 5.3. Lighting and reflections depend on the active pack and its settings. Without shaders, the beam uses the vanilla rendering path.

## Development

Build with a JDK supported by the Gradle wrapper. The mod targets Java 17; dependency versions are in `gradle.properties`.

```text
gradlew.bat build
gradlew.bat test
gradlew.bat runClient
```

On Linux or macOS, use `./gradlew`. The distributable JAR is written to `build/libs`.

Main/server code is in `src/main`, client rendering and screens in `src/client`, and regression tests in `src/test`. `gradle/laser-materials.gradle` generates the LabPBR maps and lit texture variants under `build/generated/laserMaterials`; edit the source textures in `src/main/resources/assets/justifylasers/textures/block`, not the generated copies.

For a local shader check, `runClient -PcompatibilityModsDir=<mods-directory>` adds Iris, Sodium and Indium from that directory. This development setup also expects Iris's nested ANTLR, GLSL Transformer and JCPP runtime JARs under `run/compat-libs`; they are not bundled with the mod.

## Contributing

Bug reports and pull requests are welcome. For larger changes, open an issue first. Run `gradlew.bat build` (or `./gradlew build`) before submitting a pull request.

## License

Licensed under the [MIT License](LICENSE.txt). Third-party dependencies retain their own licenses.
