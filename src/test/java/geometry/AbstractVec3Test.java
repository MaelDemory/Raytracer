package geometry;

import imaging.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbstractVec3Test {

    @Test
    void testColorConstructorAndGetters() {
        Color color = new Color(0.5, 0.8, 1.0);
        assertEquals(0.5, color.getX(), 0.0001);
        assertEquals(0.8, color.getY(), 0.0001);
        assertEquals(1.0, color.getZ(), 0.0001);
    }

    @Test
    void testColorDefaultConstructor() {
        Color color = new Color();
        assertEquals(0.0, color.getX(), 0.0001);
        assertEquals(0.0, color.getY(), 0.0001);
        assertEquals(0.0, color.getZ(), 0.0001);
    }

    @Test
    void testPointConstructorAndGetters() {
        Point point = new Point(1.0, 2.0, 3.0);
        assertEquals(1.0, point.getX(), 0.0001);
        assertEquals(2.0, point.getY(), 0.0001);
        assertEquals(3.0, point.getZ(), 0.0001);
    }

    @Test
    void testVectorConstructorAndGetters() {
        Vector vector = new Vector(4.0, 5.0, 6.0);
        assertEquals(4.0, vector.getX(), 0.0001);
        assertEquals(5.0, vector.getY(), 0.0001);
        assertEquals(6.0, vector.getZ(), 0.0001);
    }

    @Test
    void testSetters() {
        Vector vector = new Vector(1.0, 2.0, 3.0);
        vector.setX(7.0);
        vector.setY(8.0);
        vector.setZ(9.0);
        assertEquals(7.0, vector.getX(), 0.0001);
        assertEquals(8.0, vector.getY(), 0.0001);
        assertEquals(9.0, vector.getZ(), 0.0001);
    }

    @Test
    void testColorEquals() {
        Color c1 = new Color(0.5, 0.5, 0.5);
        Color c2 = new Color(0.5, 0.5, 0.5);
        Color c3 = new Color(0.6, 0.5, 0.5);

        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
        assertEquals(c1, c1);
        assertNotEquals(c1, null);
    }

    @Test
    void testPointEquals() {
        Point p1 = new Point(1.0, 2.0, 3.0);
        Point p2 = new Point(1.0, 2.0, 3.0);
        Point p3 = new Point(1.0, 2.0, 4.0);

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertEquals(p1, p1);
        assertNotEquals(p1, null);
    }

    @Test
    void testVectorEquals() {
        Vector v1 = new Vector(1.0, 0.0, 0.0);
        Vector v2 = new Vector(1.0, 0.0, 0.0);
        Vector v3 = new Vector(0.0, 1.0, 0.0);

        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
        assertEquals(v1, v1);
        assertNotEquals(v1, null);
    }

    @Test
    void testDifferentTypesDontEqual() {
        Vector vector = new Vector(1.0, 2.0, 3.0);
        Point point = new Point(1.0, 2.0, 3.0);
        Color color = new Color(1.0, 2.0, 3.0);

        assertNotEquals(vector, point);
        assertNotEquals(vector, color);
        assertNotEquals(point, color);
    }

    @Test
    void testNegativeValues() {
        Vector vector = new Vector(-1.0, -2.0, -3.0);
        assertEquals(-1.0, vector.getX(), 0.0001);
        assertEquals(-2.0, vector.getY(), 0.0001);
        assertEquals(-3.0, vector.getZ(), 0.0001);
    }
}
