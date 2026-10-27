package net.lavathelatvian.aeronautics_grappler;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Lets stuck plungers push back on the Sable ship they are stuck to. Sable's physics runs on its own
 * steps, so a plunger cannot apply force to a ship from its normal entity tick; instead every stuck
 * plunger is kept in a per-level list here, and on each physics step the ones that are on a ship are
 * asked to apply their stored reaction (see {@link PlungerProjectile#applyReaction}).
 *
 * Registers itself on mod setup, so nothing else needs to call it.
 */
@EventBusSubscriber(modid = AeronauticsGrappler.MODID)
public final class PlungerPhysics {

    // Concurrent in case the physics step is not on the same thread as entity ticking.
    private static final WorldAttached<Collection<PlungerProjectile>> STUCK_PLUNGERS =
            new WorldAttached<>(level -> ConcurrentHashMap.newKeySet());

    private PlungerPhysics() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        SableEventPlatform.INSTANCE.onPhysicsTick(PlungerPhysics::physicsTick);
    }

    /** Called by a plunger when it sticks. */
    static void add(Level level, PlungerProjectile plunger) {
        STUCK_PLUNGERS.get(level).add(plunger);
    }

    /** Called by a plunger when it is removed for any reason. */
    static void remove(Level level, PlungerProjectile plunger) {
        STUCK_PLUNGERS.get(level).remove(plunger);
    }

    private static void physicsTick(SubLevelPhysicsSystem physicsSystem, double timeStep) {
        ServerLevel level = physicsSystem.getLevel();
        for (PlungerProjectile plunger : STUCK_PLUNGERS.get(level)) {
            ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(plunger);
            if (subLevel != null) {
                plunger.applyReaction(subLevel, physicsSystem.getPhysicsHandle(subLevel));
            }
        }
    }
}