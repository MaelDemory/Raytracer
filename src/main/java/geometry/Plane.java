/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

import raytracer.Material;

import java.util.Optional;

/**
 * Plan infini défini par un point et une normale.
 */
public class Plane implements Shape {
    /**
     * Point appartenant au plan.
     */
    private final Point point;

    /**
     * Vecteur normal au plan.
     */
    private final Vector normal;

    /**
     * Matériau du plan.
     */
    private Material material;

    /** Épsilon pour les comparaisons flottantes.
     *
     */
    private static final double EPSILON = 1e-6;

    /**
     * Constructeur du plan.
     * @param point Point appartenant au plan
     * @param normal Vecteur normal au plan
     */
    public Plane(Point point, Vector normal) {
        this.point = point;
        this.normal = normal.normalize();
    }

    /**
     * Retourne le matériau du plan.
     * @return Material
     */
    @Override
    public Material getMaterial() {
        return this.material;
    }

    /**
     * Définit le matériau du plan.
     * @param material Material
     */
    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }

    /**
     * Retourne un point appartenant au plan.
     * @return Point
     */
    public Point getPoint() {
        return this.point;
    }

    /**
     * Retourne la normale normalisée du plan.
     * @return Vector
     */
    public Vector getNormalVector() {
        return this.normal;
    }

    /**
     * Alias pour compatibilité GPU/Vulkan.
     */
    public Vector getNormal() {
        return this.normal;
    }

    /**
     * Calcule l'intersection entre un rayon et le plan.
     * @param ray Ray
     * @return Optional<Intersection>
     */
    @Override
    public Optional<Intersection> intersect(Ray ray) {
        double denom = normal.scalarProduct(ray.getDirection());
        if (Math.abs(denom) < EPSILON) return Optional.empty();

        Vector qMinusO = new Vector(
                point.getX() - ray.getOrigine().getX(),
                point.getY() - ray.getOrigine().getY(),
                point.getZ() - ray.getOrigine().getZ()
        );
        double t = qMinusO.scalarProduct(normal) / denom;
        if (t < EPSILON) return Optional.empty();

        Point pHit = ray.pointAt(t);
        return Optional.of(new Intersection(t, pHit, this, normal));
    }

    /**
     * Retourne la boîte englobante du plan (non définie pour un plan infini).
     * @return Optional<BoundingBox>
     */
    @Override
    public Optional<BoundingBox> getBoundingBox() {
        return Optional.empty();
    }
}
