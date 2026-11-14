package org.sylvarion.terrainmesh.selection;

import java.util.*;

/** Owned exclusively by the server thread. Workers receive immutable snapshots. */
public final class PointSelectionManager {
    public static final class Selection {
        public UUID world;
        public final List<BoundaryPoint> boundary = new ArrayList<>();
        public final List<InteriorPoint> hills = new ArrayList<>();
        public int nextId = 1, smoothing, resolution, noise, bottom;
        public long seed, revision;
        public String surface, subsurface, foundation;
        public Selection(int smoothing, int resolution, int noise, int bottom, String surface, String subsurface, String foundation) {
            this.smoothing=smoothing; this.resolution=resolution; this.noise=noise; this.bottom=bottom;
            this.surface=surface; this.subsurface=subsurface; this.foundation=foundation;
        }
        public Snapshot snapshot(int minY, int maxY) {
            return new Snapshot(world, List.copyOf(boundary), List.copyOf(hills), smoothing, resolution, noise, bottom,
                    seed, revision, surface, subsurface, foundation, minY, maxY);
        }
    }
    public record Snapshot(UUID world, List<BoundaryPoint> boundary, List<InteriorPoint> hills,
                           int smoothing, int resolution, int noise, int bottom, long seed, long revision,
                           String surface, String subsurface, String foundation, int minY, int maxY) {}
    private final Map<UUID, Selection> selections = new HashMap<>();
    public Selection get(UUID player, java.util.function.Supplier<Selection> defaults) { return selections.computeIfAbsent(player, k -> defaults.get()); }
    public void remove(UUID player) { selections.remove(player); }
}
