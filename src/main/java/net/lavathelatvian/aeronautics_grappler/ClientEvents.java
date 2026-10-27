package net.lavathelatvian.aeronautics_grappler;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// Client-only: this class is never loaded on a dedicated server.
@EventBusSubscriber(modid = AeronauticsGrappler.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AeronauticsGrappler.PLUNGER_PROJECTILE.get(), PlungerProjectileRenderer::new);
    }
}