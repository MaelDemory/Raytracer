/**
 * Projet POO Ray Tracing - GPU Acceleration
 *
 * (c) 2025 Maël DEMORY
 */

package imaging.gpu;

import geometry.*;
import imaging.Color;
import raytracer.*;

import java.util.List;

/**
 * Classe pour sérialiser les données de scène en buffers GPU compatibles.
 * Les données sont stockées dans des tableaux de primitives pour une copie efficace vers le GPU.
 */
public class GPUSceneData {
    
    // Types de formes
    public static final int SHAPE_SPHERE = 0;
    public static final int SHAPE_TRIANGLE = 1;
    public static final int SHAPE_PLANE = 2;
    
    // Types de lumières
    public static final int LIGHT_DIRECTIONAL = 0;
    public static final int LIGHT_POINT = 1;
    
    // Données de la caméra (12 floats)
    // [lookFromX, lookFromY, lookFromZ, lookAtX, lookAtY, lookAtZ, upX, upY, upZ, fov, 0, 0]
    public final float[] camera;
    
    // Dimensions de l'image
    public final int width;
    public final int height;
    public final int maxDepth;
    
    // Couleur ambiante [R, G, B]
    public final float[] ambient;
    
    // Base orthonormée précalculée pour la caméra [u.x, u.y, u.z, v.x, v.y, v.z, w.x, w.y, w.z]
    public final float[] orthonormal;
    
    // Paramètres de pixel [pixelWidth, pixelHeight]
    public final float[] pixelParams;
    
    // Données des sphères: [centerX, centerY, centerZ, radius, matIdx, 0, 0, 0] par sphère (8 floats)
    public final float[] spheres;
    public final int sphereCount;
    
    // Données des triangles: [ax, ay, az, bx, by, bz, cx, cy, cz, matIdx, 0, 0] par triangle (12 floats)
    public final float[] triangles;
    public final int triangleCount;
    
    // Données des plans: [px, py, pz, nx, ny, nz, matIdx, 0] par plan (8 floats)
    public final float[] planes;
    public final int planeCount;
    
    // Données des matériaux: [ambR, ambG, ambB, diffR, diffG, diffB, specR, specG, specB, shininess, 0, 0] (12 floats)
    public final float[] materials;
    public final int materialCount;
    
    // Données des lumières directionnelles: [dirX, dirY, dirZ, colorR, colorG, colorB, 0, 0] (8 floats)
    public final float[] directionalLights;
    public final int directionalLightCount;
    
    // Données des lumières ponctuelles: [posX, posY, posZ, colorR, colorG, colorB, 0, 0] (8 floats)
    public final float[] pointLights;
    public final int pointLightCount;
    
    /**
     * Construit les données GPU à partir d'une scène.
     * @param scene La scène à sérialiser
     */
    public GPUSceneData(Scene scene) {
        this.width = scene.getWidth();
        this.height = scene.getHeight();
        this.maxDepth = scene.getMaxDepth();
        
        // Caméra
        Camera cam = scene.getCamera();
        this.camera = new float[] {
            (float) cam.getLookFrom().getX(),
            (float) cam.getLookFrom().getY(),
            (float) cam.getLookFrom().getZ(),
            (float) cam.getLookAt().getX(),
            (float) cam.getLookAt().getY(),
            (float) cam.getLookAt().getZ(),
            (float) cam.getUp().getX(),
            (float) cam.getUp().getY(),
            (float) cam.getUp().getZ(),
            (float) cam.getFov(),
            0f, 0f
        };
        
        // Couleur ambiante
        Color amb = scene.getAmbient();
        this.ambient = new float[] {
            (float) amb.getR(),
            (float) amb.getG(),
            (float) amb.getB()
        };
        
        // Calcul de la base orthonormée
        Orthonormal ortho = new Orthonormal(cam.getLookFrom(), cam.getLookAt(), cam.getUp());
        this.orthonormal = new float[] {
            (float) ortho.getU().getX(),
            (float) ortho.getU().getY(),
            (float) ortho.getU().getZ(),
            (float) ortho.getV().getX(),
            (float) ortho.getV().getY(),
            (float) ortho.getV().getZ(),
            (float) ortho.getW().getX(),
            (float) ortho.getW().getY(),
            (float) ortho.getW().getZ()
        };
        
        // Paramètres de pixel
        double fovRadians = Math.toRadians(cam.getFov());
        double viewportHeight = 2.0 * Math.tan(fovRadians / 2.0);
        double aspectRatio = (double) width / height;
        double viewportWidth = viewportHeight * aspectRatio;
        this.pixelParams = new float[] {
            (float) (viewportWidth / width),
            (float) (viewportHeight / height)
        };
        
        // Compter et sérialiser les formes
        List<Shape> shapes = scene.getShapes();
        int numSpheres = 0, numTriangles = 0, numPlanes = 0;
        
        for (Shape shape : shapes) {
            if (shape instanceof Sphere) numSpheres++;
            else if (shape instanceof Triangle) numTriangles++;
            else if (shape instanceof Plane) numPlanes++;
        }
        
        this.sphereCount = numSpheres;
        this.triangleCount = numTriangles;
        this.planeCount = numPlanes;
        this.materialCount = shapes.size();
        
        this.spheres = new float[Math.max(1, numSpheres * 8)];
        this.triangles = new float[Math.max(1, numTriangles * 12)];
        this.planes = new float[Math.max(1, numPlanes * 8)];
        this.materials = new float[Math.max(1, shapes.size() * 12)];
        
        int sphereIdx = 0, triangleIdx = 0, planeIdx = 0, matIdx = 0;
        
        for (Shape shape : shapes) {
            Material mat = shape.getMaterial();
            
            // Sérialiser le matériau
            int matOffset = matIdx * 12;
            materials[matOffset] = (float) mat.getAmbient().getR();
            materials[matOffset + 1] = (float) mat.getAmbient().getG();
            materials[matOffset + 2] = (float) mat.getAmbient().getB();
            materials[matOffset + 3] = (float) mat.getDiffuse().getR();
            materials[matOffset + 4] = (float) mat.getDiffuse().getG();
            materials[matOffset + 5] = (float) mat.getDiffuse().getB();
            materials[matOffset + 6] = (float) mat.getSpecular().getR();
            materials[matOffset + 7] = (float) mat.getSpecular().getG();
            materials[matOffset + 8] = (float) mat.getSpecular().getB();
            materials[matOffset + 9] = (float) mat.getShininess();
            materials[matOffset + 10] = 0f;
            materials[matOffset + 11] = 0f;
            
            if (shape instanceof Sphere) {
                Sphere sphere = (Sphere) shape;
                int offset = sphereIdx * 8;
                spheres[offset] = (float) sphere.getCenter().getX();
                spheres[offset + 1] = (float) sphere.getCenter().getY();
                spheres[offset + 2] = (float) sphere.getCenter().getZ();
                spheres[offset + 3] = (float) sphere.getRadius();
                spheres[offset + 4] = matIdx;
                spheres[offset + 5] = 0f;
                spheres[offset + 6] = 0f;
                spheres[offset + 7] = 0f;
                sphereIdx++;
            } else if (shape instanceof Triangle) {
                Triangle tri = (Triangle) shape;
                int offset = triangleIdx * 12;
                triangles[offset] = (float) tri.getA().getX();
                triangles[offset + 1] = (float) tri.getA().getY();
                triangles[offset + 2] = (float) tri.getA().getZ();
                triangles[offset + 3] = (float) tri.getB().getX();
                triangles[offset + 4] = (float) tri.getB().getY();
                triangles[offset + 5] = (float) tri.getB().getZ();
                triangles[offset + 6] = (float) tri.getC().getX();
                triangles[offset + 7] = (float) tri.getC().getY();
                triangles[offset + 8] = (float) tri.getC().getZ();
                triangles[offset + 9] = matIdx;
                triangles[offset + 10] = 0f;
                triangles[offset + 11] = 0f;
                triangleIdx++;
            } else if (shape instanceof Plane) {
                Plane plane = (Plane) shape;
                int offset = planeIdx * 8;
                planes[offset] = (float) plane.getPoint().getX();
                planes[offset + 1] = (float) plane.getPoint().getY();
                planes[offset + 2] = (float) plane.getPoint().getZ();
                planes[offset + 3] = (float) plane.getNormalVector().getX();
                planes[offset + 4] = (float) plane.getNormalVector().getY();
                planes[offset + 5] = (float) plane.getNormalVector().getZ();
                planes[offset + 6] = matIdx;
                planes[offset + 7] = 0f;
                planeIdx++;
            }
            
            matIdx++;
        }
        
        // Sérialiser les lumières
        List<AbstractLight> lights = scene.getLights();
        int numDirectional = 0, numPoint = 0;
        
        for (AbstractLight light : lights) {
            if (light instanceof DirectionalLight) numDirectional++;
            else if (light instanceof PointLight) numPoint++;
        }
        
        this.directionalLightCount = numDirectional;
        this.pointLightCount = numPoint;
        
        this.directionalLights = new float[Math.max(1, numDirectional * 8)];
        this.pointLights = new float[Math.max(1, numPoint * 8)];
        
        int dirIdx = 0, pointIdx = 0;
        
        for (AbstractLight light : lights) {
            if (light instanceof DirectionalLight) {
                DirectionalLight dirLight = (DirectionalLight) light;
                int offset = dirIdx * 8;
                Vector dir = dirLight.getDirection();
                Color col = dirLight.getColor();
                directionalLights[offset] = (float) dir.getX();
                directionalLights[offset + 1] = (float) dir.getY();
                directionalLights[offset + 2] = (float) dir.getZ();
                directionalLights[offset + 3] = (float) col.getR();
                directionalLights[offset + 4] = (float) col.getG();
                directionalLights[offset + 5] = (float) col.getB();
                directionalLights[offset + 6] = 0f;
                directionalLights[offset + 7] = 0f;
                dirIdx++;
            } else if (light instanceof PointLight) {
                PointLight pLight = (PointLight) light;
                int offset = pointIdx * 8;
                Point pos = pLight.getPosition();
                Color col = pLight.getColor();
                pointLights[offset] = (float) pos.getX();
                pointLights[offset + 1] = (float) pos.getY();
                pointLights[offset + 2] = (float) pos.getZ();
                pointLights[offset + 3] = (float) col.getR();
                pointLights[offset + 4] = (float) col.getG();
                pointLights[offset + 5] = (float) col.getB();
                pointLights[offset + 6] = 0f;
                pointLights[offset + 7] = 0f;
                pointIdx++;
            }
        }
    }
}
