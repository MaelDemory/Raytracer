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
     * Rend la scène en sélectionnant l'API graphique au lancement.
     *
     * <p>Les backends sont sondés dans l'ordre de préférence : l'API native de la
     * plateforme d'abord, puis l'API portable, puis le CPU. Un backend qui se
     * déclare indisponible, ou qui refuse la scène parce qu'elle sort de son
     * domaine, fait passer au suivant sans interrompre le rendu.</p>
     *
     * <p>La propriété système {@code raytracer.backend} force un chemin précis
     * ({@code metal}, {@code vulkan} ou {@code cpu}).</p>
     *
     * @param scene Scène à rendre
     * @return rapport contenant la durée d'exécution et le fichier généré
     * @throws IOException si le rendu échoue
     */
    public RenderReport render(Scene scene) throws IOException {
        long start = System.nanoTime();
        BufferedImage image = renderOnGpu(scene);

        if (image == null) {
            System.out.println("=== Rendu CPU parallèle ===");
            RayTracer rayTracer = new RayTracer(scene);
            image = new BufferedImage(scene.getWidth(), scene.getHeight(), BufferedImage.TYPE_INT_RGB);
            renderInParallel(scene, rayTracer, image);
        }

        long elapsed = System.nanoTime() - start;
        File outputFile = new File(scene.getOutput());
        ImageIO.write(image, "png", outputFile);
        return new RenderReport(image, TimeUnit.NANOSECONDS.toMillis(elapsed), outputFile);
    }

    /**
     * Essaie chaque backend accéléré dans l'ordre de préférence.
     * @param scene Scène à rendre
     * @return Image rendue, ou null si aucun backend n'a abouti
     */
    private BufferedImage renderOnGpu(Scene scene) {
        for (RenderBackend backend : RenderBackends.preferred()) {
            if (!backend.isAvailable()) {
                System.out.println("Backend " + backend.name() + " écarté: " + backend.describe());
                continue;
            }

            System.out.println("=== Rendu GPU (" + backend.name() + ") ===");
            System.out.println(backend.describe());
            try {
                return backend.render(scene);
            } catch (RuntimeException | LinkageError e) {
                // Scène hors domaine ou échec du périphérique : on essaie le
                // backend suivant plutôt que d'abandonner le rendu.
                System.out.println("Backend " + backend.name() + " a échoué (" + e.getMessage()
                        + "), passage au suivant.");
            }
        }
        return null;
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
