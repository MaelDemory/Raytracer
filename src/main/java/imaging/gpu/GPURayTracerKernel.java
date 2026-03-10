package imaging.gpu;

import com.aparapi.Kernel;
import com.aparapi.Range;

/**
 * Aparapi kernel for GPU ray tracing. Uses only per-work-item locals to avoid
 * OpenCL address-space issues and inlines all scene queries.
 */
public class GPURayTracerKernel extends Kernel {

    private static final float EPSILON = 1e-4f;
    private static final float SHADOW_EPSILON = 1e-3f;

    private final int width;
    private final int height;
    private final int maxDepth;

    private final float[] outputR;
    private final float[] outputG;
    private final float[] outputB;

    private final float[] camera;
    private final float[] orthonormal;
    private final float[] pixelParams;

    private final float[] spheres;
    private final int sphereCount;
    private final float[] triangles;
    private final int triangleCount;
    private final float[] planes;
    private final int planeCount;

    private final float[] materials;

    private final float[] directionalLights;
    private final int directionalLightCount;
    private final float[] pointLights;
    private final int pointLightCount;

    public GPURayTracerKernel(GPUSceneData data) {
        this.width = data.width;
        this.height = data.height;
        this.maxDepth = data.maxDepth;

        int totalPixels = width * height;
        this.outputR = new float[totalPixels];
        this.outputG = new float[totalPixels];
        this.outputB = new float[totalPixels];

        this.camera = data.camera;
        this.orthonormal = data.orthonormal;
        this.pixelParams = data.pixelParams;

        this.spheres = data.spheres;
        this.sphereCount = data.sphereCount;
        this.triangles = data.triangles;
        this.triangleCount = data.triangleCount;
        this.planes = data.planes;
        this.planeCount = data.planeCount;

        this.materials = data.materials;

        this.directionalLights = data.directionalLights;
        this.directionalLightCount = data.directionalLightCount;
        this.pointLights = data.pointLights;
        this.pointLightCount = data.pointLightCount;
    }

    @Override
    public void run() {
        int gid = getGlobalId();
        int i = gid % width;
        int j = gid / width;

        if (i >= width || j >= height) {
            return;
        }

        float pixelWidth = pixelParams[0];
        float pixelHeight = pixelParams[1];
        float a = pixelWidth * (i - width / 2.0f + 0.5f);
        float b = pixelHeight * (j - height / 2.0f + 0.5f);

        float ox = camera[0];
        float oy = camera[1];
        float oz = camera[2];

        float ux = orthonormal[0];
        float uy = orthonormal[1];
        float uz = orthonormal[2];
        float vx = orthonormal[3];
        float vy = orthonormal[4];
        float vz = orthonormal[5];
        float wx = orthonormal[6];
        float wy = orthonormal[7];
        float wz = orthonormal[8];

        float dirX = ux * a + vx * b - wx;
        float dirY = uy * a + vy * b - wy;
        float dirZ = uz * a + vz * b - wz;
        float len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        float colorR = 0f;
        float colorG = 0f;
        float colorB = 0f;

        float curOx = ox;
        float curOy = oy;
        float curOz = oz;
        float curDx = dirX;
        float curDy = dirY;
        float curDz = dirZ;

        float maskR = 1f;
        float maskG = 1f;
        float maskB = 1f;

        int depth = 0;
        int shouldContinue = 1;
        while (depth < maxDepth && shouldContinue == 1) {
            // Check if mask is too small
            if (maskR <= 1e-4f && maskG <= 1e-4f && maskB <= 1e-4f) {
                shouldContinue = 0;
            }

            if (shouldContinue == 1) {
                int hitMatIdx = -1;
                float minDist = 1e30f;

                float bestHitX = 0f;
                float bestHitY = 0f;
                float bestHitZ = 0f;
                float bestNormX = 0f;
                float bestNormY = 0f;
                float bestNormZ = 0f;

                // Spheres intersection
                int s = 0;
                while (s < sphereCount) {
                    int offset = s * 8;
                    float cx = spheres[offset];
                    float cy = spheres[offset + 1];
                    float cz = spheres[offset + 2];
                    float radius = spheres[offset + 3];
                    int matIdx = (int) spheres[offset + 4];

                    float ocx = curOx - cx;
                    float ocy = curOy - cy;
                    float ocz = curOz - cz;

                    float qa = curDx * curDx + curDy * curDy + curDz * curDz;
                    float qb = 2.0f * (ocx * curDx + ocy * curDy + ocz * curDz);
                    float qc = ocx * ocx + ocy * ocy + ocz * ocz - radius * radius;
                    float discriminant = qb * qb - 4.0f * qa * qc;
                    if (discriminant >= 0f) {
                        float sqrtDisc = sqrt(discriminant);
                        float t1 = (-qb - sqrtDisc) / (2.0f * qa);
                        float t2 = (-qb + sqrtDisc) / (2.0f * qa);
                        float t = (t1 > EPSILON) ? t1 : ((t2 > EPSILON) ? t2 : -1f);
                        if (t > EPSILON && t < minDist) {
                            minDist = t;
                            hitMatIdx = matIdx;
                            bestHitX = curOx + t * curDx;
                            bestHitY = curOy + t * curDy;
                            bestHitZ = curOz + t * curDz;
                            bestNormX = (bestHitX - cx) / radius;
                            bestNormY = (bestHitY - cy) / radius;
                            bestNormZ = (bestHitZ - cz) / radius;
                        }
                    }
                    s++;
                }

                // Triangles intersection
                int ti = 0;
                while (ti < triangleCount) {
                    int offset = ti * 12;
                    float ax = triangles[offset];
                    float ay = triangles[offset + 1];
                    float az = triangles[offset + 2];
                    float bx = triangles[offset + 3];
                    float by = triangles[offset + 4];
                    float bz = triangles[offset + 5];
                    float tcx = triangles[offset + 6];
                    float tcy = triangles[offset + 7];
                    float tcz = triangles[offset + 8];
                    int matIdx = (int) triangles[offset + 9];

                    float edge1x = bx - ax;
                    float edge1y = by - ay;
                    float edge1z = bz - az;
                    float edge2x = tcx - ax;
                    float edge2y = tcy - ay;
                    float edge2z = tcz - az;

                    float hx = curDy * edge2z - curDz * edge2y;
                    float hy = curDz * edge2x - curDx * edge2z;
                    float hz = curDx * edge2y - curDy * edge2x;
                    float det = edge1x * hx + edge1y * hy + edge1z * hz;
                    if (det > EPSILON || det < -EPSILON) {
                        float invDet = 1.0f / det;
                        float sx = curOx - ax;
                        float sy = curOy - ay;
                        float sz = curOz - az;
                        float u = invDet * (sx * hx + sy * hy + sz * hz);
                        if (u >= 0.0f && u <= 1.0f) {
                            float qx = sy * edge1z - sz * edge1y;
                            float qy = sz * edge1x - sx * edge1z;
                            float qz = sx * edge1y - sy * edge1x;
                            float v = invDet * (curDx * qx + curDy * qy + curDz * qz);
                            if (v >= 0.0f && u + v <= 1.0f) {
                                float dist = invDet * (edge2x * qx + edge2y * qy + edge2z * qz);
                                if (dist > EPSILON && dist < minDist) {
                                    minDist = dist;
                                    hitMatIdx = matIdx;
                                    bestHitX = curOx + dist * curDx;
                                    bestHitY = curOy + dist * curDy;
                                    bestHitZ = curOz + dist * curDz;
                                    float nx = edge1y * edge2z - edge1z * edge2y;
                                    float ny = edge1z * edge2x - edge1x * edge2z;
                                    float nz = edge1x * edge2y - edge1y * edge2x;
                                    float nLen = sqrt(nx * nx + ny * ny + nz * nz);
                                    bestNormX = nx / nLen;
                                    bestNormY = ny / nLen;
                                    bestNormZ = nz / nLen;
                                }
                            }
                        }
                    }
                    ti++;
                }

                // Planes intersection
                int p = 0;
                while (p < planeCount) {
                    int offset = p * 8;
                    float px = planes[offset];
                    float py = planes[offset + 1];
                    float pz = planes[offset + 2];
                    float nx = planes[offset + 3];
                    float ny = planes[offset + 4];
                    float nz = planes[offset + 5];
                    int matIdx = (int) planes[offset + 6];

                    float denom = curDx * nx + curDy * ny + curDz * nz;
                    if (denom > EPSILON || denom < -EPSILON) {
                        float ddx = px - curOx;
                        float ddy = py - curOy;
                        float ddz = pz - curOz;
                        float dist = (ddx * nx + ddy * ny + ddz * nz) / denom;
                        if (dist > EPSILON && dist < minDist) {
                            minDist = dist;
                            hitMatIdx = matIdx;
                            bestHitX = curOx + dist * curDx;
                            bestHitY = curOy + dist * curDy;
                            bestHitZ = curOz + dist * curDz;
                            bestNormX = nx;
                            bestNormY = ny;
                            bestNormZ = nz;
                        }
                    }
                    p++;
                }

                // Process hit or stop
                if (hitMatIdx < 0) {
                    shouldContinue = 0;
                }

                if (shouldContinue == 1) {
                    int matOffset = hitMatIdx * 12;
                    float ambR = materials[matOffset];
                    float ambG = materials[matOffset + 1];
                    float ambB = materials[matOffset + 2];
                    float diffR = materials[matOffset + 3];
                    float diffG = materials[matOffset + 4];
                    float diffB = materials[matOffset + 5];
                    float specR = materials[matOffset + 6];
                    float specG = materials[matOffset + 7];
                    float specB = materials[matOffset + 8];
                    float shininess = materials[matOffset + 9];

                    float locR = ambR;
                    float locG = ambG;
                    float locB = ambB;

                    float eyeDirX = -curDx;
                    float eyeDirY = -curDy;
                    float eyeDirZ = -curDz;

                    // Offset plus grand pour éviter l'auto-intersection avec la précision float
                    float shadowOffsetX = bestHitX + bestNormX * SHADOW_EPSILON;
                    float shadowOffsetY = bestHitY + bestNormY * SHADOW_EPSILON;
                    float shadowOffsetZ = bestHitZ + bestNormZ * SHADOW_EPSILON;

                    // Directional lights
                    int dl = 0;
                    while (dl < directionalLightCount) {
                        int lOffset = dl * 8;
                        float ldx = directionalLights[lOffset];
                        float ldy = directionalLights[lOffset + 1];
                        float ldz = directionalLights[lOffset + 2];
                        float lcR = directionalLights[lOffset + 3];
                        float lcG = directionalLights[lOffset + 4];
                        float lcB = directionalLights[lOffset + 5];

                        float lenL = sqrt(ldx * ldx + ldy * ldy + ldz * ldz);
                        ldx /= lenL;
                        ldy /= lenL;
                        ldz /= lenL;

                        if (isInShadow(shadowOffsetX, shadowOffsetY, shadowOffsetZ, ldx, ldy, ldz, 1e30f) == 0) {
                            float nDotL = bestNormX * ldx + bestNormY * ldy + bestNormZ * ldz;
                            if (nDotL < 0f) nDotL = 0f;

                            locR += diffR * lcR * nDotL;
                            locG += diffG * lcG * nDotL;
                            locB += diffB * lcB * nDotL;

                            if (nDotL > 0f && shininess > 0f) {
                                float hx = ldx + eyeDirX;
                                float hy = ldy + eyeDirY;
                                float hz = ldz + eyeDirZ;
                                float hLen = sqrt(hx * hx + hy * hy + hz * hz);
                                if (hLen > 0f) {
                                    hx /= hLen;
                                    hy /= hLen;
                                    hz /= hLen;

                                    float nDotH = bestNormX * hx + bestNormY * hy + bestNormZ * hz;
                                    if (nDotH < 0f) nDotH = 0f;

                                    float specFactor = pow(nDotH, shininess);
                                    locR += specR * lcR * specFactor;
                                    locG += specG * lcG * specFactor;
                                    locB += specB * lcB * specFactor;
                                }
                            }
                        }
                        dl++;
                    }

                    // Point lights
                    int pl = 0;
                    while (pl < pointLightCount) {
                        int lOffset = pl * 8;
                        float lpx = pointLights[lOffset];
                        float lpy = pointLights[lOffset + 1];
                        float lpz = pointLights[lOffset + 2];
                        float lcR = pointLights[lOffset + 3];
                        float lcG = pointLights[lOffset + 4];
                        float lcB = pointLights[lOffset + 5];

                        float ldx = lpx - bestHitX;
                        float ldy = lpy - bestHitY;
                        float ldz = lpz - bestHitZ;
                        float maxDistL = sqrt(ldx * ldx + ldy * ldy + ldz * ldz);
                        ldx /= maxDistL;
                        ldy /= maxDistL;
                        ldz /= maxDistL;

                        if (isInShadow(shadowOffsetX, shadowOffsetY, shadowOffsetZ, ldx, ldy, ldz, maxDistL) == 0) {
                            float nDotL = bestNormX * ldx + bestNormY * ldy + bestNormZ * ldz;
                            if (nDotL < 0f) nDotL = 0f;

                            locR += diffR * lcR * nDotL;
                            locG += diffG * lcG * nDotL;
                            locB += diffB * lcB * nDotL;

                            if (nDotL > 0f && shininess > 0f) {
                                float hx = ldx + eyeDirX;
                                float hy = ldy + eyeDirY;
                                float hz = ldz + eyeDirZ;
                                float hLen = sqrt(hx * hx + hy * hy + hz * hz);
                                if (hLen > 0f) {
                                    hx /= hLen;
                                    hy /= hLen;
                                    hz /= hLen;

                                    float nDotH = bestNormX * hx + bestNormY * hy + bestNormZ * hz;
                                    if (nDotH < 0f) nDotH = 0f;

                                    float specFactor = pow(nDotH, shininess);
                                    locR += specR * lcR * specFactor;
                                    locG += specG * lcG * specFactor;
                                    locB += specB * lcB * specFactor;
                                }
                            }
                        }
                        pl++;
                    }

                    // Accumulate color
                    colorR += maskR * locR;
                    colorG += maskG * locG;
                    colorB += maskB * locB;

                    // Check for reflection
                    if (depth < maxDepth - 1 && (specR > 0f || specG > 0f || specB > 0f)) {
                        maskR *= specR;
                        maskG *= specG;
                        maskB *= specB;

                        float dot = curDx * bestNormX + curDy * bestNormY + curDz * bestNormZ;
                        float refDirX = curDx - 2.0f * dot * bestNormX;
                        float refDirY = curDy - 2.0f * dot * bestNormY;
                        float refDirZ = curDz - 2.0f * dot * bestNormZ;

                        float refLen = sqrt(refDirX * refDirX + refDirY * refDirY + refDirZ * refDirZ);
                        curDx = refDirX / refLen;
                        curDy = refDirY / refLen;
                        curDz = refDirZ / refLen;

                        curOx = shadowOffsetX;
                        curOy = shadowOffsetY;
                        curOz = shadowOffsetZ;
                    } else {
                        shouldContinue = 0;
                    }
                }
            }
            depth++;
        }

        int index = j * width + i;
        outputR[index] = clamp(colorR, 0f, 1f);
        outputG[index] = clamp(colorG, 0f, 1f);
        outputB[index] = clamp(colorB, 0f, 1f);
    }

    private int isInShadow(float px, float py, float pz, float dx, float dy, float dz, float maxDist) {
        int shadow = 0;
        int s = 0;
        while (s < sphereCount && shadow == 0) {
            int offset = s * 8;
            float cx = spheres[offset];
            float cy = spheres[offset + 1];
            float cz = spheres[offset + 2];
            float radius = spheres[offset + 3];

            float ocx = px - cx;
            float ocy = py - cy;
            float ocz = pz - cz;
            float qa = dx * dx + dy * dy + dz * dz;
            float qb = 2.0f * (ocx * dx + ocy * dy + ocz * dz);
            float qc = ocx * ocx + ocy * ocy + ocz * ocz - radius * radius;
            float discriminant = qb * qb - 4.0f * qa * qc;
            if (discriminant >= 0f) {
                float sqrtDisc = sqrt(discriminant);
                float t1 = (-qb - sqrtDisc) / (2.0f * qa);
                float t2 = (-qb + sqrtDisc) / (2.0f * qa);
                float t = (t1 > EPSILON) ? t1 : ((t2 > EPSILON) ? t2 : -1f);
                if (t > EPSILON && t < maxDist) {
                    shadow = 1;
                }
            }
            s++;
        }

        int t = 0;
        while (t < triangleCount && shadow == 0) {
            int offset = t * 12;
            float ax = triangles[offset];
            float ay = triangles[offset + 1];
            float az = triangles[offset + 2];
            float bx = triangles[offset + 3];
            float by = triangles[offset + 4];
            float bz = triangles[offset + 5];
            float tcx = triangles[offset + 6];
            float tcy = triangles[offset + 7];
            float tcz = triangles[offset + 8];

            float edge1x = bx - ax;
            float edge1y = by - ay;
            float edge1z = bz - az;
            float edge2x = tcx - ax;
            float edge2y = tcy - ay;
            float edge2z = tcz - az;

            float hx = dy * edge2z - dz * edge2y;
            float hy = dz * edge2x - dx * edge2z;
            float hz = dx * edge2y - dy * edge2x;
            float det = edge1x * hx + edge1y * hy + edge1z * hz;
            if (det > EPSILON || det < -EPSILON) {
                float invDet = 1.0f / det;
                float tsx = px - ax;
                float tsy = py - ay;
                float tsz = pz - az;
                float u = invDet * (tsx * hx + tsy * hy + tsz * hz);
                if (u >= 0.0f && u <= 1.0f) {
                    float qx = tsy * edge1z - tsz * edge1y;
                    float qy = tsz * edge1x - tsx * edge1z;
                    float qz = tsx * edge1y - tsy * edge1x;
                    float v = invDet * (dx * qx + dy * qy + dz * qz);
                    if (v >= 0.0f && u + v <= 1.0f) {
                        float dist = invDet * (edge2x * qx + edge2y * qy + edge2z * qz);
                        if (dist > EPSILON && dist < maxDist) {
                            shadow = 1;
                        }
                    }
                }
            }
            t++;
        }

        int p = 0;
        while (p < planeCount && shadow == 0) {
            int offset = p * 8;
            float pxPlane = planes[offset];
            float pyPlane = planes[offset + 1];
            float pzPlane = planes[offset + 2];
            float nx = planes[offset + 3];
            float ny = planes[offset + 4];
            float nz = planes[offset + 5];

            float denom = dx * nx + dy * ny + dz * nz;
            if (denom > EPSILON || denom < -EPSILON) {
                float ddx = pxPlane - px;
                float ddy = pyPlane - py;
                float ddz = pzPlane - pz;
                float dist = (ddx * nx + ddy * ny + ddz * nz) / denom;
                if (dist > EPSILON && dist < maxDist) {
                    shadow = 1;
                }
            }
            p++;
        }

        return shadow;
    }

    private float clamp(float val, float min, float max) {
        float result = val;
        result = (result < min) ? min : result;
        result = (result > max) ? max : result;
        return result;
    }

    /** Execute the kernel on the GPU. */
    public int[] execute() {
        Range range = Range.create(width * height);
        super.execute(range);

        int[] result = new int[width * height];
        for (int idx = 0; idx < result.length; idx++) {
            int r = (int) (outputR[idx] * 255f);
            int g = (int) (outputG[idx] * 255f);
            int b = (int) (outputB[idx] * 255f);
            result[idx] = (r << 16) | (g << 8) | b;
        }

        return result;
    }

    /** Returns the rendered width. */
    public int getWidth() {
        return width;
    }

    /** Returns the rendered height. */
    public int getHeight() {
        return height;
    }
}
