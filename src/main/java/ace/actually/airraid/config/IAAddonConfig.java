package ace.actually.airraid.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import net.minecraft.world.World;

public class IAAddonConfig {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ia_pillager_addon.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static IAAddonConfig INSTANCE;

    public List<Faction> factions = new ArrayList<>();

    // --- Vanilla Raid Configuration ---
    public RaidConfig raidConfig = new RaidConfig();
    // ----------------------------------

    // --- Village Defense Configuration ---
    public VillageConfig villageConfig = new VillageConfig();
    // -------------------------------------

    public int checkIntervalTicks = 200;
    public int minSpawnDistance = 60;
    public int spawnHeight = 40;
    public int targetAcquisitionRange = 100;
    public int maxFlightHeight = -1;
    public int searchDurationSeconds = 45;

    public static int getMaxFlightHeight(World world) {
        if (INSTANCE != null && INSTANCE.maxFlightHeight > 0) {
            return INSTANCE.maxFlightHeight;
        }
        int worldMaxY = world.getBottomY() + world.getHeight();
        int seaLevel = world.getSeaLevel();
        int availableSky = Math.max(worldMaxY - seaLevel, 60);
        return seaLevel + (int) (availableSky * 0.40f);
    }

    public static class VillageConfig {
        public boolean enabled = true;
        public float parkedVehicleChance = 0.3f;
        public int maxParkedVehicles = 1;
        public List<String> structures = new ArrayList<>(List.of(
                "minecraft:village_plains",
                "minecraft:village_desert",
                "minecraft:village_savanna",
                "minecraft:village_snowy",
                "minecraft:village_taiga"
        ));
        public Map<String, Integer> vehicles = new HashMap<>();
        public String fuelItem = "minecraft:coal";
        public String weaponItem = "immersive_aircraft:heavy_crossbow";
        public int ammoCount = 64;

        public VillageConfig() {
            vehicles.put("immersive_aircraft:biplane", 60);
            vehicles.put("immersive_aircraft:gyrodyne", 40);
        }
    }

    public static class RaidConfig {
        public boolean enabled = true;
        public float spawnChancePerWave = 0.4f; // 40% chance per wave to spawn air support
        public int startWave = 3; // Only spawn from wave 3 onwards
        public int squadSize = 2;

        public Map<String, Integer> vehicles = new HashMap<>();
        public List<String> mobs = new ArrayList<>(List.of(
                "minecraft:pillager",
                "minecraft:vindicator"
        ));

        // Weapons for Raids
        public boolean enableCrossbows = true;
        public boolean enableRotaryCannons = true;
        public boolean enableBombBay = true; // Raids are destructive by nature

        public String fuelItem = "minecraft:coal";
        public Map<String, Integer> weaponAmmoCounts = new HashMap<>();

        public RaidConfig() {
            vehicles.put("immersive_aircraft:biplane", 60);
            vehicles.put("immersive_aircraft:gyrodyne", 40);

            weaponAmmoCounts.put("immersive_aircraft:rotary_cannon", 300);
            weaponAmmoCounts.put("immersive_aircraft:heavy_crossbow", 600);
            weaponAmmoCounts.put("immersive_aircraft:bomb_bay", 15);
        }
    }

    public static class Faction {
        public String name = "Default Raiders";
        public float ambushChance = 0.01f;
        public float structureSpawnChance = 0.10f;

        public float parkedVehicleChance = 0.05f;
        public int maxParkedVehicles = 2;

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
        if (INSTANCE.targetAcquisitionRange <= 0) INSTANCE.targetAcquisitionRange = 100;
        if (INSTANCE.searchDurationSeconds <= 0) INSTANCE.searchDurationSeconds = 45;
        if (INSTANCE.villageConfig == null) INSTANCE.villageConfig = new VillageConfig();
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}