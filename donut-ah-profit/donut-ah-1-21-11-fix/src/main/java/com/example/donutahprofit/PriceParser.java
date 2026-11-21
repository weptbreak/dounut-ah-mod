package com.example.donutahprofit;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls a price like "$1.5K", "2,500,000" or "3 million" out of lore lines. */
public final class PriceParser {
    private PriceParser() {}

    private static final Pattern NUM = Pattern.compile(
            "([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(thousand|million|billion|trillion|[kmbt](?![a-z]))?",
            Pattern.CASE_INSENSITIVE);

    /** A line that is just a currency symbol or icon followed by an amount, like "$ 2K". */
    private static final Pattern BARE = Pattern.compile(
            "^[^0-9A-Za-z]*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(thousand|million|billion|trillion|[kmbt])?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** Returns the total listing price, or null if none was found. */
    public static Double findPrice(List<String> lines) {
        // 1) a line that says "price"
        for (String line : lines) {
            String low = line.toLowerCase(Locale.ROOT);
            int i = low.indexOf("price");
            if (i < 0) continue;
            Matcher m = NUM.matcher(line.substring(i + 5));
            if (!m.find()) continue;
            Double v = toValue(m.group(1), m.group(2));
            if (v != null) return v;
        }
        // 2) a line that is only a symbol/icon and an amount, e.g. "$ 2K"
        for (String line : lines) {
            Matcher m = BARE.matcher(line.strip());
            if (!m.matches()) continue;
            Double v = toValue(m.group(1), m.group(2));
            if (v != null) return v;
        }
        return null;
    }

    private static Double toValue(String num, String suf) {
        try {
            double v = Double.parseDouble(num.replace(",", ""));
            if (suf != null && !suf.isEmpty()) {
                switch (Character.toLowerCase(suf.charAt(0))) {
                    case 'k' -> v *= 1e3;
                    case 'm' -> v *= 1e6;
                    case 'b' -> v *= 1e9;
                    case 't' -> v *= 1e12;
                    default -> {}
                }
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String money(double v) {
        String sign = v < 0 ? "-" : "";
        double a = Math.abs(v);
        String s;
        if (a >= 1e12) s = trim(a / 1e12) + "T";
        else if (a >= 1e9) s = trim(a / 1e9) + "B";
        else if (a >= 1e6) s = trim(a / 1e6) + "M";
        else if (a >= 1e3) s = trim(a / 1e3) + "K";
        else s = trim(a);
        return sign + "$" + s;
    }

    private static String trim(double x) {
        String s = String.format(Locale.ROOT, "%.2f", x);
        return s.replaceAll("\\.?0+$", "");
    }
}
