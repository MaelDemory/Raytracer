/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package parser;

import geometry.*;
import imaging.Color;
import raytracer.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe pour parser un fichier de scène et créer un objet Scene.
 */
public class SceneFileParser {

    /**
     * Parse le fichier de scène donné et retourne un objet Scene.
     * @param filePath Chemin vers le fichier de scène.
     * @return Scene
     * @throws IOException
     */
    public static Scene parse(String filePath) throws IOException {
        int width = 0;
        int height = 0;
        Camera camera = null;
        String output = "output.png";
        Color ambient = new Color();
        Color currentDiffuse = new Color();
        Color currentSpecular = new Color();
        double currentShininess = 1;
        List<AbstractLight> lights = new ArrayList<>();
        List<Shape> shapes = new ArrayList<>();
        List<Point> vertices = new ArrayList<>();
        int maxVerts = 0;
        int maxDepth = 1;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                String command = parts[0];

                switch (command) {
                    case "size":
                        width = Integer.parseInt(parts[1]);
                        height = Integer.parseInt(parts[2]);
                        if (width == 0 || height == 0) {
                            throw new IllegalArgumentException("La taille de l'image (size) est obligatoire");
                        }
                        break;

                    case "output":
                        output = parts[1];
                        break;

                    case "camera":
                        Point lookFrom = new Point(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        Point lookAt = new Point(
                                Double.parseDouble(parts[4]),
                                Double.parseDouble(parts[5]),
                                Double.parseDouble(parts[6]));
                        Vector up = new Vector(
                                Double.parseDouble(parts[7]),
                                Double.parseDouble(parts[8]),
                                Double.parseDouble(parts[9]));
                        int fov = Integer.parseInt(parts[10]);
                        camera = new Camera(lookFrom, lookAt, up, fov);
                        break;

                    case "ambient":
                        ambient = new Color(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        break;

                    case "diffuse":
                        currentDiffuse = new Color(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        break;

                    case "specular":
                        currentSpecular = new Color(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        break;

                    case "shininess":
                        currentShininess = Double.parseDouble(parts[1]);
                        break;

                    case "directional":
                        Vector direction = new Vector(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        Color dirColor = new Color(
                                Double.parseDouble(parts[4]),
                                Double.parseDouble(parts[5]),
                                Double.parseDouble(parts[6]));
                        lights.add(new DirectionalLight(direction, dirColor));
                        break;

                    case "point":
                        Point position = new Point(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        Color pointColor = new Color(
                                Double.parseDouble(parts[4]),
                                Double.parseDouble(parts[5]),
                                Double.parseDouble(parts[6]));
                        lights.add(new PointLight(position, pointColor));
                        break;

                    case "sphere":
                        Sphere sphere = new Sphere(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]),
                                Double.parseDouble(parts[4]));
                        sphere.setMaterial(new Material(ambient, currentDiffuse, currentSpecular, currentShininess));
                        shapes.add(sphere);
                        break;

                    case "maxverts":
                        maxVerts = Integer.parseInt(parts[1]);
                        break;

                    case "vertex":
                        Point vertex = new Point(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        vertices.add(vertex);
                        break;

                    case "tri":
                        int idx1 = Integer.parseInt(parts[1]);
                        int idx2 = Integer.parseInt(parts[2]);
                        int idx3 = Integer.parseInt(parts[3]);
                        if (idx1 >= maxVerts || idx2 >= maxVerts || idx3 >= maxVerts) {
                            throw new IllegalArgumentException("Les indices du triangle doivent être < maxverts");
                        }
                        Triangle triangle = new Triangle(
                                vertices.get(idx1),
                                vertices.get(idx2),
                                vertices.get(idx3));
                        triangle.setMaterial(new Material(ambient, currentDiffuse, currentSpecular, currentShininess));
                        shapes.add(triangle);
                        break;

                    case "triangle":
                        Point triA = new Point(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        Point triB = new Point(
                                Double.parseDouble(parts[4]),
                                Double.parseDouble(parts[5]),
                                Double.parseDouble(parts[6]));
                        Point triC = new Point(
                                Double.parseDouble(parts[7]),
                                Double.parseDouble(parts[8]),
                                Double.parseDouble(parts[9]));
                        Triangle triangleDirect = new Triangle(triA, triB, triC);
                        triangleDirect.setMaterial(new Material(ambient, currentDiffuse, currentSpecular, currentShininess));
                        shapes.add(triangleDirect);
                        break;

                    case "plane":
                        Point planePoint = new Point(
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        Vector normal = new Vector(
                                Double.parseDouble(parts[4]),
                                Double.parseDouble(parts[5]),
                                Double.parseDouble(parts[6]));
                        Plane plane = new Plane(planePoint, normal);
                        plane.setMaterial(new Material(ambient, currentDiffuse, currentSpecular, currentShininess));
                        shapes.add(plane);
                        break;

                    case "maxdepth":
                        maxDepth = Integer.parseInt(parts[1]);
                        break;

                    default:
                        throw new IllegalArgumentException("Commande inconnue : " + command);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        Scene scene = new Scene(width, height, camera, output, ambient, lights, shapes);
        scene.setMaxDepth(maxDepth);
        return scene;
    }
}
