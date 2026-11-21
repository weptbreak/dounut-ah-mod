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

    /** Returns the total listing price, or null if no "price" line was found. */
    public static Double findPrice(List<String> lines) {
        for (String line : lines) {
            String low = line.toLowerCase(Locale.ROOT);
            int i = low.indexOf("price");
            if (i < 0) continue;
            Matcher m = NUM.matcher(line.substring(i + 5));
            if (!m.find()) continue;
            try {
                double v = Double.parseDouble(m.group(1).replace(",", ""));
                String suf = m.group(2);
                if (suf != null) {
                    switch (Character.toLowerCase(suf.charAt(0))) {
                        case 'k' -> v *= 1e3;
                        case 'm' -> v *= 1e6;
                        case 'b' -> v *= 1e9;
                        case 't' -> v *= 1e12;
                        default -> {}
                    }
                }
                return v;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
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
