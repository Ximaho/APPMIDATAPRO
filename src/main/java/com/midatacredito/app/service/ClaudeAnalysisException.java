package com.midatacredito.app.service;

/**
 * Error controlado durante el análisis con Claude. El mensaje es apto para mostrarse al usuario.
 */
public class ClaudeAnalysisException extends RuntimeException {

    public ClaudeAnalysisException(String message) {
        super(message);
    }

    public ClaudeAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
