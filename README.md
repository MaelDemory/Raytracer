# Ray Tracer

Moteur de rendu par lancer de rayons développé en Java. Algorithme récursif avec accélération BVH, ombres et réflexions. L'API graphique est **détectée au lancement** : **Metal** sur macOS, **Vulkan compute** partout ailleurs, et un rendu **CPU parallèle** si aucun GPU n'est exploitable.

Le projet initial a été créé dans un cadre universitaire. Il s'agit ici du même projet qui a été retravaillé dans un cadre personnel.

Le projet initial est disponible [ici](https://github.com/MaelDemory/FISA-TI-2028-POO-DEMORY-Mael)

## Fonctionnalités

*   **Formes géométriques** : Sphères, Triangles, Plans.
*   **Éclairage** : Lumières ponctuelles et directionnelles.
*   **Matériaux** : Modèle de réflexion Phong (ambiante, diffuse, spéculaire, brillance).
*   **Accélération** : Structure BVH (Bounding Volume Hierarchy).
*   **Rendu GPU Metal** : kernel Metal Shading Language embarquant le BVH, via la Foreign Function & Memory API (macOS).
*   **Rendu GPU Vulkan** : Vulkan compute via LWJGL avec compilation GLSL → SPIR-V à l'exécution.
*   **Rendu CPU** : Fallback multi-threadé automatique si aucun GPU n'est exploitable.
*   **Détection automatique** : l'API est choisie au lancement, et un backend qui refuse la scène passe la main au suivant.
*   **Docker** : Conteneurisation pour exécuter le raytracer sans installer Java ni Maven.

## Prérequis

### Exécution locale

*   Java 24+
*   Maven
*   Pour Metal (macOS) : Command Line Tools (`xcode-select --install`). Xcode complet n'est pas
    nécessaire, le shader est compilé à l'exécution.
*   Pour Vulkan : pilotes Vulkan / MoltenVK
*   Les deux sont optionnels — le fallback CPU est automatique

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

## Choix du backend

Les backends sont sondés dans l'ordre **Metal → Vulkan → CPU**. L'API native de la plateforme
passe avant l'API portable : sur macOS, Metal parle directement au GPU là où Vulkan traverse une
couche de traduction. Un backend indisponible, ou qui refuse la scène parce qu'elle sort de son
domaine, laisse la place au suivant sans interrompre le rendu.

La propriété `raytracer.backend` force un chemin précis, utile pour comparer :

```bash
java -Draytracer.backend=metal  -cp "target/classes:target/dependency/*" Main scene.scene
java -Draytracer.backend=vulkan -cp "target/classes:target/dependency/*" Main scene.scene
java -Draytracer.backend=cpu    -cp "target/classes:target/dependency/*" Main scene.scene
```

### Performances mesurées

Apple M5 (10 cœurs), macOS 27.0, OpenJDK 26, scènes ramenées à 1920 × 1080. Vulkan n'était pas
installé sur la machine de mesure ; la colonne CPU correspond au rendu parallèle par lignes.

| Scène | Primitives | CPU parallèle | Metal | Gain |
|---|---|---|---|---|
| scene5 | 266 | 408 ms | **205 ms** | ×2,0 |
| scene4 | 100 001 | 2 476 ms | **551 ms** | ×4,5 |
| scene1 | 92 | 12 800 ms | **918 ms** | ×13,9 |

Le kernel Metal reprend les epsilons, le modèle de Phong et la saturation par niveau de réflexion
du moteur CPU. L'écart résiduel tient à la simple précision du GPU : 0,02 % des pixels sur scene4,
0,44 % sur scene5, 4,3 % sur scene1 — cette dernière cumulant douze rebonds — pour un écart moyen
inférieur à 0,5 sur 255 dans les trois cas.

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
