package raytracer;

import geometry.BoundingBox;
import geometry.Intersection;
import geometry.Ray;
import geometry.Shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Noeud de BVH binaire pour accélérer les tests d'intersection.
 */
public final class BvhNode {
    /** Épsilon pour les comparaisons flottantes. */
    private static final double EPSILON = 1e-6;

    /** Boîte englobante du noeud */
    private final BoundingBox bounds;
    /** Noeud enfant gauche */
    private final BvhNode left;
    /** Noeud enfant droit */
    private final BvhNode right;
    /** Forme contenue dans le noeud (si feuille) */
    private final Shape shape;

    /**
     * Interface de rappel pour associer un index de forme et un type de primitive
     * à une {@link Shape} lors de la sérialisation du BVH.
     */
    public interface ShapeIndexer {
        int typeOf(Shape shape);
        int indexOf(Shape shape);
    }

    /**
     * Représentation aplatie d'un noeud BVH pour l'envoi GPU.
     */
    public static record FlatNode(BoundingBox bounds, int leftIndex, int rightIndex, int shapeType, int shapeIndex) {}

    /** Primitive associant une forme à sa boîte englobante */
    private record Primitive(Shape shape, BoundingBox box) {}

    /**
     * Constructeur privé du noeud BVH.
     * @param shape
     * @param bounds
     * @param left
     * @param right
     */
    private BvhNode(Shape shape, BoundingBox bounds, BvhNode left, BvhNode right) {
        this.shape = shape;
        this.bounds = bounds;
        this.left = left;
        this.right = right;
    }

    /**
     * Construit un BVH à partir d'une liste de formes.
     * @param shapes
     * @return
     */
    static BvhNode build(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return null;
        }
        List<Primitive> primitives = new ArrayList<>(shapes.size());
        for (Shape shape : shapes) {
            BoundingBox box = shape.getBoundingBox()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Shape sans boîte englobante: " + shape.getClass().getSimpleName()));
            primitives.add(new Primitive(shape, box));
        }
        return buildRecursive(primitives, 0);
    }

    /**
     * Construit récursivement le BVH.
     * @param primitives
     * @param depth
     * @return
     */
    private static BvhNode buildRecursive(List<Primitive> primitives, int depth) {
        if (primitives.isEmpty()) {
            return null;
        }
        if (primitives.size() == 1) {
            Primitive primitive = primitives.get(0);
            return new BvhNode(primitive.shape(), primitive.box(), null, null);
        }

        int axis = depth % 3;
        primitives.sort(Comparator.comparingDouble(p -> p.box().center(axis)));
        int mid = primitives.size() / 2;
        List<Primitive> leftList = new ArrayList<>(primitives.subList(0, mid));
        List<Primitive> rightList = new ArrayList<>(primitives.subList(mid, primitives.size()));

        BvhNode left = buildRecursive(leftList, depth + 1);
        BvhNode right = buildRecursive(rightList, depth + 1);
        BoundingBox bounds = BoundingBox.surrounding(
                left != null ? left.bounds : null,
                right != null ? right.bounds : null
        );
        return new BvhNode(null, bounds, left, right);
    }

    /**
     * Trouve l'intersection la plus proche entre un rayon et les formes du BVH.
     * @param ray
     * @param maxDistance
     * @return
     */
    Optional<Intersection> findClosest(Ray ray, double maxDistance) {
        Intersection hit = traverse(ray, maxDistance);
        return Optional.ofNullable(hit);
    }

    /**
     * Teste s'il existe une intersection entre un rayon et les formes du BVH.
     * @param ray
     * @param maxDistance
     * @return
     */
    boolean hasIntersection(Ray ray, double maxDistance) {
        if (bounds != null && !bounds.intersects(ray, EPSILON, maxDistance)) {
            return false;
        }
        if (shape != null) {
            return shape.intersect(ray)
                    .map(Intersection::getDistance)
                    .filter(d -> d > EPSILON && d < maxDistance)
                    .isPresent();
        }
        if (left != null && left.hasIntersection(ray, maxDistance)) {
            return true;
        }
        return right != null && right.hasIntersection(ray, maxDistance);
    }

    /**
     * Traverse le BVH pour trouver l'intersection la plus proche.
     * @param ray
     * @param maxDistance
     * @return
     */
    private Intersection traverse(Ray ray, double maxDistance) {
        if (bounds != null && !bounds.intersects(ray, EPSILON, maxDistance)) {
            return null;
        }
        if (shape != null) {
            return shape.intersect(ray)
                    .filter(hit -> hit.getDistance() > EPSILON && hit.getDistance() < maxDistance)
                    .orElse(null);
        }
        Intersection closest = null;
        double currentMax = maxDistance;
        if (left != null) {
            Intersection hit = left.traverse(ray, currentMax);
            if (hit != null) {
                closest = hit;
                currentMax = hit.getDistance();
            }
        }
        if (right != null) {
            Intersection hit = right.traverse(ray, currentMax);
            if (hit != null && (closest == null || hit.getDistance() < closest.getDistance())) {
                closest = hit;
            }
        }
        return closest;
    }

    /**
     * Aplatis le BVH en parcours préfixe pour une consommation GPU.
     * @param root racine du BVH
     * @param indexer stratégie de mapping forme -> (type, index)
     * @param out liste qui sera remplie par les noeuds aplatis
     */
    public static void flatten(BvhNode root, ShapeIndexer indexer, List<FlatNode> out) {
        if (root == null) {
            return;
        }
        flattenRecursive(root, indexer, out);
    }

    /**
     * Retourne une nouvelle liste de noeuds aplatis.
     * @param root racine du BVH
     * @param indexer stratégie de mapping forme -> (type, index)
     * @return liste de noeuds aplatis en ordre préfixe
     */
    public static List<FlatNode> flatten(BvhNode root, ShapeIndexer indexer) {
        List<FlatNode> out = new ArrayList<>();
        flatten(root, indexer, out);
        return out;
    }

    private static int flattenRecursive(BvhNode node, ShapeIndexer indexer, List<FlatNode> out) {
        if (node == null) {
            return -1;
        }

        int selfIndex = out.size();
        out.add(null); // placeholder

        int leftIndex = flattenRecursive(node.left, indexer, out);
        int rightIndex = flattenRecursive(node.right, indexer, out);

        int shapeType = -1;
        int shapeIndex = -1;
        if (node.shape != null) {
            shapeType = indexer.typeOf(node.shape);
            shapeIndex = indexer.indexOf(node.shape);
        }

        out.set(selfIndex, new FlatNode(node.bounds, leftIndex, rightIndex, shapeType, shapeIndex));
        return selfIndex;
    }
}
