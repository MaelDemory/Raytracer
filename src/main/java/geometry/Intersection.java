/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

import imaging.Color;
import raytracer.AbstractLight;
import raytracer.Material;

/**
 * Répresentation d'une intersection entre un rayon et une forme dans l'espace 3D.
 */
public class Intersection {
    /**
     * Distance entre l'origine du rayon et le point d'intersection.
     */
    private double distance;

    /**
     * Point d'intersection dans l'espace 3D.
     */
    private Point point;

    /**
     * Forme intersectée.
     */
    private Shape shape;

    /**
     * Vecteur normal à la surface au point d'intersection.
     */
    private Vector normal;

    /**
     * Constructeur de l'intersection.
     * @param distance double
     * @param point Point
     * @param shape Shape
     * @param normal Vector
     */
    public Intersection(double distance, Point point, Shape shape, Vector normal) {
        this.distance = distance;
        this.point = point;
        this.shape = shape;
        this.normal = normal;
    }

    /**
     * Retourne la distance entre l'origine du rayon et le point d'intersection.
     * @return double
     */
    public double getDistance() {
        return this.distance;
    }

    /**
     * Retourne le point d'intersection dans l'espace 3D.
     * @return Point
     */
    public Point getPoint() {
        return this.point;
    }

    /**
     * Retourne la forme intersectée.
     * @return Shape
     */
    public Shape getShape() {
        return this.shape;
    }

    /**
     * Retourne le vecteur normal à la surface au point d'intersection.
     * @return Vector
     */
    public Vector getNormal() {
        return this.normal;
    }

    /**
     * Calcule la couleur au point d'intersection en fonction de la lumière et de la direction de l'œil.
     *
     * @param light AbstractLight
     * @param eyeDir Vector
     * @return Color
     */
    public Color computeColor(AbstractLight light, Vector eyeDir) {
        Vector lightDir = light.getDirectionFrom(this.point);
        double cosAngle = Math.max(lightDir.scalarProduct(this.normal), 0);

        Material material = shape.getMaterial();
        Color lightColor = light.getColor();

        Color diffuse = lightColor.schurMultiply(material.getDiffuse()).multiply(cosAngle);

        Vector h = lightDir.add(eyeDir).normalize();
        double specularFactor = Math.pow(Math.max(h.scalarProduct(this.normal), 0), material.getShininess());
        Color specular = lightColor.schurMultiply(material.getSpecular()).multiply(specularFactor);

        return diffuse.add(specular);
    }

}
