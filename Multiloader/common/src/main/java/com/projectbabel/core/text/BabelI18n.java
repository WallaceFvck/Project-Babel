package com.projectbabel.core.text;

import com.projectbabel.core.engine.GoogleTranslateEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.projectbabel.core.service.TranslationManager;
public final class BabelI18n {

    private static final Map<String, Map<String, String>> STRINGS = new HashMap<>();
    private static final GoogleTranslateEngine GOOGLE = new GoogleTranslateEngine();

    static {
        put("en", "screen.title", "Project Babel - Cache");
        put("en", "search", "Search...");
        put("en", "edit", "Edit");
        put("en", "translation.on", "Translation ON");
        put("en", "translation.off", "Translation OFF");
        put("en", "hud.on", "HUD ON");
        put("en", "hud.off", "HUD OFF");
        put("en", "renamed.on", "Renamed ON");
        put("en", "renamed.off", "Renamed OFF");
        put("en", "chat.on", "Chat ON");
        put("en", "chat.off", "Chat OFF");
        put("en", "turbo.on", "Turbo ON");
        put("en", "turbo.off", "Turbo OFF");
        put("en", "debug.off", "Debug OFF");
        put("en", "debug.tooltip", "Debug: Tooltip");
        put("en", "debug.quests", "Debug: Quests");
        put("en", "debug.books", "Debug: Books");
        put("en", "debug.ponder", "Debug: Ponder");
        put("en", "debug.all", "Debug: All");
        put("en", "target.client", "Target: Client");
        put("en", "target.config", "Target: Config");
        put("en", "save.lang", "Save lang");
        put("en", "force.google", "Force Google");
        put("en", "clear.cache", "Clear Cache");
        put("en", "close", "Close [ESC]");
        put("en", "source.short", "Src:");
        put("en", "target.short", "Dst:");
        put("en", "engine", "Engine");
        put("en", "empty", "No cached translations yet.");
        put("en", "no.results", "No results for \"%s\".");
        put("en", "stats", "Total: %d | Filtered: %d | Hits: %.0f%% | Queue: %d");
        put("en", "edit.hint", "Edit selected translation...");
        put("en", "save.entry", "Save");

        put("pt", "screen.title", "Project Babel - Cache");
        put("pt", "search", "Buscar...");
        put("pt", "edit", "Editar");
        put("pt", "translation.on", "Traducao ON");
        put("pt", "translation.off", "Traducao OFF");
        put("pt", "hud.on", "HUD ON");
        put("pt", "hud.off", "HUD OFF");
        put("pt", "renamed.on", "Renomeados ON");
        put("pt", "renamed.off", "Renomeados OFF");
        put("pt", "chat.on", "Chat ON");
        put("pt", "chat.off", "Chat OFF");
        put("pt", "turbo.on", "Turbo ON");
        put("pt", "turbo.off", "Turbo OFF");
        put("pt", "debug.off", "Debug OFF");
        put("pt", "debug.tooltip", "Debug: Tooltip");
        put("pt", "debug.quests", "Debug: Quests");
        put("pt", "debug.books", "Debug: Livros");
        put("pt", "debug.ponder", "Debug: Ponder");
        put("pt", "debug.all", "Debug: Tudo");
        put("pt", "target.client", "Alvo: Cliente");
        put("pt", "target.config", "Alvo: Config");
        put("pt", "save.lang", "Salvar lang");
        put("pt", "force.google", "Forcar Google");
        put("pt", "clear.cache", "Limpar Cache");
        put("pt", "close", "Fechar [ESC]");
        put("pt", "source.short", "Orig:");
        put("pt", "target.short", "Alvo:");
        put("pt", "engine", "Engine");
        put("pt", "empty", "Nenhuma traducao no cache ainda.");
        put("pt", "no.results", "Nenhum resultado para \"%s\".");
        put("pt", "stats", "Total: %d | Filtrado: %d | Hits: %.0f%% | Fila: %d");
        put("pt", "edit.hint", "Editar traducao selecionada...");
        put("pt", "save.entry", "Salvar");

        put("es", "screen.title", "Project Babel - Cache");
        put("es", "search", "Buscar...");
        put("es", "edit", "Editar");
        put("es", "translation.on", "Traduccion ON");
        put("es", "translation.off", "Traduccion OFF");
        put("es", "hud.on", "HUD ON");
        put("es", "hud.off", "HUD OFF");
        put("es", "renamed.on", "Renombrados ON");
        put("es", "renamed.off", "Renombrados OFF");
        put("es", "chat.on", "Chat ON");
        put("es", "chat.off", "Chat OFF");
        put("es", "turbo.on", "Turbo ON");
        put("es", "turbo.off", "Turbo OFF");
        put("es", "debug.off", "Debug OFF");
        put("es", "debug.tooltip", "Debug: Tooltip");
        put("es", "debug.quests", "Debug: Quests");
        put("es", "debug.books", "Debug: Libros");
        put("es", "debug.ponder", "Debug: Ponder");
        put("es", "debug.all", "Debug: Todo");
        put("es", "target.client", "Destino: Cliente");
        put("es", "target.config", "Destino: Config");
        put("es", "save.lang", "Guardar idioma");
        put("es", "force.google", "Forzar Google");
        put("es", "clear.cache", "Limpiar Cache");
        put("es", "close", "Cerrar [ESC]");
        put("es", "source.short", "Orig:");
        put("es", "target.short", "Dest:");
        put("es", "engine", "Motor");
        put("es", "empty", "Aun no hay traducciones en cache.");
        put("es", "no.results", "Sin resultados para \"%s\".");
        put("es", "stats", "Total: %d | Filtrado: %d | Hits: %.0f%% | Cola: %d");
        put("es", "edit.hint", "Editar traduccion seleccionada...");
        put("es", "save.entry", "Guardar");

        put("ru", "screen.title", "Project Babel - Кэш");
        put("ru", "search", "Поиск...");
        put("ru", "translation.on", "Перевод ВКЛ");
        put("ru", "translation.off", "Перевод ВЫКЛ");
        put("ru", "hud.on", "HUD ВКЛ");
        put("ru", "hud.off", "HUD ВЫКЛ");
        put("ru", "renamed.on", "Переименованные ВКЛ");
        put("ru", "renamed.off", "Переименованные ВЫКЛ");
        put("ru", "chat.on", "Чат ВКЛ");
        put("ru", "chat.off", "Чат ВЫКЛ");
        put("ru", "turbo.on", "Turbo ON");
        put("ru", "turbo.off", "Turbo OFF");
        put("ru", "target.client", "Цель: Клиент");
        put("ru", "target.config", "Цель: Конфиг");
        put("ru", "save.lang", "Сохранить язык");
        put("ru", "force.google", "Google");
        put("ru", "clear.cache", "Очистить кэш");
        put("ru", "close", "Закрыть [ESC]");
        put("ru", "source.short", "Исх:");
        put("ru", "target.short", "Цель:");
        put("ru", "engine", "Движок");
        put("ru", "empty", "В кэше пока нет переводов.");
        put("ru", "no.results", "Нет результатов для \"%s\".");
        put("ru", "stats", "Всего: %d | Фильтр: %d | Hits: %.0f%% | Очередь: %d");
        put("ru", "edit.hint", "Редактировать выбранный перевод...");
        put("ru", "save.entry", "Сохранить");
    }

    private BabelI18n() {}

    public static Component c(String key) {
        return Component.literal(t(key));
    }

    public static String t(String key) {
        String lang = currentLanguage();
        Map<String, String> map = STRINGS.get(lang);
        if (map != null && map.containsKey(key)) return map.get(key);

        String english = STRINGS.get("en").getOrDefault(key, key);
        if ("en".equals(lang)) return english;

        String cached = TranslationManager.getInstance().getCachedTranslation(english, "en", lang);
        if (cached != null) return cached;

        GOOGLE.translate(english, "en", lang)
            .thenAccept(result -> TranslationManager.getInstance().getCache().put(english, "en", lang, result))
            .exceptionally(error -> null);
        return english;
    }

    public static String f(String key, Object... args) {
        return String.format(t(key), args);
    }

    private static void put(String lang, String key, String value) {
        STRINGS.computeIfAbsent(lang, ignored -> new HashMap<>()).put(key, value);
    }

    private static String currentLanguage() {
        try {
            Minecraft mc = Minecraft.getInstance();
            String code = mc == null || mc.options == null ? "en" : mc.options.languageCode;
            if (code == null || code.isBlank()) return "en";
            code = code.toLowerCase(Locale.ROOT).replace('-', '_');
            if (code.startsWith("pt")) return "pt";
            if (code.startsWith("es")) return "es";
            if (code.startsWith("ru")) return "ru";
            if (code.startsWith("en")) return "en";
            int sep = code.indexOf('_');
            return sep > 0 ? code.substring(0, sep) : code;
        } catch (Exception e) {
            return "en";
        }
    }
}
