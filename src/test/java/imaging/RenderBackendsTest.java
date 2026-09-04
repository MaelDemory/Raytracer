package imaging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie la sélection du backend de rendu.
 *
 * <p>Ces tests ne touchent aucun GPU : ils portent sur l'ordre de préférence et
 * sur le forçage, qui décident du chemin emprunté au lancement.</p>
 */
class RenderBackendsTest {

    /**
     * Retourne les noms des backends d'une liste.
     */
    private static List<String> namesOf(List<RenderBackend> backends) {
        return backends.stream().map(RenderBackend::name).toList();
    }

    @Test
    void lApiNativeEstSondeeAvantLApiPortable() {
        // Sur macOS, Metal parle au GPU directement là où Vulkan passe par une
        // couche de traduction : l'ordre n'est pas arbitraire.
        assertEquals(List.of("metal", "vulkan"), namesOf(RenderBackends.all()));
    }

    @Test
    void sansForcageTousLesBackendsSontEssayes() {
        assertEquals(namesOf(RenderBackends.all()), namesOf(RenderBackends.preferred(null)));
        assertEquals(namesOf(RenderBackends.all()), namesOf(RenderBackends.preferred("")));
        assertEquals(namesOf(RenderBackends.all()), namesOf(RenderBackends.preferred("   ")));
    }

    @Test
    void unForcageNeRetientQueLeBackendDemande() {
        assertEquals(List.of("metal"), namesOf(RenderBackends.preferred("metal")));
        assertEquals(List.of("vulkan"), namesOf(RenderBackends.preferred("vulkan")));
    }

    @Test
    void leForcageIgnoreLaCasseEtLesEspaces() {
        assertEquals(List.of("vulkan"), namesOf(RenderBackends.preferred("  VULKAN ")));
        assertEquals(List.of("metal"), namesOf(RenderBackends.preferred("Metal")));
    }

    @Test
    void leForcageCpuNeLaisseAucunBackendAccelere() {
        assertTrue(RenderBackends.preferred("cpu").isEmpty());
        assertTrue(RenderBackends.preferred("CPU").isEmpty());
    }

    @Test
    void unNomInconnuRetombeSurLaSelectionAutomatique() {
        // Une faute de frappe ne doit pas priver l'utilisateur d'accélération.
        assertEquals(namesOf(RenderBackends.all()), namesOf(RenderBackends.preferred("opencl")));
    }

    @Test
    void chaqueBackendSeDecritMemeIndisponible() {
        // describe() sert à tracer pourquoi un backend a été écarté : il doit
        // répondre sur n'importe quelle machine, GPU ou non.
        for (RenderBackend backend : RenderBackends.all()) {
            assertNotNull(backend.name());
            assertFalse(backend.name().isBlank());
            String description = backend.describe();
            assertNotNull(description, "describe() ne doit jamais renvoyer null");
            assertFalse(description.isBlank(), "describe() ne doit jamais être vide");
        }
    }

    @Test
    void laDisponibiliteEstSondableSansLeverDException() {
        for (RenderBackend backend : RenderBackends.all()) {
            assertDoesNotThrow(backend::isAvailable,
                    "isAvailable() doit répondre même sans pilote installé");
        }
    }
}
