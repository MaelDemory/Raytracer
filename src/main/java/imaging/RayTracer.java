/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package imaging;

import geometry.*;
import raytracer.AbstractLight;
import raytracer.Material;
import raytracer.Scene;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Classe principale du moteur de rendu par lancer de rayons.
 */
public class RayTracer {
    /**
     * Scène à rendre
     */
    private final Scene scene;
    /** Largeur de l'image en pixels */
    private final int width;
    /** Hauteur de l'image en pixels */
    private final int height;

    /**
     * Base orthonormée de la caméra
     */
    private final Orthonormal orthonormal;

    /**
     * Largeur d'un pixel en unités de la scène
     */
    private final double pixelWidth;

    /**
     * Hauteur d'un pixel en unités de la scène
     */
    private final double pixelHeight;

    /**
     * Cache de mémorisation des couleurs de pixels pour éviter les recalculs.
     */
    private final AtomicReferenceArray<Color> pixelCache;

    /**
     * Registre partagé de caches par scène (clé faible pour éviter les fuites).
     */
    private static final Map<Scene, AtomicReferenceArray<Color>> CACHE_REGISTRY =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Epsilon pour éviter l'auto-intersection
     */
    private static final double EPSILON = 1e-5;

    /**
     * Constructeur du RayTracer
     * @param scene Scène à rendre
     */
    public RayTracer(Scene scene) {
        this.scene = scene;
        this.width = scene.getWidth();
        this.height = scene.getHeight();
        this.orthonormal = new Orthonormal(
                scene.getCamera().getLookFrom(),
                scene.getCamera().getLookAt(),
                scene.getCamera().getUp()
        );
        double fovRadians = Math.toRadians(scene.getCamera().getFov());
        double viewportHeight = 2.0 * Math.tan(fovRadians / 2.0);
        double aspectRatio = (double) scene.getWidth() / scene.getHeight();
        double viewportWidth = viewportHeight * aspectRatio;
        this.pixelHeight = viewportHeight / scene.getHeight();
        this.pixelWidth = viewportWidth / scene.getWidth();
        this.pixelCache = obtainCache(scene, this.width * this.height);
    }

    /**
     * Obtient le cache de couleurs pour la scène donnée, en le créant si nécessaire.
     * @param scene
     * @param size
     * @return
     */
    private static AtomicReferenceArray<Color> obtainCache(Scene scene, int size) {
        synchronized (CACHE_REGISTRY) {
            AtomicReferenceArray<Color> cache = CACHE_REGISTRY.get(scene);
            if (cache == null || cache.length() != size) {
                cache = new AtomicReferenceArray<>(size);
                CACHE_REGISTRY.put(scene, cache);
            }
            return cache;
        }
    }

    /**
     * Vide le cache associé à une scène (à appeler si la scène est modifiée).
     * @param scene scène dont il faut invalider le cache
     */
    public static void clearCache(Scene scene) {
        if (scene == null) {
            return;
        }
        synchronized (CACHE_REGISTRY) {
            CACHE_REGISTRY.remove(scene);
        }
    }

    /**
     * Vide tous les caches connus.
     */
    public static void clearAllCaches() {
        synchronized (CACHE_REGISTRY) {
            CACHE_REGISTRY.clear();
        }
    }

    /**
     * Calcule la couleur du pixel (i, j) en lançant un rayon dans la scène.
     * @param i Indice horizontal du pixel
     * @param j Indice vertical du pixel
     * @return Couleur du pixel
     */
    public Color getPixelColor(int i, int j) {
        if (i < 0 || i >= width || j < 0 || j >= height) {
            throw new IllegalArgumentException("Pixel hors de l'image: (" + i + "," + j + ")");
        }
        int index = j * width + i;
        Color cached = pixelCache.get(index);
        if (cached != null) {
            return cached;
        }
        Ray ray = computeRay(i, j);
        Color computed = traceRay(ray, scene.getMaxDepth());
        if (!pixelCache.compareAndSet(index, null, computed)) {
            // Un autre thread a déjà stocké la valeur, on récupère celle du cache
            return pixelCache.get(index);
        }
        return computed;
    }

    /**
     * Lance un rayon dans la scène et calcule la couleur résultante.
     * @param ray
     * @param depth
     * @return Color du point d'intersection
     */
    private Color traceRay(Ray ray, int depth) {
        if (depth <= 0) return scene.getAmbient();

        Optional<Intersection> opt = scene.findClosestIntersection(ray);
        if (opt.isEmpty()) return new Color(0, 0, 0);

        Intersection hit = opt.get();
        Material mat = hit.getShape().getMaterial();

        Color result = mat.getAmbient();

        Vector eyeDir = ray.getDirection().scalarMultiply(-1).normalize();

        for (AbstractLight light : scene.getLights()) {
            Vector lightDir = light.getDirectionFrom(hit.getPoint());
            double maxDist = light.getMaxDistance(hit.getPoint());
            if (!scene.isInShadow(offsetPoint(hit.getPoint(), hit.getNormal()), lightDir, maxDist)) {
                result = result.add(hit.computeColor(light, eyeDir));
            }
        }

        Color specular = mat.getSpecular();
        if (depth > 1 && (specular.getR() > 0 || specular.getG() > 0 || specular.getB() > 0)) {
            Vector reflectDir = calculateReflection(ray.getDirection(), hit.getNormal()).normalize();
            Ray reflectRay = new Ray(offsetPoint(hit.getPoint(), hit.getNormal()), reflectDir);
            Color reflected = traceRay(reflectRay, depth - 1);
            result = result.add(specular.schurMultiply(reflected));
        }

        return new Color(result.getR(), result.getG(), result.getB());
    }

    /**
     * Décale un point le long d'un vecteur normalisé pour éviter l'auto-intersection.
     * @param p Point à décaler
     * @param n Vecteur normalisé
     * @return Point décalé
     */
    private Point offsetPoint(Point p, Vector n) {
        return new Point(
                p.getX() + n.getX() * EPSILON,
                p.getY() + n.getY() * EPSILON,
                p.getZ() + n.getZ() * EPSILON
        );
    }

    /**
     * Calcule le vecteur réfléchi à partir d'un vecteur d'entrée et d'un vecteur normal.
     * @param in
     * @param normal
     * @return Vector réfléchi
     */
    private Vector calculateReflection(Vector in, Vector normal) {
        double dot = in.scalarProduct(normal);
        return in.add(normal.scalarMultiply(-2.0 * dot));
    }

    /**
     * Calcule le rayon partant de la caméra à travers le pixel (i, j).
     * @param i Indice horizontal du pixel
     * @param j Indice vertical du pixel
     * @return Ray
     */
    private Ray computeRay(int i, int j) {
        double a = pixelWidth * (i - scene.getWidth() / 2.0 + 0.5);
        double b = pixelHeight * (j - scene.getHeight() / 2.0 + 0.5);
        Vector dir = new Vector(
                orthonormal.getU().getX() * a + orthonormal.getV().getX() * b - orthonormal.getW().getX(),
                orthonormal.getU().getY() * a + orthonormal.getV().getY() * b - orthonormal.getW().getY(),
                orthonormal.getU().getZ() * a + orthonormal.getV().getZ() * b - orthonormal.getW().getZ()
        ).normalize();
        return new Ray(scene.getCamera().getLookFrom(), dir);
    }
}
