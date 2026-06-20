package com.enhancedechest.util;

/**
 * Parses and formats human-friendly durations.
 *
 * <p>Accepted input units: {@code s} (second), {@code m} (minute), {@code h} (hour),
 * {@code d} (day), {@code w} (week), {@code mo} (month) and {@code y} (year). A month is
 * approximated as 30 days and a year as 365 days. Simple values look like {@code 20s},
 * {@code 5m} or {@code 1h}; complex values join several components with underscores, e.g.
 * {@code 1d_2h_30m_15s}.
 *
 * <p>All methods are pure and side-effect free, which keeps them unit-testable.
 */
public final class DurationFormat {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR   = 60L * MINUTE;
    private static final long DAY    = 24L * HOUR;
    private static final long WEEK   = 7L * DAY;
    private static final long MONTH  = 30L * DAY;   // approximation
    private static final long YEAR   = 365L * DAY;  // approximation

    private DurationFormat() {
    }

    /**
     * Parses a duration string into milliseconds.
     *
     * <p>Examples: {@code 20s}, {@code 5m}, {@code 1h}, {@code 1d_2h_30m_15s}.
     *
     * @param input the duration string (case-insensitive, components separated by {@code _})
     * @return the duration in milliseconds (always positive)
     * @throws IllegalArgumentException if the string is null, empty or malformed
     */
    public static long parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Duration is null");
        }
        String trimmed = input.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Duration is empty");
        }

        long total = 0L;
        for (String component : trimmed.split("_")) {
            if (component.isEmpty()) {
                throw new IllegalArgumentException("Empty component in duration: " + input);
            }
            total += parseComponent(component, input);
        }
        if (total <= 0L) {
            throw new IllegalArgumentException("Duration must be positive: " + input);
        }
        return total;
    }

    /** Parses a single {@code <number><unit>} component into milliseconds. */
    private static long parseComponent(String component, String original) {
        // Find the boundary between the leading digits and the trailing unit letters.
        int split = 0;
        while (split < component.length() && Character.isDigit(component.charAt(split))) {
            split++;
        }
        if (split == 0 || split == component.length()) {
            throw new IllegalArgumentException("Invalid duration component '" + component + "' in: " + original);
        }

        String numberPart = component.substring(0, split);
        String unitPart = component.substring(split);

        long value;
        try {
            value = Long.parseLong(numberPart);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number in duration component '" + component + "'", ex);
        }

        long unitMillis = unitMillis(unitPart, component, original);
        return value * unitMillis;
    }

    /** Maps a unit suffix to its millisecond length. */
    private static long unitMillis(String unit, String component, String original) {
        switch (unit) {
            case "s":  return SECOND;
            case "m":  return MINUTE;
            case "h":  return HOUR;
            case "d":  return DAY;
            case "w":  return WEEK;
            case "mo": return MONTH;
            case "y":  return YEAR;
            default:
                throw new IllegalArgumentException("Unknown duration unit '" + unit + "' in component '"
                        + component + "' of: " + original);
        }
    }

    /**
     * Formats a remaining duration as a compact human-friendly string showing the two most
     * significant non-zero units, e.g. {@code "23h 45m"} or {@code "1d 4h"}.
     *
     * @param millis the remaining time in milliseconds
     * @return a short label; {@code "0s"} when the duration is zero or negative
     */
    public static String formatRemaining(long millis) {
        if (millis <= 0L) {
            return "0s";
        }

        long[] lengths = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND};
        String[] suffixes = {"y", "mo", "w", "d", "h", "m", "s"};

        StringBuilder out = new StringBuilder();
        long remaining = millis;
        int shown = 0;
        for (int i = 0; i < lengths.length && shown < 2; i++) {
            long count = remaining / lengths[i];
            if (count == 0) {
                continue; // skip zero units; show the two most significant non-zero ones
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(count).append(suffixes[i]);
            remaining -= count * lengths[i];
            shown++;
        }

        // Fallback: a sub-second positive value rounds up to "1s".
        return out.length() == 0 ? "1s" : out.toString();
    }
}
