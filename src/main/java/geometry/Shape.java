/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package geometry;

import raytracer.Material;

import java.util.Optional;

/**
 * Interface représentant une forme géométrique dans une scène 3D.
 */
public interface Shape {
    /**
     * Retourne le matériau de la forme.
     * @return Material
     */
    Material getMaterial();

    /**
     * Définit le matériau de la forme.
     * @param material Material
     */
    void setMaterial(Material material);

    /**
     * Calcule l'intersection entre un rayon et la forme.
     * @param ray Ray
     * @return Optional<Intersection>
     */
    Optional<Intersection> intersect(Ray ray);

    /**
     * Retourne la boîte englobante axis-aligned de la forme si elle est finie.
     * @return Optional<BoundingBox>
     */
    Optional<BoundingBox> getBoundingBox();
}
