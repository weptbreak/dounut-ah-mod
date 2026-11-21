package com.example.donutahprofit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Read-only client mod: scans the Donut SMP auction house screen, remembers unit prices,
 * and adds market value / resell price / profit to item tooltips. It never clicks,
 * buys or sends anything to the server.
 */
public class DonutAhProfitClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("donut-ah-profit");

    private final Set<String> seen = new HashSet<>();
    private int tick = 0;

    @Override
    public void onInitializeClient() {
        AhData.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ItemTooltipCallback.EVENT.register(this::onTooltip);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> AhData.save());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    private static AbstractContainerScreen<?> containerScreen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof AbstractContainerScreen<?> s ? s : null;
    }

    private static boolean isAh(AbstractContainerScreen<?> s) {
        if (AhData.settings.anyContainer) return true;
        String title = s.getTitle().getString().toLowerCase(Locale.ROOT);
        for (String k : AhData.settings.titleKeywords) {
            if (!k.isBlank() && title.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private void onTick(Minecraft mc) {
        tick++;
        if (tick % 1200 == 0 && AhData.dirty) AhData.save();
        if (tick % 10 != 0) return;
        AbstractContainerScreen<?> s = containerScreen();
        if (s == null || !isAh(s)) return;
        for (Slot slot : s.getMenu().slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Listing l = Listing.fromStack(st);
            if (l != null && seen.add(l.signature())) {
                AhData.record(l.key(), l.unit());
            }
        }
    }

    private void onTooltip(ItemStack stack, Item.TooltipContext ctx, TooltipFlag flag, List<Component> lines) {
        AbstractContainerScreen<?> s = containerScreen();
        if (s == null) return;
        boolean ah = isAh(s);
        AhData.Settings cfg = AhData.settings;

        if (cfg.debug) {
            lines.add(Component.literal("[AHP] screen title: \"" + s.getTitle().getString()
                    + "\" matched=" + ah).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!ah) return;

        List<String> strs = new ArrayList<>();
        for (Component c : lines) strs.add(c.getString());
        Listing l = Listing.fromLines(stack, strs);
        if (l == null) {
            if (cfg.debug) lines.add(Component.literal("[AHP] no price line found").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        lines.add(Component.empty());
        lines.add(Component.literal("AH Profit").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal("Listed: " + PriceParser.money(l.price())
                + (l.count() > 1 ? " (" + PriceParser.money(l.unit()) + " each)" : ""))
                .withStyle(ChatFormatting.GRAY));

        int n = AhData.count(l.key());
        OptionalDouble med = AhData.median(l.key());
        if (med.isEmpty() || n < cfg.minSamples) {
            lines.add(Component.literal("Gathering data (" + n + "/" + cfg.minSamples
                    + " listings seen) - browse more pages").withStyle(ChatFormatting.YELLOW));
            return;
        }

        double worth = med.getAsDouble() * l.count();
        double sellAt = worth * (1 - cfg.undercutPercent / 100.0);
        double net = sellAt * (1 - cfg.feePercent / 100.0);
        double profit = net - l.price();
        double roi = profit / l.price() * 100.0;
        ChatFormatting col = profit > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;

        lines.add(Component.literal("Worth: " + PriceParser.money(worth) + " (median of " + n + ")")
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Resell at: " + PriceParser.money(sellAt)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Profit: " + (profit > 0 ? "+" : "") + PriceParser.money(profit)
                + String.format(Locale.ROOT, " (%+.1f%%)", roi)).withStyle(col));
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommandManager.literal("ahp")
                .then(ClientCommandManager.literal("fee")
                        .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                .executes(c -> {
                                    AhData.settings.feePercent = DoubleArgumentType.getDouble(c, "percent");
                                    AhData.save();
                                    return msg(c.getSource(), "Sell fee set to " + AhData.settings.feePercent + "%");
                                })))
                .then(ClientCommandManager.literal("undercut")
                        .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                .executes(c -> {
                                    AhData.settings.undercutPercent = DoubleArgumentType.getDouble(c, "percent");
                                    AhData.save();
                                    return msg(c.getSource(), "Undercut set to " + AhData.settings.undercutPercent + "%");
                                })))
                .then(ClientCommandManager.literal("keyword")
                        .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String t = StringArgumentType.getString(c, "text");
                                    AhData.settings.titleKeywords = new ArrayList<>(
                                            Arrays.stream(t.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList());
                                    AhData.save();
                                    return msg(c.getSource(), "AH title keywords: " + AhData.settings.titleKeywords);
                                })))
                .then(ClientCommandManager.literal("any")
                        .then(ClientCommandManager.argument("on", BoolArgumentType.bool())
                                .executes(c -> {
                                    AhData.settings.anyContainer = BoolArgumentType.getBool(c, "on");
                                    AhData.save();
                                    return msg(c.getSource(), "Treat any container as AH: " + AhData.settings.anyContainer);
                                })))
                .then(ClientCommandManager.literal("debug")
                        .executes(c -> {
                            AhData.settings.debug = !AhData.settings.debug;
                            AhData.save();
                            return msg(c.getSource(), "Debug: " + AhData.settings.debug);
                        }))
                .then(ClientCommandManager.literal("clear")
                        .executes(c -> {
                            AhData.clearPrices();
                            seen.clear();
                            AhData.save();
                            return msg(c.getSource(), "Price history cleared.");
                        }))
                .then(ClientCommandManager.literal("status")
                        .executes(c -> msg(c.getSource(), "fee=" + AhData.settings.feePercent
                                + "% undercut=" + AhData.settings.undercutPercent
                                + "% keywords=" + AhData.settings.titleKeywords
                                + " any=" + AhData.settings.anyContainer
                                + " items tracked=" + AhData.prices.size()))));
    }

    private static int msg(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal("[AHP] " + text));
        return 1;
    }
}
