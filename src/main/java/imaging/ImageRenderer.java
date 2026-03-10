/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package imaging;

import raytracer.Scene;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe responsable du rendu d'une scène en une image.
 * Détecte automatiquement la meilleure méthode de rendu (Vulkan GPU ou CPU parallèle).
 */
public class ImageRenderer {

    /**
     * Rapport de rendu contenant la durée et le fichier de sortie.
     */
    public static class RenderReport {
        private final long durationMillis;
        private final File outputFile;
        private final BufferedImage image;

        private RenderReport(BufferedImage image, long durationMillis, File outputFile) {
            this.image = image;
            this.durationMillis = durationMillis;
            this.outputFile = outputFile;
        }

        public long getDurationMillis() {
            return durationMillis;
        }

        public File getOutputFile() {
            return outputFile;
        }

        public BufferedImage getImage() {
            return image;
        }
    }

    /**
     * Rend la scène en choisissant automatiquement la meilleure méthode disponible.
     * Vulkan est préféré s'il est disponible, sinon le rendu CPU parallèle est utilisé.
     *
     * @param scene Scène à rendre
     * @return rapport contenant la durée d'exécution et le fichier généré
     * @throws IOException si le rendu échoue
     */
    public RenderReport render(Scene scene) throws IOException {
        BufferedImage image;
        String suffix;
        long start = System.nanoTime();

        if (imaging.vulkan.VulkanRayTracer.isAvailable()) {
            System.out.println("=== Rendu GPU (Vulkan compute) ===");
            image = renderOnVulkan(scene);
            suffix = "";
        } else {
            System.out.println("=== Vulkan non disponible, rendu CPU parallèle ===");
            RayTracer rayTracer = new RayTracer(scene);
            image = new BufferedImage(scene.getWidth(), scene.getHeight(), BufferedImage.TYPE_INT_RGB);
            renderInParallel(scene, rayTracer, image);
            suffix = "";
        }

        long elapsed = System.nanoTime() - start;
        File outputFile = new File(scene.getOutput());
        ImageIO.write(image, "png", outputFile);
        return new RenderReport(image, TimeUnit.NANOSECONDS.toMillis(elapsed), outputFile);
    }

    private BufferedImage renderOnVulkan(Scene scene) {
        System.out.println("Initialisation Vulkan...");
        var vkTracer = new imaging.vulkan.VulkanRayTracer(scene);
        System.out.println("Rendu Vulkan (BVH) en cours...");
        return vkTracer.render();
    }

    private void renderInParallel(Scene scene, RayTracer rayTracer, BufferedImage image) throws IOException {
        int width = scene.getWidth();
        int height = scene.getHeight();
        int totalPixels = width * height;
        AtomicInteger processedPixels = new AtomicInteger(0);
        AtomicInteger lastReported = new AtomicInteger(-1);
        int threadCount = Math.min(height, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>(height);

        for (int j = 0; j < height; j++) {
            final int row = j;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < width; i++) {
                    Color color = rayTracer.getPixelColor(i, row);
                    image.setRGB(i, height - 1 - row, color.toRGB());
                    int processed = processedPixels.incrementAndGet();
                    reportProgress(processed, totalPixels, lastReported);
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                throw new IOException("Rendering interrupted", e);
            } catch (ExecutionException e) {
                executor.shutdownNow();
                throw new IOException("Rendering failed", e.getCause());
            }
        }

        executor.shutdown();
        System.out.println("\rProgression: 100.0%");
    }

    private void reportProgress(int processed, int total, AtomicInteger lastReportedTenths) {
        if (total <= 0) {
            return;
        }
        double percentage = (processed * 100.0) / total;
        int tenths = Math.min(1000, (int) Math.floor(percentage * 10.0 + 1e-9));
        int last = lastReportedTenths.get();
        if (tenths > last && lastReportedTenths.compareAndSet(last, tenths)) {
            double display = tenths / 10.0;
            System.out.printf("\rProgression: %.1f%%", display);
            System.out.flush();
        }
    }
}
