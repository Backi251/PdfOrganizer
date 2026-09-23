package com.example.pdforganizer.domain;

/**
 * Error asociado a UN archivo especifico. El procesamiento nunca se detiene
 * por un ProcessingError individual; se acumulan y se muestran al finalizar.
 */
public final class ProcessingError {

    private final String fileName;
    private final String operation;
    private final String message;
    private final String cause;

    public ProcessingError(String fileName, String operation, String message, String cause) {
        this.fileName = fileName;
        this.operation = operation;
        this.message = message;
        this.cause = cause;
    }

    public String getFileName() { return fileName; }
    public String getOperation() { return operation; }
    public String getMessage() { return message; }
    public String getCause() { return cause; }

    @Override
    public String toString() {
        return "[" + operation + "] " + fileName + ": " + message
                + (cause != null ? " (causa: " + cause + ")" : "");
    }
}
