# Sunset Grove — Third-Party 3D Assets & Licenses

All external assets are sourced from free, trusted CC0 / MIT repositories.

## Bundled Asset Sources

| Asset | License | Source |
|---|---|---|
| `models/character_player.glb` | MIT (mrdoob/three.js, rigged Mixamo humanoid) | https://raw.githubusercontent.com/mrdoob/three.js/master/examples/models/gltf/Soldier.glb |
| `texture:asphalt` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/asphalt_02 |
| `texture:concrete` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/concrete_floor_worn_001 |
| `texture:sand` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/aerial_beach_01 |
| `texture:grass` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/dry_ground_01 |
| `texture:stucco_warm` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/plastered_wall |
| `texture:brick_city` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/red_brick |
| `texture:bark` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/bark_brown_02 |
| `texture:metal_rust` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/rusty_metal_02 |
| `models\props\street_lamp.glb` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/street_lamp_01 |
| `models\props\fire_hydrant.glb` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/fire_hydrant |
| `models\props\concrete_barrier.glb` | CC0 1.0 (Poly Haven) | https://polyhaven.com/a/concrete_road_barrier |

## Zero-Code Custom Model Overrides (Sketchfab CC-BY / Adobe Mixamo)

To swap in any higher-detail realistic `.glb` model from **Sketchfab** (CC0/CC-BY) or **Adobe Mixamo**:
1. Download the model in `.glb` format.
2. Place it inside `app/src/main/assets/models/overrides/` using the target filename:
   - `character_player.glb` — Rigged third-person protagonist (Idle / Walk / Run animations)
   - `car_police.glb` — 1994 Caprice / LAPD-style cruiser
   - `car_muscle.glb` / `car_sedan.glb` — 90s West Coast muscle car / lowrider
   - `vehicle_tank.glb` — Realistic military tank (name turret node `Turret` for rotation)
   - `vehicle_monster_truck.glb` — Lifted Monster Truck
   - `gear_jetpack.glb` — Twin-thruster Jetpack
3. Rebuild the APK (`./gradlew assembleDebug`). `GltfModelManager` automatically detects overrides and normalizes scale & ground contact.
