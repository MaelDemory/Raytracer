/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package raytracer;

import geometry.Intersection;
import geometry.Point;
import geometry.Ray;
import geometry.Shape;
import geometry.Vector;
import imaging.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Représente une scène 3D contenant des formes, des lumières et une caméra.
 */
public class Scene {
    /**
     * Largeur de l'image de la scène.
     */
    private int width;

    /**
     * Hauteur de l'image de la scène.
     */
    private int height;

    /**
     * Caméra de la scène.
     */
    private Camera camera;

    /**
     * Nom du fichier de sortie pour l'image rendue.
     */
    private String output = "output.png";

    /**
     * Couleur ambiante de la scène.
     */
    private Color ambient = new Color();

    /**
     * Liste des lumières dans la scène.
     */
    private List<AbstractLight> lights = new ArrayList<>();
    /**
     * Liste des formes dans la scène.
     */
    private List<Shape> shapes = new ArrayList<>();
    /**
     * Arbre BVH construit à partir des formes bornées.
     */
    private BvhNode bvhRoot;
    /**
     * Formes sans boîte englobante (infinies) traitées à part.
     */
    private List<Shape> unboundedShapes = new ArrayList<>();
    /**
     * Profondeur maximale de récursion pour le lancer de rayons.
     */
    private int maxDepth = 1;

    /**
     * Constructeur de la scène avec les paramètres spécifiés.
     */
    public Scene(int width, int height, Camera camera, String output, Color ambient,
         List<AbstractLight> lights, List<Shape> shapes) {
        this.width = width;
        this.height = height;
        this.camera = camera;
        this.output = output;
        this.ambient = ambient;
        this.lights = new ArrayList<>(lights);
        this.shapes = new ArrayList<>(shapes);
        rebuildAccelerationStructure();
    }

    /**
     * Constructeur par défaut de la scène.
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * Définit la hauteur de l'image de la scène.
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Retourne la largeur de l'image de la scène.
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Définit la largeur de l'image de la scène.
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Retourne la caméra de la scène.
     */
    public Camera getCamera() {
        return this.camera;
    }

    /**
     * Définit la caméra de la scène.
     */
    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    /**
     * Retourne le nom du fichier de sortie pour l'image rendue.
     */
    public String getOutput() {
        return this.output;
    }

    /**
     * Définit le nom du fichier de sortie pour l'image rendue.
     */
    public void setOutput(String output) {
        this.output = output;
    }

    /**
     * Retourne la couleur ambiante de la scène.
     */
    public Color getAmbient() {
        return this.ambient;
    }

    /**
     * Définit la couleur ambiante de la scène.
     */
    public void setAmbient(Color ambient) {
        this.ambient = ambient;
    }

    /**
     * Retourne la liste des lumières dans la scène.
     */
    public List<AbstractLight> getLights() {
        return this.lights;
    }

    /**
     * Définit la liste des lumières dans la scène.
     */
    public void setLights(List<AbstractLight> lights) {
        this.lights = new ArrayList<>(lights);
    }

    /**
     * Retourne la liste des formes dans la scène.
     */
    public List<Shape> getShapes() {
        return this.shapes;
    }

    /**
     * Définit la liste des formes dans la scène.
     */
    public void setShapes(List<Shape> shapes) {
        this.shapes = new ArrayList<>(shapes);
        rebuildAccelerationStructure();
    }

    /**
     * Retourne la profondeur maximale de récursion pour le lancer de rayons.
     */
    public int getMaxDepth() {
        return this.maxDepth;
    }

    /**
     * Définit la profondeur maximale de récursion pour le lancer de rayons.
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Retourne une représentation textuelle de la scène.
     */
    @Override
    public String toString() {
        return "Scene{" +
                "width=" + width +
                ", height=" + height +
                ", camera=" + camera.toString() +
                ", output='" + output + '\'' +
                ", ambient=" + ambient.toString() +
                ", lights=" + lights.toString() +
                ", shapes=" + shapes.toString() +
                '}';
    }

    /**
     * Trouve l'intersection la plus proche entre un rayon et les formes de la scène.
     *
     * @param ray Le rayon à tester pour les intersections.
     * @return Optional<Intersection> L'intersection la plus proche, ou vide si aucune intersection n'est trouvée.
     */
    public Optional<Intersection> findClosestIntersection(Ray ray) {
        Optional<Intersection> closest = Optional.empty();
        double minDistance = Double.POSITIVE_INFINITY;

        if (bvhRoot != null) {
            closest = bvhRoot.findClosest(ray, Double.POSITIVE_INFINITY);
            if (closest.isPresent()) {
                minDistance = closest.get().getDistance();
            }
        }

        for (Shape shape : unboundedShapes) {
            Optional<Intersection> intersection = shape.intersect(ray);
            if (intersection.isPresent()) {
                double distance = intersection.get().getDistance();
                if (distance > 0 && distance < minDistance) {
                    minDistance = distance;
                    closest = intersection;
                }
            }
        }

        return closest;
    }

    /**
     * Détermine si un point est dans l'ombre par rapport à une direction de lumière donnée.
     *
     * @param point Le point à tester.
     * @param lightDir La direction de la lumière.
     * @param maxDistance La distance maximale à considérer pour l'ombre.
     * @return boolean Vrai si le point est dans l'ombre, faux sinon.
     */
    public boolean isInShadow(Point point, Vector lightDir, double maxDistance) {
        final double EPSILON = 1e-6;
        Ray shadowRay = new Ray(point, lightDir);

        if (bvhRoot != null && bvhRoot.hasIntersection(shadowRay, maxDistance)) {
            return true;
        }

        for (Shape shape : unboundedShapes) {
            Optional<Intersection> intersection = shape.intersect(shadowRay);
            if (intersection.isPresent()) {
                double distance = intersection.get().getDistance();
                if (distance > EPSILON && distance < maxDistance) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reconstruit la structure d'accélération BVH à partir des formes de la scène.
     */
    private void rebuildAccelerationStructure() {
        List<Shape> bounded = new ArrayList<>();
        List<Shape> unbounded = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape.getBoundingBox().isPresent()) {
                bounded.add(shape);
            } else {
                unbounded.add(shape);
            }
        }
        this.unboundedShapes = unbounded;
        this.bvhRoot = BvhNode.build(bounded);
    }

    /**
     * Aplatis le BVH courant pour un envoi GPU en utilisant le mappeur fourni.
     * @param indexer stratégie de mapping forme -> (type, index)
     * @return liste de noeuds aplatis en ordre préfixe
     */
    public java.util.List<BvhNode.FlatNode> flattenBvh(BvhNode.ShapeIndexer indexer) {
        if (bvhRoot == null) {
            return java.util.Collections.emptyList();
        }
        java.util.List<BvhNode.FlatNode> out = new java.util.ArrayList<>();
        BvhNode.flatten(bvhRoot, indexer, out);
        return out;
    }
}
