package com.projectbabel.core.text;

import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;

import java.util.regex.Pattern;

public class TextFilter {

    private static final ThreadLocal<Boolean> BYPASS_SCREEN_FILTER =
        ThreadLocal.withInitial(() -> false);

    private static final Pattern HUD_SPAM = Pattern.compile(
        "(fps|xyz|chunk|entities|particles|minecraft|cpu|gpu|mem|ping|loaded|biome)",
        Pattern.CASE_INSENSITIVE
    );


    public static final int PRIORITY_ITEM_NAME = 1;
    public static final int PRIORITY_UI_BUTTON = 2;
    public static final int PRIORITY_ITEM_DESC = 3;
    public static final int PRIORITY_CHAT      = 4;
    public static final int PRIORITY_UI_OTHER  = 5;
    public static final int PRIORITY_GENERIC   = 10;

    private static final Pattern FORMATTING_CODES  = Pattern.compile("\u00a7[0-9a-fk-orA-FK-OR]");
    private static final Pattern NUMERIC_PATTERN   = Pattern.compile("^[\\d\\s.,+\\-/%]+$");
    private static final Pattern PERCENT_PATTERN   = Pattern.compile("^[+\\-]?\\d+(\\.\\d+)?\\s*%$");
    private static final Pattern ONLY_SPECIAL      = Pattern.compile("^[^\\p{L}\\p{N}]+$");
    private static final Pattern RESOURCE_LOCATION = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./\\-]+$");
    private static final Pattern NBT_LIKE          = Pattern.compile("^[{\\[\"'].*[}\\]\"']$");
    private static final Pattern URL_PATTERN       = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern KEY_PATTERN       = Pattern.compile(
        "^((Left|Right)\\s+)?(Ctrl|Control|Alt|Shift|F\\d{1,2}|ESC|TAB|ENTER|SPACE|BACKSPACE|Sneak|Jump)(\\+[A-Za-z0-9]+)*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COORD_PATTERN = Pattern.compile(
        "^[XYZxyz]\\s*:?\\s*-?\\d|^-?\\d+\\.?\\d*\\s*[,/]\\s*-?\\d"
    );
    private static final Pattern VERSION_SYSTEM = Pattern.compile(
        "(?i)^(Minecraft|Forge|Java|FML|ModLauncher)\\s+[\\d.]" +
        "|\\d+\\s+mod(s)?\\s+loaded" +
        "|New (Forge|version) (version )?available" +
        "|^version\\s+[\\d.]" +
        "|^v?\\d+\\.\\d+\\.\\d+" +
        "|(build|release|snapshot)\\s+[\\d.]" +
        "|^\\d+\\s+(mods?|plugins?)" +
        "|(running|loaded|active)\\s+\\d+\\s+mod"
    );
    private static final Pattern DEBUG_DATA = Pattern.compile(
        "\\d+\\.?\\d*\\s*(fps|ms|tps|tick|ns|MB|KB|GB|MHz|GHz|B/s)" +
        "|\\d+\\s*/\\s*\\d+\\s*MB" +
        "|^[A-Z]{1,3}:\\s*-?\\d" +
        "|^Mem:|^Integrated server|^CPU:|^Display:" +
        "|@\\s*\\d+\\s*ms|^AT:|^projectbabel" +
        "|\\d+\\s*(tx|rx)[,. ]|\\d+\\.\\d+\\.\\d+/\\d+",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MIXED_TECHNICAL = Pattern.compile(
        "\\d+\\s*[/(,]\\s*\\d+\\s*[/(,]\\s*\\d+"
    );
    private static final Pattern INTERNAL_PATH = Pattern.compile(
        "^[a-z]+\\.[a-z]+\\.[a-zA-Z]|^com\\.|^org\\.|^io\\.|^java\\."
    );

    /**
     * Atributos de item: "+1.6 Attack Speed", "-0.5 Armor", "1.6 Attack Damage", etc.
     * Padrão: opcional sinal, número, espaço, palavra(s) com maiúscula.
     * Esses são dados numéricos com rótulo — não faz sentido traduzir.
     */
    private static final Pattern ITEM_ATTRIBUTE = Pattern.compile(
        "^[+\\-]?\\s*\\d+[.,]?\\d*\\s+[A-Z][a-zA-Z ]+$"
    );

    /**
     * Rótulos de atributo sem número na frente mas que são dados técnicos:
     * "When in Main Hand:", "When in Off Hand:", "Durability: 120/250"
     */
    private static final Pattern ATTRIBUTE_LABEL = Pattern.compile(
        "(?i)^When in (Main|Off) Hand:?$" +
        "|^Durability:\\s*\\d" +
        "|^NBT:|^Tags?:" +
        "|^\\(Can destroy\\)" +
        "|^\\(Can place on\\)"
    );

    private TextFilter() {}

    public static ScreenFilterBypass bypassScreenFilter() {
        boolean previous = BYPASS_SCREEN_FILTER.get();
        BYPASS_SCREEN_FILTER.set(true);
        return new ScreenFilterBypass(previous);
    }

    public static boolean isScreenFilterBypassed() {
        return BYPASS_SCREEN_FILTER.get();
    }

    public static boolean shouldTranslate(String rawText) {
        if (rawText == null) return false;
        if (!isScreenFilterBypassed()) {
            if (isDebugScreenOpen()) return false;
            if (isMenuScreen()) return false;
        }

        String text = FORMATTING_CODES.matcher(rawText).replaceAll("").trim();

        if (text.isEmpty()) return false;
        if (text.length() < ProjectBabelCommon.config().getMinTextLength()) return false;
        if (!text.chars().anyMatch(Character::isLetter)) return false;
        if (TextFormatUtils.isStandaloneRomanNumeral(text)) return false;
        if (ONLY_SPECIAL.matcher(text).matches()) return false;
        if (NUMERIC_PATTERN.matcher(text).matches()) return false;
        if (PERCENT_PATTERN.matcher(text).matches()) return false;
        if (COORD_PATTERN.matcher(text).find()) return false;
        if (RESOURCE_LOCATION.matcher(text).matches()) return false;
        if (NBT_LIKE.matcher(text).matches()) return false;
        if (URL_PATTERN.matcher(text).find()) return false;
        if (KEY_PATTERN.matcher(text).matches()) return false;
        if (DEBUG_DATA.matcher(text).find()) return false;
        if (MIXED_TECHNICAL.matcher(text).find()) return false;
        if (VERSION_SYSTEM.matcher(text).find()) return false;
        if (INTERNAL_PATH.matcher(text).find()) return false;
        if (ITEM_ATTRIBUTE.matcher(text).matches()) return false;   // "1.6 Attack Speed"
        if (ATTRIBUTE_LABEL.matcher(text).find()) return false;     // "When in Main Hand:"

        // Mais de 50% dígitos = dado dinâmico
        long digits = text.chars().filter(Character::isDigit).count();
        if (text.length() > 4 && (double) digits / text.length() > 0.5) return false;

        return true;
    }

    public static final class ScreenFilterBypass implements AutoCloseable {
        private final boolean previous;

        private ScreenFilterBypass(boolean previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            BYPASS_SCREEN_FILTER.set(previous);
        }
    }

    public static int estimatePriority(String rawText) {
        if (rawText == null) return PRIORITY_GENERIC;
        String stripped = stripFormatting(rawText).trim();
        if (stripped.isEmpty()) return PRIORITY_GENERIC;
        if (rawText.contains("\u00a7o")) return PRIORITY_ITEM_DESC;
        int len = stripped.length();
        if (len <= 40 && len >= 2 && Character.isUpperCase(stripped.charAt(0)) && !stripped.endsWith("."))
            return len <= 25 ? PRIORITY_ITEM_NAME : PRIORITY_UI_BUTTON;
        if (len <= 20 && Character.isUpperCase(stripped.charAt(0))) return PRIORITY_UI_BUTTON;
        if (len <= 80) return PRIORITY_UI_OTHER;
        return PRIORITY_GENERIC;
    }

    public static String stripFormatting(String text) {
        // Delega para TextFormatUtils para evitar duplicação de lógica
        return TextFormatUtils.stripFormatting(text);
    }

    public static String extractFormattingPrefix(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder prefix = new StringBuilder();
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == '\u00a7') {
            prefix.append(text, i, i + 2);
            i += 2;
        }
        return prefix.toString();
    }

    public static boolean isDebugScreenOpen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.options.renderDebug;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMenuScreen() {
        try {
            if (isScreenFilterBypassed()) return false;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return true;
            if (mc.player == null || mc.level == null) return true;
            // Bloqueia APENAS no menu principal real — não em qualquer tela com player==null.
            // mc.player==null ocorre também durante loading de chunks, telas de mod, etc.
            // Usar só TitleScreen garante que o mod funciona assim que o mundo carrega.
            // LevelLoadingScreen e ReceivingLevelScreen também devem bloquear (loading do mundo).
            if (mc.screen instanceof TitleScreen) return true;
            if (mc.screen instanceof LevelLoadingScreen) return true;
            if (mc.screen instanceof ReceivingLevelScreen) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
