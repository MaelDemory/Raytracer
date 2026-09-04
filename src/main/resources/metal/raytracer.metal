//
// Projet POO Ray Tracing - kernel de rendu Metal
//
// (c) 2025 Maël DEMORY
//
// Ce kernel reproduit exactement la sémantique de imaging.RayTracer :
// mêmes epsilons, même modèle de Phong, même parcours de BVH, même saturation
// des couleurs à chaque niveau de réflexion.
//

#include <metal_stdlib>
using namespace metal;

// Epsilons repris un à un du moteur CPU.
constant float EPS_BVH      = 1e-6f;   // BvhNode.EPSILON
constant float EPS_TRIANGLE = 1e-7f;   // Triangle.EPSILON
constant float EPS_PLANE    = 1e-6f;   // Plane.EPSILON
constant float EPS_OFFSET   = 1e-5f;   // RayTracer.EPSILON (décalage anti-acné)

// Le CPU travaille en double et peut se contenter d'un epsilon fixe. En simple
// précision, l'erreur relative de float32 (~1.2e-7) rend le bruit du calcul de
// distance proportionnel à la magnitude des coordonnées : une sphère de rayon
// 10000 donne déjà un bruit de l'ordre de 3e-3. Le décalage anti-auto-
// intersection est donc dérivé de la magnitude effective de chaque primitive.
constant float EPS_RELATIVE = 4e-6f;

// Profondeur de pile du parcours itératif : 32 niveaux couvrent 2^32 primitives.
constant int STACK_SIZE = 32;

// Nombre maximal de rebonds mémorisés pour replier la récurrence de réflexion.
// Le côté Java refuse de déléguer au GPU au-delà de cette valeur.
constant int MAX_BOUNCES = 16;

/// Paramètres uniformes de la passe. Chaque membre fait 16 octets, ce qui donne
/// une disposition identique côté Java, côté Objective-C et ici.
struct Params {
    int4 dims;      // width, height, maxDepth, bvhNodeCount
    int4 counts;    // sphereCount, triangleCount, planeCount, dirLightCount
    int4 offsetsA;  // pointLightCount, offSpheres, offTriangles, offPlanes
    int4 offsetsB;  // offMaterials, offDirLights, offPointLights, offBvhBounds
    float4 origin;  // camera.xyz, pixelWidth
    float4 u;       // base.u.xyz, pixelHeight
    float4 v;       // base.v.xyz, inutilisé
    float4 w;       // base.w.xyz, inutilisé
    float4 ambient; // ambiante de scène.rgb, inutilisé
};

/// Résultat d'une intersection : distance, normale, index de matériau et
/// décalage anti-auto-intersection adapté à la primitive touchée.
struct Hit {
    float t;
    float3 normal;
    int material;
    float epsilon;
};

/// Plus grande composante en valeur absolue.
static float maxAbs(float3 value) {
    return max(fabs(value.x), max(fabs(value.y), fabs(value.z)));
}

/// Décalage minimal permettant à un rayon secondaire de quitter la surface sans
/// la réintersecter, compte tenu du bruit de float32 à cette magnitude.
static float precisionEpsilon(float magnitude) {
    return max(EPS_OFFSET, magnitude * EPS_RELATIVE);
}

// ---------------------------------------------------------------------------
// Intersections élémentaires
// ---------------------------------------------------------------------------

/// Teste un rayon contre une boîte englobante par la méthode des tranches.
/// Port fidèle de BoundingBox.intersects, y compris le cas dir == 0 et les
/// comparaisons strictes.
static bool boxHit(device const float* bounds, int node,
                   float3 ro, float3 rd, float tMin, float tMax) {
    int o = node * 6;
    float3 lo = float3(bounds[o], bounds[o + 1], bounds[o + 2]);
    float3 hi = float3(bounds[o + 3], bounds[o + 4], bounds[o + 5]);

    for (int axis = 0; axis < 3; ++axis) {
        float d = rd[axis];
        float org = ro[axis];
        if (d == 0.0f) {
            if (org < lo[axis] || org > hi[axis]) {
                return false;
            }
            continue;
        }
        float inv = 1.0f / d;
        float t0 = (lo[axis] - org) * inv;
        float t1 = (hi[axis] - org) * inv;
        if (inv < 0.0f) {
            float swap = t0;
            t0 = t1;
            t1 = swap;
        }
        float newMin = max(t0, tMin);
        float newMax = min(t1, tMax);
        if (newMax <= newMin) {
            return false;
        }
        tMin = newMin;
        tMax = newMax;
    }
    return tMax > tMin;
}

/// Intersection rayon/sphère. Retourne la distance, ou -1 en cas d'échec.
static float sphereHit(device const float* data, int base,
                       float3 ro, float3 rd,
                       thread float3& normal, thread float& epsilon) {
    float3 center = float3(data[base], data[base + 1], data[base + 2]);
    float radius = data[base + 3];
    float3 oc = ro - center;
    epsilon = precisionEpsilon(maxAbs(oc) + radius);

    float a = dot(rd, rd);
    float b = 2.0f * dot(oc, rd);
    float c = dot(oc, oc) - radius * radius;
    float delta = b * b - 4.0f * a * c;
    if (delta < 0.0f) {
        return -1.0f;
    }

    float sq = sqrt(delta);
    float t1 = (-b - sq) / (2.0f * a);
    float t2 = (-b + sq) / (2.0f * a);

    float t;
    if (t1 > 0.0f) {
        t = t1;
    } else if (t2 > 0.0f) {
        t = t2;
    } else {
        return -1.0f;
    }

    normal = normalize((ro + rd * t) - center);
    return t;
}

/// Intersection rayon/triangle par Möller-Trumbore. Retourne -1 en cas d'échec.
static float triangleHit(device const float* data, int base,
                         float3 ro, float3 rd,
                         thread float3& normal, thread float& epsilon) {
    float3 a = float3(data[base], data[base + 1], data[base + 2]);
    float3 b = float3(data[base + 3], data[base + 4], data[base + 5]);
    float3 c = float3(data[base + 6], data[base + 7], data[base + 8]);
    epsilon = precisionEpsilon(max(maxAbs(a), max(maxAbs(b), maxAbs(c))));

    float3 edge1 = b - a;
    float3 edge2 = c - a;

    float3 pvec = cross(rd, edge2);
    float det = dot(edge1, pvec);
    if (fabs(det) < EPS_TRIANGLE) {
        return -1.0f;
    }
    float invDet = 1.0f / det;

    float3 tvec = ro - a;
    float u = dot(tvec, pvec) * invDet;
    if (u < 0.0f || u > 1.0f) {
        return -1.0f;
    }

    float3 qvec = cross(tvec, edge1);
    float v = dot(rd, qvec) * invDet;
    if (v < 0.0f || u + v > 1.0f) {
        return -1.0f;
    }

    float t = dot(edge2, qvec) * invDet;
    if (t < EPS_TRIANGLE) {
        return -1.0f;
    }

    normal = normalize(cross(edge1, edge2));
    return t;
}

/// Intersection rayon/plan. Retourne -1 en cas d'échec.
static float planeHit(device const float* data, int base,
                      float3 ro, float3 rd,
                      thread float3& normal, thread float& epsilon) {
    float3 point = float3(data[base], data[base + 1], data[base + 2]);
    float3 n = float3(data[base + 3], data[base + 4], data[base + 5]);
    epsilon = precisionEpsilon(max(maxAbs(point), maxAbs(ro)));

    float denom = dot(n, rd);
    if (fabs(denom) < EPS_PLANE) {
        return -1.0f;
    }
    float t = dot(point - ro, n) / denom;
    if (t < EPS_PLANE) {
        return -1.0f;
    }

    normal = n;
    return t;
}

// ---------------------------------------------------------------------------
// Parcours du BVH
// ---------------------------------------------------------------------------

/// Cherche l'intersection la plus proche : BVH d'abord, puis formes non bornées.
/// Reproduit Scene.findClosestIntersection.
static bool closestHit(constant Params& p,
                       device const float* floats,
                       device const int* nodes,
                       float3 ro, float3 rd,
                       thread Hit& hit) {
    hit.t = INFINITY;
    hit.material = -1;
    hit.epsilon = EPS_OFFSET;

    int nodeCount = p.dims.w;
    int offBounds = p.offsetsB.w;
    int offSpheres = p.offsetsA.y;
    int offTriangles = p.offsetsA.z;

    if (nodeCount > 0) {
        int stack[STACK_SIZE];
        int sp = 0;
        stack[sp++] = 0;

        while (sp > 0) {
            int node = stack[--sp];
            if (!boxHit(floats + offBounds, node, ro, rd, EPS_BVH, hit.t)) {
                continue;
            }

            int left = nodes[node * 2];
            int payload = nodes[node * 2 + 1];

            if (left >= 0) {
                // Noeud interne : les deux fils sont toujours présents.
                if (sp + 2 <= STACK_SIZE) {
                    stack[sp++] = payload;
                    stack[sp++] = left;
                }
                continue;
            }

            // Feuille : left vaut -1 pour une sphère, -2 pour un triangle.
            float3 normal = float3(0.0f);
            float epsilon = EPS_OFFSET;
            float t;
            int material;
            if (left == -1) {
                int base = offSpheres + payload * 8;
                t = sphereHit(floats, base, ro, rd, normal, epsilon);
                material = (int) floats[base + 4];
            } else {
                int base = offTriangles + payload * 12;
                t = triangleHit(floats, base, ro, rd, normal, epsilon);
                material = (int) floats[base + 9];
            }

            if (t > EPS_BVH && t < hit.t) {
                hit.t = t;
                hit.normal = normal;
                hit.material = material;
                hit.epsilon = epsilon;
            }
        }
    }

    // Formes non bornées (plans), testées linéairement comme côté CPU.
    int planeCount = p.counts.z;
    int offPlanes = p.offsetsA.w;
    for (int i = 0; i < planeCount; ++i) {
        int base = offPlanes + i * 8;
        float3 normal = float3(0.0f);
        float epsilon = EPS_OFFSET;
        float t = planeHit(floats, base, ro, rd, normal, epsilon);
        if (t > 0.0f && t < hit.t) {
            hit.t = t;
            hit.normal = normal;
            hit.material = (int) floats[base + 6];
            hit.epsilon = epsilon;
        }
    }

    return hit.material >= 0;
}

/// Teste l'existence d'une occlusion entre un point et une lumière.
/// Reproduit Scene.isInShadow.
static bool anyHit(constant Params& p,
                   device const float* floats,
                   device const int* nodes,
                   float3 ro, float3 rd, float maxDistance) {
    int nodeCount = p.dims.w;
    int offBounds = p.offsetsB.w;
    int offSpheres = p.offsetsA.y;
    int offTriangles = p.offsetsA.z;

    if (nodeCount > 0) {
        int stack[STACK_SIZE];
        int sp = 0;
        stack[sp++] = 0;

        while (sp > 0) {
            int node = stack[--sp];
            if (!boxHit(floats + offBounds, node, ro, rd, EPS_BVH, maxDistance)) {
                continue;
            }

            int left = nodes[node * 2];
            int payload = nodes[node * 2 + 1];

            if (left >= 0) {
                if (sp + 2 <= STACK_SIZE) {
                    stack[sp++] = payload;
                    stack[sp++] = left;
                }
                continue;
            }

            float3 normal = float3(0.0f);
            float epsilon = EPS_OFFSET;
            float t = (left == -1)
                ? sphereHit(floats, offSpheres + payload * 8, ro, rd, normal, epsilon)
                : triangleHit(floats, offTriangles + payload * 12, ro, rd, normal, epsilon);

            if (t > EPS_BVH && t < maxDistance) {
                return true;
            }
        }
    }

    int planeCount = p.counts.z;
    int offPlanes = p.offsetsA.w;
    for (int i = 0; i < planeCount; ++i) {
        float3 normal = float3(0.0f);
        float epsilon = EPS_OFFSET;
        float t = planeHit(floats, offPlanes + i * 8, ro, rd, normal, epsilon);
        if (t > EPS_BVH && t < maxDistance) {
            return true;
        }
    }

    return false;
}

// ---------------------------------------------------------------------------
// Ombrage
// ---------------------------------------------------------------------------

/// Calcule l'éclairage local d'un point : ambiante du matériau plus la
/// contribution Phong de chaque lumière non occultée.
///
/// Le CPU sature la couleur après chaque opération, mais toutes les
/// contributions sont positives et toutes les composantes sont déjà dans
/// [0,1] : saturer une seule fois à la fin donne exactement le même résultat.
static float3 shade(constant Params& p,
                    device const float* floats,
                    device const int* nodes,
                    float3 point, float3 normal, float3 eyeDir,
                    int material, float epsilon) {
    int matBase = p.offsetsB.x + material * 12;
    float3 matAmbient = float3(floats[matBase], floats[matBase + 1], floats[matBase + 2]);
    float3 matDiffuse = float3(floats[matBase + 3], floats[matBase + 4], floats[matBase + 5]);
    float3 matSpecular = float3(floats[matBase + 6], floats[matBase + 7], floats[matBase + 8]);
    float shininess = floats[matBase + 9];

    float3 result = matAmbient;
    float3 shadowOrigin = point + normal * epsilon;

    int dirCount = p.counts.w;
    int offDir = p.offsetsB.y;
    for (int i = 0; i < dirCount; ++i) {
        int base = offDir + i * 8;
        float3 lightDir = normalize(float3(floats[base], floats[base + 1], floats[base + 2]));
        if (anyHit(p, floats, nodes, shadowOrigin, lightDir, INFINITY)) {
            continue;
        }
        float3 lightColor = float3(floats[base + 3], floats[base + 4], floats[base + 5]);
        float cosAngle = max(dot(lightDir, normal), 0.0f);
        float3 h = normalize(lightDir + eyeDir);
        float specularFactor = pow(max(dot(h, normal), 0.0f), shininess);
        result += lightColor * matDiffuse * cosAngle
                + lightColor * matSpecular * specularFactor;
    }

    int pointCount = p.offsetsA.x;
    int offPoint = p.offsetsB.z;
    for (int i = 0; i < pointCount; ++i) {
        int base = offPoint + i * 8;
        float3 position = float3(floats[base], floats[base + 1], floats[base + 2]);
        float3 toLight = position - point;
        float maxDistance = length(toLight);
        float3 lightDir = normalize(toLight);
        if (anyHit(p, floats, nodes, shadowOrigin, lightDir, maxDistance)) {
            continue;
        }
        float3 lightColor = float3(floats[base + 3], floats[base + 4], floats[base + 5]);
        float cosAngle = max(dot(lightDir, normal), 0.0f);
        float3 h = normalize(lightDir + eyeDir);
        float specularFactor = pow(max(dot(h, normal), 0.0f), shininess);
        result += lightColor * matDiffuse * cosAngle
                + lightColor * matSpecular * specularFactor;
    }

    return clamp(result, 0.0f, 1.0f);
}

// ---------------------------------------------------------------------------
// Kernel
// ---------------------------------------------------------------------------

/// Rend un pixel par thread. Le rendu est découpé en bandes de lignes : rowOffset
/// donne la première ligne de la bande courante.
kernel void trace(constant Params& p                [[buffer(0)]],
                  device const float* floats        [[buffer(1)]],
                  device const int* nodes           [[buffer(2)]],
                  device float* out                 [[buffer(3)]],
                  constant int& rowOffset           [[buffer(4)]],
                  uint2 gid                         [[thread_position_in_grid]]) {
    int width = p.dims.x;
    int height = p.dims.y;
    int i = (int) gid.x;
    int j = (int) gid.y + rowOffset;
    if (i >= width || j >= height) {
        return;
    }

    int pixel = (j * width + i) * 3;

    int maxDepth = p.dims.z;
    if (maxDepth <= 0) {
        out[pixel] = p.ambient.x;
        out[pixel + 1] = p.ambient.y;
        out[pixel + 2] = p.ambient.z;
        return;
    }

    // Rayon primaire, identique à RayTracer.computeRay.
    float a = p.origin.w * ((float) i - (float) width / 2.0f + 0.5f);
    float b = p.u.w * ((float) j - (float) height / 2.0f + 0.5f);
    float3 ro = p.origin.xyz;
    float3 rd = normalize(p.u.xyz * a + p.v.xyz * b - p.w.xyz);

    // Passe avant : la chaîne de réflexion est linéaire, on mémorise l'éclairage
    // local et le coefficient spéculaire de chaque niveau.
    float3 localColor[MAX_BOUNCES];
    float3 specular[MAX_BOUNCES];
    int bounces = 0;
    int depth = maxDepth;

    while (depth > 0 && bounces < MAX_BOUNCES) {
        Hit hit;
        if (!closestHit(p, floats, nodes, ro, rd, hit)) {
            break;
        }

        float3 point = ro + rd * hit.t;
        float3 eyeDir = normalize(-rd);
        localColor[bounces] = shade(p, floats, nodes, point, hit.normal, eyeDir,
                                    hit.material, hit.epsilon);

        int matBase = p.offsetsB.x + hit.material * 12;
        float3 spec = float3(floats[matBase + 6], floats[matBase + 7], floats[matBase + 8]);

        if (depth > 1 && (spec.x > 0.0f || spec.y > 0.0f || spec.z > 0.0f)) {
            specular[bounces] = spec;
            ro = point + hit.normal * hit.epsilon;
            rd = normalize(rd - hit.normal * (2.0f * dot(rd, hit.normal)));
            bounces++;
            depth--;
        } else {
            specular[bounces] = float3(0.0f);
            bounces++;
            break;
        }
    }

    // Passe arrière : c_k = clamp(local_k + spec_k * c_{k+1}), comme la récursion CPU.
    float3 color = float3(0.0f);
    for (int k = bounces - 1; k >= 0; --k) {
        color = clamp(localColor[k] + specular[k] * color, 0.0f, 1.0f);
    }

    out[pixel] = color.x;
    out[pixel + 1] = color.y;
    out[pixel + 2] = color.z;
}
