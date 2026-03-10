/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package raytracer;

import imaging.Color;
import java.util.List;

/**
 * Classe utilitaire pour valider les lumières dans une scène.
 */
public class LightValidator {

    /**
     * Valide que la somme des composantes de couleur des lumières ne dépasse pas 1.0.
     * @param lights Liste des lumières à valider.
     * @throws IllegalArgumentException si la validation échoue.
     */
    public static void validateLights(List<AbstractLight> lights) {
        double totalR = 0.0;
        double totalG = 0.0;
        double totalB = 0.0;

        for (AbstractLight light : lights) {
            Color color = getLightColor(light);
            totalR += color.getR();
            totalG += color.getG();
            totalB += color.getB();
        }

        if (totalR > 1.0 || totalG > 1.0 || totalB > 1.0) {
            throw new IllegalArgumentException(
                    "La somme des couleurs des lumières ne doit pas dépasser 1.0 sur chaque composante"
            );
        }
    }

    /**
     * Récupère la couleur d'une lumière en fonction de son type.
     * @param light La lumière dont on veut obtenir la couleur.
     * @return La couleur de la lumière.
     */
    private static Color getLightColor(AbstractLight light) {
        if (light instanceof DirectionalLight) {
            return ((DirectionalLight) light).getColor();
        } else if (light instanceof PointLight) {
            return ((PointLight) light).getColor();
        }
        throw new IllegalArgumentException("Type de lumière inconnu");
    }
}
