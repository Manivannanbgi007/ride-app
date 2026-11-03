package com.fooddelivery.orderservice.client;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.function.Predicate;

import org.springframework.web.client.ResourceAccessException;

/**
 * Which failures are worth one more attempt (resilience4j.retry ... retry-exception-predicate): the connection
 * failed (refused, reset), typically while the other service restarts. Not worth it: timeouts (the service is slow,
 * asking again doubles the wait), an unknown host (it will not appear in a moment), and error answers.
 */
public class ConnectionFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        boolean connectionProblem = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) {
                return false;
            }
            if (cause instanceof ResourceAccessException) {
                connectionProblem = true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return connectionProblem;
    }
}
