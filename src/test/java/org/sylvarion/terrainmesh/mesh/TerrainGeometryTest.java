package org.sylvarion.terrainmesh.mesh;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.sylvarion.terrainmesh.selection.*;
import org.sylvarion.terrainmesh.selection.PointSelectionManager.Snapshot;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class TerrainGeometryTest {
    private static BoundaryPoint p(int x,int y,int z) {return new BoundaryPoint(x,y,z);}
    private final List<BoundaryPoint> square=List.of(p(0,80,0),p(100,90,0),p(100,90,100),p(0,80,100));
    private Snapshot snapshot(List<BoundaryPoint> boundary,List<InteriorPoint> hills,int smooth,int noise) {
        return new Snapshot(UUID.randomUUID(),boundary,hills,smooth,1,noise,0,1234,1,"GRASS_BLOCK","DIRT","STONE",-64,320);
    }
    private TerrainHeightmap build(Snapshot s) {return TerrainHeightmap.build(s,1000000,256*1048576L,50000000,()->false,i->{});}
    private TerrainInterpolator interpolator(List<BoundaryPoint> boundary,List<InteriorPoint> hills) {
        return new TerrainInterpolator(new MeshGenerator(PolygonValidator.validate(boundary,hills,1000000),boundary),boundary,hills);
    }
    @Test void concaveTriangulationPreservesAreaAndNotch() {
        var boundary=List.of(p(0,80,0),p(10,80,0),p(10,85,4),p(4,85,4),p(4,90,10),p(0,80,10));
        Polygon polygon=PolygonValidator.validate(boundary,List.of(),1000);
        var mesh=new MeshGenerator(polygon,boundary);double area=0;
        for(var t:mesh.triangles()) {
            Polygon triangle=PolygonValidator.GEOMETRY.createPolygon(new Coordinate[]{new Coordinate(t.a().x(),t.a().z()),new Coordinate(t.b().x(),t.b().z()),new Coordinate(t.c().x(),t.c().z()),new Coordinate(t.a().x(),t.a().z())});
            assertTrue(polygon.covers(triangle));area+=triangle.getArea();
        }
        assertEquals(64,area,1e-8);
        assertTrue(PolygonValidator.covers(polygon,2,8));assertFalse(PolygonValidator.covers(polygon,8,8));
        assertThrows(IllegalArgumentException.class,()->mesh.height(8,8));
    }
    @Test void boundaryHeightInterpolatesEntireSegment() {
        var i=interpolator(square,List.of(new InteriorPoint(1,p(50,120,50))));
        for(int x=0;x<=100;x++) assertEquals(80+x*.1,i.height(x,0),1e-8);
        assertEquals(90,i.height(100,33),1e-8);
    }
    @Test void multipleInteriorConstraintsAreExact() {
        var hills=List.of(new InteriorPoint(1,p(30,120,30)),new InteriorPoint(2,p(70,105,70)));
        var i=interpolator(square,hills);
        for(var hill:hills)assertEquals(hill.position().y(),i.height(hill.position().x(),hill.position().z()),1e-8);
        var map=build(snapshot(square,hills,85,5));
        for(var hill:hills)assertEquals(hill.position().y(),map.height(hill.position().x(),hill.position().z()));
        for(int x=0;x<=100;x++)assertEquals(Math.round(80+x*.1),map.height(x,0));
    }
    @Test void pointInPolygonIncludesBoundaryButHillsMustBeStrictlyInside() {
        var polygon=PolygonValidator.validate(square,List.of(),20000);
        assertTrue(PolygonValidator.covers(polygon,0,50));assertFalse(PolygonValidator.covers(polygon,-.1,50));
        assertThrows(IllegalArgumentException.class,()->PolygonValidator.validate(square,List.of(new InteriorPoint(1,p(0,90,50))),20000));
        assertThrows(IllegalArgumentException.class,()->PolygonValidator.validate(square,List.of(new InteriorPoint(1,p(101,90,50))),20000));
    }
    @Test void rejectsSelfIntersectionsTouchingEdgesAndDuplicates() {
        assertThrows(IllegalArgumentException.class,()->PolygonValidator.validate(List.of(p(0,80,0),p(10,80,10),p(0,80,10),p(10,80,0)),List.of(),1000));
        assertThrows(IllegalArgumentException.class,()->PolygonValidator.validate(List.of(p(0,80,0),p(10,80,0),p(5,80,0),p(5,80,10)),List.of(),1000));
        assertThrows(IllegalArgumentException.class,()->PolygonValidator.validate(List.of(p(0,80,0),p(10,80,0),p(0,81,0)),List.of(),1000));
    }
    @Test void noiseAttenuatesAtBoundaryAndHillsAndIsReproducible() {
        var i=interpolator(square,List.of(new InteriorPoint(1,p(50,120,50))));
        assertEquals(0,i.attenuation(0,40));assertEquals(0,i.attenuation(50,50));
        assertTrue(i.attenuation(1,40)<i.attenuation(10,40));
        assertEquals(TerrainInterpolator.noise(1.2,4.5,123),TerrainInterpolator.noise(1.2,4.5,123));
        assertNotEquals(TerrainInterpolator.noise(1.2,4.5,123),TerrainInterpolator.noise(1.2,4.5,124));
        var a=build(snapshot(square,List.of(),0,40));var b=build(snapshot(square,List.of(),0,40));
        for(int x=0;x<=100;x++)assertEquals(a.height(x,37),b.height(x,37));
    }
    @Test void worldHeightClampingAndInvalidConstraints() {
        assertEquals(-64,TerrainHeightmap.clampHeight(-100,-64,320));assertEquals(319,TerrainHeightmap.clampHeight(500,-64,320));
        var bad=List.of(p(0,400,0),p(10,80,0),p(0,80,10));assertThrows(IllegalArgumentException.class,()->build(snapshot(bad,List.of(),0,0)));
    }
    @Test void millionCellMemoryEstimateIsBoundedAndUsesLongArithmetic() {
        long bytes=TerrainHeightmap.estimateMemory(1000000,512);
        assertTrue(bytes>18000000 && bytes<32*1048576L);
        assertEquals(18000000000L+1048576,TerrainHeightmap.estimateMemory(1000000000,0));
    }
    @Test void estimatesSolidColumnsAndRespectsAreaBlockMemoryBudgets() {
        var boundary=List.of(p(0,10,0),p(10,10,0),p(10,10,10),p(0,10,10));
        var s=snapshot(boundary,List.of(),0,0);var map=build(s);
        assertEquals(121,map.columns);assertEquals(1331,map.estimatedBlocks);
        assertThrows(IllegalArgumentException.class,()->TerrainHeightmap.build(s,10,10000000,100000,()->false,i->{}));
        assertThrows(IllegalArgumentException.class,()->TerrainHeightmap.build(s,1000,1,100000,()->false,i->{}));
        assertThrows(IllegalArgumentException.class,()->TerrainHeightmap.build(s,1000,10000000,100,()->false,i->{}));
    }
    @Test void cancelledPlanningExitsWithoutResult() {
        assertThrows(CancellationException.class,()->TerrainHeightmap.build(snapshot(square,List.of(),70,0),100000,10000000,50000000,()->true,i->{}));
    }
    @Test void rasterNeverFillsConcaveNotchAndRetainsDiagonalBoundary() {
        var boundary=List.of(p(0,80,0),p(20,90,0),p(20,90,8),p(8,80,8),p(8,80,20),p(0,80,20));
        var map=build(snapshot(boundary,List.of(),70,5));
        assertFalse(map.contains(9,9));assertTrue(map.contains(8,8));
        var diagonal=List.of(p(0,80,0),p(20,100,20),p(0,80,20));var d=build(snapshot(diagonal,List.of(),70,10));
        for(int a=0;a<=20;a++) {assertTrue(d.contains(a,a));assertEquals(80+a,d.height(a,a));if(a<20)assertFalse(d.contains(a+1,a));}
    }
    @Test void collinearBoundaryPointsRetainTheirElevations() {
        var boundary=List.of(p(0,80,0),p(5,100,0),p(10,80,0),p(10,80,10),p(0,80,10));
        var map=build(snapshot(boundary,List.of(),50,0));assertEquals(100,map.height(5,0));
    }
}
