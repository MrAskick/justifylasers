# Justify Lasers

Laser optics, programmable emitters, industrial production, refocusing cubes, laser weapons and hard-light bridges.

Version **2.0.0-beta.59** (Beta), for Minecraft **1.20.1** and **1.21.1** on Fabric, Forge and NeoForge. Install this mod and the matching [Justify API](https://github.com/MrAskick/justifyapi/releases) universal JARs. Fabric also requires Fabric API.

## Build from source

Use JDK 21 or 22. Clone the matching API sources alongside this repository:

```sh
git clone --branch v2.0.0-beta.59 https://github.com/MrAskick/justifyapi.git
git clone --branch v2.0.0-beta.59 https://github.com/MrAskick/justifylasers.git
cd justifylasers
./gradlew build
```

On Windows use `gradlew.bat build`. The composite build compiles the API dependency from source. A different API checkout can be selected with `-PjustifyApiDir=/path/to/justifyapi`. Universal JARs are written to `build/libs`; choose the file for your Minecraft version. No private authoring inputs are needed.

All finished runtime assets are included. API implementation is maintained in its own repository; this repository owns content registration, models, textures, sounds, recipes and translations.

License: MIT.
