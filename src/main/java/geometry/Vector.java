/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

/**
 * Classe représentant un vecteur 3D avec des opérations vectorielles de base.
 */
public class Vector extends AbstractVec3 {

    /**
     * Constructeur de la classe Vector.
     * @param x
     * @param y
     * @param z
     */
    public Vector(double x, double y, double z) {
        super(x, y, z);
    }

    /**
     * Additionne ce vecteur avec un autre vecteur.
     * @param vector2 Vecteur à additionner
     * @return Nouveau vecteur résultant de l'addition
     */
    public Vector add(Vector vector2) {
        return new Vector(
                this.getX() + vector2.getX(),
                this.getY() + vector2.getY(),
                this.getZ() + vector2.getZ()
        );
    }

    /**
     * Soustrait un vecteur de ce vecteur.
     * @param vector2 Vecteur à soustraire
     * @return Nouveau vecteur résultant de la soustraction
     */
    public Vector subtract(Vector vector2) {
        return new Vector(
                this.getX() - vector2.getX(),
                this.getY() - vector2.getY(),
                this.getZ() - vector2.getZ()
        );
    }

    /**
     * Multiplie ce vecteur par un scalaire.
     * @param scalar Scalaire par lequel multiplier le vecteur
     * @return Nouveau vecteur résultant de la multiplication
     */
    public Vector scalarMultiply(double scalar) {
        return new Vector(
                this.getX() * scalar,
                this.getY() * scalar,
                this.getZ() * scalar
        );
    }

    /**
     * Calcule le produit vectoriel de ce vecteur avec un autre vecteur.
     * @param vector2 Vecteur avec lequel calculer le produit vectoriel
     * @return Nouveau vecteur résultant du produit vectoriel
     */
    public Vector vectorMultiply(Vector vector2) {
        return new Vector(
                this.getY() * vector2.getZ() - this.getZ() * vector2.getY(),
                this.getZ() * vector2.getX() - this.getX() * vector2.getZ(),
                this.getX() * vector2.getY() - this.getY() * vector2.getX()
        );
    }

    /**
     * Calcule le produit scalaire de ce vecteur avec un autre vecteur.
     * @param vector2
     * @return
     */
    public double scalarProduct(Vector vector2) {
        return this.getX() * vector2.getX() +
               this.getY() * vector2.getY() +
               this.getZ() * vector2.getZ();
    }

    /**
     * Calcule la longueur (norme) de ce vecteur.
     * @return double Longueur du vecteur
     */
    public double length() {
        return Math.sqrt(
                this.getX() * this.getX() +
                this.getY() * this.getY() +
                this.getZ() * this.getZ()
        );
    }

    /**
     * Normalise ce vecteur (le rend unitaire).
     * @return Nouveau vecteur normalisé
     */
    public Vector normalize() {
        double len = this.length();
        if (len == 0) {
            throw new ArithmeticException("Cannot normalize a zero-length vector");
        }
        return new Vector(
                this.getX() / len,
                this.getY() / len,
                this.getZ() / len
        );
    }

    /**
     * Vérifie l'égalité des coordonnées entre ce vecteur et un autre objet (Vector).
     * @param o   the reference object with which to compare.
     * @return boolean indiquant si les deux vecteurs ont des coordonnées identiques
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Vector vector = (Vector) o;

        return Double.compare(vector.getX(), getX()) == 0 &&
               Double.compare(vector.getY(), getY()) == 0 &&
               Double.compare(vector.getZ(), getZ()) == 0;
    }
}
