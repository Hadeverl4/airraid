package com.example.addon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class IAAddonConfig {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ia_pillager_addon.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static IAAddonConfig INSTANCE;

    public List<Faction> factions = new ArrayList<>();
    public int checkIntervalTicks = 200;
    public int minSpawnDistance = 60;
    public int spawnHeight = 40;

    public static class Faction {
        public String name = "Default Raiders";
        public float ambushChance = 0.01f;
        public float structureSpawnChance = 0.10f;

        // --- NEW: Parked Vehicle Settings ---
        public float parkedVehicleChance = 0.05f; // 5% chance per check to spawn a parked plane
        public int maxParkedVehicles = 2; // Max parked vehicles around a structure
        // ------------------------------------

        public List<String> structures = new ArrayList<>(List.of("minecraft:pillager_outpost"));

        public List<String> mobs = new ArrayList<>(List.of(
                "minecraft:pillager",
                "minecraft:vindicator",
                "minecraft:witch"
        ));

        public Map<String, Integer> vehicles = new HashMap<>();
        public int minSquadSize = 2;
        public int maxSquadSize = 4;

        public boolean enableCrossbows = true;
        public boolean enableRotaryCannons = true;
        public boolean enableBombBay = false;

        public String fuelItem = "minecraft:coal";
        public Map<String, Integer> weaponAmmoCounts = new HashMap<>();

        public Faction() {
            vehicles.put("immersive_aircraft:biplane", 50);
            vehicles.put("immersive_aircraft:airship", 30);
            vehicles.put("immersive_aircraft:gyrodyne", 10);
            vehicles.put("immersive_aircraft:quadrocopter", 5);

            weaponAmmoCounts.put("immersive_aircraft:rotary_cannon", 300);
            weaponAmmoCounts.put("immersive_aircraft:heavy_crossbow", 600);
            weaponAmmoCounts.put("immersive_aircraft:bomb_bay", 10);
        }
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, IAAddonConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                INSTANCE = new IAAddonConfig();
            }
        } else {
            INSTANCE = new IAAddonConfig();
            INSTANCE.factions.add(new Faction());
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}