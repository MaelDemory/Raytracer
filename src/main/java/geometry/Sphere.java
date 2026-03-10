/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

import raytracer.Material;

import java.util.Optional;

/**
 * Classe représentant une sphère dans l'espace 3D.
 */
public class Sphere implements Shape{

    /**
     * Centre de la sphère
     */
    private Point center;

    /**
     * Rayon de la sphère
     */
    private double radius;

    /**
     * Matériau de la sphère
     */
    private Material material;

    /**
     * Constructeur de la sphère.
     * @param x Coordonnée x du centre
     * @param y Coordonnée y du centre
     * @param z Coordonnée z du centre
     * @param radius Rayon de la sphère
     */
    public Sphere(double x, double y, double z, double radius) {
        this.center = new Point(x,y,z);
        this.radius = radius;
    }

    /**
     * Retourne le matériau de la sphère.
     * @return Material
     */
    @Override
    public Material getMaterial() {
        return this.material;
    }

    /**
     * Définit le matériau de la sphère.
     * @param material Material
     */
    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }

    /**
     * Retourne le centre de la sphère.
     * @return Point
     */
    public Point getCenter() {
        return this.center;
    }

    /**
     * Retourne le rayon de la sphère.
     * @return double
     */
    public double getRadius() {
        return this.radius;
    }

    /**
     * Calcule l'intersection entre un rayon et la sphère.
     * @param ray Ray
     * @return Optional<Intersection>
     */
    @Override
    public Optional<Intersection> intersect(Ray ray) {
        Point origine = ray.getOrigine();
        Vector direction = ray.getDirection();

        Vector oc = new Vector(
                origine.getX() - center.getX(),
                origine.getY() - center.getY(),
                origine.getZ() - center.getZ()
        );

        double a = direction.scalarProduct(direction);
        double b = 2 * oc.scalarProduct(direction);
        double c = oc.scalarProduct(oc) - radius * radius;

        double delta = b * b - 4 * a * c;

        if (delta < 0) {
            return Optional.empty();
        }

        double sqrtDelta = Math.sqrt(delta);
        double t1 = (-b - sqrtDelta) / (2 * a);
        double t2 = (-b + sqrtDelta) / (2 * a);

        double t;
        if (t1 > 0) {
            t = t1;
        } else if (t2 > 0) {
            t = t2;
        } else {
            return Optional.empty();
        }

        Point intersectionPoint = ray.pointAt(t);

        Vector normal = new Vector(
                intersectionPoint.getX() - center.getX(),
                intersectionPoint.getY() - center.getY(),
                intersectionPoint.getZ() - center.getZ()
        ).normalize();

        return Optional.of(new Intersection(t, intersectionPoint, this, normal));
    }

    /**
     * Retourne la boîte englobante de la sphère.
     * @return Optional<BoundingBox>
     */
    @Override
    public Optional<BoundingBox> getBoundingBox() {
        Point c = getCenter();
        double r = getRadius();
        BoundingBox box = new BoundingBox(
                c.getX() - r,
                c.getY() - r,
                c.getZ() - r,
                c.getX() + r,
                c.getY() + r,
                c.getZ() + r
        );
        return Optional.of(box);
    }

}
