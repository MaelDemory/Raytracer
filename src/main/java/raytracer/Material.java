/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package raytracer;

import imaging.Color;

/**
 * Représente le matériau d'une surface dans une scène 3D.
 */
public class Material {
    /**
     * Composante de couleur ambiante du matériau
     */
    private Color ambient;

    /**
     * Composante de couleur diffuse du matériau
     */
    private Color diffuse;

    /**
     * Composante de couleur spéculaire du matériau
     */
    private Color specular;

    /**
     * Brillance du matériau
     */
    private double shininess;

    /**
     * Constructeur du matériau avec les composantes de couleur et la brillance spécifiées.
     *
     * @param ambient   Composante de couleur ambiante
     * @param diffuse   Composante de couleur diffuse
     * @param specular  Composante de couleur spéculaire
     * @param shininess Brillance du matériau
     */
    public Material(Color ambient, Color diffuse, Color specular, double shininess) {
        validateColors(ambient, diffuse);
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.shininess = shininess;
    }

    /**
     * Valide que la somme des composantes ambient et diffuse ne dépasse pas 1.0 pour chaque canal de couleur.
     *
     * @param ambient Composante de couleur ambiante
     * @param diffuse Composante de couleur diffuse
     */
    private void validateColors(Color ambient, Color diffuse) {
        if (ambient.getR() + diffuse.getR() > 1.0 ||
                ambient.getG() + diffuse.getG() > 1.0 ||
                ambient.getB() + diffuse.getB() > 1.0) {
            throw new IllegalArgumentException(
                    "La somme des composantes ambient et diffuse ne doit pas dépasser 1.0"
            );
        }
    }

    /**
     * Retourne la composante de couleur ambiante du matériau.
     *
     * @return Composante de couleur ambiante
     */
    public Color getAmbient() {
        return this.ambient;
    }

    /**
     * Retourne la composante de couleur diffuse du matériau.
     *
     * @return Composante de couleur diffuse
     */
    public Color getDiffuse() {
        return this.diffuse;
    }

    /**
     * Retourne la composante de couleur spéculaire du matériau.
     *
     * @return Composante de couleur spéculaire
     */
    public Color getSpecular() {
        return this.specular;
    }

    /**
     * Retourne la brillance du matériau.
     *
     * @return Brillance du matériau
     */
    public double getShininess() {
        return this.shininess;
    }

    /**
     * Définit la composante de couleur ambiante du matériau.
     *
     * @param diffuse Composante de couleur ambiante
     */
    public void setDiffuse(Color diffuse) {
        validateColors(ambient, diffuse);
        this.diffuse = diffuse;
    }

    /**
     * Définit la composante de couleur diffuse du matériau.
     *
     * @param specular Composante de couleur diffuse
     */
    public void setSpecular(Color specular) {
        this.specular = specular;
    }
}
