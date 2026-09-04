/**
 * Projet POO Ray Tracing - Accélération Metal
 *
 * (c) 2025 Maël DEMORY
 */

package imaging.metal;

/**
 * Signale qu'un rendu Metal n'a pas pu aboutir. L'appelant est censé retomber
 * sur un rendu CPU plutôt que d'interrompre le programme.
 */
public class MetalException extends RuntimeException {

    /**
     * Construit l'exception avec un message.
     * @param message Description de l'échec
     */
    public MetalException(String message) {
        super(message);
    }

    /**
     * Construit l'exception avec un message et sa cause.
     * @param message Description de l'échec
     * @param cause Cause d'origine
     */
    public MetalException(String message, Throwable cause) {
        super(message, cause);
    }
}
