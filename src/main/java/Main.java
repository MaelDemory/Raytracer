/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

import imaging.ImageRenderer;
import parser.SceneFileParser;
import raytracer.Scene;

import java.io.IOException;

/**
 * Classe principale pour exécuter le rendu de la scène.
 */
public class Main {

    static {
        System.setProperty("org.lwjgl.system.stackSize", "2048");
        String lwjglPath = System.getProperty("org.lwjgl.librarypath");
        if (lwjglPath == null || lwjglPath.isEmpty()) {
            String[] vulkanPaths = {
                "/opt/homebrew/lib",
                "/usr/local/lib",
                System.getenv("VULKAN_SDK") != null ? System.getenv("VULKAN_SDK") + "/lib" : null
            };
            for (String path : vulkanPaths) {
                if (path != null && new java.io.File(path, "libvulkan.1.dylib").exists()) {
                    System.setProperty("org.lwjgl.librarypath", path);
                    break;
                }
            }
        }
    }

    /**
     * Point d'entrée principal de l'application.
     * @param args Arguments de la ligne de commande : args[0] = chemin vers le fichier de scène
     */
    public static void main(String[] args) {
        try {
            String scenePath = args.length > 0
                ? args[0]
                : "src/main/resources/scenes/scenes/scene4.scene";
            Scene scene = SceneFileParser.parse(scenePath);

            ImageRenderer renderer = new ImageRenderer();
            ImageRenderer.RenderReport report = renderer.render(scene);

            System.out.printf("Rendu terminé en %d ms -> %s%n", report.getDurationMillis(),
                report.getOutputFile().getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture du fichier : " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur dans le fichier de scène : " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
