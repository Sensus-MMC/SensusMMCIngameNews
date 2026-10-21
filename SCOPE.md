# Sensus MMC In-Game News — Scope & Technical Plan

Status: **draft for sign-off** · No implementation code has been written yet.

This document defines what will be built, how the in-game browser will work, which
library/engine it will use, how one codebase will target eight Minecraft
loader/version combinations, and what still needs a decision before coding starts.

---

## 1. Summary

A single item — the **News Tablet** — that opens a window rendering
<https://sensusmmc.news> exactly as a real browser does: the site's own fonts,
CSS, WebP images, the animated Voronoi canvas background, real scrolling,
clickable links and in-site navigation.

Two supporting behaviours:

1. A chat message is posted once per new article while the player is online:
   `New SensusMMC post uploaded! <link>`
2. While there are articles the player has not read, the item uses the animated
   `newstablet` texture instead of `newstablet_off`, and its tooltip shows
   `[1 unread article!]`.

The mod is one Stonecutter project with eight build nodes, plus two plain-Java
libraries that contain all the hard logic and know nothing about Minecraft.

**Recommended core decision:** the browser is a **headless Chromium process
(Edge / Chrome / Chromium already installed) driven over the Chrome DevTools
Protocol (CDP) by a small pure-Java client**. It is not JCEF, not JavaFX, and not
an OS webview. Section 6 explains why.

---

## 2. Goals and non-goals

### Goals

- Pixel-faithful rendering of the live site, including WebP, modern CSS, canvas
  animation, and fonts. No "reader mode" reimplementation of the site.
- Full interaction: hover, click, scroll, text selection is *not* required,
  keyboard input is optional but supported.
- Works offline-ish and degrades cleanly when no browser is installed, when the
  network is down, or when the browser dies.
- Identical behaviour on all eight targets, with only thin per-version bridges.
- No native libraries in the mod jar. No platform-specific binaries.
- Client-side only. Safe to join vanilla servers; installed on the client only.

### Non-goals (v1)

- Logging into the site, posting comments, or any authenticated flow.
- Playing video/audio. Audio is muted; `<video>` is not a requirement. (If the
  site adds video, Chromium renders frames but audio stays off.)
- A general-purpose in-game browser for arbitrary URLs.
- Bundling a browser binary with the mod.
- Server-side rendering or a proxy that rewrites the site.
- Mobile/controller UI, or a redesigned "native" news UI.

---

## 3. Target matrix

Eight nodes. All must compile and produce a runnable client jar.

| Node | Loader | Java | Notes |
|---|---|---|---|
| `1.7.10-forge` | Forge 10.13.4.1614 | 8 | No JSON item models, no lambdas in MC APIs, GL immediate-mode GUI |
| `1.12.2-forge` | Forge 14.23.5.2860 | 8 | JSON models, `ItemMeshDefinition`, `ITextComponent` |
| `1.20.1-forge` | Forge 47.x | 17 | Mojmaps via Unimined, `Screen`, `GuiGraphics`, `ItemProperties` |
| `1.20.1-fabric` | Fabric Loader + API | 17 | Same shared Screen code, different entrypoints |
| `1.21.1-neoforge` | NeoForge 21.1.x | 21 | `ItemProperties` still present |
| `1.21.1-fabric` | Fabric Loader + API | 21 | |
| `26.2-neoforge` | NeoForge 26.2.x | 25 | Item-model `select`/component era; APIs unverified (Phase 0) |
| `26.2-fabric` | Fabric Loader + API | 25 | |

All eight nodes compile regardless of what is installed. The **browser feature at
runtime additionally requires a Chromium-family browser on the same desktop**;
see §6.9 for the platform matrix and the caveats that come with it (Windows 7/8.1,
Snap/Flatpak, enterprise policies).

Toolchain assumption: **Gradle 9.5.1**, **Stonecutter 0.9.8**, **Unimined
1.4.2-SNAPSHOT** (26.x support only exists in snapshots). Every node is built by
one Gradle invocation, so Gradle must itself run on a JDK that can host all
toolchains (25 is fine; toolchains select 8/17/21/25 per node).

**Phase 0 exists specifically to de-risk this table.** If Gradle 9 + Unimined
snapshot cannot build 1.7.10 or 26.2 in the same build, the fallback is:

- keep 1.7.10/1.12.2 and 1.20.1+ in two Gradle builds sharing sources via
  `includeBuild`/source sets, or
- pin the newest node at 1.21.1 until Unimined ships a 26.2 release.

The rest of this document does not depend on which fallback is chosen.

---

## 4. Repository and build layout

```
settings.gradle.kts
build.gradle.kts                 # shared config; branches on sc.current.project
stonecutter.gradle.kts           # active node + node settings
gradle.properties
sensus-web/                      # PURE JAVA 8. Browser + CDP. No MC imports.
  src/main/java/...              #   browser process, CDP client, frame pump
sensus-news/                     # PURE JAVA 8. Feed, dedupe, unread state. No MC.
  src/main/java/...
src/                             # Stonecutter shared mod sources
  main/java/...                  #   tablet item logic, screen, bridges' users
  main/resources/                #   lang, textures, models, gui texture
  forge/java/...                 #   loader glue (1.7.10 / 1.12.2 / 1.20.1)
  fabric/java/...                #   client entrypoint, keybind, events
  neoforge/java/...              #   mod bus, client events
  versions/1.7.10/java/...       #   era-specific rendering / registration
  versions/1.12.2/java/...
  versions/1.20.1/java/...
  versions/1.21.1/java/...
  versions/26.2/java/...
versions/
  1.7.10-forge/                  # node projects; Stonecutter injects srcDirs
  1.12.2-forge/
  1.20.1-forge/
  1.20.1-fabric/
  1.21.1-neoforge/
  1.21.1-fabric/
  26.2-neoforge/
  26.2-fabric/
```

Rules that keep the code version-agnostic:

- `sensus-web` and `sensus-news` have **zero** Minecraft imports and compile with
  `--release 8`. JVM 25 loads class-file 52 without issue, so all nodes consume
  the same jars.
- They also have **zero third-party dependencies** (no Gson, no Netty, no
  OkHttp). JSON and the WebSocket client are implemented in the library. This
  avoids shading conflicts with the different Gson/Netty versions Minecraft
  ships in 1.7.10 vs 26.2.
- Minecraft code only ever touches the libraries through the bridge interfaces in
  section 7. A bridge implementation may not be referenced from library code.
- Shared mod code uses Stonecutter comment directives only where unavoidable
  (registration, events). Anything that would need more than a few directives
  goes into `src/versions/<mc>/` instead.

---

## 5. Product behaviour

### 5.1 The item

- ID `sensusmmc:news_tablet`, stack size 1, no durability, creative tab + recipe.
- Right-click opens the tablet screen. Sneak-right-click does the same (no
  secondary action planned) unless a "mark all read" action is wanted.
- Client-side unread state drives the model/texture:
  - unread > 0 → `newstablet` (animated, 3 frames)
  - unread = 0 → `newstablet_off`
- Tooltip appends `[1 unread article!]` (pluralised for >1). Proposed exact
  strings are in section 10.4.

### 5.2 The screen

- `gui_newstablet.png` is 256×256. The browser viewport is drawn into the black
  region **x 6..205, y 7..245** inclusive → **200×239 game pixels**.
- The rest of the texture is drawn as normal GUI chrome.
- No separate window frame is created; the screen is a normal `GuiScreen`/
  `Screen` sized 256×256, centred, with the usual dark backdrop.
- `Esc` closes. `E`/inventory does not open while the tablet is focused.

### 5.3 Interaction

| Input | Behaviour |
|---|---|
| Mouse move | Forwarded as `mouseMoved` (throttled), so `:hover` styles work |
| Left click | `mousePressed` + `mouseReleased`, follows links |
| Right click | Forwarded; context menu disabled in v1 |
| Mouse wheel | Forwarded as `mouseWheel`, page scrolls natively |
| `Esc` | Closes the tablet (does not cancel page load) |
| Scrollbar | Chromium's own scrollbar is rendered in the frame |
| Back / forward | `Mouse 4` / `Mouse 5` where the loader exposes them, otherwise `Alt+←` / `Alt+→` |
| Reload | `F5` |
| Home | `Home` key with `Ctrl` held (`Ctrl+Home`) |
| Open in OS browser | `Ctrl+O` or a clickable chat link |
| Typing | Forwarded only if the page has a focused text field (not needed for the current site) |

Navigation chrome is keyboard-only in v1 because the 200×239 screen is entirely
web content. If the bezel area of the texture has usable free space, a small
auto-hiding toolbar can be added later (see Q6).

### 5.4 New-post notification

- Poll every 5 minutes (configurable) while a world is loaded, and immediately
  on world join and on tablet open.
- Chat message: `New SensusMMC post uploaded! <link>` where `<link>` is a
  clickable URL to the article (`ClickEvent.OPEN_URL`).
- One message per new article per profile. Never repeats an article that was
  already announced, even across restarts.
- If several articles appear at once: one line per article, capped at 3, then
  `…and N more`.
- Silent when offline; retries with backoff; never spams errors into chat.

### 5.5 Unread semantics

An article is unread if its ID is not in the local `read` set. Unread count is the
number of feed entries not in that set. See Q2 for when entries get marked read.

---

## 6. Browser engine decision

### 6.1 Options considered

| Option | Fidelity | Platforms | Jar/dep cost | Verdict |
|---|---|---|---|---|
| **Headless Chromium + CDP (chosen)** | Exact: real Blink, WebP, canvas, fonts | Win/mac/Linux, uses installed browser | ~150 KB of Java, no natives | ✅ |
| JCEF / CEF in-process (OSR) | Exact | Per-OS natives (~150 MB), macOS main-thread conflict with GLFW | Huge, fragile | ❌ |
| MCEF (existing mod) | Exact | Only a few MC versions, extra mod dependency | Medium | ❌ (and doesn't cover 1.7.10/26.2) |
| JavaFX WebView | Poor: old WebKit, no WebP, weak ES6, bad canvas | JavaFX must be shipped/available; macOS thread issues | Large | ❌ |
| OS WebView (WebView2/WKWebView/WebKitGTK) | Good | Needs native bindings per OS; window layering over LWJGL is unreliable | Large | ❌ |
| Server-side render / screenshot service | Exact-ish | Adds a server dependency and latency; no interaction | Small | ❌ |
| Native reimplementation ("reader mode") | Not exact | Easy | Small | ❌ (violates requirement) |

The site uses WebP, a `<canvas>` Voronoi animation, flexbox and ES modules. Only
a real Chromium-class engine reproduces it reliably.

### 6.2 Chosen architecture

```
┌────────────────────────── Minecraft client ──────────────────────────┐
│  TabletScreen (render thread)                                        │
│    ├─ draws gui_newstablet.png                                        │
│    ├─ draws latest frame from SensusFrameBuffer (GPU texture)         │
│    └─ forwards mouse/keys ──► BrowserSession.inputQueue               │
│                                                                       │
│  BrowserSession (one daemon thread, per client)                       │
│    ├─ locates + launches Edge/Chrome/Chromium (child process)         │
│    ├─ CDP over WebSocket (ws://127.0.0.1:<random>)                    │
│    ├─ Page.startScreencast → base64 PNG → ImageIO → BufferedImage     │
│    └─ publishes frames to SensusFrameBuffer (latest-wins, 1 slot)     │
└───────────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  headless Chromium child    │
                    │  --remote-debugging-port=0  │
                    │  dedicated user-data-dir    │
                    └─────────────────────────────┘
```

Why a separate process and not an embedded engine:

- No native binaries in the jar, no per-OS packaging, no macOS
  `-XstartOnFirstThread`/AppKit conflict (the classic JCEF killer).
- A browser crash or hang cannot take the game down; the session restarts.
- Chromium's sandbox and site isolation stay intact.
- The mod works on Linux servers/desktops where a browser is present, and fails
  cleanly where it is not.

### 6.3 Process lifecycle

1. **Locate** a browser, in order:
   - explicit `browserPath` from config,
   - Windows: `%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe`,
     `%ProgramFiles%\Google\Chrome\Application\chrome.exe`,
     `%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe`, Chromium, Brave,
   - macOS: `/Applications/{Microsoft Edge,Google Chrome,Chromium,Brave Browser}.app/Contents/MacOS/...`,
   - Linux: `google-chrome`, `google-chrome-stable`, `chromium`,
     `chromium-browser`, `microsoft-edge`, `brave-browser` on `PATH`.
2. **Launch** with:
   ```
   --headless=new
   --remote-debugging-port=0
   --user-data-dir=<gameDir>/config/sensusmmc/browser-profile
   --no-first-run --no-default-browser-check --no-first-run
   --disable-background-networking --disable-component-update
   --disable-sync --disable-extensions --disable-default-apps
   --disable-features=Translate,MediaRouter,OptimizationHints
   --mute-audio --hide-crash-restore-bubble
   --window-size=<cssW>,<cssH>
   ```
   `--remote-debugging-port=0` makes the OS pick a free port; the actual URL is
   printed on stderr (`DevTools listening on ws://127.0.0.1:<port>/...`). Parsing
   that removes port collisions and the need to scan.
   The dedicated profile persists cookies, cache and *visited-link styling*
   between sessions, which matters for fidelity.
3. **Connect**: browser WebSocket → `Target.createTarget` → `Target.attachToTarget`
   (flatten) → per-session `sessionId`. `Page.enable`, `Runtime.enable`,
   `Emulation.setDeviceMetricsOverride`, `Page.navigate`.
4. **Idle**: when the tablet closes, stop the screencast and keep the process for
   90 s; then `Browser.close` and reap. Reopening within the window is instant.
5. **Shutdown**: `Browser.close`, then `destroyForcibly` after 2 s. The profile is
   left in place; a `--repair` path deletes it if Chromium refuses to start.
6. **Crash**: one automatic relaunch per 10 minutes, then an in-GUI error panel.

Only one browser process per game instance, shared by all future browser
surfaces.

### 6.4 Frame pipeline

- `Emulation.setDeviceMetricsOverride` with
  `width=400, height=478, deviceScaleFactor=2, mobile=false`.
  That is **2 CSS px per game pixel**, rendered at 2× device scale → an
  800×956 frame drawn into 200×239, i.e. a 4× supersample. 18 px site text lands
  at ~9 game pixels, comparable to Minecraft's own font, while remaining sharp.
  The viewport width is configurable (400–520 CSS px) and will be tuned against
  the real site in Phase 1; the site has no media queries, so narrower viewports
  squeeze the sidebar (see Q5).
- `Page.startScreencast {format:"png", quality:100, maxWidth:800, maxHeight:956,
  everyNthFrame:1}`. PNG is mandatory: JPEG artifacts on text would read as
  "pixelation".
- Each `Page.screencastFrame` is acknowledged **after** the frame has been
  decoded and handed to the texture. Chromium waits for the ack before sending
  the next frame, which gives free backpressure: the mod consumes at most what it
  can draw. This matters because the Voronoi canvas animates continuously.
- Decode with `javax.imageio.ImageIO` (headless-safe, no AWT window). Convert
  once to the bridge's native image format.
- `SensusFrameBuffer` holds exactly one pending frame (latest wins). Dropping
  intermediate frames is correct and keeps latency low while scrolling.
- Texture upload happens on the game's render thread, at most once per client
  tick or when a new frame is pending. Use `GL_LINEAR` magnification and
  `GL_LINEAR_MIPMAP_LINEAR` minification with mipmaps regenerated on upload, to
  avoid shimmer during scroll; if mipmap cost shows up in profiling, fall back to
  `GL_LINEAR` (the 4× supersample already reduces aliasing).
- **NPOT caveat (1.7.10):** 800×956 is not a power of two, and mipmapped NPOT
  textures are not guaranteed on GL 2.1-era hardware/drivers. Allocate a
  1024×1024 (or 1024×512) power-of-two texture and upload the frame into a
  sub-rect, or skip mipmaps entirely and use `GL_LINEAR` + clamp. This is decided
  per bridge, not in the library.
- First paint: draw a "Loading SensusMMC…" placeholder inside the screen until
  the first frame arrives, then cross-fade for ~150 ms.

### 6.5 Input

- Map game mouse coordinates inside the screen rect to CSS:
  `cssX = (mx - screenX) * (cssW / 200)`, same for Y.
- `Input.dispatchMouseEvent` for `mouseMoved` (≤ 30 Hz), `mousePressed`,
  `mouseReleased`, `mouseWheel` (`deltaX/Y` in CSS px; 1 MC notch ≈ 60 px).
- Buttons: 0 = left, 2 = right, 3/4 = back/forward via `Page.navigateToHistoryEntry`
  (or `Runtime.evaluate history.back()`), because CDP does not expose mouse
  buttons 4/5 as navigation.
- `Input.dispatchKeyEvent` (`keyDown`, `char`, `keyUp`) with DOM `key`/`code`/
  `windowsVirtualKeyCode` mapping. Needed for `F5`-style shortcuts we intercept,
  and for any future page form.
- The browser cursor is **not** captured by the screencast, so the game's own
  cursor is drawn. To hint clickability, poll
  `document.elementFromPoint(x,y)` cursor style at ~5 Hz and switch to a hand
  cursor when it is `pointer`.
- All dispatch calls are queued and written from the browser thread; the render
  thread never blocks on the socket.

### 6.6 Navigation and security

- Allowlist: `sensusmmc.news` and `www.sensusmmc.news`. Any other scheme/host
  (GitHub, Discord, `mailto:`) is intercepted by a document-start script and sent
  through `Runtime.addBinding("sensusExternal")`; the mod then opens it in the
  system browser (`Util.getPlatform().openUri` / `Desktop.browse`) after a
  one-line confirmation in chat. This keeps the tablet a "Sensus reader", not a
  general browser.
- `file://`, `javascript:` in the address bar (there is none), downloads
  (`Browser.setDownloadBehavior: deny`), `beforeunload` dialogs (auto-accept) and
  `window.open` (redirected to the external handler) are blocked.
- The debug port binds to loopback only, is randomly chosen, and exists only for
  the lifetime of the browser process. No remote debugging pipe is exposed.
- The profile directory is per-install, not shared with the user's normal browser
  profile, and contains no credentials.
- The mod never sends the player's Minecraft identity, IP, or logs to the site.
  The site sees a normal Chromium user agent (we override the default
  `HeadlessChrome` UA string to the equivalent desktop UA for compatibility).

### 6.7 Failure handling

| Failure | Behaviour |
|---|---|
| No browser found | In-screen panel: "No Chromium-based browser found", list of supported browsers, a **Retry** button, and a link to the install page opened in the OS browser |
| Browser fails to start | Same panel + the last stderr line (truncated) behind a "Details" toggle |
| Browser crashes / hung > 10 s | One silent relaunch; second failure shows the panel |
| No internet / DNS failure | Chromium error page is *not* shown; the mod detects the navigation failure and shows its own "SensusMMC is unreachable" panel with Retry and the cached last frame dimmed behind it |
| Cloudflare challenge | Rendered as-is (it is part of "exactly as online"), with a "Solve in system browser" button that opens the same URL externally. Top risk, see §12 |
| TLS failure in the Java feed poller (old JRE) | Article detection falls back to fetching through the already-running Chromium; if that is impossible, notifications are disabled for the session with one chat notice |

### 6.8 Performance budget

Expectations to validate in Phase 1 (numbers are ranges, not commitments):

| Scenario | Frames | CPU (one core) | Extra RSS | Latency |
|---|---|---|---|---|
| Tablet closed / 90 s after close | 0 | 0 % (browser reaped) | 0 | — |
| Static article, no input | ~0 fps (screencast is change-driven) | < 1 % | 120–200 MB | — |
| Homepage with Voronoi canvas | capped 15–20 fps | 10–25 % | 200–300 MB | 40–150 ms input→paint |
| Cold open (page load) | — | spike | — | 0.6–1.5 s |
| Warm reopen (< 90 s) | — | — | — | < 150 ms |

Per-frame cost at 800×956: Chromium paint + PNG encode ~5–15 ms, base64/WebSocket
~2–5 ms, decode ~5–15 ms, GPU upload ~1–3 ms. The ack-driven loop is the throttle:
if the game cannot keep up, frames are simply dropped, so the game stutters less
but the page looks choppier.

Low-end profile (config): `cssViewportWidth` 200–320, `deviceScaleFactor` 1,
frame cap 10 fps, no mipmaps. That cuts the frame to ~0.2–0.8 MB and makes text
softer, trading the "no pixelation" goal for playability.

Known limits:

- The Voronoi background animates forever, so the homepage is never a "static
  page" while the tablet is open. Closing the tablet is the only way to reach 0 %.
- 1.7.10-era integrated GPUs and GL 2.1 drivers are the worst case: NPOT texture
  handling (above), slow `glTexSubImage2D` on unified memory, and 30 fps client
  rendering leave little headroom.
- 4 GB RAM machines (common with 1.7.10) will feel the extra 150–300 MB.
- Java 8 vs 25 does not materially change the costs; the browser does the work.

### 6.9 Platform and JDK coverage (honest version)

**The feature requires a Chromium-family browser (`msedge`, `chrome`,
`chromium`, Brave) on the same desktop.** There is no fallback renderer in v1, so
"works on all platforms" really means "works on all desktops that have one of
those browsers".

| Platform | Works? | Caveats |
|---|---|---|
| Windows 10/11 x64/ARM64 | ✅ out of the box | Edge is preinstalled; Chrome/Chromium also detected |
| Windows 7 / 8.1 | ⚠️ with limits | Last Chromium is v109; no `--headless=new`, so the legacy `--headless` path must be implemented and tested. Edge is often absent. Still relevant for 1.7.10 players |
| macOS 11+ (Intel/Apple Silicon) | ✅ | Chrome/Edge/Chromium; headless needs no display and does not touch the GLFW main thread |
| macOS 10.13–10.15 | ⚠️ | Browser version is frozen by the OS; legacy headless path |
| Linux X11/Wayland (deb/rpm) | ✅ | Distro `chromium`/`google-chrome` works |
| Linux Snap/Flatpak | ⚠️ | Confinement may block the profile path under `~/.minecraft` or the loopback debugging port; must be detected and explained |
| Steam Deck | ⚠️ | Needs a browser installed (Chrome/Chromium flatpak); same confinement caveats |
| Android/iOS/ChromeOS | ❌ | No desktop Java, no child process; out of scope |

| JDK | Works? | Notes |
|---|---|---|
| 8 (1.7.10, 1.12.2) | ✅ | `ProcessBuilder`, `Socket`, `java.util.Base64`, `ImageIO` are all Java 8 APIs. No `java.net.http`; the CDP client is hand-written |
| 8u261+ | ✅ TLS 1.3 | Older 8u uses TLS 1.2, which the site still serves; polling works |
| 17 / 21 | ✅ | Used by 1.20.1 / 1.21.1 |
| 25 | ✅ | Required by 26.2; class-file 52 libraries load fine |

There is **no JNI, no `sun.*` API, no AWT window, and no JDK-specific code path**
— the same library jars run on all four JDKs. The failure modes are OS/browser
policy, not Java version.

Additional blockers worth detecting explicitly (each gets a clear error panel):

- Enterprise policy `RemoteDebuggingAllowed=false` (Chrome/Edge) or a locked-down
  profile — the browser exits with an error, the mod must say so rather than hang.
- Antivirus/EDR terminating a browser launched with a debugging port.
- An existing browser instance locking the profile (mitigated by a dedicated
  profile).
- A browser that starts but never prints `DevTools listening on ...` (timeout).

### 6.10 Trust, privacy and "does this look shady?"

Yes — this is the weakest part of the design from a player-trust perspective, and
it should be treated as a product constraint, not hand-waved.

**What the mod actually does**

- Finds an already-installed, signed browser and starts it as a child process.
- Gives it a dedicated profile under `config/sensusmmc/browser-profile`, so it
  never sees the player's normal cookies, history, passwords or extensions.
- Talks to it over a random loopback-only port, for as long as the tablet is
  open (plus 90 s).
- Loads exactly one origin: `sensusmmc.news`. Every other link is handed to the
  system browser.
- Downloads and installs nothing.

**What it does not do**

- No access to the user's real browser profile, no extension installation, no
  binary download, no telemetry, no reading of game/server data.
- No data sent anywhere except the site the player asked to read.

**Why it still looks suspicious**

- "Minecraft mod spawns Chrome with `--remote-debugging-port`" is literally a
  technique used by infostealers. EDR/AV products alert on it, and managed
  machines may block it.
- A local process could attach to the debug port while the tablet is open. It
  would only reach a throwaway profile and a public page, and any attacker with
  local code execution can do worse, but it is still an unnecessary surface.
- Mod distribution platforms review process execution harshly. Modrinth/CurseForge
  may require disclosure or reject it; some server anticheats scan child
  processes.

**Mitigations (all cheap, all recommended)**

- First-run confirmation screen: one paragraph, "uses your installed browser in a
  separate profile, connects only to sensusmmc.news", with an opt-out.
- A status line in the tablet ("Local browser: Edge 141, private profile") so the
  mechanism is visible, not hidden.
- Config switch `browserEnabled: false` and a documented "this mod starts a
  browser" line in the description.
- No port reuse, no fixed port, no remote debugging outside the session, no
  `--no-sandbox`.
- Open source, reproducible builds, no obfuscation.
- If the trust cost is unacceptable, the only alternatives are a bundled CEF
  (much larger, downloads natives, macOS thread problems) or a native re-render
  (not exact). There is no free lunch for "render the real site".

A pipe-based CDP transport (`--remote-debugging-pipe`) would remove the listening
port and the AV signature, but Java's `ProcessBuilder` cannot pass the extra file
descriptors Chromium expects, so it is not viable without a native helper.


---

## 7. Version-agnostic code and bridges

### 7.1 `sensus-web` (pure Java 8)

| Class | Responsibility |
|---|---|
| `BrowserLocator` | Find an installed Chromium browser per OS; validate `--version` |
| `BrowserProcess` | Launch, parse the DevTools URL from stderr, stop/kill, capture stderr tail |
| `CdpConnection` | Minimal RFC 6455 WebSocket client over `java.net.Socket` (masking, fragmentation, ping/pong, >1 MB payloads) |
| `CdpClient` | Request/response correlation by `id`, event dispatch by `method` + `sessionId` |
| `BrowserSession` | Target/session setup, navigation, screencast, input, external-link binding |
| `FrameBuffer` | Latest-frame slot, drop-oldest semantics, sequence number |
| `Json` | Small parser/writer sufficient for CDP and `feed.json` |
| `BrowserException` | Typed failures (`NOT_FOUND`, `LAUNCH_FAILED`, `DISCONNECTED`, `NAVIGATION_FAILED`, `TIMEOUT`) |

Public API (Minecraft code sees only this):

```java
interface BrowserSurface {
    void open(String url);                 // navigate
    void resize(int cssWidth, int cssHeight, double deviceScaleFactor);
    void mouseMove(int cssX, int cssY);
    void mouseButton(int cssX, int cssY, int button, boolean down);
    void wheel(int cssX, int cssY, double dx, double dy);
    void key(int keyCode, char ch, boolean down);
    void back(); void forward(); void reload(); void home();
    Frame pollFrame();                     // null if unchanged
    State state();                         // STARTING, LIVE, OFFLINE, CRASHED, NO_BROWSER
    String statusDetail();
    void close();
}
```

The library has **no** `Minecraft`, `GL`, `LWJGL`, AWT-window or logging-framework
dependency. It takes a path for the profile and a `Consumer<String>` for logs.

### 7.2 `sensus-news` (pure Java 8)

| Class | Responsibility |
|---|---|
| `FeedClient` | Fetch `feed.json` (or scrape fallback), parse, validate, cache |
| `Article` | id, title, url, published, author, excerpt, image |
| `ReadState` | Persisted read IDs + last announced ID, atomic save |
| `NewsService` | Poll scheduling with backoff, new-article detection, unread count |
| `NewsListener` | Callback interface (`onNewArticles(List<Article>)`, `onUnreadChanged(int)`) |

The service never touches Minecraft; the mod subscribes to it.

### 7.3 Bridges (implemented once per era)

| Bridge | Why it exists | 1.7.10 | 1.12.2 | 1.20.1 | 1.21.1 | 26.2 |
|---|---|---|---|---|---|---|
| `TextureBridge` | Upload an `int[]`/`BufferedImage` frame to a GPU texture and bind it | `DynamicTexture` / `TextureUtil` | `DynamicTexture` | `DynamicTexture` + `NativeImage` | same | verify GPU texture API |
| `ItemModelBridge` | Choose read/unread model or icon | `IIcon[]` + tick counter (or `.mcmeta`) | `ItemMeshDefinition` | `ItemProperties` | `ItemProperties` | item-model `select`/component (verify) |
| `ScreenBridge` | Mouse/key/scroll plumbing and screen lifecycle | `GuiScreen` overrides | `GuiScreen` | `Screen`/`GuiGraphics` | same | verify |
| `TextBridge` | Build components, tooltips, clickable chat | `ChatComponentText` | `TextComponentString` | `Component` | `Component` | verify |
| `ChatBridge` | Post a local client chat line | `printChatMessage` | same | `gui.getChat().addMessage` | same | verify |
| `OpenUrlBridge` | Open a URL in the OS browser | `Util.getPlatform().openUri` | same | same | same | verify |
| `ConfigPathBridge` | Where `config/` and the game dir are | `Minecraft.getMinecraft().mcDataDir` | same | `FMLPaths.CONFIGDIR` | `FabricLoader` | verify |
| `RegistrationBridge` | Item + creative tab + client tick + keybind | `GameRegistry` | `GameRegistry` + `ModelLoader` | deferred registers | same | verify |
| `LoaderBridge` | Entrypoint, client/server split, event subscription | Forge `@Mod` | Forge `@Mod` | Forge/Fabric split | NeoForge/Fabric split | verify |

Each bridge lives in `src/versions/<mc>/` and `src/<loader>/`, never in
`sensus-*`. Shared mod code is written against the bridge interfaces.

### 7.4 Threading model

```
render thread ── draws texture, polls FrameBuffer, pushes input events
browser thread ── CDP socket read loop, decode, feed polling
IO thread (news) ── HTTP only, never touches GL or MC state
```

- No Minecraft class is touched off the client thread except through
  `Minecraft.getInstance().execute(...)` for chat and state changes.
- The browser thread is a daemon; it is stopped on client shutdown.
- All queues are bounded; mouse moves coalesce, wheel events sum, clicks never
  drop.

---

## 8. Per-version implementation notes

### 8.1 1.7.10

- Java 8, no JSON item models: the item needs `registerIcons`, and animation is
  done either by returning one of three `IIcon`s based on a client tick counter
  (safe) or with a `.png.mcmeta` animation (verify item-atlas support). Ship three
  split frames `newstablet_0..2.png` so the icon path always works.
- No `ItemProperties`; the read/unread switch is in `getIconIndex` /
  `getIconFromDamage`.
- GUI: `GuiScreen.drawScreen`, `drawTexturedModalRect`, `GL11` blending. Mouse
  wheel arrives via `handleMouseInput` + `Mouse.getEventDWheel()`.
- Text: `ChatComponentText` + `ChatStyle.setChatClickEvent(new ClickEvent(OPEN_URL, url))`.
- FML: `@Mod(modid=..., clientSideOnly=true)`, `@SideOnly(Side.CLIENT)` on client
  classes. No Fabric node.
- `mcDataDir` for config/profile paths.

### 8.2 1.12.2

- JSON item models + `ModelLoader.setCustomMeshDefinition` +
  `ModelBakery.registerItemVariants` for read/unread.
- `GuiScreen.drawScreen`, `FontRenderer.drawString`, `ITextComponent`.
- `handleMouseInput` for wheel; `Minecraft.getMinecraft()` accessible statically.

### 8.3 1.20.1 / 1.21.1

- `Screen` + `GuiGraphics` (`blit`, `drawString`), `ItemProperties.register` with
  `new ResourceLocation("sensusmmc:unread")` and an item-model `overrides` entry.
- `Component.literal(...)`, `ChatType` styling; `Minecraft.getInstance().gui.getChat().addMessage`.
- Loader split: Forge `@Mod` + `FMLClientSetupEvent`, Fabric `ClientModInitializer`.
- Keybinds via the loader's own API (no shared `KeyMapping` differences beyond
  registration).

### 8.4 26.2 (highest uncertainty)

Known/likely differences to confirm in Phase 0:

- Item models are data-driven definitions (`minecraft:model`, `select`,
  `condition`, `range_dispatch`); `ItemProperties` may be gone. The unread state
  must be expressed through a component (`custom_model_data` or a custom
  component) or a client-side render-state hook.
- GUI rendering may be `GuiGraphics` with a different `blit` signature, or a
  render-state pipeline. A single `ScreenBridge` implementation absorbs it.
- Texture upload may require a `GpuTexture`/`CommandEncoder` path rather than
  `DynamicTexture`.
- Java 25 toolchain and Gradle 9 are mandatory.

If any of these turn out to be large, the fallback is to ship 26.2 with the
read/unread state shown only in the tooltip (static `newstablet_off` texture) and
the animated texture on older nodes — a small, clearly-scoped degradation.

---

## 9. News feed, unread state, notifications

### 9.1 Preferred: add a static feed

Add `https://sensusmmc.news/feed.json`, regenerated with the site build:

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

The mod tolerates unknown fields, missing optional fields, and unsorted entries.
`id` is the article slug and must be stable.

### 9.2 Fallback: scrape the homepage

The current homepage already contains everything needed:

| Field | Selector |
|---|---|
| container | `.article-entry` |
| title | `h3 a` (text) |
| url | `h3 a[href]` |
| id | last path segment of the URL |
| published | `time[datetime]` |
| author | `.meta-author` |
| excerpt | `.excerpt` |
| image | `img.entry-icon[src]` |

A small tolerant HTML tag scanner (no external parser) extracts these. This is
explicitly a fallback: a site redesign breaks it. The client uses `feed.json`
when it exists and falls back to scraping on 404.

### 9.3 Polling and dedupe

- Poll every 5 min while a world is loaded; on join; on tablet open; on manual
  refresh.
- Exponential backoff on failure: 5 → 10 → 20 → 40 min, reset on success.
- Persist `lastAnnouncedIds` (bounded, 200) and `readIds` (bounded, 500) in
  `config/sensusmmc/news-state.json`, written atomically (temp file + move).
- Never announce articles older than 30 days on first install (avoids a burst of
  historical messages on a fresh profile).
- Never announce while the player is on the main menu; queue until a world is
  loaded.

### 9.4 Unread rules

- Unread = feed entry id ∉ `readIds`.
- `readIds` grows when an article is opened (either from chat or from the tablet)
  and, if Q2 says so, when the tablet is opened.
- Cap `readIds` at 500 entries, dropping the oldest; older articles then show as
  unread again, which is harmless because they are outside the "new" window.

---

## 10. Assets, recipes, config

### 10.1 Textures

| File | Use | Notes |
|---|---|---|
| `gui_newstablet.png` (256×256) | GUI | Browser area 6,7 → 205,245 |
| `newstablet.png` (16×48) | Unread item | 3 frames of 16×16; `.mcmeta` `{"animation":{"frametime":5}}` |
| `newstablet_off.png` (16×16) | Read item | Static |

All three are supplied in `C:\Users\admin\Downloads` and will be copied into
`src/main/resources/assets/sensusmmc/textures/...`. A 1.7.10-only split
`newstablet_0..2.png` is generated at build time from the 16×48 sheet (or checked
in) for the icon-array fallback.

### 10.2 Models and lang keys

- `models/item/news_tablet.json` (+ `_unread`) with an `overrides` entry on
  `sensusmmc:unread`, plus `ModelMeshDefinition` for 1.12.2 and an icon array for
  1.7.10.
- Lang: `item.sensusmmc.news_tablet`, `item.sensusmmc.news_tablet.unread` (1 =
  `%s unread article!`), `gui.sensusmmc.news_tablet.error.*`, `chat.sensusmmc.new_post`.
- Tooltip line: `[%s unread article!]` / `[%s unread articles!]`.

### 10.3 Recipe (proposal, needs sign-off — Q4)

Shapeless or shaped, cheap enough to be an early-game utility:

```
paper  book   paper
iron   glass  iron      → 1× news_tablet
paper  iron   paper
```

Alternative: right-click a vanilla book with a compass (no recipe at all).
Creative-tab inclusion is independent of the recipe.

### 10.4 Config

`config/sensusmmc/config.json` (hand-written, no library, works on every loader):

```json
{
  "notifications": true,
  "pollMinutes": 5,
  "browserPath": "",
  "cssViewportWidth": 400,
  "openExternalLinks": true,
  "announceHistorical": false,
  "keepBrowserSeconds": 90
}
```

`config/sensusmmc/news-state.json` holds `readIds`, `lastAnnouncedIds`,
`lastCheck`. Both are client-side, human-readable, and safe to delete.

---

## 11. Packaging and distribution

- One jar per node, no shared "core" jar required at runtime (libraries are
  shaded/merged into each mod jar; they are small).
- No natives, no downloaded binaries, no installer.
- Fabric nodes declare client entrypoints only; Forge/NeoForge use
  `clientSideOnly`/`displayTest` so servers do not require the mod.
- `fabric.mod.json` / `mods.toml` / `neoforge.mods.toml` generated per node with
  the correct `minecraft` and loader version ranges.
- CI (GitHub Actions, once `gh`/Actions is available) builds the 8-node matrix on
  Ubuntu with JDK 8/17/21/25 and uploads jars. macOS/Windows smoke tests are
  manual.
- Licence: TBD (Q7). Assets are supplied by the project owner.

---

## 12. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Cloudflare serves a challenge to headless Chromium | Browser unusable | Use `--headless=new`, override the UA to a normal desktop Chrome UA, persistent profile with cookies; Phase 0 tests the real site. Fallback: "open in system browser" + a static offline mirror is *not* planned, so this must be resolved |
| R2 | Gradle 9.5.1 + Unimined snapshot + Stonecutter cannot build 1.7.10 or 26.2 together | Matrix slips | Phase 0 spike; fallback to two Gradle builds or pinning 1.21.1 |
| R3 | 26.2 rendering/item-model APIs differ from assumptions | Rework | Isolated bridges + Phase 0 compile spike; worst case static texture on 26.2 |
| R4 | Animated canvas burns CPU / bandwidth | Poor performance | Ack-driven screencast backpressure, cap at ~20 fps, stop when closed, idle shutdown |
| R5 | Chromium not installed (Linux, locked-down machines) | Feature unavailable | Clear error panel, documented requirement, config override path; optional future "download Chromium" button |
| R6 | Site has no feed and scraping breaks | Missing notifications | Add `feed.json` (preferred); tolerant scraper + unit tests against saved HTML |
| R7 | Old Java 8 JRE cannot negotiate TLS with the site | Poll fails | Detect handshake failure; route the fetch through Chromium when available; one-time chat notice |
| R8 | Debug port/profile misuse | Security concern | Loopback-only random port, per-install profile, no remote debugging enabled beyond process lifetime, no user credentials |
| R9 | Mixed Gson/Netty versions across MC versions | Class conflicts | Zero third-party dependencies; own JSON and WebSocket code |
| R10 | Legibility at 200×239 | UX | 4× supersample, configurable viewport, Phase 1 tuning on real hardware; optional zoom later |
| R11 | No `gh` CLI in this environment | Release automation | Use git + Actions UI for now; install `gh` later |
| R12 | AV/EDR flags a browser launched with a debugging port | Game-side failure, user alarm | Disclose in mod description, first-run opt-in, dedicated profile, loopback-only ephemeral port, clear error panel |
| R13 | Enterprise policy blocks remote debugging, or Snap/Flatpak confinement blocks the profile/port | Feature unavailable on managed/Linux setups | Detect and explain; document distro-package preference; allow `browserPath` override |
| R14 | Windows 7/8.1 or macOS 10.13–10.15 only have Chromium ≤109 | `--headless=new` unsupported | Implement the legacy `--headless` screencast path and test it on both |
| R15 | Modrinth/CurseForge or server anticheats object to process execution | Distribution blocked | Full disclosure, source availability, no downloads, opt-in; fall back to a non-browser UI only if the platform rejects the design |
| R16 | Mipmapped NPOT 800×956 textures on GL 2.1 hardware | Black/incomplete texture on 1.7.10 | Power-of-two 1024×1024 texture + sub-rect upload, or no mipmaps on legacy nodes |

---

## 13. Milestones

| Phase | Deliverable | Est. |
|---|---|---|
| **0. Feasibility spike** | 8-node Stonecutter/Unimined skeleton with a dummy item; 26.2 `runClient`; confirm the real site loads in headless Chromium without a Cloudflare challenge | 1–2 d |
| **1. `sensus-web`** | Standalone CLI: launch browser, open the site, stream frames to PNG files, inject clicks/scroll; runs on Java 8 and 25 | 3–5 d |
| **2. `sensus-news`** | Feed client (JSON + scraper), read/announced state, unit tests, fake HTTP server | 2–3 d |
| **3. Mod core + 1.20.1 Forge/Fabric** | Item, textures, GUI, bridge implementations, config, notifications | 4–6 d |
| **4. 1.12.2 + 1.7.10** | Legacy renderers, icon animation, chat, tooltips | 3–5 d |
| **5. 1.21.1 + 26.2** | Modern model/component bridge, texture path, keybinds | 3–6 d |
| **6. Polish** | Error panels, cross-fade, cursor hint, performance, packaging, docs, CI | 3–5 d |

Rough total: **3–5 weeks** of focused work, with Phase 0 and 26.2 carrying the
schedule risk.

---

## 14. Open questions (need a decision)

**Q1 — Feed endpoint.** May the site add a static `https://sensusmmc.news/feed.json`
(§9.1)? Recommended: yes. Without it the mod scrapes the homepage, which works
today but is brittle.

**Q2 — When is an article marked read?** Options:
(a) as soon as the tablet is opened; (b) only when that article's page is opened
from the tablet or chat; (c) opening the tablet marks everything currently
visible in the feed as read. Recommended: **(b)** — matches "unread article"
intuition, and the tablet's home page still shows the list.

**Q3 — Notification scope.** Only while in a world/on a server (recommended), or
also on the main menu? Only for articles published while playing, or also missed
ones from the last N days? Recommended: world-only, and announce anything newer
than the last seen ID regardless of age, except on a fresh profile where articles
older than 30 days are skipped.

**Q4 — Obtaining the tablet.** Recipe (§10.3), structure loot, or both?
Recommended: the cheap shaped recipe plus a rare loot chance in village/library
chests.

**Q5 — Narrow layout.** The site is a fixed desktop layout with no media queries.
At the tablet's 200 px width the two columns are cramped. Options:
(a) render exactly as online and accept the squeeze; (b) inject a minimal
`@media (max-width: 700px) { #columns { flex-direction: column } }` rule so the
sidebar stacks; (c) default to (a) with a config toggle for (b). Recommended:
**(c)**, default (a), because "exactly as online" was the requirement.

**Q6 — Navigation chrome.** Keyboard-only (recommended for v1), or a tiny
auto-hiding toolbar over the top of the screen with back/forward/reload/home?
The 200×239 area is entirely web content, so a toolbar would overlap the page.

**Q7 — Identity.** Confirm mod ID `sensusmmc`, package root `com.sensusmmc.news`,
display name "SensusMMC In-Game News", and licence (MIT? All Rights Reserved?).

**Q8 — External links.** Open GitHub/Discord links in the system browser
(recommended) or block them with a notice?

---

## Appendix A — Screen geometry

```
gui_newstablet.png          256 × 256
browser rect (inclusive)    x 6..205, y 7..245
browser size                200 × 239
css viewport (default)      400 × 478
device scale factor         2
screencast frame            800 × 956
downscale to                200 × 239  (4× supersample)
```

Mouse mapping: `cssX = (mx - guiLeft - 6) * 2`, `cssY = (my - guiTop - 7) * 2`
(with `cssW/200` instead of `2` when configured differently).

## Appendix B — CDP calls used

| Purpose | Method |
|---|---|
| Version probe + WS URL | `GET /json/version` |
| New tab | `Target.createTarget` |
| Attach | `Target.attachToTarget {flatten:true}` |
| Page lifecycle | `Page.enable`, `Runtime.enable`, `Log.enable` |
| Viewport | `Emulation.setDeviceMetricsOverride`, `Emulation.setUserAgentOverride` |
| Navigate | `Page.navigate {url}` |
| Frames | `Page.startScreencast`, `Page.screencastFrame`, `Page.screencastFrameAck`, `Page.stopScreencast` |
| Mouse | `Input.dispatchMouseEvent` |
| Keyboard | `Input.dispatchKeyEvent` |
| External links | `Runtime.addBinding`, `Runtime.bindingCalled` |
| Navigation state | `Page.frameNavigated`, `Page.loadEventFired`, `Page.navigatedWithinDocument` |
| Network failures | `Network.enable`, `Network.loadingFailed` |
| History | `Page.navigateToHistoryEntry`, `Runtime.evaluate("history.back()")` |
| Downloads | `Browser.setDownloadBehavior {behavior:"deny"}` |
| Shutdown | `Browser.close` |

## Appendix C — Definition of done

- [ ] All 8 nodes build and launch a client.
- [ ] The tablet renders the live homepage with no visible pixelation or layout
      corruption, and scrolls smoothly.
- [ ] Clicking an article navigates; back returns to the homepage; images and the
      Voronoi background render.
- [ ] Unread count, animated texture, and tooltip match the read state.
- [ ] New articles produce exactly one clickable chat line per profile.
- [ ] No browser installed → actionable error panel, no crash, no log spam.
- [ ] Closing the tablet stops the browser within 90 s; client shutdown reaps it.
- [ ] Java 8 legacy nodes run on a Java 8 JRE; 26.2 runs on Java 25.
- [ ] Docs: install note ("requires Edge/Chrome/Chromium"), config reference,
      privacy note, troubleshooting.
