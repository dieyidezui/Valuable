package com.dieyidezui.valuable.exceptions;

/**
 * Valuable 抛出的异常
 */
public class ValuableException extends RuntimeException {
    public ValuableException(Throwable cause) {
        super(cause);
    }

    public ValuableException(String message) {
        super(message);
    }
}
