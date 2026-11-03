package com.fooddelivery.aimcpserver.proposal;

/** Redis could not be reached or answered badly: proposals cannot be saved or read right now. */
public class ProposalStoreException extends RuntimeException {

    public ProposalStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
