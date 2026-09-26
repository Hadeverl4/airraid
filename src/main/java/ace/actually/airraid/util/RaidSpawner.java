package ace.actually.airraid.util;

import ace.actually.airraid.config.IAAddonConfig;
import immersive_aircraft.entity.EngineVehicle;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.inventory.VehicleInventoryDescription;
import immersive_aircraft.entity.inventory.slots.SlotDescription;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.*;

public class RaidSpawner {

    // --- Ambush Spawning ---
    public static void spawnAmbush(ServerWorld world, Vec3d targetPos, IAAddonConfig.Faction faction) {
        double angle = world.random.nextDouble() * 2 * Math.PI;
        double distance = IAAddonConfig.INSTANCE.minSpawnDistance;

        double spawnX = targetPos.x + Math.sin(angle) * distance;
        double spawnZ = targetPos.z + Math.cos(angle) * distance;
        double spawnY = targetPos.y + IAAddonConfig.INSTANCE.spawnHeight;
        int maxHeight = IAAddonConfig.getMaxFlightHeight(world);
        if (maxHeight > 0 && spawnY > maxHeight) {
            spawnY = Math.max(targetPos.y + 10, maxHeight - 10);
        }

        double dx = targetPos.x - spawnX;
        double dz = targetPos.z - spawnZ;
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);

        spawnSquadronAt(world, spawnX, spawnY, spawnZ, yaw, faction);
    }

    // --- Village Defense Vehicle Spawning ---
    public static void spawnVillageDefenseVehicle(ServerWorld world, BlockPos center, IAAddonConfig.VillageConfig cfg) {
        Random random = world.random;
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = 12 + random.nextDouble() * 15;

        int x = center.getX() + (int)(Math.sin(angle) * dist);
        int z = center.getZ() + (int)(Math.cos(angle) * dist);

        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= world.getBottomY()) return;

        BlockPos spawnPos = new BlockPos(x, y, z);
        if (world.getBlockState(spawnPos).isLiquid() || world.getFluidState(spawnPos).isStill()) return;

        Identifier vehicleId = pickWeighted(cfg.vehicles, random);
        if (vehicleId == null) return;

        EntityType<?> type = Registries.ENTITY_TYPE.get(vehicleId);
        Entity entity = type.create(world);
        if (!(entity instanceof VehicleEntity vehicle)) return;

        float yaw = random.nextFloat() * 360.0f;
        vehicle.refreshPositionAndAngles(x, y + 1, z, yaw, 0.0f);

        if (vehicle instanceof EngineVehicle engineVehicle) {
            Inventory inv = engineVehicle.getInventory();
            Set<Integer> protectedSlots = new HashSet<>();

            Item fuelItem = Registries.ITEM.get(Identifier.of(cfg.fuelItem));
            if (fuelItem == Items.AIR) fuelItem = Items.COAL;

            List<SlotDescription> boilerSlots = engineVehicle.getInventoryDescription()
                    .getSlots(VehicleInventoryDescription.BOILER);
            for (SlotDescription slot : boilerSlots) {
                protectedSlots.add(slot.index());
                inv.setStack(slot.index(), new ItemStack(fuelItem, 64));
            }

            Item weaponItem = Registries.ITEM.get(Identifier.of(cfg.weaponItem));
            if (weaponItem != Items.AIR) {
                List<SlotDescription> weaponSlots = engineVehicle.getInventoryDescription()
                        .getSlots(VehicleInventoryDescription.WEAPON);
                if (!weaponSlots.isEmpty()) {
                    int weaponIndex = weaponSlots.get(0).index();
                    inv.setStack(weaponIndex, new ItemStack(weaponItem));
                    protectedSlots.add(weaponIndex);
                }

                Item ammoItem = Items.ARROW;
                int ammoNeeded = cfg.ammoCount;
                for (int i = 0; i < inv.size(); i++) {
                    if (protectedSlots.contains(i)) continue;
                    if (!inv.getStack(i).isEmpty()) continue;
                    if (ammoNeeded <= 0) break;
                    int toAdd = Math.min(ammoNeeded, ammoItem.getMaxCount());
                    inv.setStack(i, new ItemStack(ammoItem, toAdd));
                    ammoNeeded -= toAdd;
                }
            }

            engineVehicle.setEngineTarget(0.0f);
        }

        world.spawnEntity(vehicle);
    }

    // --- Parked Vehicle Spawning ---
    public static void spawnParkedAt(ServerWorld world, BlockPos center, IAAddonConfig.Faction faction) {
        // Find a random spot around the structure
        Random random = world.random;
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = 15 + random.nextDouble() * 25; // 15 to 40 blocks away

        int x = center.getX() + (int)(Math.sin(angle) * dist);
        int z = center.getZ() + (int)(Math.cos(angle) * dist);

        // Find ground level
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

        // Ensure we aren't spawning in the sky or void
        if (y <= world.getBottomY()) return;

        float yaw = random.nextFloat() * 360.0f;

        spawnVehicle(world, x, y + 1, z, 0, yaw, faction, true);
    }

    // --- Squadron Logic ---
    public static void spawnSquadronAt(ServerWorld world, double x, double y, double z, float yaw, IAAddonConfig.Faction faction) {
        int size = faction.minSquadSize + world.random.nextInt(faction.maxSquadSize - faction.minSquadSize + 1);

        spawnVehicle(world, x, y, z, 0, yaw, faction, false);

        for (int i = 1; i < size; i++) {
            int row = (i + 1) / 2;
            int side = (i % 2 == 1) ? 1 : -1;
            spawnVehicle(world, x, y, z, side * row, yaw, faction, false);
        }
    }

    // --- Core Spawner ---
    private static void spawnVehicle(ServerWorld world, double originX, double originY, double originZ, int formationIndex, float yaw, IAAddonConfig.Faction faction, boolean isParked) {
        Identifier vehicleId = pickWeighted(faction.vehicles, world.random);
        if (vehicleId == null) return;

        EntityType<?> type = Registries.ENTITY_TYPE.get(vehicleId);
        Entity vehicle = type.create(world);
        if (vehicle == null) return;

        double x = originX;
        double z = originZ;

        // Only apply formation offset if flying
        if (!isParked && formationIndex != 0) {
            double offsetDist = 15.0;
            double rowDepth = 12.0;
            double rads = Math.toRadians(yaw);
            double perpRads = Math.toRadians(yaw + 90);
            double row = Math.abs(formationIndex);

            x += Math.sin(rads) * (row * rowDepth);
            z -= Math.cos(rads) * (row * rowDepth);
            x += Math.sin(perpRads) * (offsetDist * formationIndex);
            z -= Math.cos(perpRads) * (offsetDist * formationIndex);
        }

        int maxHeight = IAAddonConfig.getMaxFlightHeight(world);
        if (maxHeight > 0 && originY > maxHeight) originY = maxHeight - 5;
        if (originY > world.getHeight()) originY = world.getHeight() - 10;

        vehicle.refreshPositionAndAngles(x, originY, z, yaw, 0.0f);

        // Tags allow Mixin to identify AI roles
        if (!isParked) {
            if (formationIndex < 0) vehicle.addCommandTag("formation_left");
            else if (formationIndex > 0) vehicle.addCommandTag("formation_right");
            else vehicle.addCommandTag("formation_leader");
        }

        // --- INVENTORY SETUP ---
        if (vehicle instanceof EngineVehicle engineVehicle) {
            Inventory inv = engineVehicle.getInventory();
            Set<Integer> protectedSlots = new HashSet<>();

            // 1. Fuel
            Item fuelItem = Registries.ITEM.get(Identifier.of(faction.fuelItem));
            if (fuelItem == Items.AIR) fuelItem = Items.COAL;

            List<SlotDescription> boilerSlots = engineVehicle.getInventoryDescription()
                    .getSlots(VehicleInventoryDescription.BOILER);
            for (SlotDescription slot : boilerSlots) {
                protectedSlots.add(slot.index());
                inv.setStack(slot.index(), new ItemStack(fuelItem, 64));
            }

            // 2. Weapon & Ammo
            String weaponId = pickWeapon(faction, world.random);
            Item weaponItem = Registries.ITEM.get(Identifier.of(weaponId));

            Item ammoItem = Items.ARROW;
            if (weaponId.contains("rotary")) ammoItem = Items.GUNPOWDER;
            else if (weaponId.contains("bomb")) ammoItem = Items.TNT;

            if (weaponItem != Items.AIR) {
                List<SlotDescription> weaponSlots = engineVehicle.getInventoryDescription()
                        .getSlots(VehicleInventoryDescription.WEAPON);

                if (!weaponSlots.isEmpty()) {
                    int weaponIndex = weaponSlots.get(0).index();
                    inv.setStack(weaponIndex, new ItemStack(weaponItem));
                    protectedSlots.add(weaponIndex);
                }

                // Place Specific Amount of Ammo
                int totalAmmoNeeded = faction.weaponAmmoCounts.getOrDefault(weaponId, 64);
                int maxStack = ammoItem.getMaxCount();

                for (int i = 0; i < inv.size(); i++) {
                    if (protectedSlots.contains(i)) continue;
                    if (!inv.getStack(i).isEmpty()) continue;

                    if (totalAmmoNeeded <= 0) break;

                    int amountToAdd = Math.min(totalAmmoNeeded, maxStack);
                    inv.setStack(i, new ItemStack(ammoItem, amountToAdd));

                    totalAmmoNeeded -= amountToAdd;
                }
            }

            // Only start engine if flying
            if (!isParked) {
                engineVehicle.setEngineTarget(1.0f);
            } else {
                engineVehicle.setEngineTarget(0.0f);
            }
        }

        // Launch velocity (only if flying)
        if (!isParked) {
            float yawRads = (float) Math.toRadians(yaw);
            double startSpeed = 1.2;
            vehicle.setVelocity(-Math.sin(yawRads) * startSpeed, 0.0, Math.cos(yawRads) * startSpeed);
            vehicle.velocityModified = true;
        }

        world.spawnEntity(vehicle);

        // Add Crew (only if flying)
        if (!isParked) {
            boolean seatAvailable = true;
            int safetyLimit = 0;
            while (seatAvailable && safetyLimit < 10) {
                if (!addFactionRaider(world, vehicle, x, originY, z, yaw, faction)) {
                    seatAvailable = false;
                }
                safetyLimit++;
            }
        }
    }

    private static boolean addFactionRaider(ServerWorld world, Entity vehicle, double x, double y, double z, float yaw, IAAddonConfig.Faction faction) {
        if (faction.mobs.isEmpty()) return false;

        String mobId = faction.mobs.get(world.random.nextInt(faction.mobs.size()));
        EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(mobId));
        Entity raider = type.create(world);

        if (!(raider instanceof MobEntity mobRaider)) return false;

        if (mobId.contains("pillager")) {
            mobRaider.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
        } else if (mobId.contains("vindicator")) {
            mobRaider.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        }

        mobRaider.refreshPositionAndAngles(x, y, z, yaw, 0.0f);
        mobRaider.setPersistent();

        boolean success = mobRaider.startRiding(vehicle);
        if (success) {
            world.spawnEntity(mobRaider);
            return true;
        } else {
            mobRaider.discard();
            return false;
        }
    }

    private static Identifier pickWeighted(Map<String, Integer> weights, Random random) {
        if (weights.isEmpty()) return null;
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        int r = random.nextInt(totalWeight);
        int count = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            count += entry.getValue();
            if (r < count) return Identifier.of(entry.getKey());
        }
        return Identifier.of(weights.keySet().iterator().next());
    }

    private static String pickWeapon(IAAddonConfig.Faction faction, Random random) {
        List<String> allowed = new ArrayList<>();
        if (faction.enableCrossbows) allowed.add("immersive_aircraft:heavy_crossbow");
        if (faction.enableRotaryCannons) allowed.add("immersive_aircraft:rotary_cannon");
        if (faction.enableBombBay) allowed.add("immersive_aircraft:bomb_bay");

        if (allowed.isEmpty()) return "minecraft:air";
        return allowed.get(random.nextInt(allowed.size()));
    }
}