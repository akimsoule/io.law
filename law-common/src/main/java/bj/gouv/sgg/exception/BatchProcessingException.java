package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'une erreur de traitement par lot (batch).
 */
public class BatchProcessingException extends LawProcessingException {
    
    private final String batchJobName;
    private final int itemsFailed;
    private final int itemsProcessed;
    
    public BatchProcessingException(String batchJobName, String message) {
        super(String.format("Batch job '%s' failed: %s", batchJobName, message));
        this.batchJobName = batchJobName;
        this.itemsFailed = 0;
        this.itemsProcessed = 0;
    }
    
    public BatchProcessingException(String batchJobName, int itemsFailed, int itemsProcessed, String message) {
        super(String.format("Batch job '%s' failed: %s (processed: %d, failed: %d)", 
                            batchJobName, message, itemsProcessed, itemsFailed));
        this.batchJobName = batchJobName;
        this.itemsFailed = itemsFailed;
        this.itemsProcessed = itemsProcessed;
    }
    
    public BatchProcessingException(String batchJobName, String message, Throwable cause) {
        super(String.format("Batch job '%s' failed: %s", batchJobName, message), cause);
        this.batchJobName = batchJobName;
        this.itemsFailed = 0;
        this.itemsProcessed = 0;
    }
    
    public String getBatchJobName() {
        return batchJobName;
    }
    
    public int getItemsFailed() {
        return itemsFailed;
    }
    
    public int getItemsProcessed() {
        return itemsProcessed;
    }
}
