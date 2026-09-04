/**
 * Projet POO Ray Tracing - Accélération Metal
 *
 * (c) 2025 Maël DEMORY
 */

package imaging.metal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Liaison vers la bibliothèque native {@code libraytracer_metal.dylib} via la
 * Foreign Function &amp; Memory API.
 *
 * <p>Le chargement est paresseux et ne lève jamais : si la bibliothèque est
 * absente ou si la plateforme n'a pas de Metal, {@link #isAvailable()} répond
 * false et l'appelant retombe sur un rendu CPU.</p>
 */
final class MetalBridge {

    /** Nom du fichier produit par src/main/native/build.sh. */
    private static final String LIBRARY_NAME = "libraytracer_metal.dylib";

    /** Propriété système permettant de pointer explicitement la bibliothèque. */
    private static final String LIBRARY_PROPERTY = "raytracer.metal.library";

    /** Taille du tampon recevant le nom du périphérique. */
    private static final int NAME_CAPACITY = 256;

    /** Raison de l'indisponibilité, ou null si la liaison est établie. */
    private static final String FAILURE;

    /** rtm_available(). */
    private static final MethodHandle AVAILABLE;

    /** rtm_default_device_name(char*, int). */
    private static final MethodHandle DEFAULT_DEVICE_NAME;

    /** rtm_create(const char*, char**). */
    private static final MethodHandle CREATE;

    /** rtm_release(void*). */
    private static final MethodHandle RELEASE;

    /** rtm_free_string(char*). */
    private static final MethodHandle FREE_STRING;

    /** rtm_render(...). */
    private static final MethodHandle RENDER;

    static {
        String failure = null;
        MethodHandle available = null;
        MethodHandle defaultDeviceName = null;
        MethodHandle create = null;
        MethodHandle release = null;
        MethodHandle freeString = null;
        MethodHandle render = null;

        try {
            Path library = locateLibrary();
            if (library == null) {
                failure = "bibliothèque " + LIBRARY_NAME + " introuvable "
                        + "(exécuter src/main/native/build.sh)";
            } else {
                SymbolLookup lookup = SymbolLookup.libraryLookup(library, Arena.global());
                Linker linker = Linker.nativeLinker();

                available = linker.downcallHandle(
                        lookup.find("rtm_available").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT));

                defaultDeviceName = linker.downcallHandle(
                        lookup.find("rtm_default_device_name").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

                create = linker.downcallHandle(
                        lookup.find("rtm_create").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS, ValueLayout.ADDRESS));

                release = linker.downcallHandle(
                        lookup.find("rtm_release").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

                freeString = linker.downcallHandle(
                        lookup.find("rtm_free_string").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

                render = linker.downcallHandle(
                        lookup.find("rtm_render").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,   // contexte
                                ValueLayout.ADDRESS,   // params
                                ValueLayout.JAVA_INT,  // taille des params en octets
                                ValueLayout.ADDRESS,   // flottants
                                ValueLayout.JAVA_INT,  // nombre de flottants
                                ValueLayout.ADDRESS,   // noeuds
                                ValueLayout.JAVA_INT,  // nombre d'entiers
                                ValueLayout.ADDRESS,   // sortie
                                ValueLayout.JAVA_INT,  // nombre de flottants en sortie
                                ValueLayout.JAVA_INT,  // largeur
                                ValueLayout.JAVA_INT,  // hauteur
                                ValueLayout.ADDRESS)); // message d'erreur
            }
        } catch (Throwable t) {
            failure = "chargement de " + LIBRARY_NAME + " impossible: " + t.getMessage();
        }

        FAILURE = failure;
        AVAILABLE = available;
        DEFAULT_DEVICE_NAME = defaultDeviceName;
        CREATE = create;
        RELEASE = release;
        FREE_STRING = freeString;
        RENDER = render;
    }

    /**
     * Empêche l'instanciation.
     */
    private MetalBridge() {
    }

    /**
     * Cherche la bibliothèque native aux emplacements usuels.
     * @return Chemin existant, ou null si aucun candidat n'est trouvé
     */
    private static Path locateLibrary() {
        String explicit = System.getProperty(LIBRARY_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit);
            return Files.isRegularFile(path) ? path : null;
        }

        List<Path> candidates = List.of(
                Path.of("target", "native", LIBRARY_NAME),
                Path.of("native", LIBRARY_NAME),
                Path.of(LIBRARY_NAME));

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Indique si le pont natif et un périphérique Metal sont utilisables.
     * @return true si le rendu Metal est possible
     */
    static boolean isAvailable() {
        if (FAILURE != null) {
            return false;
        }
        try {
            return (int) AVAILABLE.invokeExact() != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Retourne la raison pour laquelle Metal est indisponible.
     * @return Message explicatif, ou null si Metal est utilisable
     */
    static String unavailabilityReason() {
        if (FAILURE != null) {
            return FAILURE;
        }
        return isAvailable() ? null : "aucun périphérique Metal sur ce système";
    }

    /**
     * Retourne le nom du GPU Metal par défaut.
     * @return Nom du périphérique, ou null s'il n'y en a pas
     */
    static String defaultDeviceName() {
        if (FAILURE != null) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(NAME_CAPACITY);
            int length = (int) DEFAULT_DEVICE_NAME.invokeExact(buffer, NAME_CAPACITY);
            return length > 0 ? buffer.getString(0) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Crée un contexte Metal à partir de la source du kernel.
     * @param shaderSource Source Metal Shading Language
     * @return Poignée native du contexte
     * @throws MetalException si la compilation ou l'initialisation échoue
     */
    static MemorySegment create(String shaderSource) {
        if (FAILURE != null) {
            throw new MetalException(FAILURE);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment source = arena.allocateFrom(shaderSource);
            MemorySegment errorSlot = arena.allocate(ValueLayout.ADDRESS);
            errorSlot.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            MemorySegment handle = (MemorySegment) CREATE.invokeExact(source, errorSlot);
            if (handle.address() == 0) {
                throw new MetalException(readAndFreeError(errorSlot, "création du contexte Metal"));
            }
            return handle;
        } catch (MetalException e) {
            throw e;
        } catch (Throwable t) {
            throw new MetalException("appel natif rtm_create: " + t, t);
        }
    }

    /**
     * Détruit un contexte Metal.
     * @param handle Poignée native retournée par {@link #create(String)}
     */
    static void release(MemorySegment handle) {
        if (FAILURE != null || handle == null || handle.address() == 0) {
            return;
        }
        try {
            RELEASE.invokeExact(handle);
        } catch (Throwable t) {
            // La libération ne doit jamais faire échouer un rendu déjà terminé.
        }
    }

    /**
     * Exécute le kernel sur l'image entière.
     * @param handle Contexte Metal
     * @param scene Scène linéarisée
     * @param out Tampon de sortie, width * height * 3 flottants RGB
     * @throws MetalException si le kernel échoue
     */
    static void render(MemorySegment handle, MetalScene scene, float[] out) {
        if (FAILURE != null) {
            throw new MetalException(FAILURE);
        }
        try (Arena arena = Arena.ofConfined()) {
            int[] params = scene.params();
            float[] floats = scene.floats();
            int[] nodes = scene.nodes();

            MemorySegment paramsSegment = arena.allocateFrom(ValueLayout.JAVA_INT, params);
            MemorySegment floatSegment = arena.allocateFrom(ValueLayout.JAVA_FLOAT, floats);
            MemorySegment nodeSegment = nodes.length > 0
                    ? arena.allocateFrom(ValueLayout.JAVA_INT, nodes)
                    : arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment outSegment = arena.allocate(ValueLayout.JAVA_FLOAT, out.length);
            MemorySegment errorSlot = arena.allocate(ValueLayout.ADDRESS);
            errorSlot.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            int status = (int) RENDER.invokeExact(
                    handle,
                    paramsSegment, params.length * Integer.BYTES,
                    floatSegment, floats.length,
                    nodeSegment, nodes.length,
                    outSegment, out.length,
                    scene.width(), scene.height(),
                    errorSlot);

            if (status != 0) {
                throw new MetalException(readAndFreeError(errorSlot, "exécution du kernel Metal"));
            }

            MemorySegment.copy(outSegment, ValueLayout.JAVA_FLOAT, 0, out, 0, out.length);
        } catch (MetalException e) {
            throw e;
        } catch (Throwable t) {
            throw new MetalException("appel natif rtm_render: " + t, t);
        }
    }

    /**
     * Lit le message d'erreur natif et libère la chaîne allouée côté C.
     * @param errorSlot Emplacement contenant le pointeur de message
     * @param fallback Message utilisé si le natif n'en a pas fourni
     * @return Message exploitable
     */
    private static String readAndFreeError(MemorySegment errorSlot, String fallback) {
        MemorySegment pointer = errorSlot.get(ValueLayout.ADDRESS, 0);
        if (pointer.address() == 0) {
            return fallback;
        }
        String message = pointer.reinterpret(Long.MAX_VALUE).getString(0);
        try {
            FREE_STRING.invokeExact(pointer);
        } catch (Throwable t) {
            // Une fuite d'un message d'erreur est préférable à une exception ici.
        }
        return message;
    }
}
