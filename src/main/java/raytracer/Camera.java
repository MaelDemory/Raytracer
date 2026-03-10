/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package raytracer;

import geometry.Point;
import geometry.Vector;

/**
 * Représente une caméra dans la scène 3D.
 */
public class Camera {
    /**
     *  Point de vue de la caméra.
     */
    private Point lookFrom;

    /**
     * Point que la caméra regarde.
     */
    private Point lookAt;

    /**
     * Vecteur "up" de la caméra. (indique l'orientation verticale)
     */
    private Vector up;

    /**
     * Champ de vision (Field of View) de la caméra en degrés.
     */
    private int fov;

    /**
     * Construit une caméra avec les paramètres spécifiés.
     *
     * @param lookFrom Point de vue de la caméra.
     * @param lookAt   Point que la caméra regarde.
     * @param up       Vecteur "up" de la caméra.
     * @param fov      Champ de vision (Field of View) en degrés.
     */
    public Camera(Point lookFrom, Point lookAt, Vector up, int fov) {
        this.lookFrom = lookFrom;
        this.lookAt = lookAt;
        this.up = up;
        this.fov = fov;
    }

    /**
     * Retourne le point de vue de la caméra.
     * @return Point
     */
    public Point getLookFrom() {
        return this.lookFrom;
    }

    /**
     * Retourne le point que la caméra regarde.
     * @return
     */
    public Point getLookAt() {
        return this.lookAt;
    }

    /**
     * Retourne le vecteur "up" de la caméra.
     * @return Vector
     */
    public Vector getUp() {
        return this.up;
    }

    /**
     * Retourne le champ de vision (Field of View) de la caméra en degrés.
     * @return int
     */
    public int getFov() {
        return this.fov;
    }
}
