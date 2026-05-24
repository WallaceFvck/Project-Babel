# ARCHITECTURE_GUIDE.md — Project Babel

Este documento é a referência definitiva de arquitetura do **Project Babel** após a migração para uma estrutura **Multi-Projeto Gradle** com **Architectury Loom**.

O objetivo deste guia é impedir regressões arquiteturais, duplicação entre loaders e decisões locais que comprometam a estabilidade do mod. Qualquer alteração futura deve respeitar estes limites.

---

## 1. O MUNDO MULTI-LOADER (The Big Picture)

O Project Babel é um mod **client-side** de tradução em tempo real para Minecraft 1.20.1. A arquitetura atual foi desenhada para manter uma única lógica de negócio compartilhada entre Forge e Fabric, sem duplicar engines, cache, scheduler, pipeline de tradução, regras de tooltip ou integrações.

A divisão oficial do projeto é:

```text
:common  -> lógica real do Project Babel
:forge   -> adapter fino para Forge
:fabric  -> adapter fino para Fabric
```

A regra mental principal é:

```text
O :common decide.
O :common traduz.
O :common cacheia.
O :common agenda trabalho.
O :common preserva formatação.
O :common contém os Mixins Vanilla.

:forge e :fabric apenas conectam o :common ao loader.
```

### Fluxo unificado de tradução

O fluxo correto de funcionamento é:

```text
Mixin Vanilla comum
ou
Mixin de compatibilidade de plataforma
ou
Evento nativo Forge/Fabric
        ↓
Adapter fino de plataforma
        ↓
API comum: TranslationContext / TranslationRequest / TranslationSurface
        ↓
core.pipeline
        ↓
core.cache / core.dictionary / core.guard
        ↓
core.schedule
        ↓
core.engine
        ↓
cache persistente / resultado traduzido
        ↓
reconstrução de Component preservando Style, siblings, hover e click
        ↓
renderização final no cliente
```

O pipeline não deve depender de Forge ou Fabric. O loader somente captura o ponto de entrada e delega.

### O papel dos módulos de plataforma

Os módulos `:forge` e `:fabric` existem para lidar com diferenças inevitáveis:

- entrypoints;
- eventos nativos;
- config nativa;
- registro de keybinds;
- resource reload;
- lifecycle de mundo/tela;
- mixins de compatibilidade com mods externos;
- metadados de loader (`mods.toml`, `fabric.mod.json`).

Eles não devem conter decisões de tradução, cache, dicionário, priorização, concorrência ou reconstrução de texto.

---

## 2. ÁRVORE UNIFICADA DE DIRETÓRIOS E PACOTES

Estrutura do repositório:

```text
project-babel-multiloader/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── scripts/
│   └── validate_architecture.py
│
├── common/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/projectbabel/
│       │   ├── ProjectBabelCommon.java
│       │   │
│       │   ├── api/
│       │   │   ├── TranslationContext.java
│       │   │   ├── TranslationRequest.java
│       │   │   ├── TranslationResult.java
│       │   │   ├── TranslationService.java
│       │   │   └── TranslationSurface.java
│       │   │
│       │   ├── platform/
│       │   │   ├── BabelConfigView.java
│       │   │   ├── ClientExecutor.java
│       │   │   ├── ModLookup.java
│       │   │   ├── PathsProvider.java
│       │   │   ├── PlatformServices.java
│       │   │   └── reload/
│       │   │       ├── ProjectBabelReloadBus.java
│       │   │       └── ResourceReloadBridge.java
│       │   │
│       │   ├── core/
│       │   │   ├── cache/
│       │   │   ├── dictionary/
│       │   │   ├── engine/
│       │   │   ├── guard/
│       │   │   ├── pipeline/
│       │   │   ├── schedule/
│       │   │   ├── service/
│       │   │   ├── text/
│       │   │   └── tooltip/
│       │   │
│       │   ├── minecraft/
│       │   │   ├── chat/
│       │   │   ├── render/
│       │   │   └── tooltip/
│       │   │
│       │   ├── integrations/
│       │   │   ├── access/
│       │   │   ├── books/
│       │   │   │   ├── guideme/
│       │   │   │   ├── modonomicon/
│       │   │   │   └── patchouli/
│       │   │   ├── create/
│       │   │   ├── ftblibrary/
│       │   │   ├── ftbquests/
│       │   │   ├── generic/
│       │   │   └── registry/
│       │   │       ├── create/
│       │   │       ├── ftbquests/
│       │   │       ├── guideme/
│       │   │       ├── modonomicon/
│       │   │       └── patchouli/
│       │   │
│       │   ├── mixin/
│       │   │   └── vanilla/
│       │   │       ├── AdvancementMixin.java
│       │   │       ├── BookViewScreenMixin.java
│       │   │       ├── ChatComponentMixin.java
│       │   │       ├── ClientLanguageMixin.java
│       │   │       ├── ClientTooltipMixin.java
│       │   │       ├── FontMixin.java
│       │   │       ├── GuiGraphicsMixin.java
│       │   │       ├── GuiOverlayMessageMixin.java
│       │   │       ├── ItemStackMixin.java
│       │   │       └── TooltipMixin.java
│       │   │
│       │   ├── ui/
│       │   │   ├── cache/
│       │   │   └── overlay/
│       │   │
│       │   └── debug/
│       │
│       └── resources/
│           ├── architectury.common.json
│           ├── pack.mcmeta
│           ├── projectbabel.png
│           ├── projectbabel.accesswidener
│           └── projectbabel-common.mixins.json
│
├── fabric/
│   ├── build.gradle
│   ├── gradle.properties
│   └── src/main/
│       ├── java/com/projectbabel/
│       │   ├── fabric/
│       │   │   ├── ProjectBabelFabric.java
│       │   │   ├── FabricConfigBridge.java
│       │   │   ├── FabricPlatformServices.java
│       │   │   ├── config/
│       │   │   ├── event/
│       │   │   ├── integrations/
│       │   │   └── mixin/
│       │   │       └── ProjectBabelMixinPlugin.java
│       │   │
│       │   └── mixin/
│       │       └── compat/
│       │           ├── ae2/
│       │           ├── create/
│       │           ├── enchdesc/
│       │           ├── ftblibrary/
│       │           ├── ftbquests/
│       │           ├── guideme/
│       │           ├── jade/
│       │           ├── modonomicon/
│       │           ├── patchouli/
│       │           └── refinedstorage/
│       │
│       └── resources/
│           ├── fabric.mod.json
│           └── projectbabel.mixins.json
│
└── forge/
    ├── build.gradle
    ├── gradle.properties
    └── src/main/
        ├── java/com/projectbabel/
        │   ├── forge/
        │   │   ├── ProjectBabelForge.java
        │   │   ├── ForgeClientBootstrap.java
        │   │   ├── ForgeConfigBridge.java
        │   │   ├── ForgePlatformServices.java
        │   │   ├── config/
        │   │   └── event/
        │   │
        │   └── mixin/
        │       └── compat/
        │           ├── ae2/
        │           ├── create/
        │           ├── enchdesc/
        │           ├── ftblibrary/
        │           ├── ftbquests/
        │           ├── guideme/
        │           ├── jade/
        │           ├── modonomicon/
        │           ├── patchouli/
        │           └── refinedstorage/
        │
        └── resources/
            ├── META-INF/
            │   └── mods.toml
            └── projectbabel.mixins.json
```

### Pacotes principais do `:common`

```text
com.projectbabel.api
```

Contratos públicos do pipeline de tradução. Tudo que representa uma requisição, resultado, superfície de tradução ou contexto deve passar por aqui.

```text
com.projectbabel.core.cache
```

Cache de tradução, entradas persistentes, invalidação e hooks centrais.

```text
com.projectbabel.core.dictionary
```

Dicionário, glossário, termos universais e regras de preservação de termos.

```text
com.projectbabel.core.engine
```

Engines externas e provedores de tradução, como Google, Lingva, DNS/DoH e lista de instâncias.

```text
com.projectbabel.core.pipeline
```

Triagem, decisões de tradução, skip registry, classificação e passagem entre cache, scheduler e engine.

```text
com.projectbabel.core.schedule
```

Fila, prioridade, concorrência, modo turbo, executores e backpressure.

```text
com.projectbabel.core.service
```

Fachadas de alto nível como `TranslationManager`, `TranslationServices` e `ProjectBabelTranslationService`.

```text
com.projectbabel.core.text
```

Normalização de texto, detecção de idioma, filtros, formatação, tradução de markup e tradução de templates de componentes.

```text
com.projectbabel.core.tooltip
```

Classificação e tradução de tooltips, incluindo encantamentos e descrições.

```text
com.projectbabel.core.guard
```

Guardas de renderização, bypass, proteção contra tradução indevida e regras de cache-only.

```text
com.projectbabel.minecraft.*
```

Helpers que dependem de classes Vanilla do Minecraft, mas não dependem de Forge/Fabric.

```text
com.projectbabel.integrations.*
```

Lógica comum de integração com mods parceiros. A integração deve ser genérica e agnóstica de loader sempre que possível.

```text
com.projectbabel.mixin.vanilla
```

Mixins contra classes Vanilla do Minecraft. Estes são comuns e devem ser carregados por ambos os loaders.

---

## 3. A REGRA DOS TRÊS MÓDULOS (O que vai onde)

### 3.1. Módulo `:common`

O módulo `:common` é a fonte da verdade.

É permitido no `:common`:

```text
Java puro
Minecraft Vanilla via net.minecraft.*
Component, Style, Font, GuiGraphics, ItemStack, Screen, ResourceManager
Mixins Vanilla
Access Widener comum
Engines de tradução
Cache e persistência própria
Dicionários e glossários
Pipeline de tradução
Scheduler e concorrência de tradução
Helpers de Component e tooltip
Integrações agnósticas de loader
UI própria do mod, desde que use apenas Vanilla
Debug comum
Contratos de plataforma
```

É proibido no `:common`:

```text
net.fabricmc.*
net.minecraftforge.*
FabricLoader
ModList
ForgeConfigSpec
AutoConfig/ClothConfig diretamente
@Mod
ClientModInitializer
EventBus Forge
Callbacks Fabric
ResourceManagerHelper
RegisterClientReloadListenersEvent
mods.toml
fabric.mod.json
```

Exemplo correto dentro do `:common`:

```java
if (ProjectBabelCommon.platform().mods().isLoaded("patchouli")) {
    PatchouliBookPreloader.requestWorldPreload();
}
```

Exemplo proibido dentro do `:common`:

```java
if (FabricLoader.getInstance().isModLoaded("patchouli")) {
    PatchouliBookPreloader.requestWorldPreload();
}
```

### 3.2. Módulo `:fabric`

O módulo `:fabric` é um adapter fino.

É permitido no `:fabric`:

```text
ClientModInitializer
FabricLoader
Fabric API
ResourceManagerHelper
ClientTickEvents
ItemTooltipCallback
HudRenderCallback
KeyBindingHelper
config Fabric/JSON/AutoConfig
fabric.mod.json
plugin de mixin Fabric
mixins de compatibilidade com mods no ambiente Fabric
bridges para PlatformServices
```

É proibido no `:fabric`:

```text
Engines de tradução duplicadas
Cache próprio paralelo
Scheduler próprio
Pipeline próprio
Dicionário próprio
Regras de tooltip duplicadas
Mixins Vanilla duplicados
Lógica de negócio que poderia estar no common
```

O entrypoint Fabric deve seguir este padrão:

```java
public final class ProjectBabelFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricConfigBridge.load();
        ProjectBabelCommon.init(new FabricPlatformServices());

        // registrar eventos/callbacks Fabric
        // inicializar hooks que chamam common
    }
}
```

### 3.3. Módulo `:forge`

O módulo `:forge` também é um adapter fino.

É permitido no `:forge`:

```text
@Mod
Forge event bus
FMLClientSetupEvent
ForgeConfigSpec
ModLoadingContext
ModList dentro do bridge de plataforma
RegisterClientReloadListenersEvent
mods.toml
mixins de compatibilidade com mods no ambiente Forge
bridges para PlatformServices
```

É proibido no `:forge`:

```text
Engines de tradução duplicadas
Cache próprio paralelo
Scheduler próprio
Pipeline próprio
Dicionário próprio
Regras de tooltip duplicadas
Mixins Vanilla duplicados
Lógica de negócio que poderia estar no common
```

O entrypoint Forge deve seguir este padrão:

```java
@Mod(ProjectBabelCommon.MOD_ID)
public final class ProjectBabelForge {
    public ProjectBabelForge() {
        ModLoadingContext.get().registerConfig(
            ModConfig.Type.CLIENT,
            ForgeConfigBridge.spec(),
            "projectbabel-client.toml"
        );

        ProjectBabelCommon.init(new ForgePlatformServices());

        FMLJavaModLoadingContext.get()
            .getModEventBus()
            .addListener(this::clientSetup);
    }
}
```

---

## 4. ESTRATÉGIA CENTRALIZADA DE MIXINS E ACESSIBILIDADE

### 4.1. Mixins Vanilla ficam no `:common`

Todos os mixins que alteram classes Vanilla do Minecraft ficam em:

```text
common/src/main/java/com/projectbabel/mixin/vanilla/
```

Exemplos:

```text
FontMixin
GuiGraphicsMixin
ClientTooltipMixin
ChatComponentMixin
ItemStackMixin
BookViewScreenMixin
```

Motivo: esses mixins não são responsabilidade de Forge nem de Fabric. Eles alteram classes Vanilla e devem ter uma única implementação.

Isso evita:

```text
mixins Vanilla divergentes entre loaders
duplicação de bugfix
duplicação de refmap
mixin aplicado duas vezes por engano
diferenças de comportamento entre Forge e Fabric
```

### 4.2. Config comum de mixins

O arquivo comum é:

```text
common/src/main/resources/projectbabel-common.mixins.json
```

Ele registra apenas mixins Vanilla:

```json
{
  "required": false,
  "minVersion": "0.8",
  "package": "com.projectbabel.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "projectbabel-common-refmap.json",
  "mixins": [],
  "client": [
    "vanilla.AdvancementMixin",
    "vanilla.BookViewScreenMixin",
    "vanilla.ChatComponentMixin",
    "vanilla.ClientLanguageMixin",
    "vanilla.ClientTooltipMixin",
    "vanilla.FontMixin",
    "vanilla.GuiGraphicsMixin",
    "vanilla.GuiOverlayMessageMixin",
    "vanilla.ItemStackMixin",
    "vanilla.TooltipMixin"
  ],
  "injectors": {
    "defaultRequire": 0
  }
}
```

Os arquivos de plataforma continuam existindo:

```text
fabric/src/main/resources/projectbabel.mixins.json
forge/src/main/resources/projectbabel.mixins.json
```

Mas eles devem conter apenas:

```text
compat.*
```

Nunca `vanilla.*`.

### 4.3. Refmaps separados

O refmap comum é:

```text
projectbabel-common-refmap.json
```

O refmap de plataforma é:

```text
projectbabel-platform-refmap.json
```

Não misture os dois. O objetivo é facilitar diagnóstico:

```text
falha em Vanilla -> olhar projectbabel-common.mixins.json
falha em compat mod -> olhar projectbabel.mixins.json do loader
```

### 4.4. Access Widener comum

O access widener único fica em:

```text
common/src/main/resources/projectbabel.accesswidener
```

Com cabeçalho:

```text
accessWidener v2 named
```

Ele é declarado em:

```text
common/src/main/resources/architectury.common.json
```

```json
{
  "accessWidener": "projectbabel.accesswidener"
}
```

O `common/build.gradle` aponta para ele:

```groovy
loom {
    accessWidenerPath = file('src/main/resources/projectbabel.accesswidener')
}
```

O Fabric e o Forge usam o mesmo arquivo:

```groovy
loom {
    accessWidenerPath = project(':common').loom.accessWidenerPath
}
```

No Forge, o Loom também converte o access widener:

```groovy
loom {
    accessWidenerPath = project(':common').loom.accessWidenerPath

    forge {
        mixinConfig 'projectbabel-common.mixins.json'
        mixinConfig 'projectbabel.mixins.json'

        convertAccessWideners = true
        extraAccessWideners.add loom.accessWidenerPath.get().asFile.name
    }
}
```

Regra: qualquer abertura de classe, campo ou método Vanilla deve ir no access widener comum. Não crie mecanismos paralelos por loader sem necessidade extrema.

---

## 5. ABSTRAÇÃO DE PLATAFORMA (PlatformServices & Ciclo de Vida)

### 5.1. `ProjectBabelCommon`

O `ProjectBabelCommon` é o ponto de bootstrap compartilhado:

```java
public final class ProjectBabelCommon {
    public static final String MOD_ID = "projectbabel";

    private static PlatformServices platform;

    public static void init(PlatformServices services) {
        platform = Objects.requireNonNull(services, "services");
        ProjectBabelReloadBus.initCommonListeners();
    }

    public static PlatformServices platform() {
        if (platform == null) {
            throw new IllegalStateException("Project Babel common has not been initialized");
        }
        return platform;
    }

    public static BabelConfigView config() {
        return platform().config();
    }
}
```

Os loaders chamam:

```java
ProjectBabelCommon.init(new FabricPlatformServices());
```

ou:

```java
ProjectBabelCommon.init(new ForgePlatformServices());
```

Depois disso, o `:common` nunca precisa perguntar diretamente para Fabric ou Forge.

### 5.2. `PlatformServices`

Contrato oficial:

```java
public interface PlatformServices {
    BabelConfigView config();

    ModLookup mods();

    PathsProvider paths();

    ClientExecutor clientExecutor();
}
```

#### Checar se um mod está carregado

Correto:

```java
if (ProjectBabelCommon.platform().mods().isLoaded("ftbquests")) {
    FTBQuestAutoTranslator.requestPreload();
}
```

Errado no `:common`:

```java
FabricLoader.getInstance().isModLoaded("ftbquests");
ModList.get().isLoaded("ftbquests");
```

#### Acessar diretórios

Correto:

```java
Path configDir = ProjectBabelCommon.platform().paths().configDir();
Path gameDir = ProjectBabelCommon.platform().paths().gameDir();
```

Errado no `:common`:

```java
FabricLoader.getInstance().getConfigDir();
FMLPaths.CONFIGDIR.get();
```

#### Agendar trabalho na client thread

Correto:

```java
ProjectBabelCommon.platform().clientExecutor().execute(() -> {
    Minecraft.getInstance().setScreen(new TranslationCacheScreen(...));
});
```

Errado no `:common`:

```java
Minecraft.getInstance().execute(...); // usar diretamente apenas quando for helper Vanilla cuidadosamente isolado
```

Prefira o `ClientExecutor` quando a intenção arquitetural for “voltar para a thread do cliente”.

### 5.3. Configuração por `BabelConfigView`

O `:common` só conhece:

```java
ProjectBabelCommon.config()
```

Exemplo:

```java
if (!ProjectBabelCommon.config().isEnabled()) {
    return originalComponent;
}
```

O `:common` não conhece:

```text
ForgeConfigSpec
AutoTranslateConfig
ClothConfig
AutoConfig
JSON loader específico
```

A tradução de config nativa para `BabelConfigView` é responsabilidade dos bridges:

```text
fabric/FabricConfigBridge.java
forge/ForgeConfigBridge.java
```

### 5.4. Resource Reload agnóstico

O sistema comum de reload fica em:

```text
common/src/main/java/com/projectbabel/platform/reload/
```

Contrato:

```java
@FunctionalInterface
public interface ResourceReloadBridge {
    void onClientResourcesReload(ResourceManager resourceManager);
}
```

Barramento comum:

```java
public final class ProjectBabelReloadBus {
    public static void registerClientResourceReload(ResourceReloadBridge listener) {
        CLIENT_RESOURCE_RELOAD_LISTENERS.add(listener);
    }

    public static void fireClientResourcesReload(ResourceManager resourceManager) {
        for (ResourceReloadBridge listener : CLIENT_RESOURCE_RELOAD_LISTENERS) {
            listener.onClientResourcesReload(resourceManager);
        }
    }
}
```

Fabric captura o evento nativo com `ResourceManagerHelper` e delega:

```java
ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
    .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            ProjectBabelReloadBus.fireClientResourcesReload(manager);
        }
    });
```

Forge captura o evento nativo com `RegisterClientReloadListenersEvent` e delega:

```java
@SubscribeEvent
public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
    event.registerReloadListener(ProjectBabelReloadBus::fireClientResourcesReload);
}
```

A lógica real, como reiniciar preload do GuideME, fica no `:common`.

---

## 6. AS LEIS SAGRADAS DO PROJECT BABEL

Estas regras são obrigatórias.

### 1. O `:common` não importa loader

É proibido qualquer import ou referência em `:common` a:

```text
net.fabricmc.*
net.minecraftforge.*
FabricLoader
ModList
ForgeConfigSpec
ClientModInitializer
@Mod
```

Se precisar de algo do loader, crie ou use um método em `PlatformServices`.

### 2. Nenhuma engine fora do `:common`

Engines de tradução pertencem exclusivamente a:

```text
common/src/main/java/com/projectbabel/core/engine/
```

Não duplique Google, Lingva, IA, DNS, fallback ou lista de instâncias em `:forge` ou `:fabric`.

### 3. Nenhum cache paralelo por loader

Cache pertence exclusivamente a:

```text
common/src/main/java/com/projectbabel/core/cache/
```

Os loaders não devem criar cache alternativo, mesmo que seja “temporário”.

### 4. Nenhuma thread manual fora do scheduler comum

É proibido criar concorrência livre fora de:

```text
common/src/main/java/com/projectbabel/core/schedule/
```

Evite:

```java
new Thread(...)
Executors.newFixedThreadPool(...)
CompletableFuture.supplyAsync(...) // sem executor comum
```

Use `TranslationScheduler`, `TranslationExecutors` ou abstração já existente.

### 5. Renderização nunca chama rede

Qualquer intervenção em render, tooltip, font ou `GuiGraphics` deve ser **cache-only**.

Correto:

```text
render hook -> consulta cache -> renderiza se já existe -> agenda tradução fora da render thread
```

Errado:

```text
render hook -> chamada HTTP -> espera resposta -> renderiza
```

### 6. Component nunca deve virar String sem reconstrução segura

Ao traduzir `Component`, preserve:

```text
Style
siblings
hover event
click event
insertion
font
color
formatting
```

Se a lógica achatar `Component` para `String`, ela deve reconstruir a árvore corretamente ou não deve existir.

### 7. Mixins Vanilla pertencem ao `:common`

Qualquer mixin contra `net.minecraft.*` Vanilla deve ir em:

```text
common/src/main/java/com/projectbabel/mixin/vanilla/
```

Não recrie `FontMixin`, `GuiGraphicsMixin`, `ItemStackMixin`, tooltip Vanilla ou chat Vanilla nos módulos de loader.

### 8. Mixins de compatibilidade pertencem aos módulos de plataforma

Mixins contra mods externos devem ficar em:

```text
fabric/src/main/java/com/projectbabel/mixin/compat/
forge/src/main/java/com/projectbabel/mixin/compat/
```

A lógica chamada por esses mixins deve ser movida para `:common` sempre que possível.

### 9. Config nativa só existe nos bridges

O `:common` só lê:

```java
ProjectBabelCommon.config()
```

Classes como `AutoTranslateConfig`, `ForgeConfigBridge` ou configs JSON não devem vazar para pipeline, cache, scheduler, tooltip ou engine.

### 10. Toda nova integração deve ter adapter comum

Para adicionar suporte a novo mod parceiro:

```text
common/integrations/<modid>/        -> lógica comum
common/integrations/registry/<id>/  -> adapter comum
fabric/mixin/compat/<id>/           -> mixins Fabric, se necessário
forge/mixin/compat/<id>/            -> mixins Forge, se necessário
```

Nunca coloque a lógica real apenas dentro do mixin.

### 11. Dicionário e glossário têm prioridade sobre tradução externa

Antes de chamar engine externa:

```text
consultar glossário
consultar dicionário
preservar termos universais
aplicar skip rules
consultar cache
```

Termos definidos pelo usuário não devem ser retraduzidos por engines.

### 12. Modo turbo altera política, não cria arquitetura paralela

O modo turbo pode alterar:

```text
concorrência
prioridade
backpressure
preload
timeout
fila
```

Mas não pode criar outro pipeline, outro cache, outra engine manager ou outro scheduler.

### 13. Hooks de lifecycle só delegam

Eventos Fabric e Forge devem ser finos:

```text
capturar evento
montar contexto mínimo
chamar common
```

Não implementar lógica de tradução dentro de handlers de loader.

### 14. Toda alteração arquitetural deve passar pelas validações

Antes de considerar uma fase concluída:

```powershell
./gradlew validateArchitecture
./gradlew :common:compileJava
./gradlew :fabric:compileJava
./gradlew :forge:compileJava
./gradlew build
```

Se `validateArchitecture` falhar, a alteração violou uma regra estrutural.

### 15. Preferir Gradle 8.11.1 para esta toolchain

O projeto está fixado em:

```text
Gradle 8.11.1
Architectury Plugin 3.4.162
Architectury Loom 1.13.467
Shadow com.gradleup.shadow 8.3.9
Java 17
Minecraft 1.20.1
```

Não atualizar Gradle, Loom ou Shadow sem validar `shadowJar`, `remapJar`, refmaps e Forge.

---

## 7. PROTOCOLO DE COMANDO PARA IA (Prompt de Contexto Embutido)

Use o texto abaixo como prompt de sistema ou contexto inicial ao pedir ajuda para outra IA.

```text
Você está trabalhando no Project Babel, um mod client-side de tradução em tempo real para Minecraft 1.20.1.

A arquitetura atual é Multi-Projeto Gradle com Architectury Loom e três módulos:

:common
:forge
:fabric

O módulo :common é a fonte da verdade. Ele contém toda a lógica real do mod:
- APIs de tradução;
- pipeline;
- cache;
- dicionário/glossário;
- engines de tradução;
- scheduler;
- guardas de renderização;
- helpers de Component;
- tooltip;
- integrações agnósticas;
- UI comum;
- Mixins Vanilla;
- Access Widener comum.

Os módulos :forge e :fabric são adapters finos. Eles só podem conter:
- entrypoints;
- configs nativas;
- eventos nativos;
- bridges de PlatformServices;
- registro de keybinds;
- lifecycle/reload nativo;
- mixins de compatibilidade com mods externos;
- metadados do loader.

Regras obrigatórias:

1. Nunca importe net.fabricmc.* ou net.minecraftforge.* dentro do :common.
2. Nunca use FabricLoader, ModList, ForgeConfigSpec, ClientModInitializer ou @Mod dentro do :common.
3. Se o :common precisar saber algo do loader, use ProjectBabelCommon.platform().
4. Config no :common deve passar por ProjectBabelCommon.config(), nunca por AutoTranslateConfig ou ForgeConfigSpec.
5. Mixins contra Minecraft Vanilla ficam somente em common/src/main/java/com/projectbabel/mixin/vanilla.
6. Mixins contra mods externos ficam em fabric/src/main/java/com/projectbabel/mixin/compat ou forge/src/main/java/com/projectbabel/mixin/compat.
7. Não duplique engine, cache, scheduler, pipeline ou dicionário em :forge ou :fabric.
8. Hooks de renderização devem ser cache-only. Nunca faça HTTP, IO pesado ou espera bloqueante dentro de render, Font, GuiGraphics ou tooltip.
9. Não crie Thread, Executor ou CompletableFuture sem passar pelo scheduler comum.
10. Ao traduzir Component, preserve Style, siblings, hover/click events e formatação.
11. Resource reload é unificado por ProjectBabelReloadBus. Fabric e Forge apenas disparam seus eventos nativos para esse barramento.
12. O access widener oficial é common/src/main/resources/projectbabel.accesswidener.
13. O mixin config Vanilla oficial é common/src/main/resources/projectbabel-common.mixins.json.
14. Os mixin configs de plataforma só devem conter compat.*.
15. Antes de finalizar qualquer alteração, a estrutura deve passar por:
   ./gradlew validateArchitecture
   ./gradlew :common:compileJava
   ./gradlew :fabric:compileJava
   ./gradlew :forge:compileJava
   ./gradlew build

Ao sugerir alterações, priorize mover lógica para :common e manter :forge/:fabric como adaptadores mínimos. Nunca proponha soluções que dupliquem o pipeline entre loaders.
```

---

## Apêndice A — Como adicionar uma nova engine de tradução

Local correto:

```text
common/src/main/java/com/projectbabel/core/engine/
```

Passos:

```text
1. Criar engine nova no pacote core.engine.
2. Integrar ao manager/fachada comum existente.
3. Respeitar timeout e concorrência vindos de ProjectBabelCommon.config().
4. Usar scheduler comum para qualquer chamada assíncrona.
5. Nunca chamar engine diretamente a partir de mixin.
```

---

## Apêndice B — Como adicionar suporte a novo mod parceiro

Estrutura padrão:

```text
common/src/main/java/com/projectbabel/integrations/<modid>/
common/src/main/java/com/projectbabel/integrations/registry/<modid>/
fabric/src/main/java/com/projectbabel/mixin/compat/<modid>/
forge/src/main/java/com/projectbabel/mixin/compat/<modid>/
```

Fluxo correto:

```text
Mixin compat captura texto ou evento específico
        ↓
Mixin monta contexto mínimo
        ↓
Mixin chama classe comum da integração
        ↓
Integração comum usa pipeline/cache/scheduler
        ↓
Resultado volta para o ponto de renderização ou preload
```

O mixin não deve conter política de tradução.

---

## Apêndice C — Como adicionar nova opção de configuração

Passos:

```text
1. Adicionar getter/setter em BabelConfigView.
2. Implementar no FabricConfigBridge.
3. Implementar no ForgeConfigBridge.
4. Atualizar UI comum se a opção for editável.
5. Usar ProjectBabelCommon.config() no common.
```

Proibido:

```text
common importar AutoTranslateConfig
common importar ForgeConfigSpec
common ler arquivo TOML/JSON diretamente para config de plataforma
```

---

## Apêndice D — Como diagnosticar falhas de mixin

Se falhar em classe Vanilla:

```text
1. Verificar common/src/main/resources/projectbabel-common.mixins.json.
2. Verificar refmap projectbabel-common-refmap.json.
3. Verificar access widener comum.
4. Confirmar que não há cópia do mesmo mixin em :forge ou :fabric.
```

Se falhar em mod externo:

```text
1. Verificar projectbabel.mixins.json do loader afetado.
2. Verificar se o mod alvo existe naquele loader.
3. Verificar ProjectBabelMixinPlugin no Fabric, quando aplicável.
4. Verificar se o mixin compat chama lógica comum em vez de duplicar lógica.
```

---

## Apêndice E — Comandos obrigatórios de validação

```powershell
./gradlew --version
./gradlew validateArchitecture
./gradlew :common:compileJava
./gradlew :fabric:compileJava
./gradlew :forge:compileJava
./gradlew build
```

O wrapper deve apontar para:

```text
gradle-8.11.1-bin.zip
```

Não use Gradle 9 nesta fase da toolchain sem uma rodada completa de validação de Shadow, Loom, remap e refmap.
