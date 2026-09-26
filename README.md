# ✈️ HyperRaid

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**HyperRaid** is an intense airborne combat and aerial raid mod for Minecraft (Fabric 1.21.1), integrating seamlessly with [Immersive Aircraft](https://modrinth.com/mod/immersive-aircraft).

Originally forked and heavily expanded from *AirRaid* by [adunis (Ace)](https://github.com/adunis).

---

## 🌟 Key Features

### ⚔️ Two-Faction Allegiance & Dogfights
* **Pillager Raiders:** Pilot armed biplanes, airships, and gyrodynes to raid players and villages.
* **Guard Villager Interceptors:** When villages are attacked, nearby Guard Villagers scramble to available defense aircraft, take off, and dogfight enemy invaders to protect their home!

### 🚨 Tactical Scramble AI
* During peacetime, both Pillagers and Guard Villagers stay on foot with their default weapons.
* When a threat is detected (Raid, player approach, or enemy incursion), mobs sprint to empty aircraft preloaded with fuel and scramble into the skies!

### 👁️ True Line-of-Sight & Shelter Mechanics
* **No more X-ray vision:** Aircraft AI requires direct visual line of sight (`canSee`) to acquire targets.
* **Take Shelter:** Players can hide beneath roofs, inside houses, caves, or under dense canopies to avoid being spotted.
* **Breaking Contact:** If a target stays out of sight for 5 seconds, pilots lose visual lock.

### ⏱️ Loiter, Search & Automatic Withdrawal
* After losing visual contact, raiders don't circle forever; they circle in search mode for **45 seconds**.
* If no targets reappear, the squadron throttles up, flies straight toward the horizon, and cleanly despawns once out of player range.

### 🛬 Smart Landing & Recovery (Guard Villagers)
* Once the threat is eliminated and a 15-second safety timer expires, Guard Villagers begin an automated landing sequence:
  * Avoids water/lava.
  * Throttles down, spirals into a gentle glide-slope, and flares softly upon touchdown.
  * Shuts off the engine, dismounts, and resumes foot patrol in the village!

### 🏔️ Adaptive Flight Ceiling & Anti-Space Bug
* Eliminates the bug where planes looped nose-up (-40°) into orbit.
* Automatically scales the flight altitude ceiling based on world dimension height (around Y=165 in default Overworld, below the clouds).
* Features automatic terrain collision avoidance over high mountain peaks.

---

## ⚙️ Configuration (`ia_pillager_addon.json`)

Located in your `.minecraft/config/ia_pillager_addon.json`:

```json
{
  "factions": [
    {
      "name": "Default Raiders",
      "ambushChance": 0.01,
      "structureSpawnChance": 0.05,
      "parkedVehicleChance": 0.01,
      "maxParkedVehicles": 1
    }
  ],
  "raidConfig": {
    "enabled": true,
    "spawnChancePerWave": 0.05,
    "startWave": 5,
    "squadSize": 1
  },
  "villageConfig": {
    "enabled": true,
    "parkedVehicleChance": 0.3,
    "maxParkedVehicles": 1
  },
  "searchDurationSeconds": 45,
  "maxFlightHeight": -1,
  "targetAcquisitionRange": 100
}
```

* **`maxFlightHeight`**: `-1` for automatic height based on world dimensions, or set an absolute Y value (e.g. `160`).
* **`searchDurationSeconds`**: How long raiders loiter and search before withdrawing (Default: `45`).
* **`villageConfig`**: Controls village defense aircraft spawning.

---

## 📜 Commands
* `/hyperraid reload` (or `/airraid reload`) - Reloads configuration without restarting the game.
* `/hyperraid spawn <count> [vehicle] [weapon]` - Spawns a custom test squadron.

---

## 📄 License & Credits
* Distributed under the **MIT License**.
* Core engine and inspiration derived from the original [AirRaid](https://github.com/adunis/airraid) by [adunis (Ace)](https://github.com/adunis).
* Overhauled and maintained by **Hadeverl4** as part of the **Hyper** ecosystem.
