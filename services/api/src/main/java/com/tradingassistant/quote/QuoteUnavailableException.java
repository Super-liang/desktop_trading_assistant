package com.tradingassistant.quote;

public class QuoteUnavailableException extends RuntimeException {
    public QuoteUnavailableException(String message) { super(message); }
    public QuoteUnavailableException(String message, Throwable cause) { super(message, cause); }
}
