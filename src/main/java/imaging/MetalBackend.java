/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package imaging;

import imaging.metal.MetalRayTracer;
import raytracer.Scene;

import java.awt.image.BufferedImage;

/**
 * Backend Metal, disponible sur macOS avec un GPU Metal et le pont natif compilé.
 */
public final class MetalBackend implements RenderBackend {

    /**
     * Retourne le nom court du backend.
     * @return "metal"
     */
    @Override
    public String name() {
        return "metal";
    }

    /**
     * Indique si un GPU Metal et la bibliothèque native sont utilisables.
     * @return true si le rendu Metal est possible
     */
    @Override
    public boolean isAvailable() {
        return MetalRayTracer.isAvailable();
    }

    /**
     * Décrit le périphérique Metal, ou la raison de son indisponibilité.
     * @return Description destinée à l'affichage
     */
    @Override
    public String describe() {
        return MetalRayTracer.describe();
    }

    /**
     * Rend la scène sur le GPU via Metal.
     * @param scene Scène à rendre
     * @return Image rendue
     */
    @Override
    public BufferedImage render(Scene scene) {
        return new MetalRayTracer(scene).render();
    }
}
