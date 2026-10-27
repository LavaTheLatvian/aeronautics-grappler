package net.lavathelatvian.aeronautics_grappler;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.concurrent.atomic.AtomicReference;

public class PlungerProjectile extends ThrowableItemProjectile {

    private static final int MAX_FLIGHT_TICKS = 600; // safety net only; the real limit is Config.maxRange()

    // Pull tuning
    private static final double BASE_PULL_SPEED = 0.4;
    private static final double PULL_SPEED_PER_BLOCK = 0.07;
    private static final double MAX_PULL_SPEED = 5;
    // Multiplier on the pull speed worked out above, to make the pull punchier.
    private static final double PULL_BOOST = 1.5;
    // How quickly the player's velocity is steered towards the pull velocity (0-1 per tick).
    private static final double PULL_RESPONSIVENESS = 0.4;
    // Same, but for the very first tick when the plunger sticks (higher = firmer initial tug).
    private static final double FIRST_PULL_RESPONSIVENESS = 1;
    // Distance (blocks) from the plunger, measured from your eyes, at which you settle and hang.
    private static final double HANG_DISTANCE = 1.0;
    // How hard the spring pulls per block of offset from the hang point, inside STOP_DISTANCE.
    private static final double HANG_STIFFNESS = 0.4;
    // How quickly velocity follows the spring while hanging (0-1 per tick). */
    private static final double HANG_RESPONSIVENESS = 0.5;

    // How strong the pull is on sublevels
    private static final double PLAYER_MASS = 2.5;
    private static final double TICKS_PER_SECOND = 20.0;
    // first tug power multiplier on sublevels
    private static final double FIRST_PULL_REACTION_SCALE = 2.5;
    // How far sublevels feel strongest pull
    private static final double REACTION_FADE_DISTANCE = 10.0;
    // Stops pulling nearby sublevels
    private static final double STOP_DISTANCE = 2.0;
    // Max pull on sublevel
    private static final double MAX_REACTION_IMPULSE = 9.0;

    // Synced to clients so the renderer knows how to draw the plunger and where the rope starts.
    private static final EntityDataAccessor<Boolean> DATA_STUCK =
            SynchedEntityData.defineId(PlungerProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Direction> DATA_STUCK_FACE =
            SynchedEntityData.defineId(PlungerProjectile.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Boolean> DATA_MAIN_HAND =
            SynchedEntityData.defineId(PlungerProjectile.class, EntityDataSerializers.BOOLEAN);

    // Ticks since the plunger stuck
    private int stuckTicks;
    // Where the plunger started flying (server side)
    private Vec3 flightStart = null;
    // Block the plunger is stuck to
    private BlockPos stuckBlock;

    // Impulse to be applied to sublevel
    private final AtomicReference<Vec3> pendingReaction = new AtomicReference<>(Vec3.ZERO);
    private final ForceTotal forceTotal = new ForceTotal();

    // Needed by the entity type registration.
    public PlungerProjectile(EntityType<? extends PlungerProjectile> type, Level level) {
        super(type, level);
    }

    // Used by the Grappler item when firing.
    public PlungerProjectile(Level level, LivingEntity owner) {
        super(AeronauticsGrappler.PLUNGER_PROJECTILE.get(), owner, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STUCK, false);
        builder.define(DATA_STUCK_FACE, Direction.UP);
        builder.define(DATA_MAIN_HAND, true);
    }

    // Required by ThrowableItemProjectile
    @Override
    protected Item getDefaultItem() {
        return Items.AIR;
    }

    // State shared with the renderer

    public boolean isStuck() {
        return this.entityData.get(DATA_STUCK);
    }

    // block face the plunger is stuck to
    public Direction getStuckFace() {
        return this.entityData.get(DATA_STUCK_FACE);
    }

    public int getStuckTicks() {
        return this.stuckTicks;
    }


    public boolean firedFromMainHand() {
        return this.entityData.get(DATA_MAIN_HAND);
    }

    public void setFiredFromMainHand(boolean mainHand) {
        this.entityData.set(DATA_MAIN_HAND, mainHand);
    }

    // Behavior

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            Entity owner = getOwner();
            if (owner == null || !owner.isAlive()) {
                discard();
                return;
            }
            // drop plunger is player switches item
            if (!ownerStillHoldsGrappler(owner)) {
                // apply cooldown
                if (owner instanceof Player player) {
                    GrapplerItem.applyReloadCooldown(player);
                }
                discard();
                return;
            }
        }

        // need to run this for shit to work
        super.tick();

        if (isStuck()) {
            // makes plunger not move around
            this.setDeltaMovement(Vec3.ZERO);
            stuckTicks++;
            if (!level().isClientSide()) {
                if (stuckBlockIsGone()) {
                    release();
                    return;
                }
                pullOwner();
            }
            return;
        }

        if (!level().isClientSide()) {
            if (flightStart == null) {
                flightStart = this.position();
            }
            if (tickCount > MAX_FLIGHT_TICKS
                    || this.position().distanceTo(flightStart) > Config.maxRange()) {
                discard();
            }
        }
    }

    private boolean ownerStillHoldsGrappler(Entity owner) {
        if (!(owner instanceof Player player)) {
            return true;
        }
        InteractionHand hand = firedFromMainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        return player.getItemInHand(hand).getItem() instanceof GrapplerItem;
    }

    private void pullOwner() {
        steerOwner(this.position(), PULL_RESPONSIVENESS, 1.0);
    }

    private void firstPull(Vec3 hit) {
        steerOwner(hit, FIRST_PULL_RESPONSIVENESS, FIRST_PULL_REACTION_SCALE);
    }

    // steers player to plunger
    private void steerOwner(Vec3 plungerPos, double responsiveness, double reactionScale) {
        if (!(getOwner() instanceof Player owner) || owner.level() != level()) {
            return;
        }

        // change math per tick
        Vec3 target = Sable.HELPER.projectOutOfSubLevel(level(), plungerPos);
        Vec3 toTarget = target.subtract(owner.getEyePosition());
        double distance = toTarget.length();

        owner.fallDistance = 0.0F;

        Vec3 desired;
        double lerp = responsiveness;
        if (distance < STOP_DISTANCE) {
            // Hanging logic
            double offset = distance - HANG_DISTANCE;
            desired = toTarget.scale(1.0 / distance).scale(offset * HANG_STIFFNESS);
            lerp = HANG_RESPONSIVENESS;
        } else {
            double speed = Mth.clamp(
                    (BASE_PULL_SPEED + distance * PULL_SPEED_PER_BLOCK) * PULL_BOOST,
                    BASE_PULL_SPEED, MAX_PULL_SPEED) * Config.pullStrength();
            desired = toTarget.scale(1.0 / distance).scale(speed);
        }

        Vec3 before = owner.getDeltaMovement();
        Vec3 after = before.lerp(desired, lerp);
        owner.setDeltaMovement(after);
        owner.hurtMarked = true; // sync to client

        // sublevel is pulled less when closer
        double fade = Mth.clamp((distance - STOP_DISTANCE) / (REACTION_FADE_DISTANCE - STOP_DISTANCE), 0.0, 1.0);
        addReaction(after.subtract(before).scale(reactionScale * fade));
    }

    private void addReaction(Vec3 ownerVelocityChange) {
        if (Sable.HELPER.getContaining(this) == null) {
            return;
        }
        // Blocks/tick -> m/s, and the ship is pushed the opposite way to the owner's change.
        Vec3 impulse = ownerVelocityChange.scale(-PLAYER_MASS * TICKS_PER_SECOND);
        pendingReaction.accumulateAndGet(impulse, Vec3::add);
    }

    void applyReaction(ServerSubLevel subLevel, RigidBodyHandle handle) {
        Vec3 impulse = pendingReaction.getAndSet(Vec3.ZERO);
        if (impulse.lengthSqr() < 1.0E-8) {
            return;
        }
        // cap impulse
        double length = impulse.length();
        if (length > MAX_REACTION_IMPULSE) {
            impulse = impulse.scale(MAX_REACTION_IMPULSE / length);
        }

        // applies impulse properly
        Vector3d local = subLevel.logicalPose()
                .transformNormalInverse(JOMLConversion.toJOML(impulse), new Vector3d());
        forceTotal.applyImpulseAtPoint(subLevel, JOMLConversion.toJOML(this.position()), local);
        handle.applyForcesAndReset(forceTotal);
    }

    private void release() {
        if (getOwner() instanceof Player owner) {
            GrapplerItem.applyReloadCooldown(owner);
        }
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return false;
    }

    @Override
    protected AABB makeBoundingBox() {
        AABB box = this.getDimensions(this.getPose()).makeBoundingBox(this.position());
        return box.move(0.0, -box.getYsize() / 2.0, 0.0);
    }

    private boolean stuckBlockIsGone() {
        return stuckBlock != null
                && level().getBlockState(stuckBlock).getCollisionShape(level(), stuckBlock).isEmpty();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);

        this.setDeltaMovement(Vec3.ZERO);
        this.noPhysics = true;
        this.setNoGravity(true); // makes plunger not fall once its stuck

        // stick to blocks
        Vec3 hit = result.getLocation();
        SubLevel subLevel = Sable.HELPER.getContaining(level(), hit);
        Vec3 from = subLevel != null
                ? subLevel.logicalPose().transformPositionInverse(this.position())
                : this.position();
        Vec3 backOff = hit.subtract(from).normalize().scale(0.05);
        this.setPosRaw(hit.x - backOff.x, hit.y - backOff.y, hit.z - backOff.z);

        this.setOldPosAndRot();

        if (level().isClientSide()) {
            return;
        }

        // first tug stronger
        firstPull(hit);
        this.stuckBlock = result.getBlockPos();
        this.entityData.set(DATA_STUCK_FACE, result.getDirection());
        this.entityData.set(DATA_STUCK, true);
        PlungerPhysics.add(level(), this);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            PlungerPhysics.remove(level(), this);
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}