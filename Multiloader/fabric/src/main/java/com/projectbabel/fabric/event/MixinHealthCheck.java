package com.projectbabel.fabric.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.debug.MixinHitCounter;
import com.projectbabel.api.TranslationContext;
import com.projectbabel.api.TranslationSurface;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.minecraft.tooltip.EnchantmentTooltipTranslator;
import com.projectbabel.core.pipeline.TranslationPipeline;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verificação de saúde dos Mixins e pre-warm de itens visíveis.
 */
public class MixinHealthCheck {

    private static final int PREWARM_INTERVAL_TICKS = 20;
    private static final AtomicInteger tickCounter = new AtomicInteger(0);
    private static final AtomicLong lastDiagTime = new AtomicLong(0);
    private static boolean mixinWarningLogged = false;
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
    }

    private static void onClientTick() {
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;

        int tick = tickCounter.incrementAndGet();

        if (tick % PREWARM_INTERVAL_TICKS == 0) {
            prewarmVisibleItems();
        }

        if (tick % 600 == 0) {
            checkMixinHealth();
        }
    }

    private static void prewarmVisibleItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) prewarmItem(stack);
        }

        prewarmItem(mc.player.getMainHandItem());
        prewarmItem(mc.player.getOffhandItem());

        if (mc.screen != null) {
            try {
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty()) prewarmItem(stack);
                }
            } catch (Exception ignored) {}
        }
    }

    private static void prewarmItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        try {
            if (stack.hasCustomHoverName() && !ProjectBabelCommon.config().isTranslateRenamedItems()) {
                TranslationSkipRegistry.skip(stack.getHoverName());
                return;
            }
            TranslationPipeline.translateComponent(stack.getHoverName(), TranslationContext.preload(TranslationSurface.TOOLTIP));
            prewarmTooltipLines(stack);
        } catch (Exception ignored) {}
    }

    private static void prewarmTooltipLines(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        List<Component> lines = stack.getTooltipLines(mc.player, TooltipFlag.NORMAL);
        EnchantmentTooltipTranslator.prewarmTooltipLines(lines);
    }

    private static void checkMixinHealth() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        long hits = MixinHitCounter.drain();
        long now  = System.currentTimeMillis();

        if (hits == 0 && !mixinWarningLogged) {
            long elapsed = now - lastDiagTime.get();
            if (lastDiagTime.get() > 0 && elapsed > 25_000) {
                ProjectBabelCommon.LOGGER.warn(
                    "[projectbabel] AVISO: Mixins de Font/GuiGraphics podem estar " +
                    "inativos (0 interceptacoes em 30s). Possivelmente conflito com outro mod. " +
                    "O pre-warm via tick continua funcionando para itens da hotbar.");
                mixinWarningLogged = true;
            }
        } else if (hits > 0) {
            mixinWarningLogged = false;
        }

        lastDiagTime.set(now);
        ProjectBabelCommon.LOGGER.debug(
            "[projectbabel] Saude: {} hits de mixin em 30s, cache={} entradas, pending={}",
            hits,
            TranslationManager.getInstance().getCache().size(),
            TranslationManager.getInstance().getPendingCount());
    }
}
