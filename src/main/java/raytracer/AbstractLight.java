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
 * Représente une source de lumière abstraite dans la scène.
 */
public abstract class AbstractLight {
    /**
     * Retourne la couleur de la lumière.
     * @return Color
     */
    public abstract Color getColor();

    /**
     * Retourne la direction de la lumière depuis un point donné.
     * @param point Point
     * @return Vector
     */
    public abstract Vector getDirectionFrom(Point point);

    /**
     * Retourne la distance maximale entre la lumière et un point donné.
     * @param point Point
     * @return double
     */
    public abstract double getMaxDistance(Point point);
}
