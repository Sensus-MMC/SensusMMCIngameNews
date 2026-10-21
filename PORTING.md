# Porting status

## Built and verified

All eight target nodes build successfully:

- `1.7.10-forge`
- `1.12.2-forge`
- `1.20.1-forge`
- `1.20.1-fabric`
- `1.21.1-neoforge`
- `1.21.1-fabric`
- `26.2-neoforge`
- `26.2-fabric`

`.\gradlew.bat build` and `.\gradlew.bat libraryTests` pass for all nodes.

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
- If `feed.json` is not deployed yet, the client falls back to the site's homepage
  article list so new articles can still be detected.
- Chat link text is `[New Sensus article was posted! <url>]`, with the URL clickable.
- Unread tooltip: `[1 unread article!]` (plural for multiple).
- The mod logo is a 16x nearest-neighbour upscale of `newstablet_on`.

## Era notes

### 1.20.1 / 1.21.1

The primary target. Uses `GuiGraphics`, `ItemProperties`, animated item models,
and a registered client keybind.

### 1.7.10 / 1.12.2

The page view uses legacy `GuiScreen` rendering with textured quads. 1.12.2 uses
an `ItemMeshDefinition` for the read/unread model switch. 1.7.10 uses `IIcon`
switching and the first frame of the active texture, so its unread icon is static.

### 26.2

The screen uses the `GuiGraphicsExtractor` API, `Identifier`, the new input event
classes, and an `assets/sensusmmc/items/news_tablet.json` item-model definition.
The 26.2 item model currently always uses the active texture; the unread tooltip
and read state still work. This is the remaining visual parity item for 26.2.

## Build commands

```text
.\gradlew.bat build
.\gradlew.bat libraryTests
```

The build is configured for one worker to avoid exhausting memory while eight
Minecraft toolchains are configured in one Gradle build.
