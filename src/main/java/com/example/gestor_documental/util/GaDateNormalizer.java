package com.example.gestor_documental.util;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GaDateNormalizer {

    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})(?:[ T].*)?$");
    private static final Pattern DMY_DATE = Pattern.compile("^(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})(?:\\s+.*)?$");
    private static final Pattern SQL_MONTH_DATE = Pattern.compile(
            "^(JAN(?:UARY)?|FEB(?:RUARY)?|MAR(?:CH)?|APR(?:IL)?|MAY|JUN(?:E)?|JUL(?:Y)?|AUG(?:UST)?|SEP(?:T|TEMBER)?|OCT(?:OBER)?|NOV(?:EMBER)?|DEC(?:EMBER)?)\\s+(\\d{1,2}),?\\s+(\\d{4})(?:\\s+.*)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DAY_MONTH_TEXT_DATE = Pattern.compile(
            "^(\\d{1,2})\\s+(JAN(?:UARY)?|FEB(?:RUARY)?|MAR(?:CH)?|APR(?:IL)?|MAY|JUN(?:E)?|JUL(?:Y)?|AUG(?:UST)?|SEP(?:T|TEMBER)?|OCT(?:OBER)?|NOV(?:EMBER)?|DEC(?:EMBER)?)\\s+(\\d{4})(?:\\s+.*)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("JAN", 1),
            Map.entry("JANUARY", 1),
            Map.entry("FEB", 2),
            Map.entry("FEBRUARY", 2),
            Map.entry("MAR", 3),
            Map.entry("MARCH", 3),
            Map.entry("APR", 4),
            Map.entry("APRIL", 4),
            Map.entry("MAY", 5),
            Map.entry("JUN", 6),
            Map.entry("JUNE", 6),
            Map.entry("JUL", 7),
            Map.entry("JULY", 7),
            Map.entry("AUG", 8),
            Map.entry("AUGUST", 8),
            Map.entry("SEP", 9),
            Map.entry("SEPT", 9),
            Map.entry("SEPTEMBER", 9),
            Map.entry("OCT", 10),
            Map.entry("OCTOBER", 10),
            Map.entry("NOV", 11),
            Map.entry("NOVEMBER", 11),
            Map.entry("DEC", 12),
            Map.entry("DECEMBER", 12)
    );

    private GaDateNormalizer() {
    }

    public static String toGaDate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim().replaceAll("\\s+", " ");
        if (text.isBlank()) {
            return "";
        }

        Matcher sqlMonth = SQL_MONTH_DATE.matcher(text);
        if (sqlMonth.matches()) {
            return format(sqlMonth.group(3), sqlMonth.group(1), sqlMonth.group(2), text);
        }

        Matcher dayMonthText = DAY_MONTH_TEXT_DATE.matcher(text);
        if (dayMonthText.matches()) {
            return format(dayMonthText.group(3), dayMonthText.group(2), dayMonthText.group(1), text);
        }

        String normalized = text.replace('.', '/').replace('-', '/');
        Matcher iso = ISO_DATE.matcher(normalized);
        if (iso.matches()) {
            return format(iso.group(1), iso.group(2), iso.group(3), text);
        }

        Matcher dmy = DMY_DATE.matcher(normalized);
        if (dmy.matches()) {
            return format(dmy.group(3), dmy.group(2), dmy.group(1), text);
        }

        return text.toUpperCase(Locale.ROOT);
    }

    private static String format(String year, String month, String day, String fallback) {
        return format(year, parseMonth(month), day, fallback);
    }

    private static String format(String year, Integer month, String day, String fallback) {
        if (month == null) {
            return fallback.toUpperCase(Locale.ROOT);
        }
        try {
            LocalDate parsed = LocalDate.of(Integer.parseInt(year), month, Integer.parseInt(day));
            return "%02d/%02d/%04d".formatted(parsed.getDayOfMonth(), parsed.getMonthValue(), parsed.getYear());
        } catch (RuntimeException exception) {
            return fallback.toUpperCase(Locale.ROOT);
        }
    }

    private static Integer parseMonth(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return MONTHS.get(value.toUpperCase(Locale.ROOT));
        }
    }
}
