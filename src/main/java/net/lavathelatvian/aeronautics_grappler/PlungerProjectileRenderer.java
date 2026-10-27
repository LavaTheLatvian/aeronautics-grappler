package net.lavathelatvian.aeronautics_grappler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlungerProjectileRenderer extends EntityRenderer<PlungerProjectile> {

    private static final double ROPE_TIE_OFFSET = 0.6;

    private static final float STUCK_OFFSET = 0.0F;


    public PlungerProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PlungerProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean stuck = entity.isStuck();

        float yRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // "back" points away from wall.
        // "sideways" is any vector perpendicular to wall.
        Vec3 back;
        Vec3 sideways;
        if (stuck) {
            Direction face = entity.getStuckFace();
            back = Vec3.atLowerCornerOf(face.getNormal());
            sideways = face.getAxis().isHorizontal()
                    ? new Vec3(0, 1, 0)
                    : new Vec3(0, 0, -1);
        } else {
            back = entity.calculateViewVector(-xRot, -yRot).reverse();
            sideways = entity.calculateViewVector(-xRot, -yRot - 90.0F).reverse();
        }

        Vec3 plungerPos = stuck ? entity.position() : entity.getPosition(partialTick);

        // Light is sampled half a block out in front of the wall (in the entity's own space, which is
        // where the world's light data is), so a plunger sitting in or on a block is never drawn black.
        Vec3 lightSamplePos = plungerPos.add(back.scale(0.5));

        // convert plunger to be drawn right when on sublevel
        SubLevel subLevel = Sable.HELPER.getContainingClient(plungerPos);
        Pose3dc pose = null;
        if (subLevel != null) {
            pose = ((ClientSubLevel) subLevel).renderPose(partialTick);
            plungerPos = pose.transformPosition(plungerPos);
            back = pose.transformNormal(back);
            sideways = pose.transformNormal(sideways);
        }

        Vec3 ropeEnd = plungerPos.add(back.scale(ROPE_TIE_OFFSET));
        Entity owner = entity.getOwner();
        Vec3 ropeStart = owner != null ? getLauncherPos(entity, owner, partialTick) : null;

        // Brightest of the vanilla value and the open-air value in front of the plunger.
        int sampled = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(lightSamplePos));
        int light = LightTexture.pack(
                Math.max(LightTexture.block(packedLight), LightTexture.block(sampled)),
                Math.max(LightTexture.sky(packedLight), LightTexture.sky(sampled)));

        // Angle plunger joint (only matters once stuck) and how far the spool has spun.
        float jointAngle = 0.0F;
        float spoolSpin = 0.0F;
        if (ropeStart != null) {
            if (stuck) {
                Vector3f toRope = new Vector3f(
                        (float) (ropeStart.x - ropeEnd.x),
                        (float) (ropeStart.y - ropeEnd.y),
                        (float) (ropeStart.z - ropeEnd.z)).normalize();
                Vector3f ref = new Vector3f((float) sideways.x, (float) sideways.y, (float) sideways.z);
                Vector3f normal = new Vector3f((float) back.x, (float) back.y, (float) back.z);
                jointAngle = ref.angleSigned(toRope, normal) + Mth.HALF_PI;
                if (Float.isNaN(jointAngle)) {
                    jointAngle = 0.0F;
                }
            }
            spoolSpin = (float) ropeEnd.distanceTo(ropeStart) * 90.0F * 2.6F;
        }

        poseStack.pushPose();
        if (pose != null) {
            // On sublevels custom logic
            poseStack.last().pose().identity();
            poseStack.last().normal().identity();
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            poseStack.translate(plungerPos.x - cam.x, plungerPos.y - cam.y, plungerPos.z - cam.z);
            Quaterniondc q = pose.orientation();
            poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
        }
        if (stuck) {
            poseStack.mulPose(entity.getStuckFace().getRotation());
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(xRot));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));

        poseStack.scale(1.75F, 1.75F, 1.75F);

        float extra = stuck ? STUCK_OFFSET / 1.75F : 0.0F;
        poseStack.translate(0.0F, 0.0F, 2.5F / 16.0F + extra);
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));

        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        SuperByteBuffer body = CachedBuffers.partial(SimPartialModels.LAUNCHED_PLUNGER_BODY, Blocks.AIR.defaultBlockState());
        SuperByteBuffer joint = CachedBuffers.partial(SimPartialModels.LAUNCHED_PLUNGER_JOINT, Blocks.AIR.defaultBlockState());
        SuperByteBuffer spool = CachedBuffers.partial(SimPartialModels.LAUNCHED_PLUNGER_SPOOL, Blocks.AIR.defaultBlockState());

        body.light(light).renderInto(poseStack, vb);

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(jointAngle)));
        joint.light(light).renderInto(poseStack, vb);
        poseStack.translate(0.0F, 0.0F, 3.0F / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(spoolSpin));
        spool.light(light).renderInto(poseStack, vb);
        poseStack.popPose();

        poseStack.popPose();

        // rope
        if (ropeStart != null) {
            // wobbles at first, goes straight
            float stuckTime = entity.getStuckTicks() + (stuck ? partialTick : 0.0F);
            float slack = Mth.clamp(1.0F - (entity.tickCount + partialTick) / 40.0F - stuckTime / 8.0F, 0.0F, 1.0F);

            RopeRenderer.render(buffer, ropeStart, ropeEnd, slack, entity.tickCount + partialTick);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    // where rope leaves the grappler from
    private static Vec3 getLauncherPos(PlungerProjectile plunger, Entity owner, float partialTick) {
        if (!(owner instanceof Player player)) {
            return owner.getEyePosition(partialTick);
        }

        Minecraft mc = Minecraft.getInstance();

        boolean mainHand = plunger.firedFromMainHand();
        double flip = mainHand == (player.getMainArm() == HumanoidArm.RIGHT) ? -1.0 : 1.0;

        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {

            // if in first person do fancy math
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 offset = GrapplerItem.BARREL_OFFSET;
            Vector3f look = camera.getLookVector();
            Vector3f up = camera.getUpVector();
            Vector3f left = camera.getLeftVector();
            double l = flip * offset.x;
            return camera.getPosition().add(
                    look.x() * offset.z + up.x() * offset.y + left.x() * l,
                    look.y() * offset.z + up.y() * offset.y + left.y() * l,
                    look.z() * offset.z + up.z() * offset.y + left.z() * l);
        }

        // if in third person attach rope to torso
        return player.getPosition(partialTick).add(0.0, player.getBbHeight() * 0.7, 0.0);
    }

    @Override
    public ResourceLocation getTextureLocation(PlungerProjectile entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(PlungerProjectile entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }
}