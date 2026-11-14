package org.sylvarion.terrainmesh.mesh;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.sylvarion.terrainmesh.selection.*;
import java.util.*;

public final class PolygonValidator {
    public static final GeometryFactory GEOMETRY = new GeometryFactory();
    private PolygonValidator() {}
    public static Polygon validate(List<BoundaryPoint> points, List<InteriorPoint> hills, long maxArea) {
        if (points.size()<3) throw new IllegalArgumentException("Select at least three boundary points.");
        Set<String> unique = new HashSet<>();
        Coordinate[] ring = new Coordinate[points.size()+1];
        for(int i=0;i<points.size();i++) {
            var p=points.get(i);
            if(!unique.add(p.x()+":"+p.z())) throw new IllegalArgumentException("Duplicate boundary X/Z position.");
            ring[i]=new Coordinate(p.x(),p.z());
        }
        ring[points.size()]=ring[0].copy();
        Polygon polygon=GEOMETRY.createPolygon(ring);
        var error=new IsValidOp(polygon).getValidationError();
        if(error!=null) throw new IllegalArgumentException("Invalid polygon: "+error.getMessage());
        if(polygon.getArea()<1 || polygon.getArea()>maxArea) throw new IllegalArgumentException("Polygon area must be between 1 and "+maxArea+" blocks.");
        unique.clear();
        for(var hill:hills) {
            var p=hill.position();
            if(!polygon.contains(GEOMETRY.createPoint(new Coordinate(p.x(),p.z())))) throw new IllegalArgumentException("Hill "+hill.id()+" must be strictly inside the polygon.");
            if(!unique.add(p.x()+":"+p.z())) throw new IllegalArgumentException("Duplicate hill X/Z position.");
        }
        return polygon;
    }
    public static boolean covers(Polygon polygon, double x, double z) {
        return polygon.covers(GEOMETRY.createPoint(new Coordinate(x,z)));
    }
}
