package org.sylvarion.terrainmesh.selection;

/** Block coordinates. Y denotes the top occupied block, not its upper face. */
public record BoundaryPoint(int x, int y, int z) {}
