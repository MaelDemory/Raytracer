/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package imaging;

import imaging.vulkan.VulkanRayTracer;
import raytracer.Scene;

import java.awt.image.BufferedImage;

/**
 * Backend Vulkan compute, porté par LWJGL. C'est le chemin GPU portable, utilisé
 * là où Metal n'existe pas — notamment dans l'image Docker, qui tourne sous Linux.
 */
public final class VulkanBackend implements RenderBackend {

    /**
     * Retourne le nom court du backend.
     * @return "vulkan"
     */
    @Override
    public String name() {
        return "vulkan";
    }

    /**
     * Indique si un périphérique Vulkan est accessible.
     * @return true si le rendu Vulkan est possible
     */
    @Override
    public boolean isAvailable() {
        try {
            return VulkanRayTracer.isAvailable();
        } catch (Throwable t) {
            // LWJGL peut échouer au chargement de ses natifs : c'est une
            // indisponibilité, pas une erreur de rendu.
            return false;
        }
    }

    /**
     * Décrit l'état du backend Vulkan.
     * @return Description destinée à l'affichage
     */
    @Override
    public String describe() {
        return isAvailable() ? "Vulkan: périphérique compute disponible" : "Vulkan indisponible";
    }

    /**
     * Rend la scène sur le GPU via Vulkan.
     * @param scene Scène à rendre
     * @return Image rendue
     */
    @Override
    public BufferedImage render(Scene scene) {
        try (VulkanRayTracer tracer = new VulkanRayTracer(scene)) {
            return tracer.render();
        }
    }
}
