package net.bullettrain.xenopixelsmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.missile.MissileSize;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 1:1 block-meter ballistic missiles (nozzle at origin, nose +Y). 20-sided
 * fuselage, elliptical ogive, raceway, and size-specific real-world silhouettes.
 */
public final class MissileModelDrawer {
    private static final ResourceLocation WHITE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int SIDES = 20;
    private static final float[] COS = new float[SIDES];
    private static final float[] SIN = new float[SIDES];
    private static final Vector3f N = new Vector3f();

    static {
        for (int i = 0; i < SIDES; i++) {
            double a = i * Math.PI * 2.0 / SIDES;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    private MissileModelDrawer() {
    }

    public static void draw(PoseStack pose, MultiBufferSource buffers, int packedLight, MissileSize size) {
        VertexConsumer buf = buffers.getBuffer(RenderType.entitySolid(WHITE));
        pose.pushPose();
        PoseStack.Pose last = pose.last();
        Matrix4f mat = last.pose();
        Matrix3f nrm = last.normal();
        int overlay = OverlayTexture.NO_OVERLAY;
        Livery l = Livery.of(size);
        float len = size.visualLength();
        float r = size.visualRadius();

        switch (size) {
            case SMALL -> drawAmraam(buf, mat, nrm, packedLight, overlay, l, len, r);
            case MEDIUM -> drawIskander(buf, mat, nrm, packedLight, overlay, l, len, r);
            case LARGE -> drawMinuteman(buf, mat, nrm, packedLight, overlay, l, len, r);
            case MEGA -> drawSarmat(buf, mat, nrm, packedLight, overlay, l, len, r);
        }
        pose.popPose();
    }

    /** AIM-120-class: long ogive, mid-body wings, tail fins. 3 m. */
    private static void drawAmraam(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                   int light, int ov, Livery l, float len, float r) {
        frustum(buf, mat, nrm, -len * 0.02f, 0f, r * 0.22f, r * 0.42f,
                l.exhaustR, l.exhaustG, l.exhaustB, light, ov);
        frustum(buf, mat, nrm, 0f, len * 0.04f, r * 0.55f, r * 0.38f,
                l.nozzleR, l.nozzleG, l.nozzleB, light, ov);
        disk(buf, mat, nrm, 0.001f, r * 0.32f, 0f, -1f, 0f, 0.08f, 0.08f, 0.09f, light, ov);

        frustum(buf, mat, nrm, len * 0.04f, len * 0.58f, r, r,
                l.bodyR, l.bodyG, l.bodyB, light, ov);
        ring(buf, mat, nrm, len * 0.22f, len * 0.245f, r, l, light, ov);
        raceway(buf, mat, nrm, len * 0.08f, len * 0.55f, r, l, light, ov);

        float og0 = len * 0.58f;
        ogive(buf, mat, nrm, og0, len, r * 0.98f, l.noseR, l.noseG, l.noseB, light, ov);

        float tw = Math.max(0.018f, r * 0.10f);
        clippedDelta(buf, mat, nrm, 4, len * 0.08f, len * 0.22f, len * 0.10f,
                r * 0.92f, r * 2.05f, tw, l.finR, l.finG, l.finB, light, ov);
        clippedDelta(buf, mat, nrm, 4, len * 0.48f, len * 0.58f, len * 0.52f,
                r * 0.92f, r * 1.65f, tw * 0.85f, l.finR, l.finG, l.finB, light, ov);
    }

    /** 9K720 Iskander-class: stubby ogive, boat-tail, small rear fins. 6 m. */
    private static void drawIskander(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                     int light, int ov, Livery l, float len, float r) {
        frustum(buf, mat, nrm, -len * 0.025f, 0f, r * 0.28f, r * 0.55f,
                l.exhaustR, l.exhaustG, l.exhaustB, light, ov);
        frustum(buf, mat, nrm, 0f, len * 0.05f, r * 0.72f, r * 0.50f,
                l.nozzleR, l.nozzleG, l.nozzleB, light, ov);
        disk(buf, mat, nrm, 0.001f, r * 0.42f, 0f, -1f, 0f, 0.07f, 0.07f, 0.06f, light, ov);
        frustum(buf, mat, nrm, len * 0.05f, len * 0.10f, r * 1.05f, r,
                l.bodyR * 0.78f, l.bodyG * 0.78f, l.bodyB * 0.78f, light, ov);

        frustum(buf, mat, nrm, len * 0.10f, len * 0.78f, r, r,
                l.bodyR, l.bodyG, l.bodyB, light, ov);
        ring(buf, mat, nrm, len * 0.28f, len * 0.31f, r, l, light, ov);
        ring(buf, mat, nrm, len * 0.52f, len * 0.545f, r, l, light, ov);
        raceway(buf, mat, nrm, len * 0.12f, len * 0.74f, r, l, light, ov);
        panel(buf, mat, nrm, len * 0.60f, len * 0.72f, r, l.accentR, l.accentG, l.accentB, light, ov);

        ogive(buf, mat, nrm, len * 0.78f, len, r, l.noseR, l.noseG, l.noseB, light, ov);
        clippedDelta(buf, mat, nrm, 4, len * 0.11f, len * 0.26f, len * 0.13f,
                r * 0.95f, r * 1.85f, Math.max(0.025f, r * 0.11f),
                l.finR, l.finG, l.finB, light, ov);
    }

    /** LGM-30 / Pershing-class two-stage ICBM. 10 m. */
    private static void drawMinuteman(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                      int light, int ov, Livery l, float len, float r) {
        frustum(buf, mat, nrm, -len * 0.03f, 0f, r * 0.32f, r * 0.70f,
                l.exhaustR, l.exhaustG, l.exhaustB, light, ov);
        frustum(buf, mat, nrm, 0f, len * 0.045f, r * 0.78f, r * 0.52f,
                l.nozzleR, l.nozzleG, l.nozzleB, light, ov);
        disk(buf, mat, nrm, 0.001f, r * 0.44f, 0f, -1f, 0f, 0.06f, 0.06f, 0.07f, light, ov);
        frustum(buf, mat, nrm, len * 0.045f, len * 0.08f, r * 1.08f, r * 1.02f,
                l.bodyR * 0.72f, l.bodyG * 0.72f, l.bodyB * 0.72f, light, ov);

        float s1e = len * 0.52f;
        frustum(buf, mat, nrm, len * 0.08f, s1e, r, r, l.bodyR, l.bodyG, l.bodyB, light, ov);
        ring(buf, mat, nrm, len * 0.20f, len * 0.225f, r, l, light, ov);
        ring(buf, mat, nrm, len * 0.36f, len * 0.385f, r, l, light, ov);
        raceway(buf, mat, nrm, len * 0.10f, s1e - len * 0.02f, r, l, light, ov);
        clippedDelta(buf, mat, nrm, 4, len * 0.085f, len * 0.24f, len * 0.10f,
                r * 0.96f, r * 1.95f, Math.max(0.03f, r * 0.12f),
                l.finR, l.finG, l.finB, light, ov);

        frustum(buf, mat, nrm, s1e, len * 0.56f, r * 1.06f, r * 0.90f,
                l.stripeR, l.stripeG, l.stripeB, light, ov);

        float r2 = r * 0.88f;
        frustum(buf, mat, nrm, len * 0.56f, len * 0.80f, r2, r2,
                l.bodyR * 1.08f, l.bodyG * 1.08f, l.bodyB * 1.08f, light, ov);
        ring(buf, mat, nrm, len * 0.64f, len * 0.665f, r2, l, light, ov);
        frustum(buf, mat, nrm, len * 0.80f, len * 0.84f, r2 * 1.04f, r2 * 0.92f,
                l.accentR, l.accentG, l.accentB, light, ov);
        ogive(buf, mat, nrm, len * 0.84f, len, r2 * 0.92f, l.noseR, l.noseG, l.noseB, light, ov);
        roundel(buf, mat, nrm, len * 0.68f, r2, l, light, ov);
    }

    /** RS-28 / R-36-class heavy ICBM with grid fins. 16 m. */
    private static void drawSarmat(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                   int light, int ov, Livery l, float len, float r) {
        frustum(buf, mat, nrm, -len * 0.028f, 0f, r * 0.40f, r * 0.88f,
                l.exhaustR, l.exhaustG, l.exhaustB, light, ov);
        frustum(buf, mat, nrm, 0f, len * 0.04f, r * 0.92f, r * 0.62f,
                l.nozzleR, l.nozzleG, l.nozzleB, light, ov);
        disk(buf, mat, nrm, 0.001f, r * 0.50f, 0f, -1f, 0f, 0.05f, 0.05f, 0.055f, light, ov);
        frustum(buf, mat, nrm, len * 0.04f, len * 0.075f, r * 1.10f, r * 1.02f,
                l.bodyR * 0.65f, l.bodyG * 0.65f, l.bodyB * 0.65f, light, ov);

        float s1e = len * 0.58f;
        frustum(buf, mat, nrm, len * 0.075f, s1e, r, r, l.bodyR, l.bodyG, l.bodyB, light, ov);
        ring(buf, mat, nrm, len * 0.18f, len * 0.205f, r, l, light, ov);
        ring(buf, mat, nrm, len * 0.34f, len * 0.365f, r, l, light, ov);
        ring(buf, mat, nrm, len * 0.48f, len * 0.505f, r, l, light, ov);
        raceway(buf, mat, nrm, len * 0.09f, s1e - len * 0.02f, r, l, light, ov);
        gridFins(buf, mat, nrm, len * 0.09f, r, l, light, ov);

        frustum(buf, mat, nrm, s1e, len * 0.62f, r * 1.05f, r * 0.86f,
                l.stripeR, l.stripeG, l.stripeB, light, ov);
        float r2 = r * 0.84f;
        frustum(buf, mat, nrm, len * 0.62f, len * 0.82f, r2, r2,
                l.bodyR * 1.12f, l.bodyG * 1.12f, l.bodyB * 1.12f, light, ov);
        frustum(buf, mat, nrm, len * 0.82f, len * 0.86f, r2 * 1.06f, r2 * 0.90f,
                l.accentR, l.accentG, l.accentB, light, ov);
        ogive(buf, mat, nrm, len * 0.86f, len, r2 * 0.90f, l.noseR, l.noseG, l.noseB, light, ov);
        roundel(buf, mat, nrm, len * 0.70f, r2, l, light, ov);
    }

    private static void ogive(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                              float y0, float y1, float radius,
                              float r, float g, float b, int light, int overlay) {
        int steps = 8;
        float span = y1 - y0;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps;
            float t1 = (i + 1) / (float) steps;
            float rr0 = Math.max(0.012f, radius * (float) Math.sqrt(Math.max(0.0, 1.0 - t0 * t0)));
            float rr1 = Math.max(0.008f, radius * (float) Math.sqrt(Math.max(0.0, 1.0 - t1 * t1)));
            float shade = 0.92f + 0.08f * t0;
            frustum(buf, mat, nrm, y0 + t0 * span, y0 + t1 * span, rr0, rr1,
                    r * shade, g * shade, b * shade, light, overlay);
        }
        disk(buf, mat, nrm, y1, 0.01f, 0f, 1f, 0f, 0.92f, 0.93f, 0.95f, light, overlay);
    }

    private static void ring(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                             float y0, float y1, float radius, Livery l, int light, int overlay) {
        frustum(buf, mat, nrm, y0, y1, radius * 1.035f, radius * 1.035f,
                l.stripeR, l.stripeG, l.stripeB, light, overlay);
    }

    private static void panel(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                              float y0, float y1, float radius,
                              float r, float g, float b, int light, int overlay) {
        frustum(buf, mat, nrm, y0, y1, radius * 1.02f, radius * 1.02f, r, g, b, light, overlay);
    }

    private static void raceway(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                float y0, float y1, float radius, Livery l, int light, int overlay) {
        float w = Math.max(0.035f, radius * 0.16f);
        float h = Math.max(0.028f, radius * 0.12f);
        box(buf, mat, nrm, radius * 0.92f, y0, -w * 0.5f, radius + h, y1, w * 0.5f,
                l.bodyR * 0.55f, l.bodyG * 0.55f, l.bodyB * 0.55f, light, overlay);
    }

    private static void roundel(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                float y, float radius, Livery l, int light, int overlay) {
        float h = Math.max(0.18f, radius * 0.55f);
        float y0 = y - h * 0.5f;
        float y1 = y + h * 0.5f;
        float w = h * 0.42f;
        box(buf, mat, nrm, radius * 0.90f, y0, -w, radius * 1.06f, y1, w,
                l.roundelR, l.roundelG, l.roundelB, light, overlay);
        box(buf, mat, nrm, radius * 0.94f, y0 + h * 0.22f, -w * 0.45f, radius * 1.08f, y1 - h * 0.22f, w * 0.45f,
                l.roundelInnerR, l.roundelInnerG, l.roundelInnerB, light, overlay);
    }

    private static void clippedDelta(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                     int count, float yLead, float yTrail, float yTip,
                                     float inner, float outer, float halfT,
                                     float r, float g, float b, int light, int overlay) {
        for (int i = 0; i < count; i++) {
            float ang = (float) (i * Math.PI * 2.0 / count);
            float rx = (float) Math.sin(ang);
            float rz = (float) Math.cos(ang);
            float tx = -rz * halfT;
            float tz = rx * halfT;
            float x0 = rx * inner;
            float z0 = rz * inner;
            float x1 = rx * outer;
            float z1 = rz * outer;
            quad(buf, mat, nrm,
                    x0 + tx, yLead, z0 + tz,
                    x1 + tx, yTip, z1 + tz,
                    x1 + tx, yTrail, z1 + tz,
                    x0 + tx, yTrail, z0 + tz,
                    r, g, b, light, overlay);
            quad(buf, mat, nrm,
                    x0 - tx, yTrail, z0 - tz,
                    x1 - tx, yTrail, z1 - tz,
                    x1 - tx, yTip, z1 - tz,
                    x0 - tx, yLead, z0 - tz,
                    r * 0.82f, g * 0.82f, b * 0.82f, light, overlay);
            quad(buf, mat, nrm,
                    x1 - tx, yTip, z1 - tz,
                    x1 + tx, yTip, z1 + tz,
                    x1 + tx, yTrail, z1 + tz,
                    x1 - tx, yTrail, z1 - tz,
                    r * 0.7f, g * 0.7f, b * 0.7f, light, overlay);
            quad(buf, mat, nrm,
                    x0 + tx, yLead, z0 + tz,
                    x0 - tx, yLead, z0 - tz,
                    x1 - tx, yTip, z1 - tz,
                    x1 + tx, yTip, z1 + tz,
                    r * 1.05f, g * 1.05f, b * 1.05f, light, overlay);
        }
    }

    private static void gridFins(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                 float y, float radius, Livery l, int light, int overlay) {
        float span = radius * 0.95f;
        float thick = Math.max(0.03f, radius * 0.06f);
        float y0 = y;
        float y1 = y + radius * 0.85f;
        float inner = radius * 0.98f;
        float outer = radius + span;
        float cr = l.accentR;
        float cg = l.accentG;
        float cb = l.accentB;
        for (int i = 0; i < 4; i++) {
            float ang = (float) (i * Math.PI * 0.5);
            float rx = (float) Math.sin(ang);
            float rz = (float) Math.cos(ang);
            float px = rx * inner;
            float pz = rz * inner;
            float qx = rx * outer;
            float qz = rz * outer;
            float tx = -rz;
            float tz = rx;
            frameSlat(buf, mat, nrm, px, pz, qx, qz, tx, tz, y0, y1, 0f, 1f, thick, cr, cg, cb, light, overlay);
            frameSlat(buf, mat, nrm, px, pz, qx, qz, tx, tz, y0, y1, 1f, 1f, thick, cr, cg, cb, light, overlay);
            frameSlat(buf, mat, nrm, px, pz, qx, qz, tx, tz, y0, y0 + thick * 2f, 0f, 1f, thick, cr, cg, cb, light, overlay);
            frameSlat(buf, mat, nrm, px, pz, qx, qz, tx, tz, y1 - thick * 2f, y1, 0f, 1f, thick, cr, cg, cb, light, overlay);
            for (int s = 1; s <= 2; s++) {
                float t = s / 3.0f;
                frameSlat(buf, mat, nrm, px, pz, qx, qz, tx, tz, y0, y1, t, t, thick * 0.55f,
                        cr * 0.85f, cg * 0.85f, cb * 0.85f, light, overlay);
                float yy = y0 + (y1 - y0) * t;
                frameSlat(buf, mat, nrm, px, pz, qx, qz, tx, tz, yy - thick * 0.4f, yy + thick * 0.4f, 0f, 1f,
                        thick * 0.55f, cr * 0.85f, cg * 0.85f, cb * 0.85f, light, overlay);
            }
        }
    }

    private static void frameSlat(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                  float px, float pz, float qx, float qz,
                                  float tx, float tz, float y0, float y1,
                                  float t0, float t1, float half,
                                  float r, float g, float b, int light, int overlay) {
        float x0 = px + (qx - px) * t0;
        float z0 = pz + (qz - pz) * t0;
        float x1 = px + (qx - px) * t1;
        float z1 = pz + (qz - pz) * t1;
        box(buf, mat, nrm,
                Math.min(x0, x1) - tx * half, y0, Math.min(z0, z1) - tz * half,
                Math.max(x0, x1) + tx * half, y1, Math.max(z0, z1) + tz * half,
                r, g, b, light, overlay);
    }

    private static void frustum(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                float y0, float y1, float r0, float r1,
                                float r, float g, float b, int light, int overlay) {
        float dy = y1 - y0;
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            float x00 = COS[i] * r0;
            float z00 = SIN[i] * r0;
            float x10 = COS[j] * r0;
            float z10 = SIN[j] * r0;
            float x01 = COS[i] * r1;
            float z01 = SIN[i] * r1;
            float x11 = COS[j] * r1;
            float z11 = SIN[j] * r1;
            float nx = (COS[i] + COS[j]) * 0.5f;
            float nz = (SIN[i] + SIN[j]) * 0.5f;
            float ny = dy > 1.0e-4f ? (r0 - r1) / dy : 0f;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0e-5f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            float shade = 0.72f + 0.28f * Math.abs(nx);
            // Wound bottom-i, top-i, top-j, bottom-j so the face points outwards.
            //
            // It used to run bottom-i, bottom-j, top-j, top-i, whose first two edges are +Z then +Y
            // on the +X side of the hull; Z x Y is -X, so the winding faced inwards while the normal
            // handed to the same call faced out. RenderType.entitySolid culls back faces, so the
            // shell was discarded and what remained was the inside of the far wall -- the missile
            // rendered inside-out. Reversed, the edges are +Y then +Z and Y x Z is +X, which agrees
            // with the normal.
            //
            // This is the whole curved hull: ogive, ring and panel all come through here.
            litQuad(buf, mat, nrm,
                    x00, y0, z00, x01, y1, z01, x11, y1, z11, x10, y0, z10,
                    nx, ny, nz, r * shade, g * shade, b * shade, light, overlay);
        }
    }

    private static void disk(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                             float y, float radius, float nx, float ny, float nz,
                             float r, float g, float b, int light, int overlay) {
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            // The two branches were the wrong way round, the same inversion the hull had. Centre
            // then i then j winds to -Y: the edges are +X and then round the circle, and X x Z is
            // -Y. So an upward cap has to be wound centre, j, i to face +Y.
            if (ny >= 0f) {
                litTri(buf, mat, nrm,
                        0f, y, 0f,
                        COS[j] * radius, y, SIN[j] * radius,
                        COS[i] * radius, y, SIN[i] * radius,
                        nx, ny, nz, r, g, b, light, overlay);
            } else {
                litTri(buf, mat, nrm,
                        0f, y, 0f,
                        COS[i] * radius, y, SIN[i] * radius,
                        COS[j] * radius, y, SIN[j] * radius,
                        nx, ny, nz, r, g, b, light, overlay);
            }
        }
    }

    private static void box(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float b, int light, int overlay) {
        litQuad(buf, mat, nrm, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f, r, g, b, light, overlay);
        litQuad(buf, mat, nrm, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f, r * 0.82f, g * 0.82f, b * 0.82f, light, overlay);
        litQuad(buf, mat, nrm, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f, r * 1.08f, g * 1.08f, b * 1.08f, light, overlay);
        litQuad(buf, mat, nrm, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0f, -1f, 0f, r * 0.62f, g * 0.62f, b * 0.62f, light, overlay);
        litQuad(buf, mat, nrm, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f, r * 0.9f, g * 0.9f, b * 0.9f, light, overlay);
        litQuad(buf, mat, nrm, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f, r * 0.9f, g * 0.9f, b * 0.9f, light, overlay);
    }

    private static void quad(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, int light, int overlay) {
        float ax = x1 - x0;
        float ay = y1 - y0;
        float az = z1 - z0;
        float bx = x3 - x0;
        float by = y3 - y0;
        float bz = z3 - z0;
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        litQuad(buf, mat, nrm, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
                nx, ny, nz, r, g, b, light, overlay);
    }

    private static void litQuad(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float nx, float ny, float nz,
                                float r, float g, float b, int light, int overlay) {
        lit(buf, mat, nrm, x0, y0, z0, nx, ny, nz, r, g, b, 0f, 0f, light, overlay);
        lit(buf, mat, nrm, x1, y1, z1, nx, ny, nz, r, g, b, 1f, 0f, light, overlay);
        lit(buf, mat, nrm, x2, y2, z2, nx, ny, nz, r, g, b, 1f, 1f, light, overlay);
        lit(buf, mat, nrm, x3, y3, z3, nx, ny, nz, r, g, b, 0f, 1f, light, overlay);
    }

    private static void litTri(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                               float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float nx, float ny, float nz,
                               float r, float g, float b, int light, int overlay) {
        lit(buf, mat, nrm, x0, y0, z0, nx, ny, nz, r, g, b, 0.5f, 0.5f, light, overlay);
        lit(buf, mat, nrm, x1, y1, z1, nx, ny, nz, r, g, b, 0f, 1f, light, overlay);
        lit(buf, mat, nrm, x2, y2, z2, nx, ny, nz, r, g, b, 1f, 1f, light, overlay);
        lit(buf, mat, nrm, x0, y0, z0, nx, ny, nz, r, g, b, 0.5f, 0.5f, light, overlay);
    }

    private static void lit(VertexConsumer buf, Matrix4f mat, Matrix3f nrm,
                            float x, float y, float z,
                            float nx, float ny, float nz,
                            float r, float g, float b, float u, float v, int light, int overlay) {
        N.set(nx, ny, nz);
        nrm.transform(N);
        if (N.lengthSquared() > 1.0e-8f) N.normalize();
        buf.addVertex(mat, x, y, z)
                .setColor(Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), 1f)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(N.x, N.y, N.z);
    }

    private record Livery(float bodyR, float bodyG, float bodyB,
                          float stripeR, float stripeG, float stripeB,
                          float accentR, float accentG, float accentB,
                          float noseR, float noseG, float noseB,
                          float finR, float finG, float finB,
                          float nozzleR, float nozzleG, float nozzleB,
                          float exhaustR, float exhaustG, float exhaustB,
                          float roundelR, float roundelG, float roundelB,
                          float roundelInnerR, float roundelInnerG, float roundelInnerB) {
        static Livery of(MissileSize size) {
            return switch (size) {
                case SMALL -> new Livery(
                        0.93f, 0.94f, 0.96f,
                        1.00f, 0.42f, 0.08f,
                        0.15f, 0.45f, 0.95f,
                        0.78f, 0.80f, 0.84f,
                        0.18f, 0.20f, 0.24f,
                        0.16f, 0.16f, 0.18f,
                        1.00f, 0.48f, 0.08f,
                        1.00f, 1.00f, 1.00f,
                        0.10f, 0.40f, 0.95f);
                case MEDIUM -> new Livery(
                        0.42f, 0.50f, 0.24f,
                        1.00f, 0.86f, 0.12f,
                        0.20f, 0.55f, 0.22f,
                        0.58f, 0.60f, 0.42f,
                        0.22f, 0.26f, 0.14f,
                        0.14f, 0.14f, 0.12f,
                        1.00f, 0.55f, 0.10f,
                        1.00f, 0.92f, 0.20f,
                        0.15f, 0.35f, 0.10f);
                case LARGE -> new Livery(
                        0.30f, 0.32f, 0.36f,
                        0.95f, 0.12f, 0.14f,
                        0.75f, 0.18f, 0.16f,
                        0.72f, 0.74f, 0.76f,
                        0.16f, 0.16f, 0.18f,
                        0.12f, 0.12f, 0.14f,
                        1.00f, 0.40f, 0.08f,
                        0.95f, 0.95f, 0.97f,
                        0.90f, 0.15f, 0.18f);
                case MEGA -> new Livery(
                        0.07f, 0.08f, 0.10f,
                        0.96f, 0.74f, 0.18f,
                        0.85f, 0.62f, 0.12f,
                        0.82f, 0.78f, 0.62f,
                        0.10f, 0.10f, 0.12f,
                        0.08f, 0.08f, 0.09f,
                        1.00f, 0.50f, 0.10f,
                        0.96f, 0.78f, 0.22f,
                        0.95f, 0.20f, 0.12f);
            };
        }
    }
}
