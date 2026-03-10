/**
 * Scene data serialization for Vulkan compute ray tracing with BVH.
 *
 * (c) 2025 Mael DEMORY
 */
package imaging.vulkan;

import geometry.Orthonormal;
import geometry.Plane;
import geometry.Shape;
import geometry.Sphere;
import geometry.Triangle;
import raytracer.BvhNode;
import raytracer.Camera;
import raytracer.Material;
import raytracer.Scene;
import imaging.Color;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens a Scene into GPU-friendly buffers (SSBOs) including BVH nodes.
 */
public final class VulkanSceneData {

    // Shape types
    public static final int SHAPE_SPHERE = 0;
    public static final int SHAPE_TRIANGLE = 1;
    public static final int SHAPE_PLANE = 2;

    // Camera / image params
    public final float[] camera;       // 12 floats
    public final int width;
    public final int height;
    public final int maxDepth;
    public final float[] ambient;      // 3 floats
    public final float[] orthonormal;  // 9 floats
    public final float[] pixelParams;  // 2 floats

    // Geometry buffers (same layout as Aparapi path for reuse)
    public final float[] spheres;   // per sphere: [cx,cy,cz,radius, matIdx,0,0,0]
    public final int sphereCount;
    public final float[] triangles; // per tri: [ax,ay,az,bx,by,bz,cx,cy,cz,matIdx,0,0]
    public final int triangleCount;
    public final float[] planes;    // per plane: [px,py,pz,nx,ny,nz,matIdx,0]
    public final int planeCount;

    public final float[] materials; // per material: 12 floats
    public final int materialCount;

    // Lights
    public final float[] directionalLights; // [dirX,dirY,dirZ, colorR,colorG,colorB,0,0]
    public final int directionalLightCount;
    public final float[] pointLights;       // [posX,posY,posZ, colorR,colorG,colorB,0,0]
    public final int pointLightCount;

    // BVH (flattened): bounds stored separately from metadata for simpler std430 packing
    // bvhMinBounds: vec4(minX,minY,minZ,0)
    // bvhMaxBounds: vec4(maxX,maxY,maxZ,0)
    // bvhMeta: uvec4(leftIdx, rightIdx, shapeType, shapeIndex) with 0xFFFFFFFF for null/inner
    public final float[] bvhMinBounds;
    public final float[] bvhMaxBounds;
    public final int[] bvhMeta;
    public final int bvhNodeCount;

    public VulkanSceneData(Scene scene) {
        this.width = scene.getWidth();
        this.height = scene.getHeight();
        this.maxDepth = scene.getMaxDepth();

        // Camera
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

        // Ambient
        Color amb = scene.getAmbient();
        this.ambient = new float[] {
            (float) amb.getR(),
            (float) amb.getG(),
            (float) amb.getB()
        };

        // Orthonormal base
        Orthonormal ortho = new Orthonormal(cam.getLookFrom(), cam.getLookAt(), cam.getUp());
        this.orthonormal = new float[] {
            (float) ortho.getU().getX(), (float) ortho.getU().getY(), (float) ortho.getU().getZ(),
            (float) ortho.getV().getX(), (float) ortho.getV().getY(), (float) ortho.getV().getZ(),
            (float) ortho.getW().getX(), (float) ortho.getW().getY(), (float) ortho.getW().getZ()
        };

        double fovRadians = Math.toRadians(cam.getFov());
        double viewportHeight = 2.0 * Math.tan(fovRadians / 2.0);
        double aspectRatio = (double) width / height;
        double viewportWidth = viewportHeight * aspectRatio;
        this.pixelParams = new float[] {
            (float) (viewportWidth / width),
            (float) (viewportHeight / height)
        };

        // First pass: count shapes
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
        this.materials = new float[Math.max(1, materialCount * 12)];

        Map<Shape, Integer> sphereIndex = new IdentityHashMap<>();
        Map<Shape, Integer> triangleIndex = new IdentityHashMap<>();
        Map<Shape, Integer> planeIndex = new IdentityHashMap<>();

        int sphereIdx = 0, triIdx = 0, planeIdx = 0, matIdx = 0;
        for (Shape shape : shapes) {
            Material mat = shape.getMaterial();
            int matOffset = matIdx * 12;
            materials[matOffset]     = (float) mat.getAmbient().getR();
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

            if (shape instanceof Sphere sphere) {
                int offset = sphereIdx * 8;
                spheres[offset] = (float) sphere.getCenter().getX();
                spheres[offset + 1] = (float) sphere.getCenter().getY();
                spheres[offset + 2] = (float) sphere.getCenter().getZ();
                spheres[offset + 3] = (float) sphere.getRadius();
                spheres[offset + 4] = matIdx;
                spheres[offset + 5] = 0f;
                spheres[offset + 6] = 0f;
                spheres[offset + 7] = 0f;
                sphereIndex.put(shape, sphereIdx);
                sphereIdx++;
            } else if (shape instanceof Triangle tri) {
                int offset = triIdx * 12;
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
                triangleIndex.put(shape, triIdx);
                triIdx++;
            } else if (shape instanceof Plane plane) {
                int offset = planeIdx * 8;
                planes[offset] = (float) plane.getPoint().getX();
                planes[offset + 1] = (float) plane.getPoint().getY();
                planes[offset + 2] = (float) plane.getPoint().getZ();
                planes[offset + 3] = (float) plane.getNormal().getX();
                planes[offset + 4] = (float) plane.getNormal().getY();
                planes[offset + 5] = (float) plane.getNormal().getZ();
                planes[offset + 6] = matIdx;
                planes[offset + 7] = 0f;
                planeIndex.put(shape, planeIdx);
                planeIdx++;
            }
            matIdx++;
        }

        // Lights
        var lights = scene.getLights();
        int dirCount = 0, pointCount = 0;
        for (var l : lights) {
            if (l instanceof raytracer.DirectionalLight) dirCount++; else if (l instanceof raytracer.PointLight) pointCount++;
        }
        this.directionalLightCount = dirCount;
        this.pointLightCount = pointCount;
        this.directionalLights = new float[Math.max(1, dirCount * 8)];
        this.pointLights = new float[Math.max(1, pointCount * 8)];

        int dIdx = 0, pIdx = 0;
        for (var l : lights) {
            if (l instanceof raytracer.DirectionalLight dl) {
                int o = dIdx * 8;
                directionalLights[o] = (float) dl.getDirection().getX();
                directionalLights[o + 1] = (float) dl.getDirection().getY();
                directionalLights[o + 2] = (float) dl.getDirection().getZ();
                directionalLights[o + 3] = (float) dl.getColor().getR();
                directionalLights[o + 4] = (float) dl.getColor().getG();
                directionalLights[o + 5] = (float) dl.getColor().getB();
                directionalLights[o + 6] = 0f; directionalLights[o + 7] = 0f;
                dIdx++;
            } else if (l instanceof raytracer.PointLight pl) {
                int o = pIdx * 8;
                pointLights[o] = (float) pl.getPosition().getX();
                pointLights[o + 1] = (float) pl.getPosition().getY();
                pointLights[o + 2] = (float) pl.getPosition().getZ();
                pointLights[o + 3] = (float) pl.getColor().getR();
                pointLights[o + 4] = (float) pl.getColor().getG();
                pointLights[o + 5] = (float) pl.getColor().getB();
                pointLights[o + 6] = 0f; pointLights[o + 7] = 0f;
                pIdx++;
            }
        }

        // BVH flattening
        List<BvhNode.FlatNode> flatNodes = scene.flattenBvh(new BvhNode.ShapeIndexer() {
            @Override
            public int typeOf(Shape shape) {
                if (shape instanceof Sphere) return SHAPE_SPHERE;
                if (shape instanceof Triangle) return SHAPE_TRIANGLE;
                if (shape instanceof Plane) return SHAPE_PLANE;
                return -1;
            }
            @Override
            public int indexOf(Shape shape) {
                Integer idx = sphereIndex.get(shape);
                if (idx != null) return idx;
                idx = triangleIndex.get(shape);
                if (idx != null) return idx;
                idx = planeIndex.get(shape);
                return idx != null ? idx : -1;
            }
        });

        this.bvhNodeCount = flatNodes.size();
        int nodeCount = Math.max(1, bvhNodeCount);
        this.bvhMinBounds = new float[nodeCount * 4];
        this.bvhMaxBounds = new float[nodeCount * 4];
        this.bvhMeta = new int[nodeCount * 4];

        final int NONE = 0xFFFFFFFF;
        for (int i = 0; i < flatNodes.size(); i++) {
            var n = flatNodes.get(i);
            int minOffset = i * 4;
            int maxOffset = i * 4;
            int metaOffset = i * 4;
            var b = n.bounds();
            bvhMinBounds[minOffset] = (float) b.minX();
            bvhMinBounds[minOffset + 1] = (float) b.minY();
            bvhMinBounds[minOffset + 2] = (float) b.minZ();
            bvhMinBounds[minOffset + 3] = 0f;
            bvhMaxBounds[maxOffset] = (float) b.maxX();
            bvhMaxBounds[maxOffset + 1] = (float) b.maxY();
            bvhMaxBounds[maxOffset + 2] = (float) b.maxZ();
            bvhMaxBounds[maxOffset + 3] = 0f;
            bvhMeta[metaOffset] = n.leftIndex() >= 0 ? n.leftIndex() : NONE;
            bvhMeta[metaOffset + 1] = n.rightIndex() >= 0 ? n.rightIndex() : NONE;
            bvhMeta[metaOffset + 2] = n.shapeType() >= 0 ? n.shapeType() : NONE;
            bvhMeta[metaOffset + 3] = n.shapeIndex() >= 0 ? n.shapeIndex() : NONE;
        }

        // Ensure arrays are non-empty even if BVH absent
        if (flatNodes.isEmpty()) {
            bvhMinBounds[0] = bvhMaxBounds[0] = 0f;
            bvhMeta[0] = bvhMeta[1] = bvhMeta[2] = bvhMeta[3] = NONE;
        }
    }
}
