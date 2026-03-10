package geometry;

/**
 * Boîte englobante axis-aligned (AABB) utilisée par le BVH.
 */
public final class BoundingBox {
    /** Coordonnées minimales de la boîte englobante. */
    private final double minX;
    private final double minY;
    private final double minZ;
    /** Coordonnées maximales de la boîte englobante. */
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    /** Construit une boîte englobante à partir des coordonnées minimales et maximales.
     * @param minX Coordonnée minimale en x
     * @param minY Coordonnée minimale en y
     * @param minZ Coordonnée minimale en z
     * @param maxX Coordonnée maximale en x
     * @param maxY Coordonnée maximale en y
     * @param maxZ Coordonnée maximale en z
     */
    public BoundingBox(double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid bounding box extents");
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * Retourne les coordonnées minimales et maximales de la boîte englobante.
     */
    public double minX() { return minX; }
    public double minY() { return minY; }
    public double minZ() { return minZ; }
    public double maxX() { return maxX; }
    public double maxY() { return maxY; }
    public double maxZ() { return maxZ; }

    /**
     * Centre de la boîte englobante selon l'axe spécifié.
     * @param axis
     * @return
     */
    public double center(int axis) {
        return switch (axis) {
            case 0 -> 0.5 * (minX + maxX);
            case 1 -> 0.5 * (minY + maxY);
            default -> 0.5 * (minZ + maxZ);
        };
    }

    /**
     * Crée une boîte englobante entourant deux autres boîtes.
     * @param a Première boîte
     * @param b Deuxième boîte
     * @return Nouvelle boîte englobante
     */
    public static BoundingBox surrounding(BoundingBox a, BoundingBox b) {
        if (a == null) return b;
        if (b == null) return a;
        double minX = Math.min(a.minX, b.minX);
        double minY = Math.min(a.minY, b.minY);
        double minZ = Math.min(a.minZ, b.minZ);
        double maxX = Math.max(a.maxX, b.maxX);
        double maxY = Math.max(a.maxY, b.maxY);
        double maxZ = Math.max(a.maxZ, b.maxZ);
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Test d'intersection rayon/boîte via la méthode des tranches.
     */
    public boolean intersects(Ray ray, double tMin, double tMax) {
        double ox = ray.getOrigine().getX();
        double oy = ray.getOrigine().getY();
        double oz = ray.getOrigine().getZ();
        double dx = ray.getDirection().getX();
        double dy = ray.getDirection().getY();
        double dz = ray.getDirection().getZ();

        if (!intersectsAxis(ox, dx, minX, maxX, tMin, tMax)) {
            return false;
        }
        double[] range = updateRange(ox, dx, minX, maxX, tMin, tMax);
        tMin = range[0];
        tMax = range[1];

        if (!intersectsAxis(oy, dy, minY, maxY, tMin, tMax)) {
            return false;
        }
        range = updateRange(oy, dy, minY, maxY, tMin, tMax);
        tMin = range[0];
        tMax = range[1];

        if (!intersectsAxis(oz, dz, minZ, maxZ, tMin, tMax)) {
            return false;
        }
        range = updateRange(oz, dz, minZ, maxZ, tMin, tMax);
        tMin = range[0];
        tMax = range[1];

        return tMax > tMin;
    }

    /**
     * Test d'intersection le long d'un axe.
     * @param origin
     * @param dir
     * @param min
     * @param max
     * @param tMin
     * @param tMax
     * @return
     */
    private boolean intersectsAxis(double origin, double dir, double min, double max, double tMin, double tMax) {
        if (dir == 0.0) {
            return origin >= min && origin <= max;
        }
        double invDir = 1.0 / dir;
        double t0 = (min - origin) * invDir;
        double t1 = (max - origin) * invDir;
        if (invDir < 0.0) {
            double tmp = t0;
            t0 = t1;
            t1 = tmp;
        }
        double newMin = Math.max(t0, tMin);
        double newMax = Math.min(t1, tMax);
        return newMax > newMin;
    }

    /**
     * Met à jour la plage d'intersection le long d'un axe.
     * @param origin
     * @param dir
     * @param min
     * @param max
     * @param tMin
     * @param tMax
     * @return
     */
    private double[] updateRange(double origin, double dir, double min, double max, double tMin, double tMax) {
        if (dir == 0.0) {
            return new double[] {tMin, tMax};
        }
        double invDir = 1.0 / dir;
        double t0 = (min - origin) * invDir;
        double t1 = (max - origin) * invDir;
        if (invDir < 0.0) {
            double tmp = t0;
            t0 = t1;
            t1 = tmp;
        }
        double newMin = Math.max(t0, tMin);
        double newMax = Math.min(t1, tMax);
        return new double[] {newMin, newMax};
    }
}
