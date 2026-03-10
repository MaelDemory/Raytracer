/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

/**
 * Classe abstraite représentant un vecteur en 3D.
 */
public abstract class AbstractVec3 {
    /**
     * Coordonnée x du vecteur.
     */
    private double x;

    /**
     * Coordonnée y du vecteur.
     */
    private double y;

    /**
     * Coordonnée z du vecteur.
     */
    private double z;

    /**
     * Constructeur par défaut.
     */
    public AbstractVec3() {}


    /**
     * Construit un vecteur à partir de ses coordonnées x, y et z.
     * @param x
     * @param y
     * @param z
     */
    public AbstractVec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Retourne la coordonnée x du vecteur.
     * @return double
     */
    public double getX() {
        return this.x;
    }

    /**
     *  Retourne la coordonnée y du vecteur.
     * @return double
     */
    public double getY() {
        return this.y;
    }

    /**
     * Retourne la coordonnée z du vecteur.
     * @return double
     */
    public double getZ() {
        return this.z;
    }

    /**
     * Définit la coordonnée x du vecteur.
     * @param x double
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Définit la coordonnée y du vecteur.
     * @param y double
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Définit la coordonnée z du vecteur.
     * @param z double
     */
    public void setZ(double z) {
        this.z = z;
    }

}
