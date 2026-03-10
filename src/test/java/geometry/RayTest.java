package geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RayTest {

    private static final double EPSILON = 1e-5;

    @Test
    void testConstructorAndGetters() {
        Point origin = new Point(1.0, 2.0, 3.0);
        Vector direction = new Vector(0.0, 1.0, 0.0);
        Ray ray = new Ray(origin, direction);

        assertEquals(origin, ray.getOrigine());
        assertEquals(direction, ray.getDirection());
    }

    @Test
    void testPointAt() {
        Point origin = new Point(1.0, 2.0, 3.0);
        Vector direction = new Vector(0.0, 1.0, 0.0); // Direction Y
        Ray ray = new Ray(origin, direction);

        Point p0 = ray.pointAt(0.0);
        assertEquals(1.0, p0.getX(), EPSILON);
        assertEquals(2.0, p0.getY(), EPSILON);
        assertEquals(3.0, p0.getZ(), EPSILON);

        Point p10 = ray.pointAt(10.0);
        assertEquals(1.0, p10.getX(), EPSILON);
        assertEquals(12.0, p10.getY(), EPSILON);
        assertEquals(3.0, p10.getZ(), EPSILON);

        Point pNeg = ray.pointAt(-5.0);
        assertEquals(1.0, pNeg.getX(), EPSILON);
        assertEquals(-3.0, pNeg.getY(), EPSILON);
        assertEquals(3.0, pNeg.getZ(), EPSILON);
    }
}
