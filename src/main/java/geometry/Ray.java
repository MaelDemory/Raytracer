/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

/**
 * Représente un rayon dans l'espace 3D, défini par une origine et une direction.
 */
public class Ray {
    /**
     * Origine du rayon.
     */
    private Point origine;

    /**
     * Direction du rayon.
     */
    private Vector direction;

    /**
     * Constructeur du rayon.
     * @param origine
     * @param direction
     */
    public Ray(Point origine, Vector direction) {
        this.origine = origine;
        this.direction = direction;
    }

    /**
     * Retourne l'origine du rayon.
     * @return Point
     */
    public Point getOrigine() {
        return this.origine;
    }

    /**
     * Retourne la direction du rayon.
     * @return Vector
     */
    public Vector getDirection() {
        return this.direction;
    }

    /**
     * Calcule le point le long du rayon à une distance t de l'origine.
     * @param t Distance le long du rayon
     * @return Point
     */
    public Point pointAt(double t) {
        return new Point(
            origine.getX() + t * direction.getX(),
            origine.getY() + t * direction.getY(),
            origine.getZ() + t * direction.getZ()
        );
    }
}
