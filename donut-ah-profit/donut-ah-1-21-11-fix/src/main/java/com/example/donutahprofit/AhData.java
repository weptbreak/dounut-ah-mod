package com.example.donutahprofit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Settings + locally observed unit prices, saved as JSON in the config folder. */
public final class AhData {
    private AhData() {}

    public static class Settings {
        /** Percent the server takes when you sell (set this to the real AH fee). */
        public double feePercent = 0;
        /** How far below market value to list, in percent. */
        public double undercutPercent = 1;
        /** Listings of an item you must have seen before values are shown. */
        public int minSamples = 3;
        public int maxSamples = 40;
        /** A container screen counts as the AH if its title contains one of these. */
        public List<String> titleKeywords = new ArrayList<>(List.of("auction"));
        /** Treat every container screen as the AH (use if the title can't be matched). */
        public boolean anyContainer = false;
        /** Shows the screen title and parse status in tooltips. */
        public boolean debug = false;
    }

    public static Settings settings = new Settings();
    public static Map<String, List<Double>> prices = new HashMap<>();
    public static boolean dirty = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path settingsFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("donut-ah-profit-settings.json");
    }

    private static Path pricesFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("donut-ah-profit-prices.json");
    }

    public static void load() {
        try {
            if (Files.exists(settingsFile())) {
                Settings s = GSON.fromJson(Files.readString(settingsFile()), Settings.class);
                if (s != null) settings = s;
            }
            if (Files.exists(pricesFile())) {
                Map<String, List<Double>> p = GSON.fromJson(Files.readString(pricesFile()),
                        new TypeToken<Map<String, List<Double>>>() {}.getType());
                if (p != null) prices = p;
            }
        } catch (Exception e) {
            DonutAhProfitClient.LOG.warn("Could not load data", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(settingsFile(), GSON.toJson(settings));
            Files.writeString(pricesFile(), GSON.toJson(prices));
            dirty = false;
        } catch (IOException e) {
            DonutAhProfitClient.LOG.warn("Could not save data", e);
        }
    }

    public static void record(String key, double unitPrice) {
        List<Double> list = prices.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(unitPrice);
        while (list.size() > settings.maxSamples) list.remove(0);
        dirty = true;
    }

    public static int count(String key) {
        List<Double> l = prices.get(key);
        return l == null ? 0 : l.size();
    }

    /** Median unit price of recent listings, or empty if none. */
    public static OptionalDouble median(String key) {
        List<Double> l = prices.get(key);
        if (l == null || l.isEmpty()) return OptionalDouble.empty();
        List<Double> s = new ArrayList<>(l);
        Collections.sort(s);
        int n = s.size();
        return OptionalDouble.of(n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0);
    }

    public static void clearPrices() {
        prices.clear();
        dirty = true;
    }
}
