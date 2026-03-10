/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

/**
 * Base orthonormée définie par trois vecteurs unitaires perpendiculaires u, v, w.
 */
public class Orthonormal {
    /**
     * Vecteur unitaire u.
     */
    private Vector u;

    /**
     * Vecteur unitaire v.
     */
    private Vector v;

    /**
     * Vecteur unitaire w.
     */
    private Vector w;

    /**
     * Construit une base orthonormée à partir d'un point d'observation, d'un point regardé et d'un vecteur "up".
     *
     * @param lookFrom Point d'observation.
     * @param lookAt   Point regardé.
     * @param up       Vecteur "up" définissant l'orientation verticale.
     */
    public Orthonormal(Point lookFrom, Point lookAt, Vector up) {
        Vector lookDirection = new Vector(
                lookFrom.getX() - lookAt.getX(),
                lookFrom.getY() - lookAt.getY(),
                lookFrom.getZ() - lookAt.getZ()
        );
        this.w = lookDirection.normalize();
        this.u = up.vectorMultiply(w).normalize();
        this.v = w.vectorMultiply(u).normalize();
    }

    /**
     * Retourne le vecteur unitaire u.
     * @return Vector
     */
    public Vector getU() {
        return this.u;
    }

    /**
     * Retourne le vecteur unitaire v.
     * @return Vector
     */
    public Vector getV() {
        return this.v;
    }

    /**
     * Retourne le vecteur unitaire w.
     * @return Vector
     */
    public Vector getW() {
        return this.w;
    }
}
