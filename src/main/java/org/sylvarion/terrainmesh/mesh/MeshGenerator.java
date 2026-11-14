package org.sylvarion.terrainmesh.mesh;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator;
import org.locationtech.jts.index.strtree.STRtree;
import org.sylvarion.terrainmesh.selection.BoundaryPoint;
import java.util.*;

/** JTS constrained polygon triangulation: concave boundary segments are retained. */
public final class MeshGenerator {
    public record Vertex(double x, double y, double z) {}
    public record Triangle(Vertex a, Vertex b, Vertex c) {
        public double height(double x,double z) {
            double d=(b.z-c.z)*(a.x-c.x)+(c.x-b.x)*(a.z-c.z);
            double u=((b.z-c.z)*(x-c.x)+(c.x-b.x)*(z-c.z))/d;
            double v=((c.z-a.z)*(x-c.x)+(a.x-c.x)*(z-c.z))/d;
            return u>=-1e-8 && v>=-1e-8 && u+v<=1+1e-8 ? u*a.y+v*b.y+(1-u-v)*c.y : Double.NaN;
        }
    }
    private final List<Triangle> triangles=new ArrayList<>();
    private final STRtree index=new STRtree();
    public MeshGenerator(Polygon polygon,List<BoundaryPoint> boundary) {
        Geometry mesh=PolygonTriangulator.triangulate(polygon);
        for(int i=0;i<mesh.getNumGeometries();i++) {
            Coordinate[] c=mesh.getGeometryN(i).getCoordinates();
            Triangle t=new Triangle(vertex(c[0],boundary),vertex(c[1],boundary),vertex(c[2],boundary));
            triangles.add(t); index.insert(mesh.getGeometryN(i).getEnvelopeInternal(),t);
        }
        index.build();
    }
    private Vertex vertex(Coordinate c,List<BoundaryPoint> boundary) {
        for(var p:boundary) if(p.x()==c.x && p.z()==c.y) return new Vertex(c.x,p.y(),c.y);
        throw new IllegalStateException("Triangulator introduced an unexpected vertex.");
    }
    public List<Triangle> triangles() { return List.copyOf(triangles); }
    public double height(double x,double z) {
        for(Object candidate:index.query(new Envelope(x,x,z,z))) {
            double h=((Triangle)candidate).height(x,z);
            if(Double.isFinite(h)) return h;
        }
        throw new IllegalArgumentException("Point is outside triangulation.");
    }
}
