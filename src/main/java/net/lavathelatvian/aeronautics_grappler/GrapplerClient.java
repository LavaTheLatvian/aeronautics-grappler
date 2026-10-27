package net.lavathelatvian.aeronautics_grappler;

import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = AeronauticsGrappler.MODID, value = Dist.CLIENT)
public class GrapplerClient {

    // Draws the grappler model through GrapplerItemRenderer (loaded plunger that disappears when fired).
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(
                SimpleCustomRenderer.create(AeronauticsGrappler.GRAPPLER.get(), new GrapplerItemRenderer()),
                AeronauticsGrappler.GRAPPLER.get());
    }
}