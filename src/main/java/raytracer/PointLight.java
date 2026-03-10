/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package raytracer;

import geometry.Point;
import geometry.Vector;
import imaging.Color;

/**
 * Représente une lumière ponctuelle dans la scène 3D.
 */
public class PointLight extends AbstractLight {
    /**
     * Position de la lumière ponctuelle.
     */
    private Point position;

    /**
     * Couleur de la lumière ponctuelle.
     */
    private Color color;

    /**
     * Construit une lumière ponctuelle avec la position et la couleur spécifiées.
     *
     * @param position Position de la lumière ponctuelle.
     * @param color    Couleur de la lumière ponctuelle.
     */
    public PointLight(Point position, Color color) {
        this.position = position;
        this.color = color;
    }

    /**
     * Retourne la position de la lumière ponctuelle.
     * @return Point
     */
    public Point getPosition() {
        return this.position;
    }

    /**
     * Retourne la couleur de la lumière ponctuelle.
     * @return Color
     */
    public Color getColor() {
        return this.color;
    }

    /**
     * Retourne la direction de la lumière depuis un point donné.
     * @param point Point
     * @return Vector
     */
    @Override
    public Vector getDirectionFrom(Point point) {
        return new Vector(
                this.position.getX() - point.getX(),
                this.position.getY() - point.getY(),
                this.position.getZ() - point.getZ()
        ).normalize();
    }

    /**
     * Retourne la distance maximale entre la lumière et un point donné.
     * @param point Point
     * @return double
     */
    @Override
    public double getMaxDistance(Point point) {
        return new Vector(
                position.getX() - point.getX(),
                position.getY() - point.getY(),
                position.getZ() - point.getZ()
        ).length();
    }
}
