/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

/**
 * Représente un point dans un espace 3D.
 */
public class Point extends AbstractVec3 {
    /**
     * Constructeur d'un point à partir de ses coordonnées x, y et z.
     * @param x
     * @param y
     * @param z
     */
    public Point(double x, double y, double z) {
        super(x, y, z);
    }

    /**
     * Soustrait un point à ce point et retourne le vecteur résultant.
     * @param point2 Point à soustraire
     * @return Vector résultant de la soustraction
     */
    public Vector subtract(Point point2) {
        return new Vector(
                this.getX() - point2.getX(),
                this.getY() - point2.getY(),
                this.getZ() - point2.getZ()
        );
    }

    /**
     * Multiplie ce point par un scalaire et retourne le point résultant.
     * @param scalar Scalaire par lequel multiplier le point
     * @return Point résultant de la multiplication
     */
    public Point multiply(double scalar) {
        return new Point(
                this.getX() * scalar,
                this.getY() * scalar,
                this.getZ() * scalar
        );
    }

    /**
     * Vérifie l'égalité des coordonnées entre ce point et un autre objet (Point).
     * @param o   Objet à comparer
     * @return boolean indiquant si les deux points ont des coordonnées identiques
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Point point = (Point) o;

        return Double.compare(point.getX(), getX()) == 0 &&
               Double.compare(point.getY(), getY()) == 0 &&
               Double.compare(point.getZ(), getZ()) == 0;

    }
}
