package geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VectorIntegrationTest {

    private static final double EPSILON = 1e-5;

    @Test
    void testOrthonormalBasisProperty() {
        // Vérifie que le produit vectoriel de deux vecteurs orthogonaux est orthogonal aux deux
        Vector u = new Vector(1.0, 0.0, 0.0);
        Vector v = new Vector(0.0, 1.0, 0.0);
        Vector w = u.vectorMultiply(v); // Devrait être (0, 0, 1)

        // w doit être orthogonal à u et v (produit scalaire nul)
        assertEquals(0.0, w.scalarProduct(u), EPSILON);
        assertEquals(0.0, w.scalarProduct(v), EPSILON);
        
        // w doit être unitaire
        assertEquals(1.0, w.length(), EPSILON);
    }

    @Test
    void testReflectionCalculation() {
        // Simulation du calcul de réflexion : R = I - 2(N.I)N
        // Rayon incident I (45 degrés vers le bas)
        Vector incident = new Vector(1.0, -1.0, 0.0).normalize();
        // Normale N (vers le haut)
        Vector normal = new Vector(0.0, 1.0, 0.0);

        double dot = incident.scalarProduct(normal);
        Vector reflection = incident.subtract(normal.scalarMultiply(2.0 * dot));

        // Le rayon réfléchi doit aller vers le haut à 45 degrés (1, 1, 0) normalisé
        Vector expected = new Vector(1.0, 1.0, 0.0).normalize();

        assertEquals(expected.getX(), reflection.getX(), EPSILON);
        assertEquals(expected.getY(), reflection.getY(), EPSILON);
        assertEquals(expected.getZ(), reflection.getZ(), EPSILON);
    }

    @Test
    void testChainedOperations() {
        // (v1 + v2) * 2 - v3
        Vector v1 = new Vector(1.0, 2.0, 3.0);
        Vector v2 = new Vector(0.5, 0.5, 0.5);
        Vector v3 = new Vector(1.0, 1.0, 1.0);

        Vector result = v1.add(v2).scalarMultiply(2.0).subtract(v3);

        // (1.5, 2.5, 3.5) * 2 = (3, 5, 7)
        // (3, 5, 7) - (1, 1, 1) = (2, 4, 6)
        
        assertEquals(2.0, result.getX(), EPSILON);
        assertEquals(4.0, result.getY(), EPSILON);
        assertEquals(6.0, result.getZ(), EPSILON);
    }
    
    @Test
    void testTriangleNormal() {
        // Calcul de la normale d'un triangle défini par 3 points
        Point p1 = new Point(0, 0, 0);
        Point p2 = new Point(1, 0, 0);
        Point p3 = new Point(0, 1, 0);

        // Vecteurs des côtés
        Vector v1 = new Vector(p2.getX() - p1.getX(), p2.getY() - p1.getY(), p2.getZ() - p1.getZ());
        Vector v2 = new Vector(p3.getX() - p1.getX(), p3.getY() - p1.getY(), p3.getZ() - p1.getZ());

        // Produit vectoriel pour la normale
        Vector normal = v1.vectorMultiply(v2).normalize();

        // Pour un triangle dans le plan XY (sens anti-horaire), la normale doit être Z (0, 0, 1)
        assertEquals(0.0, normal.getX(), EPSILON);
        assertEquals(0.0, normal.getY(), EPSILON);
        assertEquals(1.0, normal.getZ(), EPSILON);
    }
}
