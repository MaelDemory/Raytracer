package imaging.metal;

import geometry.Plane;
import geometry.Point;
import geometry.Shape;
import geometry.Sphere;
import geometry.Triangle;
import geometry.Vector;
import imaging.Color;
import org.junit.jupiter.api.Test;
import raytracer.Camera;
import raytracer.DirectionalLight;
import raytracer.Material;
import raytracer.Scene;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie la sérialisation de la scène vers les tampons du kernel Metal.
 *
 * <p>Ces tests n'ont pas besoin d'un GPU : ils portent sur la disposition
 * mémoire, qui est le contrat partagé entre Java et le shader.</p>
 */
class MetalSceneTest {

    /** Indices des mots du bloc de paramètres, alignés sur struct Params. */
    private static final int P_WIDTH = 0;
    private static final int P_HEIGHT = 1;
    private static final int P_MAX_DEPTH = 2;
    private static final int P_NODE_COUNT = 3;
    private static final int P_SPHERE_COUNT = 4;
    private static final int P_TRIANGLE_COUNT = 5;
    private static final int P_PLANE_COUNT = 6;
    private static final int P_DIR_LIGHT_COUNT = 7;
    private static final int P_OFF_SPHERES = 9;
    private static final int P_OFF_TRIANGLES = 10;
    private static final int P_OFF_PLANES = 11;
    private static final int P_OFF_MATERIALS = 12;

    /**
     * Applique un matériau reconnaissable, dont l'ambiante encode l'ordre.
     */
    private static Shape withMaterial(Shape shape, double tag) {
        shape.setMaterial(new Material(
                new Color(tag, tag, tag),
                new Color(0.5, 0.5, 0.5),
                new Color(0.0, 0.0, 0.0),
                8.0));
        return shape;
    }

    /**
     * Construit une scène mêlant sphères, triangles et un plan.
     */
    private static Scene mixedScene(int sphereCount, int triangleCount, boolean withPlane) {
        List<Shape> shapes = new ArrayList<>();
        double tag = 0.1;
        for (int i = 0; i < sphereCount; i++) {
            shapes.add(withMaterial(new Sphere(i * 4.0, 0, 0, 1.0), tag));
        }
        for (int i = 0; i < triangleCount; i++) {
            double z = -5.0 - i;
            shapes.add(withMaterial(new Triangle(
                    new Point(0, 0, z), new Point(1, 0, z), new Point(0, 1, z)), tag));
        }
        if (withPlane) {
            shapes.add(withMaterial(new Plane(new Point(0, -3, 0), new Vector(0, 1, 0)), tag));
        }

        Camera camera = new Camera(new Point(0, 0, 20), new Point(0, 0, 0), new Vector(0, 1, 0), 60);
        Scene scene = new Scene(320, 200, camera, "test.png", new Color(0.05, 0.05, 0.05),
                List.of(new DirectionalLight(new Vector(0, -1, 0), new Color(1, 1, 1))),
                shapes);
        scene.setMaxDepth(3);
        return scene;
    }

    @Test
    void lesCompteursEtDecalagesDecriventLeBlocDeFlottants() {
        MetalScene data = new MetalScene(mixedScene(5, 3, true));
        int[] params = data.params();

        assertEquals(320, params[P_WIDTH]);
        assertEquals(200, params[P_HEIGHT]);
        assertEquals(3, params[P_MAX_DEPTH]);
        assertEquals(5, params[P_SPHERE_COUNT]);
        assertEquals(3, params[P_TRIANGLE_COUNT]);
        assertEquals(1, params[P_PLANE_COUNT]);
        assertEquals(1, params[P_DIR_LIGHT_COUNT]);

        // Chaque section commence là où la précédente finit.
        assertEquals(0, params[P_OFF_SPHERES]);
        assertEquals(5 * 8, params[P_OFF_TRIANGLES]);
        assertEquals(5 * 8 + 3 * 12, params[P_OFF_PLANES]);
        assertEquals(5 * 8 + 3 * 12 + 8, params[P_OFF_MATERIALS]);

        // Un arbre sur 8 formes bornées compte 15 noeuds ; le plan reste dehors.
        assertEquals(2 * 8 - 1, params[P_NODE_COUNT]);
        assertEquals(params[P_NODE_COUNT] * 2, data.nodes().length);
    }

    @Test
    void chaqueFeuilleReferenceUnSlotValideDeSonTypeDeForme() {
        MetalScene data = new MetalScene(mixedScene(6, 4, false));
        int[] params = data.params();
        int[] nodes = data.nodes();

        int sphereCount = params[P_SPHERE_COUNT];
        int triangleCount = params[P_TRIANGLE_COUNT];
        boolean[] sphereSeen = new boolean[sphereCount];
        boolean[] triangleSeen = new boolean[triangleCount];

        for (int i = 0; i < params[P_NODE_COUNT]; i++) {
            int left = nodes[i * 2];
            int payload = nodes[i * 2 + 1];
            if (left >= 0) {
                continue;
            }
            if (left == -1) {
                assertTrue(payload >= 0 && payload < sphereCount, "slot de sphère hors bornes");
                assertFalse(sphereSeen[payload], "slot de sphère référencé deux fois");
                sphereSeen[payload] = true;
            } else if (left == -2) {
                assertTrue(payload >= 0 && payload < triangleCount, "slot de triangle hors bornes");
                assertFalse(triangleSeen[payload], "slot de triangle référencé deux fois");
                triangleSeen[payload] = true;
            } else {
                fail("type de feuille inattendu: " + left);
            }
        }

        for (boolean seen : sphereSeen) {
            assertTrue(seen, "sphère absente des feuilles");
        }
        for (boolean seen : triangleSeen) {
            assertTrue(seen, "triangle absent des feuilles");
        }
    }

    @Test
    void chaqueFormePointeVersSonPropreMateriau() {
        MetalScene data = new MetalScene(mixedScene(4, 2, true));
        int[] params = data.params();
        float[] floats = data.floats();

        int shapeCount = params[P_SPHERE_COUNT] + params[P_TRIANGLE_COUNT] + params[P_PLANE_COUNT];
        boolean[] used = new boolean[shapeCount];

        for (int i = 0; i < params[P_SPHERE_COUNT]; i++) {
            markMaterial(used, (int) floats[params[P_OFF_SPHERES] + i * 8 + 4]);
        }
        for (int i = 0; i < params[P_TRIANGLE_COUNT]; i++) {
            markMaterial(used, (int) floats[params[P_OFF_TRIANGLES] + i * 12 + 9]);
        }
        for (int i = 0; i < params[P_PLANE_COUNT]; i++) {
            markMaterial(used, (int) floats[params[P_OFF_PLANES] + i * 8 + 6]);
        }

        for (int i = 0; i < shapeCount; i++) {
            assertTrue(used[i], "matériau " + i + " jamais référencé");
        }

        // Le bloc de flottants doit contenir tous les matériaux annoncés.
        assertTrue(floats.length >= params[P_OFF_MATERIALS] + shapeCount * 12,
                "bloc de flottants trop court pour les matériaux");
    }

    @Test
    void uneProfondeurTropGrandeEstRefuseeAuLieuDEtreTronquee() {
        Scene scene = mixedScene(3, 0, false);
        scene.setMaxDepth(MetalScene.MAX_BOUNCES + 1);

        UnsupportedOperationException error =
                assertThrows(UnsupportedOperationException.class, () -> new MetalScene(scene));
        assertTrue(error.getMessage().contains("profondeur"),
                "le message doit désigner la profondeur de réflexion");
    }

    /**
     * Marque un index de matériau comme référencé, en vérifiant son unicité.
     */
    private static void markMaterial(boolean[] used, int index) {
        assertTrue(index >= 0 && index < used.length, "index de matériau hors bornes: " + index);
        assertFalse(used[index], "matériau partagé par deux formes: " + index);
        used[index] = true;
    }
}
