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
 * Représente une lumière directionnelle dans la scène 3D.
 */
public class DirectionalLight extends AbstractLight {
    /**
     * Direction de la lumière.
     */
    private Vector direction;

    /**
     * Couleur de la lumière.
     */
    private Color color;

    /**
     * Construit une lumière directionnelle avec la direction et la couleur spécifiées.
     *
     * @param direction Direction de la lumière.
     * @param color     Couleur de la lumière.
     */
    public DirectionalLight(Vector direction, Color color) {
        this.direction = direction;
        this.color = color;
    }

    /**
     * Retourne la direction de la lumière.
     * @return Vector
     */
    public Vector getDirection() {
        return this.direction;
    }

    /**
     * Retourne la couleur de la lumière.
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
        return this.direction.normalize();
    }

    /**
     * Retourne la distance maximale de la lumière depuis un point donné.
     * Pour une lumière directionnelle, cette distance est infinie.
     * @param point Point
     * @return double
     */
    @Override
    public double getMaxDistance(Point point) {
        return Double.POSITIVE_INFINITY;
    }
}
