/**
 * Projet POO Ray Tracing
 *
 * (c) 2025 Maël DEMORY
 */

package imaging;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue des backends accélérés, dans l'ordre de préférence.
 *
 * <p>L'ordre place l'API native de la plateforme avant l'API portable : sur
 * macOS, Metal parle directement au GPU, là où Vulkan passe par une couche de
 * traduction. Ailleurs — dans l'image Docker par exemple — Metal se déclare
 * indisponible et Vulkan prend la main.</p>
 */
public final class RenderBackends {

    /**
     * Propriété système forçant un backend précis, par son nom ou "cpu".
     * Utile pour comparer les chemins de rendu sur une même machine.
     */
    public static final String SELECTION_PROPERTY = "raytracer.backend";

    /**
     * Valeur de {@link #SELECTION_PROPERTY} demandant le rendu CPU.
     */
    private static final String CPU = "cpu";

    /**
     * Empêche l'instanciation.
     */
    private RenderBackends() {
    }

    /**
     * Retourne tous les backends connus, du plus spécifique au plus portable.
     * @return Liste ordonnée des backends
     */
    public static List<RenderBackend> all() {
        return List.of(new MetalBackend(), new VulkanBackend());
    }

    /**
     * Retourne les backends à essayer, en tenant compte d'un éventuel forçage.
     *
     * <p>Sans forçage, la liste complète est renvoyée dans l'ordre de préférence.
     * Avec un nom de backend, seul celui-ci est retenu. Avec "cpu", la liste est
     * vide et l'appelant rend sur le processeur.</p>
     *
     * @param forced Valeur du forçage, ou null pour la sélection automatique
     * @return Liste ordonnée des backends à essayer, éventuellement vide
     */
    public static List<RenderBackend> preferred(String forced) {
        if (forced == null || forced.isBlank()) {
            return all();
        }

        String wanted = forced.trim().toLowerCase();
        if (CPU.equals(wanted)) {
            return List.of();
        }

        List<RenderBackend> selected = new ArrayList<>(1);
        for (RenderBackend backend : all()) {
            if (backend.name().equals(wanted)) {
                selected.add(backend);
            }
        }
        if (selected.isEmpty()) {
            System.out.println("Backend inconnu: " + forced + " (valeurs possibles: "
                    + names() + ", " + CPU + "). Sélection automatique.");
            return all();
        }
        return List.copyOf(selected);
    }

    /**
     * Retourne les backends à essayer d'après la propriété système.
     * @return Liste ordonnée des backends à essayer
     */
    public static List<RenderBackend> preferred() {
        return preferred(System.getProperty(SELECTION_PROPERTY));
    }

    /**
     * Retourne les noms des backends connus, séparés par des virgules.
     * @return Noms disponibles pour le forçage
     */
    public static String names() {
        return String.join(", ", all().stream().map(RenderBackend::name).toList());
    }
}
