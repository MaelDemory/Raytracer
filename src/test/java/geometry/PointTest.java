package geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointTest {

    private static final double EPSILON = 1e-5;

    @Test
    void testSubtractPoint() {
        Point p1 = new Point(1.0, 2.0, 3.0);
        Point p2 = new Point(4.0, 5.0, 6.0);
        Vector result = p1.subtract(p2);

        assertEquals(-3.0, result.getX(), EPSILON);
        assertEquals(-3.0, result.getY(), EPSILON);
        assertEquals(-3.0, result.getZ(), EPSILON);
    }

    @Test
    void testMultiply() {
        Point p = new Point(1.0, -2.0, 3.0);
        Point result = p.multiply(2.5);

        assertEquals(2.5, result.getX(), EPSILON);
        assertEquals(-5.0, result.getY(), EPSILON);
        assertEquals(7.5, result.getZ(), EPSILON);
    }

    @Test
    void testEquals() {
        Point p1 = new Point(1.0, 2.0, 3.0);
        Point p2 = new Point(1.0, 2.0, 3.0);
        Point p3 = new Point(1.0, 2.0, 4.0);

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertNotEquals(p1, null);
        assertNotEquals(p1, new Object());
    }
}
