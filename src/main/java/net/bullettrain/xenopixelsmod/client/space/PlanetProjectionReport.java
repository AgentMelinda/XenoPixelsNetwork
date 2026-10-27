package net.bullettrain.xenopixelsmod.client.space;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** On-demand planet texture identity report for {@code /xenoset space planets projections check}. */
public final class PlanetProjectionReport {
    private PlanetProjectionReport() {}

    /**
     * One section per renderer: far chart projections, near replicated Sable bodies, seeded small planets.
     * {@code wrong} counts confirmed identity/resource mismatches; {@code unverified} counts cases where the
     * renderer or registered texture type does not expose enough information to prove its pixels' source.
     */
    public record Report(List<String> far, List<String> near, List<String> small, List<String> benchmark, int wrong,
                         int unverified, boolean perBodyMaps, String aimed) {}

    /**
     * Render thread only: reads what each renderer last bound and what it would bind now. Runs once per command,
     * never per frame. Bounded by SpaceSnapshot.MAX_BODIES (32) far bodies, PhysicalPlanetBindings.MAX_ENTRIES (64)
     * near bindings (a map, so ids are unique) and SmallPlanetField.nearby's 9 systems x MAX_PLANETS_PER_SYSTEM
     * small candidates (one per system cell and slot, so ids are unique).
     */
    public static Report current(boolean perBodyMaps) {
        var resources = Minecraft.getInstance().getResourceManager();
        int[] wrong = {0};
        int[] unverified = {0};
        var checks = PlanetProjectionTexture.check(OrbitPlanetProxies.projectedBodies(), perBodyMaps,
                OrbitPlanetProxies.renderedSelections(), OrbitPlanetProxies.drawnPasses(), OrbitPlanetProxies.reportPass(),
                DragonBallPlanetClient::projectionTextureForSlot,
                DragonBallPlanetClient::textureForSlot, location -> resources.getResource(location).isPresent());
        // The bound selection is the last one made, possibly frames ago; say separately whether it projects now.
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var textures = Minecraft.getInstance().getTextureManager();
        var level = Minecraft.getInstance().level;
        var container = level == null ? null : dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        Object placeholder = net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getTexture();
        List<String> far = new ArrayList<>(checks.size());
        for (var check : checks) {
            // The key check says which texture was bound; the texture-object check says what that key really holds.
            // Neither proves the sampled pixels.
            var upload = farUpload(textures, placeholder, check);
            boolean liveBound = PhysicalPlanetRenderer.hasLiveBodyBinding(check.bodyId(), container);
            boolean projectedNow = PlanetDuplicateCheck.farProjectedNow(
                    OrbitPlanetProxies.wouldDraw(check.bodyId(), camera, 1.0F),
                    PlanetHakaiClient.progress(check.bodyId(), 1.0F) >= 0, liveBound);
            // A far proxy drawn while an UNBOUND live sub-level occupies its sphere is a second globe the near
            // renderer cannot suppress (it only knows bound bodies). Only reachable where the two are both visible.
            String overlap = PlanetDuplicateCheck.shouldCheckUnboundOverlap(check.drawn(), projectedNow, liveBound)
                    ? unboundSubLevelOverlap(container, check.bodyId()) : null;
            if (!check.correct() || PlanetTextureChecks.uploadKnownWrong(upload) || overlap != null) wrong[0]++;
            if (upload == PlanetTextureChecks.Upload.UNVERIFIED) unverified[0]++;
            far.add(PlanetProjectionTexture.describe(check) + " | texture-object=" + upload
                    + (projectedNow ? " | projected now" : " | not projected now")
                    + (overlap == null ? "" : " | POSSIBLE DUPLICATE: " + overlap));
        }

        // Generated SDB and small-planet textures are dynamic, so only packaged maps are checked for existence.
        List<String> near = new ArrayList<>();
        for (var result : PlanetTextureChecks.check(PhysicalPlanetRenderer.nearObserved(),
                PhysicalPlanetRenderer::expectedNear,
                location -> DragonBallPlanetClient.ownsTexture(location) || resources.getResource(location).isPresent())) {
            var upload = !result.drawn() ? PlanetTextureChecks.Upload.UNVERIFIED
                    : result.texture() == null ? PlanetTextureChecks.Upload.NOT_REGISTERED
                    : PlanetTextureChecks.upload(textures.getTexture(result.texture(), null), placeholder, result.texture());
            if (result.verdict() != PlanetTextureChecks.Verdict.OWN || PlanetTextureChecks.uploadKnownWrong(upload)) wrong[0]++;
            if (upload == PlanetTextureChecks.Upload.UNVERIFIED) unverified[0]++;
            near.add(PlanetTextureChecks.describe("near", result) + " | texture-object=" + upload
                    + (PhysicalPlanetRenderer.nearLive(result.id()) ? " | live Sable body now" : " | no live body now"));
        }

        var generated = SmallPlanetFarRenderer.generated();
        List<String> small = new ArrayList<>();
        for (var result : PlanetTextureChecks.check(SmallPlanetFarRenderer.observed(), generated::get, location -> true)) {
            if (result.verdict() == PlanetTextureChecks.Verdict.UNKNOWN) unverified[0]++;
            else if (result.verdict() != PlanetTextureChecks.Verdict.OWN) wrong[0]++;
            small.add(PlanetTextureChecks.describe("small", result)
                    + " | " + SmallPlanetFarRenderer.drawState(result.id()));
        }
        // Benchmark SDB spheres are drawn with no production check; flag every slot a production path also draws.
        var productionSlots = new java.util.HashSet<Integer>();
        for (var projected : OrbitPlanetProxies.projectedBodies()) {
            int slot = net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity.slotFromPlanetKey(projected.id());
            if (slot > 0) productionSlots.add(slot);
        }
        for (var observed : PhysicalPlanetRenderer.nearObserved()) {
            int slot = net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity.slotFromPlanetKey(observed.id());
            if (slot > 0) productionSlots.add(slot);
        }
        var stars = DragonBallPlanetClient.benchmarkStars();
        var overlaps = PlanetDuplicateCheck.benchmarkOverlaps(stars, productionSlots);
        List<String> benchmark = new ArrayList<>(stars.size());
        for (int star : stars) benchmark.add(PlanetDuplicateCheck.describe(star, overlaps.contains(star)));
        wrong[0] += overlaps.size();
        return new Report(far, near, small, benchmark, wrong[0], unverified[0], perBodyMaps, aimed(checks));
    }

    /**
     * The first live Sable sub-level whose bounds intersect this far body's projection sphere, or null. Called only
     * for a body drawn now with no live binding; bounded by the client's replicated sub-levels, once per command.
     */
    private static String unboundSubLevelOverlap(dev.ryanhcode.sable.api.sublevel.SubLevelContainer container,
                                                 String bodyId) {
        if (container == null) return null;
        var centre = new org.joml.Vector3d();
        double[] row = OrbitPlanetProxies.find(bodyId);
        if (row == null || !OrbitPlanetProxies.drawCenter(bodyId, 1.0F, centre)) return null;
        for (var sub : container.getAllSubLevels()) {
            if (sub == null || sub.isRemoved()) continue;
            var box = sub.boundingBox();
            if (box == null) continue;
            if (PlanetDuplicateCheck.sphereOverlapsBox(centre.x, centre.y, centre.z, row[3],
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
                return "unbound live sub-level " + sub.getUniqueId() + " overlaps this projection sphere";
        }
        return null;
    }

    /**
     * What the texture manager holds for a far body's actual bound key: the downsampled copy (far key) on the
     * per-body branch, the full HD map while burning. Strip and SDB textures are reported as UNVERIFIED.
     * Uses the no-side-effect getTexture(key, default) overload, so the report never registers anything.
     */
    private static PlanetTextureChecks.Upload farUpload(net.minecraft.client.renderer.texture.TextureManager textures,
                                                        Object placeholder, PlanetProjectionTexture.Check check) {
        if (check.texture() == null) return PlanetTextureChecks.Upload.NOT_REGISTERED;
        if (!check.drawn()) return PlanetTextureChecks.Upload.UNVERIFIED;
        return switch (check.path()) {
            case BODY_MAP -> PlanetTextureChecks.upload(
                    textures.getTexture(PlanetFarProjectionCache.farKey(check.texture()), null), placeholder, check.texture());
            case BURNING_MAP -> PlanetTextureChecks.upload(textures.getTexture(check.texture(), null), placeholder, check.texture());
            case STYLE_STRIP, SDB_SLOT -> PlanetTextureChecks.Upload.UNVERIFIED;
        };
    }

    /**
     * The checked body whose draw centre is nearest the crosshair, so a globe on screen can be matched to its line.
     * Uses the same centres the renderers draw at (far: live pose when replicated; near: live pose; small: seed).
     */
    private static String aimed(List<PlanetProjectionTexture.Check> farChecks) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var eye = camera.getPosition();
        var look = camera.getLookVector();
        var centre = new org.joml.Vector3d();
        String best = null;
        double bestAngle = Double.MAX_VALUE, bestDistance = 0;
        List<String[]> candidates = new ArrayList<>();
        for (var check : farChecks) candidates.add(new String[]{"far", check.bodyId()});
        for (var body : PhysicalPlanetRenderer.nearObserved()) candidates.add(new String[]{"near", body.id()});
        for (var body : SmallPlanetFarRenderer.observed()) candidates.add(new String[]{"small", body.id()});
        for (String[] candidate : candidates) {
            double x, y, z;
            if ("small".equals(candidate[0])) {
                double[] position = SmallPlanetFarRenderer.position(candidate[1]);
                if (position == null) continue;
                x = position[0]; y = position[1]; z = position[2];
            } else {
                boolean found = "far".equals(candidate[0]) ? OrbitPlanetProxies.drawCenter(candidate[1], 1.0F, centre)
                        : PhysicalPlanetRenderer.nearCenter(candidate[1], 1.0F, centre);
                if (!found) continue;
                x = centre.x; y = centre.y; z = centre.z;
            }
            double angle = PlanetTextureChecks.offAxisDegrees(eye.x, eye.y, eye.z, look.x(), look.y(), look.z(), x, y, z);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = candidate[0] + " " + candidate[1];
                bestDistance = Math.sqrt(eye.distanceToSqr(x, y, z));
            }
        }
        if (best == null) return "Nearest to crosshair: no checked body has a known centre";
        return String.format(java.util.Locale.ROOT, "Nearest to crosshair: %s, %.1f deg off-centre, %.0f blocks away",
                best, bestAngle, bestDistance);
    }
}
