package ace.actually.airraid.util;

import ace.actually.airraid.config.IAAddonConfig;
import immersive_aircraft.entity.VehicleEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class WorldTickHandler implements ServerTickEvents.EndTick {
    private int ticks = 0;

    @Override
    public void onEndTick(MinecraftServer server) {
        ticks++;
        if (ticks < IAAddonConfig.INSTANCE.checkIntervalTicks) return;
        ticks = 0;

        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) continue;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.isSpectator() || player.isCreative()) continue;

                for (IAAddonConfig.Faction faction : IAAddonConfig.INSTANCE.factions) {

                    // A. Ambush Check (Flying Squadron)
                    if (faction.ambushChance > 0 && world.random.nextFloat() < faction.ambushChance) {
                        RaidSpawner.spawnAmbush(world, player.getPos(), faction);
                        continue;
                    }

                    // B. Structure Check
                    if (!faction.structures.isEmpty()) {
                        BlockPos structurePos = getNearestStructurePos(world, player.getBlockPos(), faction);

                        if (structurePos != null) {

                            // 1. Raid Spawn (Flying)
                            if (faction.structureSpawnChance > 0 && world.random.nextFloat() < faction.structureSpawnChance) {
                                RaidSpawner.spawnAmbush(world, player.getPos(), faction);
                            }

                            // 2. Parked Vehicle Spawn (Ground)
                            if (faction.parkedVehicleChance > 0 && faction.maxParkedVehicles > 0) {
                                if (world.random.nextFloat() < faction.parkedVehicleChance) {
                                    // Count existing vehicles in 50 block radius
                                    int existing = world.getEntitiesByClass(VehicleEntity.class,
                                            new Box(structurePos).expand(50.0), e -> true).size();

                                    if (existing < faction.maxParkedVehicles) {
                                        RaidSpawner.spawnParkedAt(world, structurePos, faction);
                                    }
                                }
                            }
                        }
                    }
                }

                // C. Village Defense Vehicle Check
                if (IAAddonConfig.INSTANCE.villageConfig != null && IAAddonConfig.INSTANCE.villageConfig.enabled) {
                    var vCfg = IAAddonConfig.INSTANCE.villageConfig;
                    if (vCfg.parkedVehicleChance > 0 && vCfg.maxParkedVehicles > 0) {
                        BlockPos villagePos = getNearestVillagePos(world, player.getBlockPos(), vCfg);
                        if (villagePos != null) {
                            if (world.random.nextFloat() < vCfg.parkedVehicleChance) {
                                int existing = world.getEntitiesByClass(VehicleEntity.class,
                                        new Box(villagePos).expand(60.0), e -> true).size();
                                if (existing < vCfg.maxParkedVehicles) {
                                    RaidSpawner.spawnVillageDefenseVehicle(world, villagePos, vCfg);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockPos getNearestStructurePos(ServerWorld world, BlockPos pos, IAAddonConfig.Faction faction) {
        for (String structId : faction.structures) {
            var structureKey = net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(structId));

            // Check if structure exists nearby
            BlockPos found = world.locateStructure(
                    net.minecraft.registry.tag.TagKey.of(RegistryKeys.STRUCTURE, Identifier.of(structId)),
                    pos, 100, false);

            if (found != null && Math.sqrt(found.getSquaredDistance(pos)) < 100) {
                return found;
            }
        }
        return null;
    }

    private BlockPos getNearestVillagePos(ServerWorld world, BlockPos pos, IAAddonConfig.VillageConfig vCfg) {
        for (String structId : vCfg.structures) {
            BlockPos found = world.locateStructure(
                    net.minecraft.registry.tag.TagKey.of(RegistryKeys.STRUCTURE, Identifier.of(structId)),
                    pos, 100, false);
            if (found != null && Math.sqrt(found.getSquaredDistance(pos)) < 100) {
                return found;
            }
        }
        return null;
    }
}
