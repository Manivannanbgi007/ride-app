package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import com.fooddelivery.aimcpserver.support.ToolException;

/** Validation of tool arguments. The model produces them, so nothing is trusted: a bad value gets a clear message back. */
public final class ToolArgs {

    public static final int MAX_RANGE_DAYS = 366;

    private ToolArgs() {
    }

    /** An inclusive calendar-day range in the business time zone. */
    public record DateRange(LocalDate from, LocalDate to) {
    }

    public static long positiveId(String name, Long value) {
        if (value == null || value <= 0) {
            throw ToolException.invalid(name + " must be a positive number.");
        }
        return value;
    }

    public static int limit(Integer value, int defaultValue, int max) {
        if (value == null) {
            return defaultValue;
        }
        if (value < 1 || value > max) {
            throw ToolException.invalid("limit must be between 1 and " + max + ".");
        }
        return value;
    }

    public static int intInRange(String name, Integer value, int min, int max) {
        if (value == null || value < min || value > max) {
            throw ToolException.invalid(name + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    /** Optional text: null/blank becomes null; longer than max is refused (never silently cut). */
    public static String optionalText(String name, String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw ToolException.invalid(name + " is too long (at most " + max + " characters).");
        }
        return trimmed;
    }

    public static LocalDate date(String name, String value) {
        try {
            return LocalDate.parse(value == null ? "" : value.strip());
        } catch (DateTimeParseException e) {
            throw ToolException.invalid(name + " must be a date written yyyy-MM-dd, for example 2026-10-01.");
        }
    }

    /**
     * Both empty means today. Only from: until today. Only to: that single day. At most 366 days.
     * The clock is the business time zone's, so "today" is the platform's today.
     */
    public static DateRange dateRange(String from, String to, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        boolean noFrom = from == null || from.isBlank();
        boolean noTo = to == null || to.isBlank();
        LocalDate start;
        LocalDate end;
        if (noFrom && noTo) {
            start = today;
            end = today;
        } else if (noFrom) {
            end = date("toDate", to);
            start = end;
        } else {
            start = date("fromDate", from);
            end = noTo ? (today.isBefore(start) ? start : today) : date("toDate", to);
        }
        if (end.isBefore(start)) {
            throw ToolException.invalid("toDate must not be before fromDate.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw ToolException.invalid("The period may be at most " + MAX_RANGE_DAYS + " days.");
        }
        return new DateRange(start, end);
    }

    /** Case-insensitive enum value, or null when absent. The error lists the allowed values. */
    public static <E extends Enum<E>> E optionalEnum(String name, String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            StringBuilder allowed = new StringBuilder();
            for (E constant : type.getEnumConstants()) {
                allowed.append(allowed.isEmpty() ? "" : ", ").append(constant.name());
            }
            throw ToolException.invalid(name + " must be one of: " + allowed + ".");
        }
    }

    public static <E extends Enum<E>> E requiredEnum(String name, String value, Class<E> type) {
        E result = optionalEnum(name, value, type);
        if (result == null) {
            throw ToolException.invalid(name + " is required.");
        }
        return result;
    }
}
