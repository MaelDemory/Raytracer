/**
 * Projet POO Ray Tracing - Accélération GPU
 *
 * (c) 2025 Maël DEMORY
 */

package raytracer;

import geometry.BoundingBox;
import geometry.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * Vue aplatie du BVH d'une scène, destinée aux backends qui ne peuvent pas suivre
 * de pointeurs Java (kernels GPU).
 *
 * <p>L'arbre est linéarisé dans l'ordre préfixe. Chaque noeud occupe six flottants
 * de bornes et deux entiers de topologie : un noeud interne référence ses deux fils
 * par leur index, une feuille porte l'index de sa forme dans {@link #leafShapes}.</p>
 */
public final class FlatBvh {

    /** Bornes des noeuds, 6 flottants par noeud : minX, minY, minZ, maxX, maxY, maxZ. */
    private final float[] bounds;

    /** Index du fils gauche, ou -1 si le noeud est une feuille. */
    private final int[] leftChild;

    /** Index du fils droit pour un noeud interne, index de forme pour une feuille. */
    private final int[] rightOrShape;

    /** Formes portées par les feuilles, dans l'ordre où les feuilles les référencent. */
    private final List<Shape> leafShapes;

    /** Formes sans boîte englobante, absentes de l'arbre et testées linéairement. */
    private final List<Shape> unboundedShapes;

    /**
     * Construit la vue aplatie.
     * @param bounds Bornes des noeuds
     * @param leftChild Index des fils gauches
     * @param rightOrShape Index des fils droits ou des formes de feuille
     * @param leafShapes Formes référencées par les feuilles
     * @param unboundedShapes Formes sans boîte englobante
     */
    private FlatBvh(float[] bounds, int[] leftChild, int[] rightOrShape,
                    List<Shape> leafShapes, List<Shape> unboundedShapes) {
        this.bounds = bounds;
        this.leftChild = leftChild;
        this.rightOrShape = rightOrShape;
        this.leafShapes = leafShapes;
        this.unboundedShapes = unboundedShapes;
    }

    /**
     * Aplatit le BVH de la scène donnée.
     * @param scene Scène dont l'arbre doit être linéarisé
     * @return Vue aplatie, éventuellement vide si la scène n'a aucune forme bornée
     */
    public static FlatBvh of(Scene scene) {
        BvhNode root = scene.bvhRoot();
        List<Shape> unbounded = new ArrayList<>(scene.unboundedShapes());

        if (root == null) {
            return new FlatBvh(new float[0], new int[0], new int[0], List.of(), unbounded);
        }

        int nodeCount = countNodes(root);
        Builder builder = new Builder(nodeCount);
        builder.append(root);
        return new FlatBvh(builder.bounds, builder.leftChild, builder.rightOrShape,
                builder.leafShapes, unbounded);
    }

    /**
     * Compte les noeuds de l'arbre.
     * @param node Racine du sous-arbre
     * @return Nombre de noeuds
     */
    private static int countNodes(BvhNode node) {
        if (node == null) {
            return 0;
        }
        return 1 + countNodes(node.left()) + countNodes(node.right());
    }

    /**
     * État mutable de la linéarisation.
     */
    private static final class Builder {
        /** Bornes accumulées. */
        private final float[] bounds;
        /** Fils gauches accumulés. */
        private final int[] leftChild;
        /** Fils droits ou index de forme accumulés. */
        private final int[] rightOrShape;
        /** Formes de feuille accumulées. */
        private final List<Shape> leafShapes = new ArrayList<>();
        /** Prochain index de noeud libre. */
        private int next;

        /**
         * Prépare les tableaux pour le nombre de noeuds annoncé.
         * @param nodeCount Nombre de noeuds de l'arbre
         */
        Builder(int nodeCount) {
            this.bounds = new float[nodeCount * 6];
            this.leftChild = new int[nodeCount];
            this.rightOrShape = new int[nodeCount];
        }

        /**
         * Écrit le sous-arbre à partir du prochain index libre.
         * @param node Racine du sous-arbre
         * @return Index attribué à ce noeud
         */
        int append(BvhNode node) {
            int index = next++;
            writeBounds(index, node.bounds());

            if (node.shape() != null) {
                leftChild[index] = -1;
                rightOrShape[index] = leafShapes.size();
                leafShapes.add(node.shape());
                return index;
            }

            // Un noeud interne du BVH a toujours ses deux fils : buildRecursive ne
            // descend qu'avec au moins deux primitives, donc chaque moitié est non vide.
            leftChild[index] = append(node.left());
            rightOrShape[index] = append(node.right());
            return index;
        }

        /**
         * Écrit les six bornes du noeud.
         * @param index Index du noeud
         * @param box Boîte englobante du noeud
         */
        private void writeBounds(int index, BoundingBox box) {
            int offset = index * 6;
            bounds[offset] = (float) box.minX();
            bounds[offset + 1] = (float) box.minY();
            bounds[offset + 2] = (float) box.minZ();
            bounds[offset + 3] = (float) box.maxX();
            bounds[offset + 4] = (float) box.maxY();
            bounds[offset + 5] = (float) box.maxZ();
        }
    }

    /**
     * Retourne le nombre de noeuds de l'arbre aplati.
     * @return Nombre de noeuds
     */
    public int nodeCount() {
        return leftChild.length;
    }

    /**
     * Retourne les bornes des noeuds, 6 flottants par noeud.
     * @return Tableau des bornes
     */
    public float[] bounds() {
        return bounds;
    }

    /**
     * Retourne les index de fils gauche, -1 pour une feuille.
     * @return Tableau des fils gauches
     */
    public int[] leftChild() {
        return leftChild;
    }

    /**
     * Retourne les index de fils droit, ou l'index de forme pour une feuille.
     * @return Tableau des fils droits
     */
    public int[] rightOrShape() {
        return rightOrShape;
    }

    /**
     * Retourne les formes portées par les feuilles.
     * @return Liste des formes de feuille
     */
    public List<Shape> leafShapes() {
        return leafShapes;
    }

    /**
     * Retourne les formes sans boîte englobante, testées hors de l'arbre.
     * @return Liste des formes non bornées
     */
    public List<Shape> unboundedShapes() {
        return unboundedShapes;
    }
}
