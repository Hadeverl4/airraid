package ace.actually.airraid.mixin;

import ace.actually.airraid.config.IAAddonConfig;
import ace.actually.airraid.util.RaidSpawner;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raid.class)
public abstract class RaidMixin {

    // Shadow the method, not the field (safer against mapping changes)
    @Shadow public abstract int getGroupsSpawned();

    // Shadow the world field directly (since getWorld() method might be missing/named differently)
    @Shadow @Final private ServerWorld world;

    @Inject(method = "spawnNextWave", at = @At("RETURN"))
    private void onSpawnWave(BlockPos pos, CallbackInfo ci) {
        IAAddonConfig.RaidConfig config = IAAddonConfig.INSTANCE.raidConfig;

        if (!config.enabled) return;

        // Use the shadowed method
        if (this.getGroupsSpawned() < config.startWave) return;

        // Use the shadowed field
        if (this.world.random.nextFloat() < config.spawnChancePerWave) {

            // Convert the RaidConfig to a Faction object so the Spawner can understand it
            IAAddonConfig.Faction raidFaction = new IAAddonConfig.Faction();
            raidFaction.vehicles = config.vehicles;
            raidFaction.mobs = config.mobs;
            raidFaction.weaponAmmoCounts = config.weaponAmmoCounts;
            raidFaction.enableCrossbows = config.enableCrossbows;
            raidFaction.enableRotaryCannons = config.enableRotaryCannons;
            raidFaction.enableBombBay = config.enableBombBay;
            raidFaction.minSquadSize = config.squadSize;
            raidFaction.maxSquadSize = config.squadSize;
            raidFaction.fuelItem = config.fuelItem;

            // Spawn the squadron above the raid spawn position
            float yaw = this.world.random.nextFloat() * 360.0f;

            RaidSpawner.spawnSquadronAt(
                    this.world,
                    pos.getX(),
                    pos.getY() + 50, // Spawn high up
                    pos.getZ(),
                    yaw,
                    raidFaction
            );
        }
    }
}