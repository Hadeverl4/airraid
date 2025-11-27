package com.example.addon.mixin;

import immersive_aircraft.entity.*;
import immersive_aircraft.entity.weapon.RotaryCannon;
import immersive_aircraft.entity.weapon.Weapon;
import immersive_aircraft.entity.weapon.BombBay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
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

    @Inject(method = "damage", at = @At(value = "INVOKE", target = "Limmersive_aircraft/entity/VehicleEntity;discard()V"))
    private void forceDropOnCreativeKill(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (self.getFirstPassenger() instanceof MobEntity) {
            vehicle.dropStack(new ItemStack(vehicle.asItem()));
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void allowMobPilot(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        VehicleEntity vehicle = (VehicleEntity) (Object) this;

        if (self.getWorld().isClient) return;

        Entity passenger = self.getFirstPassenger();

        if (passenger instanceof MobEntity mob) {
            float inputYaw = 0.0f;
            float inputPitch = 0.0f;
            float inputThrottle = 0.0f;
            boolean wantToFire = false;

            LivingEntity target = mob.getTarget();

            // 1. Target Acquisition
            if (target == null) {
                target = self.getWorld().getPlayers().stream()
                        .filter(p -> !p.isSpectator())
                        .filter(p -> p.squaredDistanceTo(self) < 300 * 300)
                        .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(self)))
                        .orElse(null);
                if (target != null) mob.setTarget(target);
            }

            boolean isVtol = vehicle instanceof AirshipEntity || vehicle instanceof GyrodyneEntity || vehicle instanceof QuadrocopterEntity;

            boolean hasTurret = vehicle instanceof WarshipEntity;
            boolean hasBombBay = false;

            if (vehicle instanceof InventoryVehicleEntity invVehicle) {
                for (List<Weapon> list : invVehicle.getWeapons().values()) {
                    for (Weapon w : list) {
                        if (w instanceof RotaryCannon) hasTurret = true;
                        if (w instanceof BombBay || w.getStack().toString().contains("bomb")) hasBombBay = true;
                    }
                }
            }

            // 2. Combat Flight Logic
            if (target != null) {
                // --- COLLISION AVOIDANCE ---
                Vec3d selfPos = self.getPos();
                Vec3d targetPos = target.getPos();
                Vec3d toTarget = targetPos.subtract(selfPos);
                double realDist = toTarget.length(); // Keep track of real distance for firing range logic

                Vec3d avoidance = Vec3d.ZERO;
                // Scan for nearby vehicles to avoid
                List<Entity> nearbyVehicles = self.getWorld().getOtherEntities(self, self.getBoundingBox().expand(25.0), e -> e instanceof VehicleEntity);

                for (Entity neighbor : nearbyVehicles) {
                    Vec3d toSelf = selfPos.subtract(neighbor.getPos());
                    double distSq = toSelf.lengthSquared();
                    if (distSq < 625.0 && distSq > 0.01) { // Within 25 blocks
                        double dist = Math.sqrt(distSq);
                        // Force increases as distance decreases.
                        // Multiplier 2.5 ensures avoidance is prioritized over target tracking when very close.
                        double force = (25.0 - dist) / 25.0;
                        avoidance = avoidance.add(toSelf.normalize().multiply(force * 2.5));
                    }
                }

                // Calculate "Virtual Target"
                // The plane will fly towards this point instead of the actual enemy
                // This blends the desire to hit the enemy with the desire to not hit a friend
                Vec3d desiredDir = toTarget.normalize().add(avoidance).normalize();
                Vec3d virtualTargetPos = selfPos.add(desiredDir.multiply(realDist));

                double dx = virtualTargetPos.x - self.getX();
                double dy = virtualTargetPos.y - self.getY();
                double dz = virtualTargetPos.z - self.getZ();
                // recalculate hDist based on virtual target for steering math
                double hDist = Math.sqrt(dx*dx + dz*dz);

                // --- FLIGHT MATH ---
                int groundY = self.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int)self.getX(), (int)self.getZ());
                boolean unsafeLow = self.getY() < groundY + 15;

                int targetGroundY = self.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int)target.getX(), (int)target.getZ());
                boolean isAirTarget = target.getY() > targetGroundY + 10;

                float angleToTarget = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                float yawDiff = MathHelper.wrapDegrees(angleToTarget - self.getYaw());

                if (isVtol) {
                    double desiredY = target.getY() + 10;
                    if (hasBombBay) desiredY = target.getY() + 30;

                    // Use dy from virtual target, so if avoidance pushes up, we fly up
                    if (self.getY() < desiredY - 5) { inputPitch = 1.0f; inputThrottle = 0.0f; }
                    else if (self.getY() < desiredY) { inputPitch = 1.0f; inputThrottle = hDist > 15 ? 0.6f : 0.0f; }
                    else if (self.getY() > desiredY + 10) { inputPitch = -1.0f; inputThrottle = hDist > 15 ? 0.6f : 0.0f; }
                    else { inputPitch = 0.15f; inputThrottle = hDist > 15 ? 0.6f : 0.0f; }

                    inputYaw = -MathHelper.clamp(yawDiff / 45.0f, -1.0f, 1.0f);
                } else {
                    inputThrottle = 1.0f;
                    float targetPitchDeg;

                    if (unsafeLow) {
                        targetPitchDeg = -30.0f;
                    } else if (hasBombBay) {
                        if (hDist > 20) {
                            targetPitchDeg = (self.getY() > target.getY() + 40) ? 5.0f : 0.0f;
                        } else {
                            targetPitchDeg = 0.0f;
                        }
                    } else if (isAirTarget) {
                        float leadYaw = angleToTarget;
                        if (hDist < 30) targetPitchDeg = -40.0f;
                        else {
                            float directPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
                            targetPitchDeg = MathHelper.clamp(directPitch, -30, 30);
                        }
                    } else {
                        if (hDist > 50) {
                            float directPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
                            targetPitchDeg = MathHelper.clamp(directPitch, -25, 10);
                        } else {
                            targetPitchDeg = -30.0f;
                        }
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

                // Fire Trigger (Check REAL distance, not virtual)
                if (hasBombBay) {
                    // Re-calc horizontal distance to ACTUAL target for bombing accuracy
                    double realHDist = Math.sqrt(toTarget.x*toTarget.x + toTarget.z*toTarget.z);
                    if (realHDist < 4.0 && self.getY() > target.getY() + 5) wantToFire = true;
                } else if (hasTurret) {
                    if (realDist < 120) wantToFire = true;
                } else {
                    float tolerance = isVtol ? 35.0f : 25.0f;
                    // For fixed guns, we use yawDiff derived from virtual target
                    // This means if we are dodging a collision, we likely won't fire, which is good behavior
                    if (Math.abs(yawDiff) < tolerance && realDist < 120) wantToFire = true;
                }

                mob.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getPos());
            } else {
                if (self.getY() < self.getWorld().getSeaLevel() + 60) inputPitch = isVtol ? 0.3f : -0.1f;
                inputThrottle = isVtol ? 0.3f : 0.8f;
                inputYaw = 0.05f;
            }

            if (isVtol) vehicle.setInputs(inputYaw, inputPitch, inputThrottle);
            else vehicle.setInputs(inputYaw, 0.0f, inputPitch);

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
                            if (hitE instanceof RaiderEntity || (hitE instanceof VehicleEntity && hasRaiderPassenger(hitE))) {
                                isSafe = false;
                            }
                        }
                    }

                    if (isSafe) {
                        boolean fired = false;
                        for (List<Weapon> weaponList : invVehicle.getWeapons().values()) {
                            if (weaponList != null && !weaponList.isEmpty()) {
                                for (Weapon weapon : weaponList) {
                                    weapon.fire(aimDir);
                                    fired = true;
                                }
                            }
                        }
                        if (fired) fireCooldown = 5;
                    }
                }
            }
            if (fireCooldown > 0) fireCooldown--;

            if (vehicle instanceof EngineVehicle engineVehicle) {
                if (engineVehicle.getEngineTarget() < 1.0f) engineVehicle.setEngineTarget(1.0f);
            }

            mob.setYaw(self.getYaw());
            mob.setHeadYaw(self.getYaw());
        }
    }

    @Unique
    private boolean hasRaiderPassenger(Entity vehicle) {
        for (Entity passenger : vehicle.getPassengerList()) {
            if (passenger instanceof RaiderEntity) return true;
        }
        return false;
    }
}