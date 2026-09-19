# Assembly costs

Defaults for 2.0.0-alpha.35, including chemical synthesis, spectral conditioning and laser cutting. Laser-Absorbing Glass uses the workbench. Times assume 20 ticks/second; FE and Fabric E use the same numeric values.
These are the direct costs of one operation, excluding the materials' own processing costs.

| Schematic | Seconds | Energy/tick | Total energy |
| --- | ---: | ---: | ---: |
| Chemical Synthesizer | 41 | 128 | 104,960 |
| Spectrum Module | 37 | 160 | 118,400 |
| Laser Cutter (4 casings) | 62 | 304 | 376,960 |
| Reinforced Laser Housing | 12 | 48 | 11,520 |
| Focusing Lens Assembly | 16 | 72 | 23,040 |
| Beam Controller | 20 | 112 | 44,800 |
| Energy Core | 28 | 152 | 85,120 |
| Optical Resonator | 32 | 176 | 112,640 |
| Solar Absorber | 15 | 88 | 26,400 |
| Small Solar Concentrator | 44 | 64 | 56,320 |
| Powered Laser Emitter | 60 | 320 | 384,000 |
| Hard Light Bridge Emitter | 64 | 336 | 430,080 |
| Corner Hard Light Bridge Emitter | 26 | 136 | 70,720 |
| Laser Gun | 50 | 256 | 256,000 |
| LightSaber | 40 | 192 | 153,600 |
| Light Staff | 70 | 384 | 537,600 |
| Laser Turret Stand | 56 | 288 | 322,560 |
| Refocusing Cube | 80 | 448 | 716,800 |
| Laser Receiver | 24 | 120 | 57,600 |
| Laser Mirror | 14 | 56 | 15,680 |
| Beam Splitter | 76 | 416 | 632,320 |
| Beam Combiner | 84 | 464 | 779,520 |
| Extraterrestrial Tablet | 30 | 144 | 86,400 |
| Energy Receiver | 68 | 352 | 478,720 |
| Configurator | 9 | 28 | 5,040 |
| Block Destruction Module | 36 | 200 | 144,000 |
| Entity Damage Module | 38 | 208 | 158,080 |
| Healing Module | 45 | 232 | 208,800 |
| Lift Module | 39 | 216 | 168,480 |
| Lowering Module | 35 | 196 | 137,200 |
| Block Drops Module | 8 | 32 | 5,120 |
| Loot Collector Module | 21 | 96 | 40,320 |
| Silk Touch Module | 42 | 224 | 188,160 |
| Scorch Marks Module | 6 | 24 | 2,880 |
| Ignition Module | 10 | 40 | 8,000 |
| Range Module | 18 | 80 | 28,800 |
| Advanced Range Module | 48 | 248 | 238,080 |
| Thickness Module | 22 | 104 | 45,760 |
| Thickness Module II | 54 | 280 | 302,400 |
| Target Filter Module | 34 | 184 | 125,120 |

Basic housings and passive optics are cheaper than electronics, resonators and complete weapons.
Multi-port optical systems and power-conversion hardware occupy the expensive end of the range.
Continuous power is needed when a recipe's total exceeds the default 100,000-unit machine buffer;
running out of energy pauses the batch without losing its ingredients or progress.

## Configuration and data packs

For an Assembly Chamber recipe with explicit `ticks` and `energy`:

```
duration = floor(recipe.ticks  * laserAssemblyTicks / 400)
draw     = floor(recipe.energy * assemblyPerTick   / 160)
```

Both results have a minimum of 1. Duration is capped at 72,000 ticks and draw at the integer limit.
The default multipliers are 400 and 160, so the table matches the JSON recipes exactly.
Changing either common setting scales all assembly recipes while retaining their relative complexity.
A zero recipe field keeps the legacy behavior of using the corresponding machine setting directly.
Explicit costs for other machine kinds remain absolute.
