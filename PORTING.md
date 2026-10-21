# Porting status

## Built and verified

- `1.20.1-forge`
- `1.20.1-fabric`
- `1.21.1-neoforge`
- `1.21.1-fabric`

All four nodes pass `build` and `libraryTests`. Each production jar contains the
shared page tiles, feed, GUI texture, logo, and loader metadata.

## Shared behaviour

- Cropped 200x239 page view inside the 212x251 tablet frame, scaled up slightly.
- Mouse wheel, keyboard scrolling, internal page links, and external links.
- Persistent known/read state in `config/sensusmmc-news-state.json`.
- Config in `config/sensusmmc-news.properties`:
  - `requireTablet=false` enables the client keybind and client-only use.
  - `requireTablet=true` disables the keybind and requires the item.
  - `chatNotifications=true` enables the new-article chat message.
  - `pollSeconds=300` and `feedUrl=https://sensusmmc.news/feed.json`.
- Bundled feed seeds known posts, so the first online check does not spam chat.
- Chat link text is `[New Sensus article was posted! <url>]`, with the URL clickable.
- Unread tooltip: `[1 unread article!]` (plural for multiple).
- The mod logo is a 16x nearest-neighbour upscale of `newstablet_on`.

## Still staged

### 26.2 NeoForge / Fabric

Sources for the 26.2 API generation are staged under `src/modern262`,
`src/fabric262`, and `src/neoforge262`, but the nodes are not registered in
`settings.gradle.kts` yet. 26.2 is a large API break:

- `ResourceLocation` became `Identifier`.
- `GuiGraphics` became `GuiGraphicsExtractor`.
- Screen input uses `MouseButtonEvent` / `KeyEvent`.
- `Item.use` returns `InteractionResult`.
- The old item `overrides` model format was replaced by item-model definitions.
- Fabric key registration moved to `KeyMappingHelper`.
- The old `ItemProperties` model predicate API is gone.

The ports need their own item-model JSON and a 26.2 model-property strategy before
they can be enabled.

### 1.7.10 / 1.12.2 Forge

These need a separate legacy GUI (GL textured quads, no `GuiGraphics`), legacy
item rendering (`IIcon` / `ItemMeshDefinition`), `GameRegistry` item registration,
legacy keybind and chat APIs, and an `mcmod.info` resource.

## Build commands

```text
.\gradlew.bat :1.20.1-forge:build
.\gradlew.bat :1.20.1-fabric:build
.\gradlew.bat :1.21.1-neoforge:build
.\gradlew.bat :1.21.1-fabric:build
.\gradlew.bat libraryTests
```
