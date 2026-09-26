package ace.actually.airraid.mixin;

import ace.actually.airraid.config.IAAddonConfig;
import immersive_aircraft.entity.*;
import immersive_aircraft.entity.inventory.VehicleInventoryDescription;
import immersive_aircraft.entity.weapon.RotaryCannon;
import immersive_aircraft.entity.weapon.Weapon;
import immersive_aircraft.entity.weapon.BombBay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {

    @Unique private int fireCooldown = 0;
    @Unique private int burstTicks = 0;
    @Unique private int burstCooldownTicks = 0;
    @Unique private int scrambleCheckCooldown = 0;
    @Unique private int peaceTicks = 0;
    @Unique private int lostSightTicks = 0;

    @Inject(method = "damage", at = @At(value = "INVOKE", target = "Limmersive_aircraft/entity/VehicleEntity;discard()V"))
    private void forceDropOnCreativeKill(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (self.getFirstPassenger() instanceof RaiderEntity || isGuardVillager(self.getFirstPassenger())) {
            vehicle.dropStack(new ItemStack(vehicle.asItem()));
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void allowMobPilot(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        VehicleEntity vehicle = (VehicleEntity) (Object) this;

        if (self.getWorld().isClient) return;

        Entity passenger = self.getFirstPassenger();

        if (passenger == null) {
            peaceTicks = 0;
            lostSightTicks = 0;
            // Check for nearby scramble candidates when under threat
            if (self.isOnGround() && --scrambleCheckCooldown <= 0) {
                scrambleCheckCooldown = 20; // Check once per second
                tryScramblePilot(self, vehicle);
            }
            return;
        }

        boolean isRaiderPilot = passenger instanceof RaiderEntity;
        boolean isGuardPilot = isGuardVillager(passenger);

        if ((isRaiderPilot || isGuardPilot) && passenger instanceof MobEntity mob) {
            float inputYaw = 0.0f;
            float inputPitch = 0.0f;
            float inputThrottle = 0.0f;
            boolean wantToFire = false;

            LivingEntity target = mob.getTarget();

            // Validate existing target with Line of Sight (canSee)
            if (target != null) {
                if (!target.isAlive() || target.isSpectator()) {
                    target = null;
                    mob.setTarget(null);
                    lostSightTicks = 0;
                } else if (isGuardPilot && isFriendlyToGuards(target)) {
                    target = null;
                    mob.setTarget(null);
                    lostSightTicks = 0;
                } else if (isRaiderPilot && target instanceof RaiderEntity) {
                    target = null;
                    mob.setTarget(null);
                    lostSightTicks = 0;
                } else if (!mob.canSee(target)) {
                    // Line of sight blocked by wall, roof, or terrain
                    lostSightTicks++;
                    if (lostSightTicks > 100) { // Lost for more than 5 seconds
                        target = null;
                        mob.setTarget(null);
                        lostSightTicks = 0;
                    }
                } else {
                    lostSightTicks = 0;
                }
            }

            // 1. Target Acquisition based on Allegiance with Line of Sight (canSee)
            if (target == null) {
                double range = IAAddonConfig.INSTANCE.targetAcquisitionRange;
                if (isGuardPilot) {
                    target = self.getWorld().getEntitiesByClass(
                            LivingEntity.class,
                            self.getBoundingBox().expand(range),
                            e -> e.isAlive() && !e.isSpectator() && (e instanceof RaiderEntity || hasRaiderPassenger(e.getVehicle())) && mob.canSee(e)
                    ).stream()
                    .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(self)))
                    .orElse(null);
                } else if (isRaiderPilot) {
                    target = self.getWorld().getEntitiesByClass(
                            LivingEntity.class,
                            self.getBoundingBox().expand(range),
                            e -> e.isAlive() && !e.isSpectator() && mob.canSee(e) && (
                                    (e instanceof PlayerEntity p && !p.isCreative())
                                    || isGuardVillager(e)
                                    || e instanceof IronGolemEntity
                                    || e instanceof VillagerEntity
                                    || hasFriendlyPassenger(e.getVehicle())
                            )
                    ).stream()
                    .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(self)))
                    .orElse(null);
                }
                if (target != null) {
                    mob.setTarget(target);
                    lostSightTicks = 0;
                }
            }

            // Track peace timer (duration without targets)
            if (target != null) {
                peaceTicks = 0;
            } else {
                peaceTicks++;
            }

            boolean isVtol = vehicle instanceof AirshipEntity || vehicle instanceof GyrodyneEntity || vehicle instanceof QuadrocopterEntity;

            // Weapon Capability & Type Detection
            boolean isWarship = vehicle instanceof WarshipEntity;
            boolean isRotary = false;
            boolean isCrossbow = isWarship;
            boolean hasBombBay = false;

            if (vehicle instanceof InventoryVehicleEntity invVehicle) {
                for (List<Weapon> list : invVehicle.getWeapons().values()) {
                    for (Weapon w : list) {
                        if (w instanceof RotaryCannon) isRotary = true;
                        if (w.getStack().toString().contains("crossbow")) isCrossbow = true;
                        if (w instanceof BombBay || w.getStack().toString().contains("bomb")) hasBombBay = true;
                    }
                }
            }

            boolean hasTurret = isWarship || isRotary;

            // 2. Combat Flight & Loiter/Landing Logic
            if (target != null) {
                // Collision Avoidance
                Vec3d selfPos = self.getPos();
                Vec3d targetPos = target.getPos();
                Vec3d toTarget = targetPos.subtract(selfPos);
                double realDist = toTarget.length();

                Vec3d avoidance = Vec3d.ZERO;
                List<Entity> nearbyVehicles = self.getWorld().getOtherEntities(self, self.getBoundingBox().expand(25.0), e -> e instanceof VehicleEntity);

                for (Entity neighbor : nearbyVehicles) {
                    Vec3d toSelf = selfPos.subtract(neighbor.getPos());
                    double distSq = toSelf.lengthSquared();
                    if (distSq < 625.0 && distSq > 0.01) {
                        double dist = Math.sqrt(distSq);
                        double force = (25.0 - dist) / 25.0;
                        avoidance = avoidance.add(toSelf.normalize().multiply(force * 2.5));
                    }
                }

                Vec3d desiredDir = toTarget.normalize().add(avoidance).normalize();
                Vec3d virtualTargetPos = selfPos.add(desiredDir.multiply(realDist));

                double dx = virtualTargetPos.x - self.getX();
                double dy = virtualTargetPos.y - self.getY();
                double dz = virtualTargetPos.z - self.getZ();
                double hDist = Math.sqrt(dx*dx + dz*dz);

                // Flight Inputs
                int groundY = self.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int)self.getX(), (int)self.getZ());
                int effectiveMaxHeight = Math.max(IAAddonConfig.getMaxFlightHeight(self.getWorld()), groundY + 15);
                boolean unsafeLow = self.getY() < groundY + 15;
                boolean aboveCeiling = self.getY() >= effectiveMaxHeight;
                boolean nearCeiling = self.getY() >= effectiveMaxHeight - 8;

                int targetGroundY = self.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int)target.getX(), (int)target.getZ());
                boolean isAirTarget = target.getY() > targetGroundY + 10;

                float angleToTarget = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                float yawDiff = MathHelper.wrapDegrees(angleToTarget - self.getYaw());

                if (isVtol) {
                    double desiredY = target.getY() + 10;
                    if (hasBombBay) desiredY = target.getY() + 30;
                    if (desiredY > effectiveMaxHeight) desiredY = effectiveMaxHeight;

                    if (aboveCeiling) {
                        inputPitch = -1.0f;
                        inputThrottle = hDist > 15 ? 0.6f : 0.0f;
                    } else if (self.getY() < desiredY - 5) { inputPitch = 1.0f; inputThrottle = 0.0f; }
                    else if (self.getY() < desiredY) { inputPitch = 1.0f; inputThrottle = hDist > 15 ? 0.6f : 0.0f; }
                    else if (self.getY() > desiredY + 10) { inputPitch = -1.0f; inputThrottle = hDist > 15 ? 0.6f : 0.0f; }
                    else { inputPitch = 0.15f; inputThrottle = hDist > 15 ? 0.6f : 0.0f; }

                    inputYaw = -MathHelper.clamp(yawDiff / 45.0f, -1.0f, 1.0f);
                } else {
                    inputThrottle = 1.0f;
                    float targetPitchDeg;

                    if (unsafeLow) {
                        targetPitchDeg = -30.0f;
                    } else if (aboveCeiling) {
                        targetPitchDeg = 15.0f;
                    } else if (hasBombBay) {
                        if (hDist > 20) targetPitchDeg = (self.getY() > target.getY() + 40) ? 5.0f : 0.0f;
                        else targetPitchDeg = 0.0f;
                    } else if (isAirTarget) {
                        float leadYaw = angleToTarget;
                        double targetClampedY = Math.min(target.getY(), effectiveMaxHeight);
                        double clampedDy = targetClampedY - self.getY();
                        float directPitch = (float) -Math.toDegrees(Math.atan2(clampedDy, Math.max(hDist, 5.0)));
                        targetPitchDeg = MathHelper.clamp(directPitch, -30, 30);
                    } else {
                        if (hDist > 50) {
                            float directPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
                            targetPitchDeg = MathHelper.clamp(directPitch, -25, 10);
                        } else {
                            targetPitchDeg = -30.0f;
                        }
                    }

                    if (!unsafeLow && nearCeiling && targetPitchDeg < 0.0f) {
                        targetPitchDeg = 0.0f;
                    }

                    inputYaw = -MathHelper.clamp(yawDiff / 60.0f, -0.8f, 0.8f);

                    float currentPitch = self.getPitch();
                    float pitchError = targetPitchDeg - currentPitch;
                    float elevator = pitchError * 0.04f;

                    if (currentPitch < -45.0f) elevator = Math.max(elevator, 0.1f);
                    if (currentPitch > 45.0f) elevator = Math.min(elevator, -0.3f);
                    if (Math.abs(inputYaw) > 0.2f) elevator -= 0.15f;

                    inputPitch = MathHelper.clamp(elevator, -0.8f, 0.8f);
                }

                // --- FIRE TRIGGER ---
                if (hasBombBay) {
                    double realHDist = Math.sqrt(toTarget.x*toTarget.x + toTarget.z*toTarget.z);
                    if (realHDist < 4.0 && self.getY() > target.getY() + 5) wantToFire = true;
                } else if (hasTurret) {
                    if (realDist < 120) wantToFire = true;
                } else {
                    float tolerance = isVtol ? 35.0f : 25.0f;
                    if (Math.abs(yawDiff) < tolerance && realDist < 120) wantToFire = true;
                }

                mob.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos());
            } else {
                // No current target
                int groundY = self.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int)self.getX(), (int)self.getZ());
                int effectiveMaxHeight = Math.max(IAAddonConfig.getMaxFlightHeight(self.getWorld()), groundY + 15);

                if (isGuardPilot) {
                    // Guard Villager Landing Procedure after 15s (300 ticks) of peace
                    boolean isLanding = peaceTicks > 300;
                    if (isLanding) {
                        BlockPos groundPos = new BlockPos((int)self.getX(), groundY, (int)self.getZ());
                        boolean isLiquid = self.getWorld().getBlockState(groundPos).isLiquid()
                                || self.getWorld().getFluidState(groundPos).isStill();

                        if (isLiquid) {
                            // Over water/lava: keep flying to find solid land
                            peaceTicks = 200;
                            inputThrottle = isVtol ? 0.3f : 0.8f;
                            inputYaw = 0.08f;
                            inputPitch = 0.0f;
                        } else {
                            double altitudeAboveGround = self.getY() - groundY;
                            boolean hasLanded = self.isOnGround() || (altitudeAboveGround <= 1.5 && self.getVelocity().lengthSquared() < 0.1);

                            if (hasLanded) {
                                if (vehicle instanceof EngineVehicle engineVehicle) {
                                    engineVehicle.setEngineTarget(0.0f);
                                }
                                vehicle.setInputs(0.0f, 0.0f, 0.0f);
                                mob.stopRiding();
                                peaceTicks = 0;
                                return;
                            }

                            // Performing landing approach
                            if (isVtol) {
                                inputYaw = 0.0f;
                                inputThrottle = 0.0f;
                                inputPitch = altitudeAboveGround > 4.0 ? -0.5f : -0.2f;
                            } else {
                                inputThrottle = 0.2f; // Idle speed
                                inputYaw = 0.12f;     // Gentle spiral descent

                                float targetPitchDeg;
                                if (altitudeAboveGround > 8.0) {
                                    targetPitchDeg = 8.0f;
                                } else if (altitudeAboveGround > 2.0) {
                                    targetPitchDeg = 1.0f;
                                } else {
                                    targetPitchDeg = -4.0f; // Flare nose up
                                }

                                float currentPitch = self.getPitch();
                                float pitchError = targetPitchDeg - currentPitch;
                                float elevator = pitchError * 0.04f;
                                inputPitch = MathHelper.clamp(elevator, -0.6f, 0.6f);
                            }
                        }
                    } else {
                        // Guard Patrol while waiting to see if enemies reappear
                        if (self.getY() > effectiveMaxHeight) inputPitch = isVtol ? -0.8f : 0.2f;
                        else if (self.getY() < self.getWorld().getSeaLevel() + 40) inputPitch = isVtol ? 0.3f : -0.1f;
                        else inputPitch = 0.0f;
                        inputThrottle = isVtol ? 0.3f : 0.8f;
                        inputYaw = 0.05f;
                    }
                } else if (isRaiderPilot) {
                    // Raider Pilot: Loiter / Search for searchDurationSeconds, then withdraw and despawn
                    int maxSearchTicks = IAAddonConfig.INSTANCE.searchDurationSeconds * 20;

                    if (peaceTicks <= maxSearchTicks) {
                        // Loiter / Search circling area
                        if (self.getY() > effectiveMaxHeight) inputPitch = isVtol ? -0.8f : 0.2f;
                        else if (self.getY() < self.getWorld().getSeaLevel() + 40) inputPitch = isVtol ? 0.3f : -0.1f;
                        else inputPitch = 0.0f;

                        inputThrottle = isVtol ? 0.3f : 0.75f;
                        inputYaw = 0.06f; // circling/searching
                    } else {
                        // Withdrawal / Fly-Away
                        if (self.getY() < self.getWorld().getSeaLevel() + 60) inputPitch = isVtol ? 0.2f : -0.1f;
                        else inputPitch = 0.0f;

                        inputThrottle = isVtol ? 0.5f : 1.0f; // full speed away
                        inputYaw = 0.0f;                      // straight flight

                        // Check if far away from players to despawn
                        PlayerEntity nearestPlayer = self.getWorld().getClosestPlayer(self, 200.0);
                        boolean canDespawn = nearestPlayer == null || nearestPlayer.squaredDistanceTo(self) > 120.0 * 120.0;
                        if (canDespawn || peaceTicks > maxSearchTicks + 400) {
                            for (Entity p : self.getPassengerList()) {
                                p.discard();
                            }
                            self.discard();
                            return;
                        }
                    }
                }
            }

            if (isVtol) vehicle.setInputs(inputYaw, inputPitch, inputThrottle);
            else vehicle.setInputs(inputYaw, 0.0f, inputPitch);

            // --- BURST FIRE LOGIC ---
            int burstDuration = 100;
            int burstRest = 200;
            int shotInterval = 10;

            if (isRotary) {
                burstDuration = 40;
                burstRest = 100;
                shotInterval = 2;
            } else if (hasBombBay) {
                burstDuration = 10;
                burstRest = 40;
                shotInterval = 5;
            }

            // Burst State Machine
            if (burstCooldownTicks > 0) {
                burstCooldownTicks--;
                wantToFire = false;
            } else if (wantToFire) {
                burstTicks++;
                if (burstTicks >= burstDuration) {
                    burstCooldownTicks = burstRest;
                    burstTicks = 0;
                    wantToFire = false;
                }
            } else {
                if (burstTicks > 0) burstTicks--;
            }

            // --- EXECUTE FIRE ---
            if (wantToFire && fireCooldown <= 0) {
                if (vehicle instanceof InventoryVehicleEntity invVehicle) {

                    Vector3f aimDir;
                    if (target != null) {
                        Vec3d targetCenter = target.getBoundingBox().getCenter();
                        Vec3d selfCenter = self.getBoundingBox().getCenter();
                        Vec3d diff = targetCenter.subtract(selfCenter).normalize();
                        aimDir = new Vector3f((float) diff.x, (float) diff.y, (float) diff.z);
                    } else {
                        Vec3d lookVec = self.getRotationVector();
                        aimDir = new Vector3f((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);
                    }

                    boolean isSafe = true;
                    if (!hasBombBay) {
                        Vec3d rayStart = self.getEyePos();
                        Vec3d rayDirVec = new Vec3d(aimDir.x, aimDir.y, aimDir.z).multiply(100.0);
                        Vec3d rayEnd = rayStart.add(rayDirVec);

                        Box box = self.getBoundingBox().stretch(rayDirVec).expand(1.0);

                        EntityHitResult hit = ProjectileUtil.raycast(
                                self,
                                rayStart,
                                rayEnd,
                                box,
                                e -> !e.isSpectator() && e.isAlive() && e != self && !e.isConnectedThroughVehicle(self),
                                100.0 * 100.0
                        );

                        if (hit != null) {
                            Entity hitE = hit.getEntity();
                            if (isRaiderPilot) {
                                if (hitE instanceof RaiderEntity || hasRaiderPassenger(hitE)) {
                                    isSafe = false;
                                }
                            } else if (isGuardPilot) {
                                if (isFriendlyToGuards(hitE) || hasFriendlyPassenger(hitE)) {
                                    isSafe = false;
                                }
                            }
                        }
                    }

                    if (isSafe) {
                        boolean fired = false;

                        // 1. Fire Warship Turret
                        if (isWarship) {
                            ((WarshipEntity)vehicle).fireWeapon(-1, 0, aimDir);
                            fired = true;
                        }

                        // 2. Fire Regular Weapons
                        for (List<Weapon> weaponList : invVehicle.getWeapons().values()) {
                            if (weaponList != null && !weaponList.isEmpty()) {
                                for (Weapon weapon : weaponList) {
                                    weapon.fire(aimDir);
                                    fired = true;
                                }
                            }
                        }
                        if (fired) fireCooldown = shotInterval;
                    }
                }
            }
            if (fireCooldown > 0) fireCooldown--;

            if (vehicle instanceof EngineVehicle engineVehicle) {
                boolean isLanding = isGuardPilot && peaceTicks > 300;
                if (!isLanding) {
                    if (engineVehicle.getEngineTarget() < 1.0f) engineVehicle.setEngineTarget(1.0f);
                } else {
                    if (engineVehicle.getEngineTarget() > 0.2f) engineVehicle.setEngineTarget(0.2f);
                }
            }

            mob.setYaw(self.getYaw());
            mob.setHeadYaw(self.getYaw());
        }
    }

    @Unique
    private static boolean isGuardVillager(Entity entity) {
        if (entity == null) return false;
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id != null) {
            String path = id.getPath().toLowerCase();
            String namespace = id.getNamespace().toLowerCase();
            return namespace.contains("guard") || path.contains("guard");
        }
        return false;
    }

    @Unique
    private static boolean isFriendlyToGuards(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof PlayerEntity || entity instanceof VillagerEntity || entity instanceof IronGolemEntity) return true;
        return isGuardVillager(entity);
    }

    @Unique
    private boolean hasRaiderPassenger(Entity vehicle) {
        if (vehicle == null) return false;
        for (Entity passenger : vehicle.getPassengerList()) {
            if (passenger instanceof RaiderEntity) return true;
        }
        return false;
    }

    @Unique
    private boolean hasFriendlyPassenger(Entity vehicle) {
        if (vehicle == null) return false;
        for (Entity passenger : vehicle.getPassengerList()) {
            if (isFriendlyToGuards(passenger)) return true;
        }
        return false;
    }

    @Unique
    private void tryScramblePilot(Entity self, VehicleEntity vehicle) {
        if (!(self.getWorld() instanceof ServerWorld world)) return;

        // Check fuel if engine vehicle
        if (vehicle instanceof EngineVehicle engineVehicle) {
            boolean hasFuel = false;
            var desc = engineVehicle.getInventoryDescription();
            if (desc != null) {
                var boilerSlots = desc.getSlots(VehicleInventoryDescription.BOILER);
                for (var slot : boilerSlots) {
                    if (!engineVehicle.getInventory().getStack(slot.index()).isEmpty()) {
                        hasFuel = true;
                        break;
                    }
                }
            }
            if (!hasFuel) return;
        }

        // Search for candidate mobs within 24 blocks
        List<MobEntity> nearby = world.getEntitiesByClass(
                MobEntity.class,
                self.getBoundingBox().expand(24.0),
                m -> m.isAlive() && !m.hasVehicle() && (m instanceof RaiderEntity || isGuardVillager(m))
        );

        MobEntity bestCandidate = null;
        double bestDistSq = Double.MAX_VALUE;

        for (MobEntity candidate : nearby) {
            boolean isGuard = isGuardVillager(candidate);
            boolean isRaider = candidate instanceof RaiderEntity;

            boolean underThreat = false;
            LivingEntity curTarget = candidate.getTarget();

            if (isGuard) {
                if (curTarget != null && curTarget.isAlive() && curTarget instanceof RaiderEntity) {
                    underThreat = true;
                } else {
                    boolean raiderNearby = !world.getEntitiesByClass(
                            RaiderEntity.class,
                            candidate.getBoundingBox().expand(30.0),
                            e -> e.isAlive() && !e.isSpectator()
                    ).isEmpty();
                    if (raiderNearby) underThreat = true;
                }
            } else if (isRaider) {
                if (curTarget != null && curTarget.isAlive() && (curTarget instanceof PlayerEntity || isGuardVillager(curTarget) || curTarget instanceof VillagerEntity)) {
                    underThreat = true;
                } else {
                    boolean playerNearby = !world.getPlayers(
                            p -> !p.isCreative() && !p.isSpectator() && p.squaredDistanceTo(candidate) < 30 * 30
                    ).isEmpty();
                    boolean guardNearby = !world.getEntitiesByClass(
                            MobEntity.class,
                            candidate.getBoundingBox().expand(30.0),
                            e -> e.isAlive() && isGuardVillager(e)
                    ).isEmpty();
                    if (playerNearby || guardNearby) underThreat = true;
                }
            }

            if (underThreat) {
                double distSq = candidate.squaredDistanceTo(self);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestCandidate = candidate;
                }
            }
        }

        if (bestCandidate != null) {
            if (bestDistSq <= 9.0) {
                bestCandidate.startRiding(vehicle);
            } else {
                bestCandidate.getNavigation().startMovingTo(self, 1.3);
            }
        }
    }
}