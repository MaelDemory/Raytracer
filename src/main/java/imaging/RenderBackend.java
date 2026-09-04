/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package imaging;

import raytracer.Scene;

import java.awt.image.BufferedImage;

/**
 * Chemin de rendu accéléré par une API graphique.
 *
 * <p>Les implémentations sont sondées à l'exécution : la machine décide quelle
 * API est utilisable, et une scène qui sort du domaine d'un backend le fait
 * passer au suivant sans interrompre le rendu.</p>
 */
public interface RenderBackend {

    /**
     * Retourne le nom court de l'API, utilisé pour la sélection et les traces.
     * @return Nom du backend, par exemple "metal" ou "vulkan"
     */
    String name();

    /**
     * Indique si l'API est utilisable sur cette machine.
     * @return true si le backend peut tenter un rendu
     */
    boolean isAvailable();

    /**
     * Décrit l'état du backend, qu'il soit disponible ou non.
     * @return Description destinée à l'affichage
     */
    String describe();

    /**
     * Rend la scène.
     * @param scene Scène à rendre
     * @return Image rendue
     * @throws RuntimeException si le rendu échoue ou si la scène sort du domaine du backend
     */
    BufferedImage render(Scene scene);
}
