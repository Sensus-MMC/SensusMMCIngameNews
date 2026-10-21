package sensusmmc.news.client;

import sensusmmc.news.ResourceLocations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;
import sensusmmc.sensuspages.Page;
import sensusmmc.sensuspages.PageLink;
import sensusmmc.sensuspages.PageManifest;
import sensusmmc.sensuspages.PageViewport;
import sensusmmc.sensuspages.Tile;

public final class NewsTabletScreen extends Screen {
    private static final ResourceLocation GUI_TEXTURE =
        ResourceLocations.of(SensusNews.MOD_ID, "textures/gui/gui_newstablet.png");
    private static final ResourceLocation MANIFEST =
        ResourceLocations.of(SensusNews.MOD_ID, "pages/manifest.json");
    private static final Set<ResourceLocation> REGISTERED = new HashSet<ResourceLocation>();
    private static final int GUI_WIDTH = 212;
    private static final int GUI_HEIGHT = 251;
    private static final int CONTENT_X = 6;
    private static final int CONTENT_Y = 6;
    private static final int CONTENT_WIDTH = 200;
    private static final int CONTENT_HEIGHT = 239;

    private PageManifest manifest;
    private Page page;
    private double tabletScale = 1.0;
    private int left;
    private int top;
    private double scroll;

    public NewsTabletScreen() {
        super(Component.literal("News Tablet"));
    }

    @Override
    protected void init() {
        double guiScale = minecraft.getWindow().getGuiScale();
        double desired = 4.8 / guiScale;
        double fit = Math.min((width - 24.0) / GUI_WIDTH, (height - 24.0) / GUI_HEIGHT);
        tabletScale = Math.max(0.5, Math.min(desired, fit));
        left = (int) Math.round((width - GUI_WIDTH * tabletScale) / 2.0);
        top = (int) Math.round((height - GUI_HEIGHT * tabletScale) / 2.0);
        registerTexture(GUI_TEXTURE, false);
        NewsTabletContent.ensureBundledFeed();
        loadManifest();
        showPage("home");
        NewsTabletState.markAllRead();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0.0);
        pose.scale((float) tabletScale, (float) tabletScale, 1.0f);
        graphics.blit(GUI_TEXTURE, 0, 0, 0.0f, 0.0f, GUI_WIDTH, GUI_HEIGHT, GUI_WIDTH, GUI_HEIGHT);
        pose.popPose();
        drawPage(graphics);
        if (page == null) {
            int textX = left + (int) Math.round(CONTENT_X * tabletScale) + 8;
            int textY = top + (int) Math.round(CONTENT_Y * tabletScale) + 8;
            graphics.drawString(font, "News pages unavailable", textX, textY, 0xFFFF5555, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page == null || manifest == null) return false;
        double maximum = page.scrollMax(manifest.viewport());
        if (maximum <= 0.0) return false;
        scroll = Math.max(0.0, Math.min(maximum, scroll - delta * 24.0));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || page == null || manifest == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double contentX = (mouseX - (left + CONTENT_X * tabletScale)) / tabletScale;
        double contentY = (mouseY - (top + CONTENT_Y * tabletScale)) / tabletScale;
        if (contentX < 0.0 || contentY < 0.0 || contentX >= CONTENT_WIDTH || contentY >= CONTENT_HEIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        PageLink link = page.linkAt(contentX, contentY, scroll, manifest.viewport().scale());
        if (link == null) return false;
        if (link.external()) {
            Util.getPlatform().openUri(link.href());
        } else {
            openInternal(link.href());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_UP) {
            scrollBy(-16.0);
            return true;
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            scrollBy(16.0);
            return true;
        }
        if (keyCode == InputConstants.KEY_PAGEUP) {
            scrollBy(-CONTENT_HEIGHT * 0.8);
            return true;
        }
        if (keyCode == InputConstants.KEY_PAGEDOWN) {
            scrollBy(CONTENT_HEIGHT * 0.8);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void loadManifest() {
        manifest = null;
        if (minecraft == null) return;
        try {
            Resource resource = minecraft.getResourceManager().getResource(MANIFEST).orElse(null);
            if (resource == null) return;
            try (InputStream input = resource.open()) {
                manifest = PageManifest.fromJson(read(input));
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private void showPage(String id) {
        if (manifest == null) return;
        Page next = manifest.page(id);
        if (next == null) return;
        page = next;
        scroll = 0.0;
        for (Tile tile : next.tiles()) {
            registerTexture(tileTexture(tile), true);
        }
    }

    private void openInternal(String href) {
        if (manifest == null) return;
        String normalized = href;
        int hash = normalized.indexOf('#');
        if (hash >= 0) normalized = normalized.substring(0, hash);
        int query = normalized.indexOf('?');
        if (query >= 0) normalized = normalized.substring(0, query);
        if (normalized.isEmpty()) return;
        for (Page candidate : manifest.pages()) {
            if (candidate.url().equals(normalized)) {
                showPage(candidate.id());
                return;
            }
        }
    }

    private void drawPage(GuiGraphics graphics) {
        if (page == null || manifest == null) return;
        PageViewport viewport = manifest.viewport();
        double maximum = page.scrollMax(viewport);
        scroll = Math.max(0.0, Math.min(maximum, scroll));
        double contentLeft = left + CONTENT_X * tabletScale;
        double contentTop = top + CONTENT_Y * tabletScale;
        double contentWidth = CONTENT_WIDTH * tabletScale;
        double contentHeight = CONTENT_HEIGHT * tabletScale;
        graphics.enableScissor(
            (int) Math.floor(contentLeft),
            (int) Math.floor(contentTop),
            (int) Math.ceil(contentLeft + contentWidth),
            (int) Math.ceil(contentTop + contentHeight)
        );
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(contentLeft, contentTop - scroll * tabletScale, 0.0);
        pose.scale((float) (viewport.scale() * tabletScale), (float) (viewport.scale() * tabletScale), 1.0f);
        for (Tile tile : page.tiles()) {
            ResourceLocation texture = tileTexture(tile);
            graphics.blit(
                texture,
                0,
                tile.y(),
                0.0f,
                0.0f,
                tile.contentWidth(),
                tile.contentHeight(),
                tile.width(),
                tile.height()
            );
        }
        pose.popPose();
        graphics.disableScissor();
    }

    private void scrollBy(double amount) {
        if (page == null || manifest == null) return;
        double maximum = page.scrollMax(manifest.viewport());
        scroll = Math.max(0.0, Math.min(maximum, scroll + amount));
    }

    private void registerTexture(ResourceLocation location, boolean smooth) {
        TextureManager manager = minecraft.getTextureManager();
        if (REGISTERED.add(location)) {
            manager.register(location, new SimpleTexture(location));
        }
        AbstractTexture texture = manager.getTexture(location);
        texture.setFilter(smooth, false);
    }

    private static ResourceLocation tileTexture(Tile tile) {
        return ResourceLocations.of(SensusNews.MOD_ID, "textures/gui/pages/" + tile.file());
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
