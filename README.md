# AirRaid ✈️

**AirRaid** is a Fabric mod for Minecraft 1.21.1 that brings aerial warfare to the Illager factions. By integrating with [Immersive Aircraft](https://modrinth.com/mod/immersive-aircraft), Pillagers, Vindicators, and Witches can now pilot Biplanes, Gyrodynes, and Airships to rain fire down upon players and villages.

![Banner Image](https://placehold.co/600x200?text=AirRaid+Addon) *<!-- Replace with a screenshot of a pillager in a plane -->*

## ✨ Features

*   **Aerial AI**: Custom AI logic allows mobs to fly aircraft, perform strafing runs, use rotary cannons, drop bombs, and dogfight with players.
*   **Natural Ambushes**: While exploring the world, you may be ambushed by a roaming squadron of Illager aircraft.
*   **Structure Spawning**: Pillager Outposts now spawn with parked aircraft and can launch defensive squadrons when players get too close.
*   **Raid Integration**: Vanilla Village Raids are significantly harder! From **Wave 3** onwards, the Illagers will call in air support.
*   **Configuration**: Every aspect of the spawning logic, from squad sizes to ammunition counts, is fully configurable.

## 🛠️ Requirements

*   Minecraft **1.21.1**
*   [Fabric Loader](https://fabricmc.net/)
*   [Fabric API](https://modrinth.com/mod/fabric-api)
*   [Immersive Aircraft](https://modrinth.com/mod/immersive-aircraft)

## 🎮 Commands

*   `/airraid spawn <count> [vehicle] [weapon]`
    *   Spawns a squadron immediately at your location.
    *   *Requires OP (Level 2)*.
*   `/airraid reload`
    *   Reloads the `ia_pillager_addon.json` configuration file without restarting the server.

## ⚙️ Configuration

The config file is located at `config/ia_pillager_addon.json`.

### Global Settings
*   `checkIntervalTicks`: How often the mod checks for ambush/structure spawns (Default: 200 ticks / 10 seconds).
*   `minSpawnDistance`: Minimum distance from the player to spawn aircraft (Default: 60 blocks).
*   `spawnHeight`: How high above the player aircraft spawn (Default: 40 blocks).

### Factions (Natural Spawning)
Controls random ambushes and structure generation.
*   `ambushChance`: Chance per check interval to spawn a random ambush (0.0 - 1.0).
*   `structureSpawnChance`: Chance to spawn a defensive squadron near an Outpost.
*   `parkedVehicleChance`: Chance to spawn empty, parked aircraft at an Outpost.
*   `vehicles`: A weighted list of which aircraft spawn.
*   `weaponAmmoCounts`: Defines how much ammo (arrows/gunpowder/tnt) the AI spawns with.

### Raid Config (Vanilla Raids)
*   `enabled`: If false, disables raid integration.
*   `startWave`: The raid wave number where air support begins (Default: 3).
*   `spawnChancePerWave`: The chance air support arrives during a specific wave.
*   `enableBombBay`: If true, raiders will drop TNT on villages.

## 🤝 License

This mod is licensed under the MIT License.