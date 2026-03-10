package imaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorTest {

    private static final double EPSILON = 1e-5;

    @Test
    void testClamping() {
        Color c = new Color(1.5, -0.5, 2.0);
        assertEquals(1.0, c.getR(), EPSILON);
        assertEquals(0.0, c.getG(), EPSILON);
        assertEquals(1.0, c.getB(), EPSILON);
    }

    @Test
    void testAdd() {
        Color c1 = new Color(0.1, 0.2, 0.3);
        Color c2 = new Color(0.1, 0.2, 0.3);
        Color result = c1.add(c2);

        assertEquals(0.2, result.getR(), EPSILON);
        assertEquals(0.4, result.getG(), EPSILON);
        assertEquals(0.6, result.getB(), EPSILON);
    }

    @Test
    void testMultiplyScalar() {
        Color c = new Color(0.2, 0.3, 0.4);
        Color result = c.multiply(2.0);

        assertEquals(0.4, result.getR(), EPSILON);
        assertEquals(0.6, result.getG(), EPSILON);
        assertEquals(0.8, result.getB(), EPSILON);
    }

    @Test
    void testSchurMultiply() {
        Color c1 = new Color(0.5, 0.5, 0.5);
        Color c2 = new Color(0.5, 0.0, 1.0);
        Color result = c1.schurMultiply(c2);

        assertEquals(0.25, result.getR(), EPSILON);
        assertEquals(0.0, result.getG(), EPSILON);
        assertEquals(0.5, result.getB(), EPSILON);
    }
}
