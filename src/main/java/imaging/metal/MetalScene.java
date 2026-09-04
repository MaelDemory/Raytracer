/**
 * Projet POO Ray Tracing - Accélération Metal
 *
 * (c) 2025 Maël DEMORY
 */

package imaging.metal;

import geometry.Orthonormal;
import geometry.Plane;
import geometry.Shape;
import geometry.Sphere;
import geometry.Triangle;
import imaging.Color;
import raytracer.AbstractLight;
import raytracer.Camera;
import raytracer.DirectionalLight;
import raytracer.FlatBvh;
import raytracer.Material;
import raytracer.PointLight;
import raytracer.Scene;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scène linéarisée pour le kernel Metal.
 *
 * <p>Tous les tableaux de flottants sont concaténés en un seul bloc et le kernel
 * y accède par les décalages publiés dans le bloc de paramètres. Cela réduit le
 * nombre de tampons Metal à quatre et garde la signature du pont natif courte.</p>
 */
public final class MetalScene {

    /** Nombre de mots de 4 octets du bloc de paramètres. */
    static final int PARAM_WORDS = 36;

    /** Rebonds maximaux supportés par le kernel. */
    static final int MAX_BOUNCES = 16;

    /** Feuille de BVH portant une sphère. */
    private static final int LEAF_SPHERE = -1;

    /** Feuille de BVH portant un triangle. */
    private static final int LEAF_TRIANGLE = -2;

    /** Bloc de paramètres uniformes, 36 mots de 4 octets. */
    private final int[] params = new int[PARAM_WORDS];

    /** Concaténation des tableaux flottants de la scène. */
    private final float[] floats;

    /** Topologie du BVH, deux entiers par noeud. */
    private final int[] nodes;

    /** Largeur de l'image. */
    private final int width;

    /** Hauteur de l'image. */
    private final int height;

    /**
     * Sérialise la scène.
     * @param scene Scène à préparer
     * @throws UnsupportedOperationException si la scène sort du domaine du kernel
     */
    public MetalScene(Scene scene) {
        this.width = scene.getWidth();
        this.height = scene.getHeight();

        int maxDepth = scene.getMaxDepth();
        if (maxDepth > MAX_BOUNCES) {
            throw new UnsupportedOperationException(
                    "profondeur de réflexion " + maxDepth + " au-delà des " + MAX_BOUNCES
                            + " rebonds supportés par le kernel");
        }

        FlatBvh bvh = FlatBvh.of(scene);

        // Les formes portées par les feuilles gardent leur ordre d'apparition dans
        // l'arbre ; les formes non bornées (plans) sont testées à part.
        List<Shape> leafShapes = bvh.leafShapes();
        List<Shape> unbounded = bvh.unboundedShapes();

        List<Sphere> spheres = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();
        List<Plane> planes = new ArrayList<>();
        Map<Shape, Integer> leafKind = new IdentityHashMap<>();
        Map<Shape, Integer> leafSlot = new IdentityHashMap<>();

        for (Shape shape : leafShapes) {
            if (shape instanceof Sphere sphere) {
                leafKind.put(shape, LEAF_SPHERE);
                leafSlot.put(shape, spheres.size());
                spheres.add(sphere);
            } else if (shape instanceof Triangle triangle) {
                leafKind.put(shape, LEAF_TRIANGLE);
                leafSlot.put(shape, triangles.size());
                triangles.add(triangle);
            } else {
                throw new UnsupportedOperationException(
                        "forme bornée non supportée par le kernel: " + shape.getClass().getSimpleName());
            }
        }

        for (Shape shape : unbounded) {
            if (shape instanceof Plane plane) {
                planes.add(plane);
            } else {
                throw new UnsupportedOperationException(
                        "forme non bornée non supportée par le kernel: " + shape.getClass().getSimpleName());
            }
        }

        // L'index de matériau suit l'ordre sphères, triangles, plans : c'est aussi
        // celui dans lequel les matériaux sont écrits ci-dessous.
        List<Shape> materialOrder = new ArrayList<>(spheres.size() + triangles.size() + planes.size());
        materialOrder.addAll(spheres);
        materialOrder.addAll(triangles);
        materialOrder.addAll(planes);
        Map<Shape, Integer> materialIndex = new IdentityHashMap<>();
        for (int i = 0; i < materialOrder.size(); i++) {
            materialIndex.put(materialOrder.get(i), i);
        }

        List<DirectionalLight> directionalLights = new ArrayList<>();
        List<PointLight> pointLights = new ArrayList<>();
        for (AbstractLight light : scene.getLights()) {
            if (light instanceof DirectionalLight directional) {
                directionalLights.add(directional);
            } else if (light instanceof PointLight point) {
                pointLights.add(point);
            } else {
                throw new UnsupportedOperationException(
                        "lumière non supportée par le kernel: " + light.getClass().getSimpleName());
            }
        }

        // Découpage du bloc de flottants.
        int offSpheres = 0;
        int offTriangles = offSpheres + spheres.size() * 8;
        int offPlanes = offTriangles + triangles.size() * 12;
        int offMaterials = offPlanes + planes.size() * 8;
        int offDirLights = offMaterials + materialOrder.size() * 12;
        int offPointLights = offDirLights + directionalLights.size() * 8;
        int offBvhBounds = offPointLights + pointLights.size() * 8;
        int total = offBvhBounds + bvh.nodeCount() * 6;

        // Metal refuse un tampon vide : une scène sans aucune donnée en garde un mot.
        this.floats = new float[Math.max(1, total)];

        writeSpheres(spheres, materialIndex, offSpheres);
        writeTriangles(triangles, materialIndex, offTriangles);
        writePlanes(planes, materialIndex, offPlanes);
        writeMaterials(materialOrder, offMaterials);
        writeDirectionalLights(directionalLights, offDirLights);
        writePointLights(pointLights, offPointLights);
        System.arraycopy(bvh.bounds(), 0, floats, offBvhBounds, bvh.nodeCount() * 6);

        this.nodes = buildNodes(bvh, leafKind, leafSlot);

        writeParams(scene, bvh.nodeCount(), spheres.size(), triangles.size(), planes.size(),
                directionalLights.size(), pointLights.size(),
                offSpheres, offTriangles, offPlanes, offMaterials, offDirLights,
                offPointLights, offBvhBounds);
    }

    /**
     * Écrit les sphères : centre, rayon, index de matériau.
     * @param spheres Sphères à écrire
     * @param materialIndex Index de matériau par forme
     * @param offset Décalage de la section dans le bloc de flottants
     */
    private void writeSpheres(List<Sphere> spheres, Map<Shape, Integer> materialIndex, int offset) {
        for (int i = 0; i < spheres.size(); i++) {
            Sphere sphere = spheres.get(i);
            int base = offset + i * 8;
            floats[base] = (float) sphere.getCenter().getX();
            floats[base + 1] = (float) sphere.getCenter().getY();
            floats[base + 2] = (float) sphere.getCenter().getZ();
            floats[base + 3] = (float) sphere.getRadius();
            floats[base + 4] = materialIndex.get(sphere);
        }
    }

    /**
     * Écrit les triangles : trois sommets, index de matériau.
     * @param triangles Triangles à écrire
     * @param materialIndex Index de matériau par forme
     * @param offset Décalage de la section dans le bloc de flottants
     */
    private void writeTriangles(List<Triangle> triangles, Map<Shape, Integer> materialIndex, int offset) {
        for (int i = 0; i < triangles.size(); i++) {
            Triangle triangle = triangles.get(i);
            int base = offset + i * 12;
            floats[base] = (float) triangle.getA().getX();
            floats[base + 1] = (float) triangle.getA().getY();
            floats[base + 2] = (float) triangle.getA().getZ();
            floats[base + 3] = (float) triangle.getB().getX();
            floats[base + 4] = (float) triangle.getB().getY();
            floats[base + 5] = (float) triangle.getB().getZ();
            floats[base + 6] = (float) triangle.getC().getX();
            floats[base + 7] = (float) triangle.getC().getY();
            floats[base + 8] = (float) triangle.getC().getZ();
            floats[base + 9] = materialIndex.get(triangle);
        }
    }

    /**
     * Écrit les plans : point, normale, index de matériau.
     * @param planes Plans à écrire
     * @param materialIndex Index de matériau par forme
     * @param offset Décalage de la section dans le bloc de flottants
     */
    private void writePlanes(List<Plane> planes, Map<Shape, Integer> materialIndex, int offset) {
        for (int i = 0; i < planes.size(); i++) {
            Plane plane = planes.get(i);
            int base = offset + i * 8;
            floats[base] = (float) plane.getPoint().getX();
            floats[base + 1] = (float) plane.getPoint().getY();
            floats[base + 2] = (float) plane.getPoint().getZ();
            floats[base + 3] = (float) plane.getNormalVector().getX();
            floats[base + 4] = (float) plane.getNormalVector().getY();
            floats[base + 5] = (float) plane.getNormalVector().getZ();
            floats[base + 6] = materialIndex.get(plane);
        }
    }

    /**
     * Écrit les matériaux : ambiante, diffus, spéculaire, brillance.
     * @param shapes Formes dans l'ordre des index de matériau
     * @param offset Décalage de la section dans le bloc de flottants
     */
    private void writeMaterials(List<Shape> shapes, int offset) {
        for (int i = 0; i < shapes.size(); i++) {
            Material material = shapes.get(i).getMaterial();
            int base = offset + i * 12;
            writeColor(material.getAmbient(), base);
            writeColor(material.getDiffuse(), base + 3);
            writeColor(material.getSpecular(), base + 6);
            floats[base + 9] = (float) material.getShininess();
        }
    }

    /**
     * Écrit les lumières directionnelles : direction, couleur.
     * @param lights Lumières à écrire
     * @param offset Décalage de la section dans le bloc de flottants
     */
    private void writeDirectionalLights(List<DirectionalLight> lights, int offset) {
        for (int i = 0; i < lights.size(); i++) {
            DirectionalLight light = lights.get(i);
            int base = offset + i * 8;
            floats[base] = (float) light.getDirection().getX();
            floats[base + 1] = (float) light.getDirection().getY();
            floats[base + 2] = (float) light.getDirection().getZ();
            writeColor(light.getColor(), base + 3);
        }
    }

    /**
     * Écrit les lumières ponctuelles : position, couleur.
     * @param lights Lumières à écrire
     * @param offset Décalage de la section dans le bloc de flottants
     */
    private void writePointLights(List<PointLight> lights, int offset) {
        for (int i = 0; i < lights.size(); i++) {
            PointLight light = lights.get(i);
            int base = offset + i * 8;
            floats[base] = (float) light.getPosition().getX();
            floats[base + 1] = (float) light.getPosition().getY();
            floats[base + 2] = (float) light.getPosition().getZ();
            writeColor(light.getColor(), base + 3);
        }
    }

    /**
     * Écrit une couleur sur trois flottants consécutifs.
     * @param color Couleur à écrire
     * @param base Index du premier flottant
     */
    private void writeColor(Color color, int base) {
        floats[base] = (float) color.getR();
        floats[base + 1] = (float) color.getG();
        floats[base + 2] = (float) color.getB();
    }

    /**
     * Construit la topologie du BVH attendue par le kernel : pour un noeud interne
     * les index des deux fils, pour une feuille le type négatif puis le slot.
     * @param bvh Arbre aplati
     * @param leafKind Type de feuille par forme
     * @param leafSlot Slot dans le tableau typé par forme
     * @return Tableau de deux entiers par noeud
     */
    private static int[] buildNodes(FlatBvh bvh, Map<Shape, Integer> leafKind, Map<Shape, Integer> leafSlot) {
        int nodeCount = bvh.nodeCount();
        int[] result = new int[nodeCount * 2];
        int[] leftChild = bvh.leftChild();
        int[] rightOrShape = bvh.rightOrShape();
        List<Shape> leafShapes = bvh.leafShapes();

        for (int i = 0; i < nodeCount; i++) {
            if (leftChild[i] >= 0) {
                result[i * 2] = leftChild[i];
                result[i * 2 + 1] = rightOrShape[i];
            } else {
                Shape shape = leafShapes.get(rightOrShape[i]);
                result[i * 2] = leafKind.get(shape);
                result[i * 2 + 1] = leafSlot.get(shape);
            }
        }
        return result;
    }

    /**
     * Remplit le bloc de paramètres uniformes.
     * @param scene Scène rendue
     * @param nodeCount Nombre de noeuds du BVH
     * @param sphereCount Nombre de sphères
     * @param triangleCount Nombre de triangles
     * @param planeCount Nombre de plans
     * @param dirLightCount Nombre de lumières directionnelles
     * @param pointLightCount Nombre de lumières ponctuelles
     * @param offSpheres Décalage des sphères
     * @param offTriangles Décalage des triangles
     * @param offPlanes Décalage des plans
     * @param offMaterials Décalage des matériaux
     * @param offDirLights Décalage des lumières directionnelles
     * @param offPointLights Décalage des lumières ponctuelles
     * @param offBvhBounds Décalage des bornes du BVH
     */
    private void writeParams(Scene scene, int nodeCount, int sphereCount, int triangleCount,
                             int planeCount, int dirLightCount, int pointLightCount,
                             int offSpheres, int offTriangles, int offPlanes, int offMaterials,
                             int offDirLights, int offPointLights, int offBvhBounds) {
        Camera camera = scene.getCamera();
        Orthonormal base = new Orthonormal(camera.getLookFrom(), camera.getLookAt(), camera.getUp());

        double fovRadians = Math.toRadians(camera.getFov());
        double viewportHeight = 2.0 * Math.tan(fovRadians / 2.0);
        double viewportWidth = viewportHeight * ((double) width / height);
        float pixelWidth = (float) (viewportWidth / width);
        float pixelHeight = (float) (viewportHeight / height);

        params[0] = width;
        params[1] = height;
        params[2] = scene.getMaxDepth();
        params[3] = nodeCount;

        params[4] = sphereCount;
        params[5] = triangleCount;
        params[6] = planeCount;
        params[7] = dirLightCount;

        params[8] = pointLightCount;
        params[9] = offSpheres;
        params[10] = offTriangles;
        params[11] = offPlanes;

        params[12] = offMaterials;
        params[13] = offDirLights;
        params[14] = offPointLights;
        params[15] = offBvhBounds;

        putFloat(16, (float) camera.getLookFrom().getX());
        putFloat(17, (float) camera.getLookFrom().getY());
        putFloat(18, (float) camera.getLookFrom().getZ());
        putFloat(19, pixelWidth);

        putFloat(20, (float) base.getU().getX());
        putFloat(21, (float) base.getU().getY());
        putFloat(22, (float) base.getU().getZ());
        putFloat(23, pixelHeight);

        putFloat(24, (float) base.getV().getX());
        putFloat(25, (float) base.getV().getY());
        putFloat(26, (float) base.getV().getZ());

        putFloat(28, (float) base.getW().getX());
        putFloat(29, (float) base.getW().getY());
        putFloat(30, (float) base.getW().getZ());

        putFloat(32, (float) scene.getAmbient().getR());
        putFloat(33, (float) scene.getAmbient().getG());
        putFloat(34, (float) scene.getAmbient().getB());
    }

    /**
     * Range un flottant dans le bloc de paramètres, qui est typé en entiers.
     * @param index Index du mot
     * @param value Valeur à ranger
     */
    private void putFloat(int index, float value) {
        params[index] = Float.floatToRawIntBits(value);
    }

    /**
     * Retourne le bloc de paramètres uniformes.
     * @return Tableau de 36 mots de 4 octets
     */
    int[] params() {
        return params;
    }

    /**
     * Retourne la concaténation des tableaux flottants.
     * @return Bloc de flottants
     */
    float[] floats() {
        return floats;
    }

    /**
     * Retourne la topologie du BVH.
     * @return Deux entiers par noeud
     */
    int[] nodes() {
        return nodes;
    }

    /**
     * Retourne la largeur de l'image.
     * @return Largeur en pixels
     */
    int width() {
        return width;
    }

    /**
     * Retourne la hauteur de l'image.
     * @return Hauteur en pixels
     */
    int height() {
        return height;
    }
}
