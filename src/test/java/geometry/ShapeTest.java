package geometry;

import org.junit.jupiter.api.Test;
import raytracer.Material;
import imaging.Color;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShapeTest {

    private static final double EPSILON = 1e-5;

    @Test
    void testSphereIntersection() {
        Sphere sphere = new Sphere(0, 0, 0, 1.0);
        Ray ray = new Ray(new Point(0, 0, -5), new Vector(0, 0, 1)); // Rayon vers la sphère

        Optional<Intersection> intersection = sphere.intersect(ray);
        assertTrue(intersection.isPresent());
        assertEquals(4.0, intersection.get().getDistance(), EPSILON); // 5 - 1 = 4
        
        // Vérification de la normale au point d'intersection (0, 0, -1)
        Vector normal = intersection.get().getNormal();
        assertEquals(0.0, normal.getX(), EPSILON);
        assertEquals(0.0, normal.getY(), EPSILON);
        assertEquals(-1.0, normal.getZ(), EPSILON);
    }

    @Test
    void testSphereNoIntersection() {
        Sphere sphere = new Sphere(0, 0, 0, 1.0);
        Ray ray = new Ray(new Point(0, 0, -5), new Vector(0, 1, 0)); // Rayon parallèle

        Optional<Intersection> intersection = sphere.intersect(ray);
        assertFalse(intersection.isPresent());
    }

    @Test
    void testPlaneIntersection() {
        Plane plane = new Plane(new Point(0, 0, 0), new Vector(0, 1, 0)); // Plan XZ
        Ray ray = new Ray(new Point(0, 5, 0), new Vector(0, -1, 0)); // Rayon vers le bas

        Optional<Intersection> intersection = plane.intersect(ray);
        assertTrue(intersection.isPresent());
        assertEquals(5.0, intersection.get().getDistance(), EPSILON);
        
        // Normale du plan
        Vector normal = intersection.get().getNormal();
        assertEquals(0.0, normal.getX(), EPSILON);
        assertEquals(1.0, normal.getY(), EPSILON);
        assertEquals(0.0, normal.getZ(), EPSILON);
    }

    @Test
    void testPlaneParallelNoIntersection() {
        Plane plane = new Plane(new Point(0, 0, 0), new Vector(0, 1, 0));
        Ray ray = new Ray(new Point(0, 5, 0), new Vector(1, 0, 0)); // Parallèle au plan

        Optional<Intersection> intersection = plane.intersect(ray);
        assertFalse(intersection.isPresent());
    }

    @Test
    void testTriangleIntersection() {
        Point p1 = new Point(0, 0, 0);
        Point p2 = new Point(1, 0, 0);
        Point p3 = new Point(0, 1, 0);
        Triangle triangle = new Triangle(p1, p2, p3);

        Ray ray = new Ray(new Point(0.2, 0.2, -1), new Vector(0, 0, 1)); // Passe par le triangle

        Optional<Intersection> intersection = triangle.intersect(ray);
        assertTrue(intersection.isPresent());
        assertEquals(1.0, intersection.get().getDistance(), EPSILON);
    }

    @Test
    void testTriangleMiss() {
        Point p1 = new Point(0, 0, 0);
        Point p2 = new Point(1, 0, 0);
        Point p3 = new Point(0, 1, 0);
        Triangle triangle = new Triangle(p1, p2, p3);

        Ray ray = new Ray(new Point(2, 2, -1), new Vector(0, 0, 1)); // Passe à côté

        Optional<Intersection> intersection = triangle.intersect(ray);
        assertFalse(intersection.isPresent());
    }
    
    @Test
    void testMaterialHandling() {
        Sphere sphere = new Sphere(0, 0, 0, 1);
        Material mat = new Material(new Color(0.1, 0.1, 0.1), new Color(0.5, 0.5, 0.5), new Color(0.5, 0.5, 0.5), 10);
        sphere.setMaterial(mat);
        
        assertEquals(mat, sphere.getMaterial());
    }
}
