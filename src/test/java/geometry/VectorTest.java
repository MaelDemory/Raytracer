package geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VectorTest {

    private static final double EPSILON = 1e-5;

    @Test
    void testAdd() {
        Vector v1 = new Vector(1.0, 2.0, 3.0);
        Vector v2 = new Vector(4.0, 5.0, 6.0);
        Vector result = v1.add(v2);

        assertEquals(5.0, result.getX(), EPSILON);
        assertEquals(7.0, result.getY(), EPSILON);
        assertEquals(9.0, result.getZ(), EPSILON);
    }

    @Test
    void testSubtract() {
        Vector v1 = new Vector(4.0, 5.0, 6.0);
        Vector v2 = new Vector(1.0, 2.0, 3.0);
        Vector result = v1.subtract(v2);

        assertEquals(3.0, result.getX(), EPSILON);
        assertEquals(3.0, result.getY(), EPSILON);
        assertEquals(3.0, result.getZ(), EPSILON);
    }

    @Test
    void testScalarMultiply() {
        Vector v = new Vector(1.0, -2.0, 3.0);
        Vector result = v.scalarMultiply(2.5);

        assertEquals(2.5, result.getX(), EPSILON);
        assertEquals(-5.0, result.getY(), EPSILON);
        assertEquals(7.5, result.getZ(), EPSILON);
    }

    @Test
    void testVectorMultiply() {
        // Produit vectoriel de vecteurs unitaires X et Y donne Z
        Vector v1 = new Vector(1.0, 0.0, 0.0);
        Vector v2 = new Vector(0.0, 1.0, 0.0);
        Vector result = v1.vectorMultiply(v2);

        assertEquals(0.0, result.getX(), EPSILON);
        assertEquals(0.0, result.getY(), EPSILON);
        assertEquals(1.0, result.getZ(), EPSILON);

        // Test anti-commutativité
        Vector result2 = v2.vectorMultiply(v1);
        assertEquals(0.0, result2.getX(), EPSILON);
        assertEquals(0.0, result2.getY(), EPSILON);
        assertEquals(-1.0, result2.getZ(), EPSILON);
    }

    @Test
    void testScalarProduct() {
        Vector v1 = new Vector(1.0, 2.0, 3.0);
        Vector v2 = new Vector(4.0, -5.0, 6.0);
        double result = v1.scalarProduct(v2);

        // 1*4 + 2*(-5) + 3*6 = 4 - 10 + 18 = 12
        assertEquals(12.0, result, EPSILON);
    }

    @Test
    void testLength() {
        Vector v = new Vector(3.0, 4.0, 0.0);
        assertEquals(5.0, v.length(), EPSILON);

        Vector v2 = new Vector(1.0, 1.0, 1.0);
        assertEquals(Math.sqrt(3), v2.length(), EPSILON);
    }

    @Test
    void testNormalize() {
        Vector v = new Vector(3.0, 0.0, 0.0);
        Vector normalized = v.normalize();

        assertEquals(1.0, normalized.getX(), EPSILON);
        assertEquals(0.0, normalized.getY(), EPSILON);
        assertEquals(0.0, normalized.getZ(), EPSILON);
        assertEquals(1.0, normalized.length(), EPSILON);
    }

    @Test
    void testNormalizeZeroVector() {
        Vector v = new Vector(0.0, 0.0, 0.0);
        assertThrows(ArithmeticException.class, v::normalize, "Devrait lancer une exception pour la normalisation d'un vecteur nul");
    }
}
