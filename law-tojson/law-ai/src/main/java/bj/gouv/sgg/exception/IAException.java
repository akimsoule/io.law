package bj.gouv.sgg.exception;

/**
 * Exception générique pour les erreurs liées aux providers IA.
 */
public class IAException extends RuntimeException {
    public IAException() { 
        super(); 
    }
    
    public IAException(String message) { 
        super(message); 
    }
    
    public IAException(String message, Throwable cause) { 
        super(message, cause); 
    }
    
    public IAException(Throwable cause) { 
        super(cause); 
    }
}
