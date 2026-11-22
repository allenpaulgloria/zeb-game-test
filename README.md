# Temu Theft Auto — Native Android 3D GTA: San Andreas-Style Sandbox

A native Android (**Kotlin + Google Filament PBR + Jetpack Compose**) third-person 3D open-world sandbox game inspired by *Grand Theft Auto: San Andreas*.

## Features
- **3D PBR Third-Person World (Google Filament `1.56.0` + `gltfio`)**: Sun-soaked 1990s West Coast district (**Grove Circle** cul-de-sac, **Vinewood Strip** palm avenue & stunt ramps, **Verona Beachfront** sand/ocean, and **Downtown Crossings** with landable flat rooftops), bathed in golden-hour directional shadows, ACES filmic tone mapping, and amber California distance haze.
- **Trusted Free 3D Assets (`tools/fetch_assets.py`)**: Uses rigged animated humanoid (`Soldier.glb`, Three.js MIT), Poly Haven CC0 3D street props (`street_lamp_01`, `fire_hydrant`, `concrete_road_barrier`) and 1K CC0 PBR textures, plus smooth-shaded PBR vehicles, **Rhino Army Tank** (independent rotating turret + explosive cannon), **Monster Truck**, and **Jetpack**. Supports zero-code drop-in model swaps via `app/src/main/assets/models/overrides/` (see [ASSETS_LICENSE.md](ASSETS_LICENSE.md)).
- **1–6 Star Wanted & Police Pursuit System**: Escalates from reckless collisions, vehicle theft, and tank cannon chaos; LSPD cruisers with flashing red/blue lightbars pursue via A* road routing and roadblocks; hide out of line-of-sight until flashing stars clear for **WANTED EVADED / RESPECT+**, or face dramatic slow-motion **BUSTED** / **WASTED** banners.
- **Iconic San Andreas HUD**: Rotating circular radar minimap with orbiting `N` marker and flashing police blips, green `$` cash readout, crimson health & armor/vehicle bars, 6 wanted stars, and district/vehicle toasts.
- **YouTube Intro & Toggleable In-Game Track (`IeIUm-aqaCg`)**: Launches with an embedded San Andreas-themed YouTube intro screen (`https://youtu.be/IeIUm-aqaCg?si=vXmCyZb0KGrciZXf`) featuring a **KEEP PLAYING IN-GAME** toggle, plus an in-game HUD **`♫ TRACK: ON/OFF`** button, collapsible mini-radio player, and Pause Menu **REPLAY INTRO** option.
- **Pause Menu & 22 Real GTA: San Andreas Cheat Codes (Double-Tap to Activate)**:
  Tap **PAUSE (`||`) → CHEAT CODES**, filter by category, and **double-tap** any code to trigger live effects with the classic top-left `"Cheat activated"` notification:
  - **Player:** `YECGAA` (`ROCKETMAN` — Jetpack), `HESOYAM` (Health/Armor/`+$250,000`/Repair), `BAGUVIX` (Semi-Infinite Health), `KANGAROO` (Super Jump)
  - **Vehicles:** `AIWPRTON` (Rhino Army Tank), `AGBDLCID` (`MONSTERMASH` — Monster Truck), `PDNEJOH` (Hotring Racer), `CQZIJMB` (Bloodring Banger), `COXEFGU` (Nitro), `PGGOMOY` (Perfect Handling + Hop), `RIPAZHA` (Flying Cars), `JCNRUAD` (Smash N' Boom), `CPKTNWT` (Blow Up Nearby Cars), `YLTEICZ` (Aggressive Drivers)
  - **Police:** `AEZAKMI` (Never Wanted), `ASNAEB` (Clear Wanted), `OSRBLHH` (+2 Stars), `LJSPQK` (Max 6 Stars)
  - **World:** `PPGWJHT` (Fast Motion), `LIYOAAY` (Slow Motion), `AFZLLQLL` (Sunny Day), `OFVIAC` (21:00 Orange Sky)

## Building & Running
```bash
# 1. (Optional) Re-fetch/verify external 3D PBR assets & audio
python tools/fetch_assets.py --verify

# 2. Run unit tests & build Debug APK
./gradlew testDebugUnitTest assembleDebug
```
The compiled debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
