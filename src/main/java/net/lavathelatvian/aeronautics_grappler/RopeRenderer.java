package net.lavathelatvian.aeronautics_grappler;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RopeRenderer {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("simulated", "textures/block/rope_particle.png");

    private static final float HALF_THICKNESS = 1.0F / 16.0F; // rope is 2/16 of a block thick
    private static final float U_MAX = 2.0F / 16.0F;          // each face uses a 2px-wide strip of the texture
    private static final float V_PER_BLOCK = 2.0F;            // texture repeats this many times per block of rope
    private static final double MAX_LENGTH = 256.0;
    private static final double WOBBLE_AMPLITUDE = 0.35;

    private RopeRenderer() {
    }

    public static void render(MultiBufferSource buffer, Vec3 start, Vec3 end, float slack, float time) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.05 || length > MAX_LENGTH) {
            return;
        }

        // Two directions perpendicular to the rope, used to wobble it.
        Vec3 axis = delta.scale(1.0 / length);
        Vec3 reference = Math.abs(axis.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 sideDir = axis.cross(reference).normalize();
        Vec3 liftDir = sideDir.cross(axis).normalize();

        // Center line of the rope.
        int segments = Mth.clamp((int) Math.ceil(length * 3.0), 4, 128);
        double amplitude = WOBBLE_AMPLITUDE * slack * Math.min(1.0, length / 6.0);
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double envelope = Math.sin(Math.PI * t); // pinned at both ends
            double along = t * length;
            double sideWave = Math.sin(time * 0.35 + along * 1.2);
            double liftWave = Math.cos(time * 0.27 + along * 0.9);
            points[i] = start.add(delta.scale(t))
                    .add(sideDir.scale(sideWave * amplitude * envelope))
                    .add(liftDir.scale(liftWave * amplitude * envelope));
        }

        // Build a square cross-section at every point. The frame is carried along the rope so the
        // tube does not twist.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3[][] corners = new Vec3[segments + 1][4];
        Vec3[][] normals = new Vec3[segments + 1][4];
        int[] light = new int[segments + 1];
        float[] v = new float[segments + 1];

        Vec3 n1 = sideDir;
        float distance = 0.0F;
        for (int i = 0; i <= segments; i++) {
            Vec3 tangent = points[Math.min(i + 1, segments)].subtract(points[Math.max(i - 1, 0)]).normalize();
            n1 = n1.subtract(tangent.scale(n1.dot(tangent))).normalize();
            Vec3 n2 = tangent.cross(n1);

            Vec3 a = n1.scale(HALF_THICKNESS);
            Vec3 b = n2.scale(HALF_THICKNESS);
            Vec3 centre = points[i].subtract(camera);
            corners[i][0] = centre.subtract(a).subtract(b);
            corners[i][1] = centre.add(a).subtract(b);
            corners[i][2] = centre.add(a).add(b);
            corners[i][3] = centre.subtract(a).add(b);

            // Normal of the face between corner f and corner f+1.
            normals[i][0] = n2.reverse();
            normals[i][1] = n1;
            normals[i][2] = n2;
            normals[i][3] = n1.reverse();

            light[i] = LevelRenderer.getLightColor(level, BlockPos.containing(points[i]));

            if (i > 0) {
                distance += (float) points[i].distanceTo(points[i - 1]);
            }
            v[i] = distance * V_PER_BLOCK;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int i = 0; i < segments; i++) {
            int j = i + 1;
            for (int face = 0; face < 4; face++) {
                int next = (face + 1) % 4;
                vertex(consumer, corners[i][face], 0.0F, v[i], light[i], normals[i][face]);
                vertex(consumer, corners[i][next], U_MAX, v[i], light[i], normals[i][face]);
                vertex(consumer, corners[j][next], U_MAX, v[j], light[j], normals[j][face]);
                vertex(consumer, corners[j][face], 0.0F, v[j], light[j], normals[j][face]);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Vec3 pos, float u, float v, int light, Vec3 normal) {
        consumer.addVertex((float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }
}