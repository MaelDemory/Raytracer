/**
 * Projet POO Ray Tracing - GPU Acceleration
 *
 * (c) 2025 Maël DEMORY
 */

package imaging.gpu;

import com.aparapi.device.Device;
import com.aparapi.device.OpenCLDevice;
import raytracer.Scene;

import java.awt.image.BufferedImage;

/**
 * Ray Tracer GPU utilisant Aparapi/OpenCL.
 * Cette classe fournit le rendu accéléré par GPU pour les scènes de ray tracing.
 */
public class GPURayTracer {
    
    private final Scene scene;
    private final GPUSceneData gpuData;
    
    /**
     * Construit un GPURayTracer pour la scène donnée.
     * @param scene La scène à rendre
     */
    public GPURayTracer(Scene scene) {
        this.scene = scene;
        this.gpuData = new GPUSceneData(scene);
    }
    
    /**
     * Vérifie si le rendu GPU est disponible sur ce système.
     * @return true si le GPU est disponible
     */
    public static boolean isGPUAvailable() {
        try {
            Device best = Device.best();
            return best != null && best instanceof OpenCLDevice;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Retourne des informations sur le GPU disponible.
     * @return Description du GPU ou message d'erreur
     */
    public static String getGPUInfo() {
        try {
            Device best = Device.best();
            if (best != null && best instanceof OpenCLDevice) {
                OpenCLDevice oclDevice = (OpenCLDevice) best;
                return String.format("GPU: %s (OpenCL %s)", 
                    oclDevice.getName(), 
                    oclDevice.getOpenCLPlatform().getVersion());
            }
            return "Aucun GPU OpenCL disponible, utilisation du CPU";
        } catch (Exception e) {
            return "Erreur lors de la détection du GPU: " + e.getMessage();
        }
    }
    
    /**
     * Rend la scène en utilisant le GPU.
     * @return Image rendue
     */
    public BufferedImage render() {
        GPURayTracerKernel kernel = new GPURayTracerKernel(gpuData);
        
        try {
            System.out.println("Initialisation GPU...");
            System.out.print("Rendu GPU en cours");
            System.out.flush();
            
            // Exécuter le kernel
            int[] pixels = kernel.execute();
            
            System.out.println(" [100.0%] ✓");
            System.out.println("Copie des pixels depuis le GPU...");
            
            // Créer l'image
            int width = kernel.getWidth();
            int height = kernel.getHeight();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            
            long totalPixels = (long) width * height;
            long nextThreshold = totalPixels / 1000; // 0.1%
            long pixelCount = 0;
            int lastPercent = -1;
            
            // Copier les pixels (avec inversion Y pour correspondre au rendu CPU)
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    int srcIndex = j * width + i;
                    image.setRGB(i, height - 1 - j, pixels[srcIndex]);
                    
                    pixelCount++;
                    if (pixelCount >= nextThreshold) {
                        int percent = (int) ((pixelCount * 1000) / totalPixels);
                        if (percent != lastPercent) {
                            System.out.printf("\rCopie des pixels: %.1f%%", percent / 10.0);
                            System.out.flush();
                            lastPercent = percent;
                        }
                        nextThreshold = ((percent + 1) * totalPixels) / 1000;
                    }
                }
            }
            
            System.out.println("\rCopie des pixels: 100.0% ✓");
            return image;
        } finally {
            kernel.dispose();
        }
    }
    
    /**
     * Rend la scène et retourne les pixels RGB directement.
     * @return Tableau de pixels RGB (format 0xRRGGBB)
     */
    public int[] renderToPixels() {
        GPURayTracerKernel kernel = new GPURayTracerKernel(gpuData);
        
        try {
            return kernel.execute();
        } finally {
            kernel.dispose();
        }
    }
    
    /**
     * Retourne la largeur de l'image.
     */
    public int getWidth() {
        return gpuData.width;
    }
    
    /**
     * Retourne la hauteur de l'image.
     */
    public int getHeight() {
        return gpuData.height;
    }
}
