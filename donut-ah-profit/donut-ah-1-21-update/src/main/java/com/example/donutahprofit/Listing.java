package com.example.donutahprofit;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** One AH listing read from an item stack. */
public record Listing(String key, double price, int count, double unit, String signature) {

    public static Listing fromStack(ItemStack st) {
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return null;
        List<String> lines = new ArrayList<>();
        for (Component c : lore.lines()) lines.add(c.getString());
        return fromLines(st, lines);
    }

    public static Listing fromLines(ItemStack st, List<String> lines) {
        Double price = PriceParser.findPrice(lines);
        if (price == null || price <= 0) return null;
        int count = Math.max(1, st.getCount());
        String seller = "";
        for (String l : lines) {
            if (l.toLowerCase(Locale.ROOT).contains("seller")) {
                seller = l;
                break;
            }
        }
        String key = keyOf(st);
        return new Listing(key, price, count, price / count, key + "|" + price + "|" + count + "|" + seller);
    }

    /** Item id, plus enchantments so a Sharpness V book isn't priced like a Mending book. */
    static String keyOf(ItemStack s) {
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        List<String> parts = new ArrayList<>();
        addEnchants(parts, s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        addEnchants(parts, s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
        if (parts.isEmpty()) return id;
        Collections.sort(parts);
        return id + "#" + String.join(",", parts);
    }

    private static void addEnchants(List<String> out, ItemEnchantments e) {
        for (var holder : e.keySet()) {
            out.add(holder.unwrapKey().map(k -> k.location().toString()).orElse("?") + ":" + e.getLevel(holder));
        }
    }
}
