/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package imaging;

import geometry.AbstractVec3;

/**
 * Classe représentant une couleur RGB avec des composantes entre 0.0 et 1.0
 */
public class Color extends AbstractVec3 {
    /**
     * Constructeur par défaut initialisant la couleur noire (0.0, 0.0, 0.0)
     */
    public Color() {
        super(0.0, 0.0, 0.0);
    }

    /**
     * Constructeur de la classe Color avec des valeurs pour les composantes R, G et B
     * Les valeurs sont ajustées entre 0.0 et 1.0
     * @param r Composante rouge
     * @param g Composante verte
     * @param b Composante bleue
     */
    public Color(double r, double g, double b) {
        super(clamp(r), clamp(g), clamp(b));
    }

    /**
     * Méthode utilitaire pour contraindre une valeur entre 0.0 et 1.0
     * @param value Valeur à contraindre
     * @return Valeur contrainte entre 0.0 et 1.0
     */
    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Retourne la composante rouge de la couleur
     * @return Composante rouge
     */
    public double getR() {
        return super.getX();
    }

    public double getG() {
        return super.getY();
    }

    public double getB() {
        return super.getZ();
    }

    /**
     * Additionne cette couleur avec une autre couleur
     * @param color2 Couleur à additionner
     * @return Nouvelle couleur résultant de l'addition
     */
    public Color add(Color color2) {
        return new Color(
                this.getR() + color2.getR(),
                this.getG() + color2.getG(),
                this.getB() + color2.getB()
        );
    }

    /**
     * Multiplie cette couleur par un scalaire
     * @param scalar Scalaire par lequel multiplier la couleur
     * @return Nouvelle couleur résultant de la multiplication
     */
    public Color multiply(double scalar) {
        return new Color(
                this.getR() * scalar,
                this.getG() * scalar,
                this.getB() * scalar
        );
    }

    /**
     * Effectue le produit de Schur (produit Hadamard) entre cette couleur et une autre couleur
     * @param color2 Couleur avec laquelle effectuer le produit de Schur
     * @return Nouvelle couleur résultant du produit de Schur
     */
    public Color schurMultiply(Color color2) {
        return new Color(
                this.getR() * color2.getR(),
                this.getG() * color2.getG(),
                this.getB() * color2.getB()
        );
    }

    /**
     * Vérifie l'égalité entre cette couleur et un autre objet (Color)
     * @param o   the reference object with which to compare.
     * @return boolean indiquant si les deux couleurs sont identiques
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Color color = (Color) o;

        return Double.compare(color.getX(), getX()) == 0 &&
               Double.compare(color.getY(), getY()) == 0 &&
               Double.compare(color.getZ(), getZ()) == 0;
    }

    /**
     * Convertit la couleur en une valeur RGB entière
     * @return Valeur RGB entière
     */
    public int toRGB(){
        int red = (int) Math.round(getR() * 255);
        int green = (int) Math.round(getG() * 255);
        int blue = (int) Math.round(getB() * 255);
        return (
                ((red & 0xff) << 16)
                        + ((green & 0xff) << 8)
                        + (blue & 0xff));
    }
}
