# Sensus MMC In-Game News — Scope & Technical Plan (v2, B1)

Status: **draft for sign-off** · Rendering approach changed to build-time rendered pages.
The full runtime-browser (CDP) design is preserved in git history (`57c0a1d`).

This document defines what will be built, how the tablet displays the site, how one
codebase targets eight Minecraft loader/version combinations, and what still needs
a decision before coding starts.

---

## 1. Summary

A single item — the **News Tablet** — that opens a window showing
<https://sensusmmc.news> as the site really renders: the same layout, type,
colours, borders and images, with working scrolling and clickable links. It is a
**cropped, scrollable view of a real Chromium render**, not a reimplementation and
not a live browser.

Two supporting behaviours:

1. A chat message once per new article while the player is online:
   `New SensusMMC post uploaded! <link>`
2. While there are unread articles the item uses the animated `newstablet`
   texture and its tooltip shows `[1 unread article!]`.

### Decisions at a glance

- **Rendering:** the site's build pipeline renders each page with Playwright
  (Chromium) at exactly **400×478 CSS px, DPR 2 → 800×956 px**, slices it into
  1024×1024 PNG tiles, and exports a link map. The mod downloads, caches and
  scrolls those tiles.
- **No browser at runtime.** No child process, no debug port, no native
  libraries, no JavaScript on the player's machine.
- **Aspect ratio is fixed by construction:** the capture viewport is exactly
  `2 × 200 × 2 × 239`, so one game pixel is always four source pixels. The page is
  cropped to the window and scrolled; it is never scaled to fit.
- **Libraries:** `sensus-pages` (page cache/tiles/links) and `sensus-news` (feed,
  read state, notifications), both pure Java 8 with no Minecraft imports and no
  third-party dependencies.
- **Eight Stonecutter nodes**, same as before; only the texture bridge differs per
  era.

---

## 2. Goals and non-goals

### Goals

- Show the site as Chromium rendered it, at the correct proportions and framing.
- Scrolling, links, images, article navigation.
- Work on every target, on any Java 8/17/21/25 runtime, with no browser installed.
- No native code, no downloaded executables, no runtime process spawning.
- Readable text at the 200×239 window size.
- Degrade cleanly when offline or when page assets are missing.

### Non-goals (v1)

- Pixel-identical to a live desktop browser at 1200 px wide (impossible in a
  200 px window; see §6.8).
- Hover/focus/active styles, canvas animation, live-updating content.
- Text selection or copying.
- A general-purpose browser.
- Authenticated flows, comments, video.
- Redesigning the site into a "native" UI.

---

## 3. Target matrix

Eight nodes, all producing a runnable client jar. The rendering path is identical
for all of them; only the texture/model/GUI bridges differ.

| Node | Loader | Java | Notes |
|---|---|---|---|
| `1.7.10-forge` | Forge 10.13.4.1614 | 8 | No JSON item models, GL 2.1, NPOT limits |
| `1.12.2-forge` | Forge 14.23.5.2860 | 8 | JSON models, `ItemMeshDefinition` |
| `1.20.1-forge` | Forge 47.x | 17 | `Screen`, `GuiGraphics`, `ItemProperties` |
| `1.20.1-fabric` | Fabric Loader + API | 17 | Same shared screen code |
| `1.21.1-neoforge` | NeoForge 21.1.x | 21 | `ItemProperties` still present |
| `1.21.1-fabric` | Fabric Loader + API | 21 | |
| `26.2-neoforge` | NeoForge 26.2.x | 25 | Item-model component era; texture API unverified |
| `26.2-fabric` | Fabric Loader + API | 25 | |

Toolchain: **Gradle 9.5.1**, **Stonecutter 0.9.8**, **Unimined 1.4.2-SNAPSHOT**.
Phase 0 verifies that 1.7.10 and 26.2 can live in one build; fallback is two
Gradle builds or pinning the newest node at 1.21.1.

There is **no browser requirement** at runtime anymore, which removes the entire
platform-support caveat from v1 of this document.

---

## 4. Repository and build layout

```
settings.gradle.kts
build.gradle.kts                 # shared; branches on sc.current.project
stonecutter.gradle.kts
sensus-pages/                    # PURE JAVA 8. Manifest, tiles, cache, links.
sensus-news/                     # PURE JAVA 8. Feed, read state, notifications.
src/
  main/java/...                  # shared mod code (item, screen, bridges' users)
  main/resources/                # lang, textures, models, GUI texture
  forge/java/...                 # loader glue
  fabric/java/...
  neoforge/java/...
  versions/1.7.10/java/...       # era-specific texture/model/GUI code
  versions/1.12.2/java/...
  versions/1.20.1/java/...
  versions/1.21.1/java/...
  versions/26.2/java/...
versions/
  1.7.10-forge/ ... 26.2-fabric/ # node projects
site-tools/                      # optional: Playwright capture script (or in site repo)
```

Rules:

- `sensus-*` has zero Minecraft imports, zero third-party deps, `--release 8`.
- JSON and PNG handling are written in-house (`ImageIO` for PNG, no Gson).
- Minecraft code only sees bridge interfaces; no library references a bridge.
- Era-specific code goes in `src/versions/<mc>/`, not in Stonecutter directives.

The **capture script belongs to the site repository** (it runs in the site's CI
next to the static build). This repo owns only the consumer format. `site-tools/`
is where a reference implementation can live if you prefer to keep it here.

---

## 5. Product behaviour

### 5.1 The item

- `sensusmmc:news_tablet`, stack size 1, no durability, creative tab + recipe.
- Right-click opens the tablet screen.
- Unread > 0 → animated `newstablet`; unread = 0 → `newstablet_off`.
- Tooltip appends `[1 unread article!]` / `[N unread articles!]`.

### 5.2 The screen

- `gui_newstablet.png` is 256×256; the content region is **x 6..205, y 7..245**,
  i.e. **200×239 game pixels**.
- The page is drawn into that rect and clipped to it. No stretching, no
  letterboxing, no fitting the whole page.
- `Esc` closes. The screen is a normal `GuiScreen`/`Screen`, centred.

### 5.3 Interaction

| Input | Behaviour |
|---|---|
| Wheel | Scroll the page by 40 px (configurable), clamped |
| PageUp/PageDown | Scroll by one viewport minus 20 px overlap |
| Home/End | Top / bottom |
| Left click | Follow the link under the cursor |
| Hover | Pointer cursor over a link hotspot |
| `Esc` | Close |
| Scroll bar | Thin overlay thumb drawn by the mod on the right edge |
| Back | `Backspace` returns to the page you came from (in-memory history) |
| Reload | `F5` revalidates the page manifest |
| Home | `Ctrl+Home` returns to the home page |
| Open externally | `Ctrl+O` opens the current page in the OS browser |

### 5.4 Notifications

- Poll `feed.json` every 5 minutes while a world is loaded, on join, and on open.
- `New SensusMMC post uploaded! <link>` with a clickable URL.
- One message per article per profile; never repeats across restarts.
- Multiple new articles: one line each, cap 3, then `…and N more`.
- Silent when offline; exponential backoff on failure.

### 5.5 Unread

Unread = feed entry whose id is not in the local `read` set. Count drives the
texture and tooltip. See Q2 for when entries are marked read.

---

## 6. Rendering approach (B1)

### 6.1 Why not a runtime browser

| Option | Fidelity | Runtime cost | Trust | Verdict |
|---|---|---|---|---|
| **Build-time Chromium → tiles (chosen)** | Real Chromium pixels, frozen | ~20–50 MB RAM, no process | None beyond HTTPS | ✅ |
| Installed browser + CDP | Live, exact | 150–300 MB, child process, debug port | AV/EDR flags, store review risk | Alternative only |
| Bundled CEF/JCEF | Live, exact | 100–200 MB per platform, natives | Worse supply chain | ❌ |
| OS webview | Good | Per-OS JNI, capture problems | Medium | ❌ |
| JavaFX WebView | Poor (WebP/JS/CSS) | Big | Low | ❌ |
| Native reader | Not exact | Tiny | None | Fallback if B1 rejected |

Chromium still does the rendering — just on the site's CI, where it is an ordinary
build tool. The player receives pixels, not an engine.

### 6.2 Geometry contract (aspect ratio and cropping)

This is the part that must be exact.

```
GUI content rect        200 × 239 game px      ratio 200/239 = 0.83682
Capture CSS viewport    400 × 478 CSS px       ratio 400/478 = 0.83682
Capture device pixels   800 × 956 px           ratio 800/956 = 0.83682
Draw scale              0.25 device px / game px  (4 source px per game px)
```

Rules:

1. **Uniform scale only.** `scaleX == scaleY == 0.25`. There is no code path that
   scales X and Y independently.
2. **Never fit the page.** The page image is taller than the window; the client
   crops a 200×239 window out of it and scrolls. Shrinking the whole page to fit
   would make text unreadable and is explicitly forbidden.
3. **No horizontal crop, no horizontal scroll.** The capture width is exactly
   2× the window width, so the page fits horizontally at 1:1.
4. **Vertical crop:** `sourceY = scrollGamePx × 4`, `sourceHeight = 239 × 4 = 956`,
   clamped to `pageHeightDevicePx - 956`. Scroll positions are integers in game
   px, and 4 is an integer, so sampling stays pixel-aligned and never shimmers.
5. **Short pages:** if a page is shorter than 239 game px, draw it at the top and
   fill the remainder with the panel background. Never stretch.
6. **Last tile:** if `pageHeightDevicePx` is not a multiple of 1024, the final tile
   has a shorter content area; the client draws only the available rows.
7. **Viewport is part of the schema.** The manifest records `cssWidth: 400,
   dpr: 2`. A client whose window is 200×239 accepts only that viewport; a
   mismatch is a hard error, never a rescale.
8. **Images inside the page** are rasterised by Chromium at their CSS size with
   `object-fit` respected; the client applies no additional per-image scaling.
9. **GUI scale changes** (Minecraft's own GUI scale) multiply both axes equally;
   the geometry contract is unchanged.

If the window size or GUI scale policy ever changes, pages must be re-rendered.
That is a build-time change, not a client-side stretch.

### 6.3 Build output format

```
pages/index.json
pages/home/manifest.json
pages/home/0000.png, 0001.png, ...
pages/welcome-to-sensus-mmc/manifest.json
pages/welcome-to-sensus-mmc/0000.png, ...
```

`pages/index.json`:

```json
{
  "version": 1,
  "generated": "2026-05-30T10:00:00Z",
  "pages": [
    { "id": "home", "url": "/", "hash": "sha256:…" },
    { "id": "welcome-to-sensus-mmc", "url": "/articles/welcome-to-sensus-mmc",
      "hash": "sha256:…" }
  ]
}
```

`pages/<id>/manifest.json`:

```json
{
  "version": 1,
  "id": "home",
  "url": "https://sensusmmc.news/",
  "title": "Sensus MMC - Reliable MMC News",
  "contentHash": "sha256:…",
  "viewport": { "cssWidth": 400, "cssHeight": 478, "dpr": 2 },
  "image": { "width": 800, "height": 5736, "tileWidth": 1024, "tileHeight": 1024 },
  "tiles": [
    { "file": "0000.png", "hash": "sha256:…" },
    { "file": "0001.png", "hash": "sha256:…" }
  ],
  "links": [
    { "x": 16, "y": 210, "w": 180, "h": 24,
      "href": "/articles/welcome-to-sensus-mmc", "title": "Welcome to Sensus MMC" }
  ]
}
```

Link coordinates are **CSS pixels relative to the document**. The client converts
them with the same 0.5 factor used for the image, then subtracts the scroll offset.

Tiles are 1024×1024 PNGs with the 800-wide content left-aligned and the remaining
256 px filled by **edge-extension** (repeat the last content column), not
transparency. That matters for mipmaps: a transparent gutter would bleed a dark
seam into the right edge at lower mip levels.

### 6.4 Capture pipeline (site CI)

1. Build the static site locally (`_site/`), then serve it on a local port or use
   `file://`. **Do not capture the live URL** — this avoids Cloudflare's bot
   handling and makes the output reproducible.
2. Playwright with a pinned browser revision, viewport **400×478**, DPR **2**,
   locale/timezone fixed, `prefers-reduced-motion: reduce`.
3. Wait for `document.fonts.ready`, all `<img>` `decode()`, and network idle.
   **Fail the build if the expected fonts are not available** —
   `document.fonts.check('18px Georgia')` and `'18px Arial'` must be true,
   otherwise every line break silently shifts.
4. Stop animations, freeze the clock, hide the caret.
5. Handle the fixed Voronoi canvas (Q9):
   - preferred: temporarily set it to `position: absolute; height: <docHeight>px`,
     resize its backing store, seed its RNG, draw one frame, then capture; or
   - fallback: hide it and composite `#1a1a1a`.
6. Enable overlay scrollbars so the content width is not reduced by 15 px.
7. `page.screenshot({ fullPage: true })`, capped at 16384 device px for height
   (~8192 CSS px). Longer pages are split into continuation pages with stable ids
   (`article-id`, `article-id-2`, …) and the link map adjusted.
8. Slice into 800×1024 strips, pad to 1024 with edge extension, write PNG.
9. Extract links via `getBoundingClientRect()` + scroll offsets, classify internal
   vs external, store area for smallest-target hit-testing.
10. Write hashes, manifests, index. Publish `pages/` next to the site.

PNG (not WebP) is the client format: Chromium has already done the WebP decoding,
so the mod needs no WebP support and the PNGs are text-friendly and lossless.

### 6.5 Client pipeline

- `PageStore` fetches `index.json` and manifests over HTTPS with ETag/If-None-Match,
  verifies hashes, and keeps a disk LRU cache (`config/sensusmmc/pages/`,
  50 MB default).
- Tiles are fetched lazily on demand, on a background thread, with an LRU of four
  GPU textures (~21 MB VRAM with mips).
- Decode with `ImageIO` off-thread; build the mip chain on the **CPU** (box filter,
  1024→1) so no GL extension is required on 1.7.10; upload each level with
  `glTexImage2D` and set `GL_TEXTURE_MAX_LEVEL`.
- Upload on the render thread only. Drop the `BufferedImage` after upload.
- Draw the intersecting 1–2 tiles with `GL_LINEAR`/mipmap filtering, clipped with
  `GL_SCISSOR_TEST` to the 200×239 rect.
- Prefetch the next tile when within 512 source px of the edge; prefetch the next
  page's first tile when a link is hovered for >250 ms.
- Loading: a 64 px-wide blurred placeholder and a 150 ms fade-in. Cached pages
  paint immediately from disk.
- Offline: cached pages remain usable; uncached pages show the offline panel.

### 6.6 Prefetch and freshness

- After a feed poll, prefetch `index.json`, the home manifest and its first two
  tiles, plus the newest 2–3 article manifests and first tiles (15 MB budget).
- Pages are rebuilt when the site is published, not on a timer. The client's
  `index.json` hash tells it when a page changed.
- A page render is valid until the site's next build; there is no periodic
  re-render (Q11).

### 6.7 Latency

| Action | Expected |
|---|---|
| Scroll, tiles resident | ~16 ms (one frame) |
| Scroll into an unfetched tile | 100–500 ms placeholder, usually hidden by prefetch |
| Open a cached page | 100–300 ms (disk read + decode + upload) |
| Open an uncached page | 0.3–0.8 s first tile on broadband; 1–3 s for the page |
| Internal link, prefetched | 50–150 ms |
| Internal link, not prefetched | 0.3–1 s |
| External link | OS browser launch, 0.5–2 s |
| New-article chat line | poll interval (5 min) + site deploy |

Prefetching is what makes this feel instant. The alternative (CDP) is 150–300 MB
of RAM, 40–150 ms input latency and 15–20 fps scrolling; B1 scrolls at the game's
frame rate because it is just a textured quad.

### 6.8 What is and is not identical

**Identical to a Chromium screenshot of the page at 400×478 CSS:**

- layout, wrapping, spacing, borders, colours, fonts and glyph metrics (same
  engine, same fonts, if the capture host has them);
- images, including WebP, already rasterised;
- link positions.

**Not identical:**

- the live page at desktop width — 400 CSS px is the squeezed two-column layout,
  and no 200 px window can show the 1200 px version legibly;
- hover/focus/active styling, canvas animation, transitions, smooth JS scrolling;
- text selection, native scrollbar, native cursor;
- subpixel antialiasing (headless is grayscale; at 4× downscale this is invisible);
- content newer than the last site build.

Honest phrasing for the mod page: *"Shows SensusMMC exactly as Chromium renders it
at the tablet's width, with scrolling and links."* Not "a live browser".

---

## 7. Libraries and bridges

### 7.1 `sensus-pages` (pure Java 8)

| Class | Responsibility |
|---|---|
| `PageIndex` / `PageManifest` | Parse and validate `index.json` and manifests |
| `PageClient` | HTTPS fetch, ETag, hash verification, redirect policy |
| `PageCache` | Disk LRU, atomic writes, size cap, clear |
| `Tile` / `TileCache` | Decode, mip chain, LRU of decoded/uploaded tiles |
| `PageView` | Scroll/clamp math, visible tile list, source rects |
| `LinkMap` | CSS→game conversion, hit-testing, internal/external classification |
| `Json` | Minimal parser/writer |

```java
interface PageSurface {
    void open(String pageId);
    void scrollBy(int gamePx);
    void scrollTo(int gamePx);
    int  maxScroll();
    LinkHit hitTest(int localX, int localY); // null or href + title
    Frame  frame();                          // current visible tile refs
    State  state();                          // LOADING, READY, OFFLINE, MISSING
    void   close();
}
```

### 7.2 `sensus-news` (pure Java 8)

`FeedClient`, `Article`, `ReadState`, `NewsService`, `NewsListener` — unchanged
from v1. Polling, dedupe and persistence live here.

### 7.3 Bridges

| Bridge | Responsibility |
|---|---|
| `TextureBridge` | Create/update/delete a 1024×1024 GPU texture with a CPU mip chain |
| `ItemModelBridge` | Read/unread model or icon selection |
| `ScreenBridge` | Screen lifecycle, mouse wheel/keys, scissor, drawing |
| `TextBridge` | Tooltip/chat components |
| `ChatBridge` | Local client chat line |
| `OpenUrlBridge` | Open external URLs in the OS browser |
| `ConfigPathBridge` | Game/config directories |
| `RegistrationBridge` | Item, creative tab, client tick |

No `BrowserLocator`, no CDP, no process handling, no input forwarding.

### 7.4 Threading

```
render thread   draw tiles, hit-test links, upload pending decoded tiles
page thread     HTTP, hash verify, disk cache, PNG decode, mip chain
news thread     feed poll (can share the page thread pool)
```

No Minecraft class is touched off-thread except via
`Minecraft.getInstance().execute(...)`.

---

## 8. Per-version notes

### 8.1 1.7.10

- GL 2.1: POT 1024×1024 tiles, CPU mip chain, `glTexImage2D` per level,
  `GL_TEXTURE_MAX_LEVEL`, `GL_LINEAR_MIPMAP_LINEAR`.
- Check `GL_MAX_TEXTURE_SIZE`; if < 1024, use 512 tiles (render pipeline emits
  both sizes or the client downsamples at load time — Q13).
- Draw with `GL_SCISSOR_TEST` and `GL11` quads; `GuiScreen.drawScreen`.
- Item icon animation via three `IIcon` frames or a `.mcmeta` animation.
- Wheel via `handleMouseInput` + `Mouse.getEventDWheel()`.

### 8.2 1.12.2

- `DynamicTexture` per tile registered with `TextureManager`, deleted on eviction.
- JSON item models + `ItemMeshDefinition` for unread state.
- `GuiScreen` + `FontRenderer`, `handleMouseInput`.

### 8.3 1.20.1 / 1.21.1

- `NativeImage` → texture; `TextureUtil.prepareImage` + per-level upload or
  `DynamicTexture` for the base level. `GuiGraphics.blit` with a custom texture.
- `ItemProperties` override for unread.
- Loader split: Forge `@Mod` / Fabric `ClientModInitializer`.

### 8.4 26.2

- Texture upload path (`GpuTexture`/command encoder) and `GuiGraphics` blit
  signature to be confirmed in Phase 0.
- Item-model `select`/component instead of `ItemProperties`.
- Worst case: no mipmaps on 26.2 and the unread state shown only in the tooltip.

---

## 9. Feed, unread and notifications

### 9.1 `feed.json` (preferred)

```json
{
  "version": 1,
  "generated": "2026-05-30T09:33:39.771Z",
  "articles": [
    {
      "id": "welcome-to-sensus-mmc",
      "title": "Welcome to Sensus MMC",
      "url": "https://sensusmmc.news/articles/welcome-to-sensus-mmc",
      "published": "2026-05-30T09:33:39.771Z",
      "author": "Sensus MMC",
      "excerpt": "Short introduction",
      "image": "https://sensusmmc.news/assets/Sensus-logo.png"
    }
  ]
}
```

### 9.2 Fallback: scrape the homepage

Selectors: `.article-entry`, `h3 a`, `time[datetime]`, `.meta-author`, `.excerpt`,
`img.entry-icon`. Tolerant tag scanner, no external parser. Explicitly brittle.

### 9.3 Polling and dedupe

- 5 min while in a world; on join; on open; manual refresh.
- Backoff 5 → 10 → 20 → 40 min, reset on success.
- Persist `readIds` (500 cap) and `lastAnnouncedIds` (200 cap) atomically.
- Skip articles older than 30 days on a fresh profile.
- Queue notifications until a world is loaded.

---

## 10. Assets, models, recipe, config

### 10.1 Textures

| File | Use |
|---|---|
| `gui_newstablet.png` (256×256) | GUI, content rect 6,7–205,245 |
| `newstablet.png` (16×48) | Unread, 3 frames, `.mcmeta` animation |
| `newstablet_off.png` (16×16) | Read |

1.7.10 additionally gets `newstablet_0..2.png` generated from the sheet for the
icon-array fallback.

### 10.2 Model/lang

`models/item/news_tablet.json` + `_unread`, `overrides` on `sensusmmc:unread`;
`ModelMeshDefinition` on 1.12.2; icon array on 1.7.10.
Lang: `item.sensusmmc.news_tablet`, `…unread`, `chat.sensusmmc.new_post`,
`gui.sensusmmc.error.*`. Tooltip: `[%s unread article!]` / `[%s unread articles!]`.

### 10.3 Recipe (Q4)

```
paper  book   paper
iron   glass  iron      → 1× news_tablet
paper  iron   paper
```

Optionally also a rare village/library chest loot entry.

### 10.4 Config

`config/sensusmmc/config.json`:

```json
{
  "notifications": true,
  "pollMinutes": 5,
  "pageCacheMb": 50,
  "prefetchMb": 15,
  "scrollPixels": 40,
  "openExternalLinks": true,
  "keepPagesOffline": true
}
```

`config/sensusmmc/news-state.json`: `readIds`, `lastAnnouncedIds`, `lastCheck`.

---

## 11. Packaging and distribution

- One jar per node; libraries merged in; no natives; no downloaded executables.
- Client-only; no server component; no packets.
- Fabric client entrypoints only; Forge/NeoForge `clientSideOnly`/`displayTest`.
- CI builds the 8-node matrix with JDK 8/17/21/25.
- The site's CI publishes `pages/` and `feed.json` alongside the site.
- Licence: TBD (Q7).

---

## 12. Security and review

The runtime no longer executes anything. The client is an HTTPS downloader, a PNG
decoder and a cache.

| Risk | Mitigation |
|---|---|
| Malformed PNG / decoder exploit | Validate IHDR before decode: ≤ 2048×2048, ≤ 16 M pixels, ≤ 4 MB; decode off-thread; placeholder on failure |
| Oversized/malicious manifest | Max 2 MB, strict schema, caps on pages/tiles/links, no recursive structures |
| Path traversal via tile names | `[a-z0-9/_-]+\.png` only, resolve and verify `startsWith(cacheRoot)` |
| Cache poisoning | SHA-256 per tile/manifest from `index.json`, verified after download; versioned cache |
| Dangerous links | Internal only `https://sensusmmc.news/...`; external only `http(s)` and opened by the OS; reject `javascript:`, `data:`, `file:` |
| DoS via huge pages | Page height cap, tile cap, prefetch budget, LRU eviction, HTTP timeouts |
| Privacy | One origin, no cookies, no telemetry, no player identifiers; IP visible to the site as with any visit |
| Content rights | Tiles are hosted by the site, not bundled; third-party images inside pages need rights review; Georgia rasterisation is generally fine but OFL fonts remove the ambiguity (Q10) |

Distribution/review:

- Modrinth/CurseForge: no process execution, no native code, no executable
  downloads. Disclose network access and the local cache.
- Antivirus/EDR: nothing unusual; no browser launch, no debug port.
- Servers/anticheat: client-only, no packets, nothing to flag.
- Privacy note: what is downloaded, what is cached, how to clear it.
- Determinism: pinned Playwright, local site build, frozen clock/animations,
  font checks — a page diff is a content diff, not a rendering lottery.

---

## 13. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Fixed Voronoi canvas leaves seams or only covers the first viewport | Stretch to document height with a seeded frame, or use a flat background (Q9) |
| R2 | Capture host lacks Georgia/Arial → line breaks shift | Capture on Windows CI, or install fonts, or move the site to OFL webfonts; fail the build if fonts are missing |
| R3 | Article longer than the 16384 px capture cap | Split into continuation pages with stable ids |
| R4 | 1.7.10 lacks POT mip support or max texture < 1024 | CPU mip chain, POT tiles, 512 fallback |
| R5 | 4× minification makes small text soft | 2× DPR source, mipmaps, legibility test on real GUI scales |
| R6 | Page assets grow large | Tiles + LRU + 50 MB disk cap + 15 MB prefetch cap |
| R7 | Content is frozen at build time | Rebuild on publish; acceptable for bi-weekly issues |
| R8 | 26.2 texture/GUI APIs differ | Bridge isolation + Phase 0; worst case no mips/tooltip-only unread |
| R9 | Gradle/Unimined cannot build all 8 nodes together | Phase 0; split builds or pin 1.21.1 |
| R10 | Third-party image rights in rasterised pages | Keep tiles hosted, review content, link out where needed |
| R11 | No `gh` CLI | git + Actions UI; install later |
| R12 | Viewport/window mismatch if the GUI is resized | Manifest records the viewport; reject mismatches instead of stretching |

---

## 14. Build order

The order is driven by two rules: **the riskiest unknowns get tested first**, and
**nothing UI-related is built before a real captured page exists**. The reference
implementation is 1.20.1 because it has both loaders and the fastest iteration;
26.2 comes last because it has a fallback.

### Stage 0 — de-risk (2–3 d, two parallel tracks)

| Track | Work | Gate |
|---|---|---|
| **A. Toolchain** | Root Gradle + Stonecutter 8 nodes + Unimined snapshot; empty entrypoints; `build` all nodes; launch 1.20.1 | All 8 compile |
| **A. GL probe** | 1.7.10: create a 1024² POT texture, upload a CPU mip chain, draw with scissor | Correct image, no GL errors |
| **A. 26.2 probe** | Create/draw a GPU texture; item-model `select`; `GuiGraphics` blit signature | A texture renders on 26.2 |
| **B. Capture** | Playwright on the local `_site`, 400×478 @ DPR 2, font check, fixed-canvas handling, one page → tiles + manifest + links | Page bundle looks right at 200×239 |

If the toolchain gate fails: split builds or pin 1.21.1. If the 26.2 probe fails:
tooltip-only unread + no mips. If the canvas gate fails: flat background (Q9).

### Stage 1 — freeze the format (1 d)

- Schema v1 for `index.json` and `manifest.json`.
- Checked-in fixtures: home, a normal article, a long (continuation) page.
- Decide Q5 (width), Q9 (background), Q10 (fonts/host), Q12 (splitting).
- Gate: fixtures validate and render from the CLI with no network.

### Stage 2 — `sensus-pages` (3–4 d)

JSON, fetch/ETag/hash, disk LRU, decode limits, CPU mip chain, scroll/tile math,
link hit-testing, prefetch. Dev CLI: composite the 200×239 viewport at a given
scroll offset to a PNG, and print hit-tests.

Gate: CLI composites match the capture at several offsets; geometry unit tests
pass; hostile/corrupt fixtures are rejected.

### Stage 3 — 1.20.1 Forge vertical slice (3–4 d)

Item, registration, model/texture, screen, `TextureBridge`, wheel/click/hover,
error and offline panels. Fixtures first, live site second.

Gate: playable in a dev client; cached page opens; links navigate.

### Stage 4 — news, unread, notifications (2–3 d, overlaps Stage 3)

`sensus-news` with a fake server; wire chat, read state, model override, tooltip.

Gate: one chat line per new article; texture toggles; survives restart.

### Stage 5 — 1.20.1 Fabric (1–2 d)

Loader glue only; shared screen and libraries.

Gate: parity with Forge.

### Stage 6 — modern (3–5 d)

1.21.1 NeoForge + Fabric: `NativeImage`, `ItemProperties`, keybinds. Then 26.2:
confirm the texture path and item-model component, apply the Stage 0 fallbacks if
needed.

Gate: all modern nodes launch and render.

### Stage 7 — legacy (4–6 d)

1.12.2 (2–3 d): `DynamicTexture`, `ModelMeshDefinition`, GUI, wheel. Then 1.7.10
(2–3 d): POT tiles + mips, scissor, icon animation, chat. Both on Java 8.

Gate: no GL errors, correct crop, crisp text at GUI scale ≥ 2.

### Stage 8 — polish and release (3–5 d)

Prefetch tuning, transitions, GUI-scale hint, `/sensusmmc clearcache`, docs,
privacy note, CI matrix, jars, mod metadata.

Gate: Appendix C checklist.

### Critical path and parallel work

```
Stage 0 ──► Stage 1 ──► Stage 2 ──► Stage 3 ──► Stage 5 ──► Stage 6 ──► Stage 8
                          │            │
                          └──► Stage 4 ┘
                                       └──► Stage 7 ──┘
```

- Stage 0 Track A and Track B are independent and start together.
- Stage 4 can run beside Stage 3 once the item exists.
- 1.12.2, 1.7.10 and the 26.2 work are independent of each other.
- 26.2 never blocks another node; it ships with a documented degradation if its
  probes fail.

### First week, concretely

1. Scaffold the 8-node build and get `1.20.1-forge:runClient` up.
2. Capture one real page (home) to the fixture format, including the font check.
3. Prove the 1.7.10 POT/mip/scissor path and the 26.2 texture path.
4. Freeze schema v1 and land the fixture bundle.
5. Start `sensus-pages` with the scroll/crop math and the CLI compositor.

Total estimate: **3–5 weeks**, with Stage 0 and 26.2 carrying the schedule risk.

---

## 15. Open questions

**Q1 — `feed.json`.** Add it to the site? Recommended: yes.
**Q2 — Read marking.** Only when an article is opened (recommended), or when the
tablet opens?
**Q3 — Notifications.** World-only, announce anything newer than last seen
(recommended)?
**Q4 — Obtaining.** The recipe above, loot, or both?
**Q5 — Capture width.** 400 CSS px (exact 2× the window, recommended) vs 480
(roomier columns, ~7.5 game px text)?
**Q6 — Navigation chrome.** Keyboard + overlay scrollbar only (recommended)?
**Q7 — Identity/licence.** `sensusmmc`, `com.sensusmmc.news`, which licence?
**Q8 — External links.** Open in the OS browser after confirmation (recommended)?
**Q9 — Background.** Stretch one seeded Voronoi frame to full page height
(recommended, closer to the site) vs flat `#1a1a1a` (simpler, deterministic)?
**Q10 — Capture host/fonts.** Windows CI with Georgia/Arial (recommended), install
MS fonts on Linux CI, or switch the site to OFL webfonts?
**Q11 — Freshness.** Rebuild pages on publish only (recommended), or also on a
nightly cron?
**Q12 — Continuation pages.** Split long articles at 8192 CSS px with a "next page"
link (recommended)?

---

## Appendix A — Geometry worked example

```
window              200 × 239 game px
capture viewport    400 × 478 CSS px
device frame         800 × 956 px
page image           800 × 5736 px
scroll = 120 game px
source rect          x 0, y 480, w 800, h 956
draw                 200 × 239 at uniform 0.25 scale
link at CSS (16, 210) → game (8, 105) → screen (14, 112) after scroll = 0
```

Scrolling is clamped to `[0, (5736 - 956) / 4] = [0, 1195]` game px.

## Appendix B — Runtime-browser design (not chosen)

The full CDP design — process launch, screencast frames, input forwarding,
platform/JDK matrix, trust analysis, performance numbers — is in commit
`57c0a1d` (`SCOPE.md` v1) and in §6.1 above. It remains viable if live rendering
becomes a requirement, at the cost of a browser dependency, 150–300 MB RAM, and
the AV/store-review conversation described there.

## Appendix C — Definition of done

- [ ] All 8 nodes build and launch.
- [ ] A captured page shows correct proportions: 200×239 window, uniform 0.25
      scale, no stretching, correct crop at top/bottom/end of page.
- [ ] Scrolling is smooth and pixel-aligned; no shimmer.
- [ ] Clicks navigate; back returns; images are present.
- [ ] Cached pages open offline.
- [ ] Unread texture + tooltip and one chat line per new article.
- [ ] No process spawn, no native code, no debug port.
- [ ] 1.7.10 runs on Java 8; 26.2 runs on Java 25.
- [ ] Docs: capture requirements, cache location/clear, privacy, troubleshooting.
