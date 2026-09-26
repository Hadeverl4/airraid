# âœˆï¸ HyperRaid

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**HyperRaid** is an overhauled aerial warfare mod for Minecraft 1.21.1 (Fabric), built on top of [Immersive Aircraft](https://modrinth.com/mod/immersive-aircraft). It transforms the skies into a dynamic, two-faction theater of war featuring **Pillager Air Raids** and **Village Air Defense** with intelligent, tactical AI.

Originally forked and heavily expanded from *AirRaid* by Ace.

---

## ðŸŒŸ Key Features

### âš”ï¸ Two-Faction Allegiance & Dogfights
* **Pillager Raiders:** Pilot armed biplanes, airships, and gyrodynes to raid players and villages.
* **Guard Villager Interceptors:** When villages are attacked, nearby Guard Villagers scramble to available defense aircraft, take off, and dogfight enemy invaders to protect their home!

### ðŸš¨ Tactical Scramble AI
* During peacetime, both Pillagers and Guard Villagers stay on foot with their default weapons.
* When a threat is detected (Raid, player approach, or enemy incursion), mobs sprint to empty aircraft preloaded with fuel and scramble into the skies!

### ðŸ‘ï¸ True Line-of-Sight & Shelter Mechanics
* **No more X-ray vision:** Aircraft AI requires direct visual line of sight (`canSee`) to acquire targets.
* **Take Shelter:** Players can hide beneath roofs, inside houses, caves, or under dense canopies to avoid being spotted.
* **Breaking Contact:** If a target stays out of sight for 5 seconds, pilots lose visual lock.

### â±ï¸ Loiter, Search & Automatic Withdrawal
* After losing visual contact, raiders don't circle forever; they circle in search mode for **45 seconds**.
* If no targets reappear, the squadron throttles up, flies straight toward the horizon, and cleanly despawns once out of player range.

### ðŸ›¬ Smart Landing & Recovery (Guard Villagers)
* Once the threat is eliminated and a 15-second safety timer expires, Guard Villagers begin an automated landing sequence:
  * Avoids water/lava.
  * Throttles down, spirals into a gentle glide-slope, and flares softly upon touchdown.
  * Shuts off the engine, dismounts, and resumes foot patrol in the village!

### ðŸ”ï¸ Adaptive Flight Ceiling & Anti-Space Bug
* Eliminates the bug where planes looped nose-up (-40Â°) into orbit.
* Automatically scales the flight altitude ceiling based on world dimension height (around Y=165 in default Overworld, below the clouds).
* Features automatic terrain collision avoidance over high mountain peaks.

---

## âš™ï¸ Configuration (`ia_pillager_addon.json`)

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

## ðŸ“œ Commands
* `/hyperraid reload` (or `/airraid reload`) - Reloads configuration without restarting the game.
* `/hyperraid spawn <count> [vehicle] [weapon]` - Spawns a custom test squadron.

---

## ðŸ“„ License & Credits
* Distributed under the **MIT License**.
* Core engine and inspiration derived from the original [AirRaid](https://github.com/adunis/airraid) by Ace.
* Overhauled and maintained by **Hadeverl4** as part of the **Hyper** ecosystem.