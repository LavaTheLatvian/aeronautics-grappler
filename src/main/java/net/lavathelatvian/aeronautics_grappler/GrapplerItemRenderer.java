package net.lavathelatvian.aeronautics_grappler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Draws the grappler's own model, plus a loaded plunger on the barrel. The plunger is drawn from
 * Simulated's partial models at runtime (nothing of it is copied into this mod) and is hidden
 * while a plunger is out, so it disappears when you fire.
 */
public class GrapplerItemRenderer extends CustomRenderedItemModelRenderer {

    // Where the loaded plunger sits, relative to the middle of the item model, in 1/16 block units:
    // sideways, up, forward (forward is -Z). Tune these until it sits on your barrel.
    private static final float PLUNGER_X = 0.0F;
    private static final float PLUNGER_Y = 0.5F;
    private static final float PLUNGER_Z = -4.5F;

    // Reload animation. Everything is timed in ticks since the cooldown started
    // (the cooldown after reeling in is GrapplerItem.RETRACT_COOLDOWN_TICKS long).

    /** How many ticks after retracting the plunger reappears and starts sliding in. 0 = immediately. */
    private static final float COOLDOWN_TICKS = (float) Config.cooldown();
    private static final float ANIMATION_START_DELAY_TICKS = COOLDOWN_TICKS - 5;
    /** How long the slide-in takes, in ticks. */
    private static final float SLIDE_DURATION_TICKS = 12.0F;
    /** How far out the plunger starts, in blocks. */
    private static final float SLIDE_DISTANCE = 1.0F / 12.0F;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer,
                          int light, int overlay) {
        renderer.render(model.getOriginalModel(), light);

        LocalPlayer player = Minecraft.getInstance().player;
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float percent = player == null ? 0.0F
                : player.getCooldowns().getCooldownPercent(stack.getItem(), partialTicks);
        float ticksLeft = percent * GrapplerItem.RETRACT_COOLDOWN_TICKS;
        float ticksElapsed = GrapplerItem.RETRACT_COOLDOWN_TICKS - ticksLeft;

        if (!hasPlungerOut() && ticksElapsed >= ANIMATION_START_DELAY_TICKS) {
            renderLoadedPlunger(ms, buffer, light, ticksElapsed - ANIMATION_START_DELAY_TICKS);
        }
    }

    /** Same three parts the Plunger Launcher draws: body, joint, and the spool just behind the cup. */
    private static void renderLoadedPlunger(PoseStack ms, MultiBufferSource buffer, int light, float ticksIntoAnimation) {
        SuperByteBuffer body = CachedBuffers.partial(SimPartialModels.LAUNCHED_PLUNGER_BODY, Blocks.AIR.defaultBlockState());
        SuperByteBuffer joint = CachedBuffers.partial(SimPartialModels.LAUNCHED_PLUNGER_JOINT, Blocks.AIR.defaultBlockState());
        SuperByteBuffer spool = CachedBuffers.partial(SimPartialModels.LAUNCHED_PLUNGER_SPOOL, Blocks.AIR.defaultBlockState());

        // Slide-in: 1 = fully out, 0 = seated; eased (cubed) so it speeds up into place.
        float slide = 1.0F - Mth.clamp(ticksIntoAnimation / SLIDE_DURATION_TICKS, 0.0F, 1.0F);
        slide = slide * slide * slide;

        ms.pushPose();
        ms.translate(PLUNGER_X / 16.0F, PLUNGER_Y / 16.0F, PLUNGER_Z / 16.0F - slide * SLIDE_DISTANCE);

        body.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        joint.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));

        ms.translate(0.0F, 0.0F, 3.0F / 16.0F);
        spool.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    /** True while the local player has a plunger in flight or stuck. */
    private static boolean hasPlungerOut() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return false;
        }
        // Not a bounding-box search: a plunger stuck to a ship sits far away in the ship's plot.
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof PlungerProjectile plunger
                    && !plunger.isRemoved()
                    && plunger.getOwner() == player) {
                return true;
            }
        }
        return false;
    }
}