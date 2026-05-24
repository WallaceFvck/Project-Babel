package com.projectbabel.forge.event;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.debug.MixinHitCounter;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.tooltip.TooltipTranslationController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

/**
 * Verificação de saúde dos Mixins e pre-warm de itens visíveis.
 *
 * Problema em modpacks: outros mods (JEI, REI, FTB Quests) também fazem
 * Mixin em Font/GuiGraphics com prioridades variadas. Em alguns casos,
 * nossos @ModifyVariable são sobrescritos ou o pipeline é redirecionado
 * e o texto nunca passa pelo nosso mixin.
 *
 * Solução complementar: a cada tick, pre-warm das traduções dos itens
 * visíveis na hotbar e inventário. Assim mesmo que o mixin de Font não
 * intercepte o draw, as traduções já estão no cache quando chegam.
 *
 * Também detecta se a traducao está funcionando e loga diagnóstico.
 */
@Mod.EventBusSubscriber(modid = ProjectBabelCommon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MixinHealthCheck {

    // Pre-warm a cada 20 ticks (1 segundo) — não todo tick para não sobrecarregar
    private static final int PREWARM_INTERVAL_TICKS = 20;
    private static final AtomicInteger tickCounter = new AtomicInteger(0);

    // Diagnóstico: quantas vezes o mixin foi acionado desde o último check
    private static final AtomicLong lastDiagTime = new AtomicLong(0);
    private static boolean mixinWarningLogged = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ProjectBabelCommon.config().isEnabled()) return;
        if (!LanguageDetector.shouldModBeActive()) return;

        int tick = tickCounter.incrementAndGet();

        // Pre-warm de itens visíveis
        if (tick % PREWARM_INTERVAL_TICKS == 0) {
            prewarmVisibleItems();
        }

        // Diagnóstico de saúde dos mixins a cada 30 segundos
        if (tick % 600 == 0) {
            checkMixinHealth();
        }
    }

    /**
     * Pre-aquece traduções dos itens na hotbar e inventário do player.
     * Garante que o cache já tem as traduções antes do draw acontecer,
     * contornando conflitos com outros mixins.
     */
    private static void prewarmVisibleItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (TextFilter.isMenuScreen() || TextFilter.isDebugScreenOpen()) return;


        // Hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) prewarmItem(stack);
        }

        // Item na mão principal e offhand
        prewarmItem(mc.player.getMainHandItem());
        prewarmItem(mc.player.getOffhandItem());

        // Se inventário está aberto, pré-aquece todos os slots visíveis
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
            TooltipTranslationController.prewarmItemName(stack);
            prewarmTooltipLines(stack);
        } catch (Exception ignored) {}
    }

    private static void prewarmTooltipLines(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        List<Component> lines = stack.getTooltipLines(mc.player, TooltipFlag.NORMAL);
        TooltipTranslationController.prewarmItemTooltipLines(lines, TooltipFlag.NORMAL);
    }

    /**
     * Verifica se os mixins estão sendo acionados.
     * Se não houver hits por 30s com o jogo rodando, loga aviso.
     */
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
