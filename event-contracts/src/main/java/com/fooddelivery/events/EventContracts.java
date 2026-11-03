package com.fooddelivery.events;

import org.apache.avro.util.ClassSecurityValidator;
import org.apache.avro.util.ClassSecurityValidator.ClassSecurityPredicate;

/**
 * Setup that every service using these events needs once at startup.
 */
public final class EventContracts {

    /** Package of the classes generated from the .avsc schemas. */
    public static final String EVENTS_PACKAGE = "com.fooddelivery.events";

    /** A private lock: other code cannot hold it (unlike the class object a static synchronized method uses). */
    private static final Object LOCK = new Object();

    private static boolean trusted;

    private EventContracts() {
    }

    /**
     * Avro (since 1.12) creates classes only from trusted packages, to block deserialization attacks
     * through crafted schemas. Adds this library's event classes to the trusted ones, keeping the
     * existing rules for everything else.
     */
    public static void trustEventClasses() {
        synchronized (LOCK) {
            if (trusted) {
                return;
            }
            ClassSecurityPredicate existing = ClassSecurityValidator.getGlobal();
            ClassSecurityValidator.setGlobal(type -> isEventPackage(type.getPackageName()) || existing.isTrusted(type));
            trusted = true;
        }
    }

    /** This package or one below it (not merely a package whose name starts the same, like "...eventsx"). */
    static boolean isEventPackage(String packageName) {
        return packageName.equals(EVENTS_PACKAGE) || packageName.startsWith(EVENTS_PACKAGE + ".");
    }
}
