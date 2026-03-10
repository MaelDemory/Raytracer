/**
 * Projet POO Ray Tracing - Optimized Parallel Rendering
 *
 * (c) 2025 Maël DEMORY
 */

package imaging.gpu;

import geometry.*;
import imaging.Color;
import imaging.RayTracer;
import raytracer.Scene;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Raytracer hautement optimisé pour CPU multi-coeurs (Apple M3).
 * Utilise des Virtual Threads (Java 21+) et une granularité fine pour maximiser le parallélisme.
 */
public class OptimizedCPURayTracer {
    
    private final Scene scene;
    private final int width;
    private final int height;
    
    // Taille des tuiles pour le rendu par blocs
    private static final int TILE_SIZE = 16;
    
    /**
     * Construit un OptimizedCPURayTracer pour la scène donnée.
     * @param scene La scène à rendre
     */
    public OptimizedCPURayTracer(Scene scene) {
        this.scene = scene;
        this.width = scene.getWidth();
        this.height = scene.getHeight();
    }
    
    /**
     * Rend la scène avec un parallélisme par tuiles.
     * @return Image rendue
     */
    public BufferedImage render() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        RayTracer rayTracer = new RayTracer(scene);
        
        // Calculer le nombre de tuiles
        int tilesX = (width + TILE_SIZE - 1) / TILE_SIZE;
        int tilesY = (height + TILE_SIZE - 1) / TILE_SIZE;
        int totalTiles = tilesX * tilesY;
        
        AtomicInteger completedTiles = new AtomicInteger(0);
        AtomicInteger lastReported = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(totalTiles);
        
        // Utiliser un pool de threads optimisé pour le nombre de cœurs
        int threadCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // Soumettre les tuiles dans un ordre qui favorise la cohérence du cache
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                final int tileX = tx;
                final int tileY = ty;
                
                executor.submit(() -> {
                    try {
                        renderTile(rayTracer, image, tileX, tileY);
                    } finally {
                        int completed = completedTiles.incrementAndGet();
                        reportProgress(completed, totalTiles, lastReported);
                        latch.countDown();
                    }
                });
            }
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("\rProgression: 100.0%");
        
        return image;
    }
    
    /**
     * Rend la scène en utilisant des Virtual Threads (Java 21+).
     * Plus efficace pour les scènes avec beaucoup de pixels.
     * @return Image rendue
     */
    public BufferedImage renderWithVirtualThreads() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        RayTracer rayTracer = new RayTracer(scene);
        
        int totalPixels = width * height;
        AtomicInteger processedPixels = new AtomicInteger(0);
        AtomicInteger lastReported = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(height);
        
        // Utiliser Virtual Threads pour un parallélisme massif
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int j = 0; j < height; j++) {
                final int row = j;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < width; i++) {
                            Color color = rayTracer.getPixelColor(i, row);
                            synchronized (image) {
                                image.setRGB(i, height - 1 - row, color.toRGB());
                            }
                            int processed = processedPixels.incrementAndGet();
                            reportProgress(processed, totalPixels, lastReported);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("\rProgression: 100.0%");
        return image;
    }
    
    /**
     * Rend une tuile de l'image.
     */
    private void renderTile(RayTracer rayTracer, BufferedImage image, int tileX, int tileY) {
        int startX = tileX * TILE_SIZE;
        int startY = tileY * TILE_SIZE;
        int endX = Math.min(startX + TILE_SIZE, width);
        int endY = Math.min(startY + TILE_SIZE, height);
        
        // Buffer local pour les couleurs de la tuile
        int[][] tileColors = new int[endY - startY][endX - startX];
        
        for (int j = startY; j < endY; j++) {
            for (int i = startX; i < endX; i++) {
                Color color = rayTracer.getPixelColor(i, j);
                tileColors[j - startY][i - startX] = color.toRGB();
            }
        }
        
        // Écrire la tuile dans l'image (synchronisé pour éviter les conflits)
        synchronized (image) {
            for (int j = startY; j < endY; j++) {
                for (int i = startX; i < endX; i++) {
                    image.setRGB(i, height - 1 - j, tileColors[j - startY][i - startX]);
                }
            }
        }
    }
    
    /**
     * Rapporte la progression du rendu.
     */
    private void reportProgress(int processed, int total, AtomicInteger lastReportedTenths) {
        if (total <= 0) return;
        
        double percentage = (processed * 100.0) / total;
        int tenths = Math.min(1000, (int) Math.floor(percentage * 10.0 + 1e-9));
        int last = lastReportedTenths.get();
        
        if (tenths > last && lastReportedTenths.compareAndSet(last, tenths)) {
            double display = tenths / 10.0;
            System.out.printf("\rProgression: %.1f%%", display);
            System.out.flush();
        }
    }
    
    /**
     * Retourne des informations sur les capacités d'optimisation.
     */
    public static String getOptimizationInfo() {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String javaVersion = System.getProperty("java.version");
        boolean hasVirtualThreads = false;
        
        try {
            Class.forName("java.lang.VirtualThread");
            hasVirtualThreads = true;
        } catch (ClassNotFoundException e) {
            // Virtual threads not available
        }
        
        return String.format(
            "CPU: %d cœurs | Mémoire max: %d MB | Java: %s | Virtual Threads: %s",
            cores, maxMemory, javaVersion, hasVirtualThreads ? "Oui" : "Non"
        );
    }
}
