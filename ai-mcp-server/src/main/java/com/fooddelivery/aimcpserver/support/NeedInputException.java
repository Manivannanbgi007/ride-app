package com.fooddelivery.aimcpserver.support;

/**
 * A tool cannot go on until the user answers a question (which restaurant, which address, how to pay...). The message
 * starts with NEED_INPUT or AMBIGUOUS and is handed to the model as a normal result (not an error): the model must
 * relay the question in plain words, never ask the user for internal ids.
 */
public class NeedInputException extends RuntimeException {

    public NeedInputException(String message) {
        super(message);
    }
}
