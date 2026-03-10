# Ray Tracer

Moteur de rendu par lancer de rayons développé en Java. Algorithme récursif avec accélération BVH, ombres et réflexions. Le rendu est **automatiquement** délégué au GPU via **Vulkan compute** s'il est disponible, sinon un rendu **CPU parallèle** est utilisé.

Le projet initial a été créé dans un cadre universitaire. Il s'agit ici du même projet qui a été retravaillé dans un cadre personnel.

Le projet initial est disponible [ici](https://github.com/MaelDemory/FISA-TI-2028-POO-DEMORY-Mael)

## Fonctionnalités

*   **Formes géométriques** : Sphères, Triangles, Plans.
*   **Éclairage** : Lumières ponctuelles et directionnelles.
*   **Matériaux** : Modèle de réflexion Phong (ambiante, diffuse, spéculaire, brillance).
*   **Accélération** : Structure BVH (Bounding Volume Hierarchy).
*   **Rendu GPU** : Vulkan compute via LWJGL avec compilation GLSL → SPIR-V à l'exécution.
*   **Rendu CPU** : Fallback multi-threadé automatique si Vulkan n'est pas disponible.
*   **Détection automatique** : Aucune configuration manuelle du mode de rendu requise.
*   **Docker** : Conteneurisation pour exécuter le raytracer sans installer Java ni Maven.

## Prérequis

### Exécution locale

*   Java 24+
*   Maven
*   Pilotes Vulkan (optionnel — le fallback CPU est automatique)

### Exécution via Docker

*   Docker

## Installation et Compilation

```bash
git clone https://github.com/MaelDemory/FISA-TI-2028-POO-DEMORY-Mael
cd raytracer
mvn clean package -DskipTests
```

## Utilisation

### Exécution locale

```bash
# Scène par défaut
java --enable-native-access=ALL-UNNAMED -cp "target/classes;target/dependency/*" Main

# Scène spécifique
java --enable-native-access=ALL-UNNAMED -cp "target/classes;target/dependency/*" Main src/main/resources/scenes/scenes/scene4.scene
```

> Sur Linux/macOS, remplacez `;` par `:` dans le classpath.

### Exécution via Docker

```bash
# Build de l'image
docker build -t raytracer .

# Rendu de la scène par défaut (scene4)
docker run --rm -v "$(pwd)/output:/app/output" raytracer

# Rendu d'une scène spécifique embarquée
docker run --rm -v "$(pwd)/output:/app/output" raytracer scenes/scenes/scene1.scene

# Rendu d'une scène personnalisée (depuis l'hôte)
docker run --rm -v "$(pwd)/output:/app/output" -v "$(pwd)/ma_scene.scene:/app/ma_scene.scene" raytracer ma_scene.scene
```

L'image rendue sera déposée dans le dossier `output/` sur votre machine.

> **Note** : Dans un conteneur Docker standard, Vulkan n'est pas disponible. Le raytracer utilise automatiquement le rendu CPU parallèle.

## Tests

```bash
mvn test
```

## Format de fichier de scène

Les fichiers `.scene` sont des fichiers texte décrivant la scène :

| Commande | Description |
|---|---|
| `size width height` | Taille de l'image de sortie |
| `output filename.png` | Fichier image généré |
| `camera ...` | Position, point visé, vecteur haut, FOV |
| `ambient r g b` | Couleur ambiante globale |
| `diffuse r g b` | Composante diffuse du matériau courant |
| `specular r g b` | Composante spéculaire du matériau courant |
| `shininess val` | Brillance du matériau courant |
| `sphere x y z radius` | Ajoute une sphère |
| `tri v1 v2 v3` | Ajoute un triangle (indices des sommets `vertex`) |
| `plane x y z nx ny nz` | Ajoute un plan (point + normale) |
| `point x y z r g b` | Lumière ponctuelle |
| `directional x y z r g b` | Lumière directionnelle |

## Auteur

Maël DEMORY
