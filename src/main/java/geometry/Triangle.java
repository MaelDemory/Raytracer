/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

import raytracer.Material;

import java.util.Optional;

/**
 * Représente un triangle dans l'espace 3D.
 */
public class Triangle implements Shape {

    /**
     * Sommet A du triangle
     */
    private final Point a;

    /**
     * Sommet B du triangle
     */
    private final Point b;

    /**
     * Sommet C du triangle
     */
    private final Point c;

    /**
     * Matériau du triangle
     */
    private Material material;

    /**
     * Épsilon pour les comparaisons flottantes.
     */
    private static final double EPSILON = 1e-7;

    /**
     * Constructeur du triangle
     * @param a Sommet A du triangle
     * @param b Sommet B du triangle
     * @param c Sommet C du triangle
     */
    public Triangle(Point a, Point b, Point c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    /**
     * Retourne le matériau du triangle.
     * @return Material
     */
    @Override
    public Material getMaterial() {
        return this.material;
    }

    /**
     * Définit le matériau du triangle.
     * @param material Material
     */
    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }

    /**
     * Retourne le sommet A du triangle.
     * @return Point
     */
    public Point getA() {
        return this.a;
    }

    /**
     * Retourne le sommet B du triangle.
     * @return Point
     */
    public Point getB() {
        return this.b;
    }

    /**
     * Retourne le sommet C du triangle.
     * @return Point
     */
    public Point getC() {
        return this.c;
    }

    /**
     * Calcule l'intersection entre un rayon et le triangle.
     * @param ray Ray
     * @return Optional<Intersection>
     */
    @Override
    public Optional<Intersection> intersect(Ray ray) {
        Vector edge1 = new Vector(
                b.getX() - a.getX(),
                b.getY() - a.getY(),
                b.getZ() - a.getZ()
        );
        Vector edge2 = new Vector(
                c.getX() - a.getX(),
                c.getY() - a.getY(),
                c.getZ() - a.getZ()
        );

        Vector pvec = ray.getDirection().vectorMultiply(edge2);
        double det = edge1.scalarProduct(pvec);

        if (Math.abs(det) < EPSILON) return Optional.empty();
        double invDet = 1.0 / det;

        Vector tvec = new Vector(
                ray.getOrigine().getX() - a.getX(),
                ray.getOrigine().getY() - a.getY(),
                ray.getOrigine().getZ() - a.getZ()
        );

        double u = tvec.scalarProduct(pvec) * invDet;
        if (u < 0.0 || u > 1.0) return Optional.empty();

        Vector qvec = tvec.vectorMultiply(edge1);
        double v = ray.getDirection().scalarProduct(qvec) * invDet;
        if (v < 0.0 || u + v > 1.0) return Optional.empty();

        double t = edge2.scalarProduct(qvec) * invDet;
        if (t < EPSILON) return Optional.empty();

        Point hitPoint = ray.pointAt(t);
        Vector normal = edge1.vectorMultiply(edge2).normalize();

        return Optional.of(new Intersection(t, hitPoint, this, normal));
    }

    /**
     * Retourne la boîte englobante du triangle.
     * @return Optional<BoundingBox>
     */
    @Override
    public Optional<BoundingBox> getBoundingBox() {
        double minX = Math.min(a.getX(), Math.min(b.getX(), c.getX()));
        double minY = Math.min(a.getY(), Math.min(b.getY(), c.getY()));
        double minZ = Math.min(a.getZ(), Math.min(b.getZ(), c.getZ()));
        double maxX = Math.max(a.getX(), Math.max(b.getX(), c.getX()));
        double maxY = Math.max(a.getY(), Math.max(b.getY(), c.getY()));
        double maxZ = Math.max(a.getZ(), Math.max(b.getZ(), c.getZ()));
        return Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }
}
