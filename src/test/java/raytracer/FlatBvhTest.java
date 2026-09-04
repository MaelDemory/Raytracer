package raytracer;

import geometry.BoundingBox;
import geometry.Plane;
import geometry.Point;
import geometry.Shape;
import geometry.Sphere;
import geometry.Vector;
import imaging.Color;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie que la linéarisation du BVH préserve la structure de l'arbre : c'est
 * la seule chose sur laquelle le kernel GPU peut s'appuyer, il n'a pas accès
 * aux objets Java.
 */
class FlatBvhTest {

    private static final double EPSILON = 1e-6;

    /**
     * Construit une scène de test contenant les formes données.
     */
    private static Scene sceneWith(List<Shape> shapes) {
        Material material = new Material(
                new Color(0.1, 0.1, 0.1),
                new Color(0.6, 0.6, 0.6),
                new Color(0.0, 0.0, 0.0),
                10.0);
        for (Shape shape : shapes) {
            shape.setMaterial(material);
        }
        Camera camera = new Camera(new Point(0, 0, 10), new Point(0, 0, 0), new Vector(0, 1, 0), 45);
        return new Scene(64, 64, camera, "test.png", new Color(0, 0, 0),
                Collections.emptyList(), shapes);
    }

    /**
     * Crée une grille de sphères, assez nombreuse pour forcer plusieurs niveaux.
     */
    private static List<Shape> sphereGrid(int count) {
        List<Shape> shapes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            shapes.add(new Sphere(i * 3.0, (i % 4) * 2.0, (i % 3) * -2.0, 1.0));
        }
        return shapes;
    }

    @Test
    void arbreVideQuandAucuneFormeBornee() {
        Scene scene = sceneWith(List.of(new Plane(new Point(0, 0, 0), new Vector(0, 1, 0))));
        FlatBvh flat = FlatBvh.of(scene);

        assertEquals(0, flat.nodeCount());
        assertTrue(flat.leafShapes().isEmpty());
        assertEquals(1, flat.unboundedShapes().size());
    }

    @Test
    void arbreBinaireCompletSurLesFormesBornees() {
        int count = 17;
        Scene scene = sceneWith(sphereGrid(count));
        FlatBvh flat = FlatBvh.of(scene);

        // Un arbre binaire dont chaque feuille porte une primitive et dont chaque
        // noeud interne a exactement deux fils compte 2n - 1 noeuds.
        assertEquals(2 * count - 1, flat.nodeCount());
        assertEquals(count, flat.leafShapes().size());
        assertTrue(flat.unboundedShapes().isEmpty());
    }

    @Test
    void chaqueFormeBorneeApparaitDansExactementUneFeuille() {
        List<Shape> shapes = sphereGrid(12);
        Scene scene = sceneWith(shapes);
        FlatBvh flat = FlatBvh.of(scene);

        Map<Shape, Integer> occurrences = new IdentityHashMap<>();
        for (Shape shape : flat.leafShapes()) {
            occurrences.merge(shape, 1, Integer::sum);
        }

        assertEquals(shapes.size(), occurrences.size());
        for (Shape shape : shapes) {
            assertEquals(1, occurrences.get(shape), "forme absente ou dupliquée dans les feuilles");
        }
    }

    @Test
    void lesIndexDeFilsRestentDansLesBornesEtNeBouclentPas() {
        Scene scene = sceneWith(sphereGrid(20));
        FlatBvh flat = FlatBvh.of(scene);

        int[] left = flat.leftChild();
        int[] right = flat.rightOrShape();
        boolean[] visited = new boolean[flat.nodeCount()];

        for (int i = 0; i < flat.nodeCount(); i++) {
            if (left[i] < 0) {
                assertTrue(right[i] >= 0 && right[i] < flat.leafShapes().size(),
                        "index de forme hors bornes pour la feuille " + i);
                continue;
            }
            assertTrue(left[i] > i && left[i] < flat.nodeCount(), "fils gauche invalide");
            assertTrue(right[i] > i && right[i] < flat.nodeCount(), "fils droit invalide");
            assertFalse(visited[left[i]], "fils gauche déjà référencé");
            assertFalse(visited[right[i]], "fils droit déjà référencé");
            visited[left[i]] = true;
            visited[right[i]] = true;
        }

        // Tous les noeuds sauf la racine sont référencés une fois.
        for (int i = 1; i < flat.nodeCount(); i++) {
            assertTrue(visited[i], "noeud " + i + " non atteignable");
        }
        assertFalse(visited[0], "la racine ne doit être le fils de personne");
    }

    @Test
    void lesBornesDunNoeudEnglobentCellesDeSesFils() {
        Scene scene = sceneWith(sphereGrid(15));
        FlatBvh flat = FlatBvh.of(scene);

        float[] bounds = flat.bounds();
        int[] left = flat.leftChild();
        int[] right = flat.rightOrShape();

        for (int i = 0; i < flat.nodeCount(); i++) {
            if (left[i] < 0) {
                continue;
            }
            assertContains(bounds, i, left[i]);
            assertContains(bounds, i, right[i]);
        }
    }

    @Test
    void lesBornesDuneFeuilleCorrespondentAuBoxDeSaForme() {
        Scene scene = sceneWith(sphereGrid(9));
        FlatBvh flat = FlatBvh.of(scene);

        float[] bounds = flat.bounds();
        int[] left = flat.leftChild();
        int[] right = flat.rightOrShape();

        for (int i = 0; i < flat.nodeCount(); i++) {
            if (left[i] >= 0) {
                continue;
            }
            Shape shape = flat.leafShapes().get(right[i]);
            BoundingBox box = shape.getBoundingBox().orElseThrow();
            int offset = i * 6;
            assertEquals(box.minX(), bounds[offset], EPSILON * Math.abs(box.minX() + 1));
            assertEquals(box.minY(), bounds[offset + 1], EPSILON * Math.abs(box.minY() + 1));
            assertEquals(box.minZ(), bounds[offset + 2], EPSILON * Math.abs(box.minZ() + 1));
            assertEquals(box.maxX(), bounds[offset + 3], EPSILON * Math.abs(box.maxX() + 1));
            assertEquals(box.maxY(), bounds[offset + 4], EPSILON * Math.abs(box.maxY() + 1));
            assertEquals(box.maxZ(), bounds[offset + 5], EPSILON * Math.abs(box.maxZ() + 1));
        }
    }

    /**
     * Vérifie que les bornes du parent contiennent celles de l'enfant, à la
     * tolérance de l'arrondi vers le simple flottant près.
     */
    private static void assertContains(float[] bounds, int parent, int child) {
        int p = parent * 6;
        int c = child * 6;
        float slack = 1e-3f;
        for (int axis = 0; axis < 3; axis++) {
            assertTrue(bounds[p + axis] <= bounds[c + axis] + slack,
                    "borne min du parent au-dessus de celle du fils, axe " + axis);
            assertTrue(bounds[p + 3 + axis] >= bounds[c + 3 + axis] - slack,
                    "borne max du parent en dessous de celle du fils, axe " + axis);
        }
    }
}
