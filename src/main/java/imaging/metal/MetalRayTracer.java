/**
 * Projet POO Ray Tracing - Accélération Metal
 *
 * (c) 2025 Maël DEMORY
 */

package imaging.metal;

import raytracer.Scene;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/**
 * Rendu accéléré par le GPU via Metal.
 *
 * <p>Le kernel embarque le BVH de la scène et applique la même sémantique que
 * {@link imaging.RayTracer} : mêmes epsilons, même modèle de Phong, même
 * saturation des couleurs à chaque niveau de réflexion. Les images produites
 * sont donc comparables pixel à pixel avec le rendu CPU, à la précision du
 * simple flottant près.</p>
 */
public final class MetalRayTracer {

    /** Chemin de la source du kernel dans les ressources. */
    private static final String SHADER_RESOURCE = "/metal/raytracer.metal";

    /** Scène à rendre. */
    private final Scene scene;

    /**
     * Construit le traceur pour la scène donnée.
     * @param scene Scène à rendre
     */
    public MetalRayTracer(Scene scene) {
        this.scene = scene;
    }

    /**
     * Indique si un rendu Metal est possible sur cette machine.
     * @return true si la bibliothèque native et un GPU Metal sont disponibles
     */
    public static boolean isAvailable() {
        return MetalBridge.isAvailable();
    }

    /**
     * Décrit le backend Metal, qu'il soit disponible ou non.
     * @return Description destinée à l'affichage
     */
    public static String describe() {
        if (!isAvailable()) {
            return "Metal indisponible: " + MetalBridge.unavailabilityReason();
        }
        String name = MetalBridge.defaultDeviceName();
        return "Metal: " + (name != null ? name : "GPU par défaut");
    }

    /**
     * Rend la scène sur le GPU.
     * @return Image rendue
     * @throws MetalException si la scène sort du domaine du kernel ou si le GPU échoue
     */
    public BufferedImage render() {
        MetalScene data = new MetalScene(scene);
        int width = data.width();
        int height = data.height();
        float[] pixels = new float[width * height * 3];

        MemorySegment context = MetalBridge.create(loadShaderSource());
        try {
            MetalBridge.render(context, data, pixels);
        } finally {
            MetalBridge.release(context);
        }

        return toImage(pixels, width, height);
    }

    /**
     * Convertit le tampon RGB flottant en image, en inversant l'axe vertical
     * comme le fait le rendu CPU.
     * @param pixels Tampon RGB, trois flottants par pixel
     * @param width Largeur de l'image
     * @param height Hauteur de l'image
     * @return Image RGB
     */
    private static BufferedImage toImage(float[] pixels, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int base = (j * width + i) * 3;
                int red = Math.round(pixels[base] * 255f);
                int green = Math.round(pixels[base + 1] * 255f);
                int blue = Math.round(pixels[base + 2] * 255f);
                image.setRGB(i, height - 1 - j,
                        ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));
            }
        }
        return image;
    }

    /**
     * Charge la source du kernel depuis les ressources.
     * @return Source Metal Shading Language
     * @throws MetalException si la ressource est absente ou illisible
     */
    private static String loadShaderSource() {
        try (InputStream stream = MetalRayTracer.class.getResourceAsStream(SHADER_RESOURCE)) {
            if (stream == null) {
                throw new MetalException("ressource " + SHADER_RESOURCE + " absente du classpath");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MetalException("lecture de " + SHADER_RESOURCE + " impossible", e);
        }
    }
}
