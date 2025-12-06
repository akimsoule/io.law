package bj.gouv.sgg.exception;

/**
 * Exception levée lors de l'envoi de notifications (Telegram, etc.)
 */
public class NotificationException extends RuntimeException {
    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
