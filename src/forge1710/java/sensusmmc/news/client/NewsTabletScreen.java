package sensusmmc.news.client;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;
import sensusmmc.sensuspages.Page;
import sensusmmc.sensuspages.PageLink;
import sensusmmc.sensuspages.PageManifest;
import sensusmmc.sensuspages.PageViewport;
import sensusmmc.sensuspages.Tile;

public final class NewsTabletScreen extends GuiScreen {
    private static final ResourceLocation GUI_TEXTURE =
        new ResourceLocation(SensusNews.MOD_ID, "textures/gui/gui_newstablet.png");
    private static final ResourceLocation MANIFEST =
        new ResourceLocation(SensusNews.MOD_ID, "pages/manifest.json");
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 320;
    private static final int CONTENT_X = 8;
    private static final int CONTENT_Y = 6;
    private static final int CONTENT_WIDTH = 305;
    private static final int CONTENT_HEIGHT = 308;

    private PageManifest manifest;
    private Page page;
    private double tabletScale = 1.0;
    private int left;
    private int top;
    private double scroll;

    @Override
    public void initGui() {
        double guiScale = mc.gameSettings.guiScale == 0
            ? new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor()
            : mc.gameSettings.guiScale;
        double desired = 6.4 / guiScale;
        double fit = Math.min((width - 12.0) / GUI_WIDTH, (height - 12.0) / GUI_HEIGHT);
        tabletScale = Math.max(0.5, Math.min(desired, fit));
        left = (int) Math.round((width - GUI_WIDTH * tabletScale) / 2.0);
        top = (int) Math.round((height - GUI_HEIGHT * tabletScale) / 2.0);
        NewsTabletContent.ensureBundledFeed();
        loadManifest();
        showPage("home");
        NewsTabletState.markAllRead();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glPushMatrix();
        GL11.glTranslatef(left, top, 0.0F);
        GL11.glScalef((float) tabletScale, (float) tabletScale, 1.0F);
        drawQuad(GUI_TEXTURE, 0.0F, 0.0F, GUI_WIDTH, GUI_HEIGHT, 1.0F, 1.0F, false);
        GL11.glPopMatrix();
        drawPage();
        if (page == null) {
            int textX = left + (int) Math.round(CONTENT_X * tabletScale) + 8;
            int textY = top + (int) Math.round(CONTENT_Y * tabletScale) + 8;
            fontRendererObj.drawString("News pages unavailable", textX, textY, 0xFFFF5555);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollBy(wheel > 0 ? -24.0 : 24.0);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || page == null || manifest == null) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        double contentX = (mouseX - (left + CONTENT_X * tabletScale)) / tabletScale;
        double contentY = (mouseY - (top + CONTENT_Y * tabletScale)) / tabletScale;
        if (contentX < 0.0 || contentY < 0.0 || contentX >= CONTENT_WIDTH || contentY >= CONTENT_HEIGHT) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        PageLink link = page.linkAt(contentX, contentY, scroll, manifest.viewport().scale());
        if (link == null) return;
        if (link.external()) {
            try {
                Desktop.getDesktop().browse(new URI(link.href()));
            } catch (Exception error) {
                error.printStackTrace();
            }
        } else {
            openInternal(link.href());
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            scrollBy(-16.0);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            scrollBy(16.0);
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            scrollBy(-CONTENT_HEIGHT * 0.8);
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            scrollBy(CONTENT_HEIGHT * 0.8);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void loadManifest() {
        manifest = null;
        try {
            IResource resource = mc.getResourceManager().getResource(MANIFEST);
            if (resource == null) return;
            try (InputStream input = resource.getInputStream()) {
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

    private void drawPage() {
        if (page == null || manifest == null) return;
        PageViewport viewport = manifest.viewport();
        double maximum = page.scrollMax(viewport);
        scroll = Math.max(0.0, Math.min(maximum, scroll));
        double contentLeft = left + CONTENT_X * tabletScale;
        double contentTop = top + CONTENT_Y * tabletScale;
        double contentWidth = CONTENT_WIDTH * tabletScale;
        double contentHeight = CONTENT_HEIGHT * tabletScale;
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int factor = resolution.getScaleFactor();
        int scissorX = (int) Math.floor(contentLeft * factor);
        int scissorY = mc.displayHeight - (int) Math.ceil((contentTop + contentHeight) * factor);
        int scissorWidth = (int) Math.ceil(contentWidth * factor);
        int scissorHeight = (int) Math.ceil(contentHeight * factor);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(
            (float) contentLeft,
            (float) (contentTop - scroll * tabletScale),
            0.0F
        );
        float pageScale = (float) (viewport.scale() * tabletScale);
        GL11.glScalef(pageScale, pageScale, 1.0F);
        for (Tile tile : page.tiles()) {
            drawQuad(
                tileTexture(tile),
                0.0F,
                tile.y(),
                tile.contentWidth(),
                tile.contentHeight(),
                (float) tile.contentWidth() / tile.width(),
                (float) tile.contentHeight() / tile.height(),
                true
            );
        }
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void scrollBy(double amount) {
        if (page == null || manifest == null) return;
        double maximum = page.scrollMax(manifest.viewport());
        scroll = Math.max(0.0, Math.min(maximum, scroll + amount));
    }

    private static void drawQuad(
        ResourceLocation texture,
        float x,
        float y,
        float width,
        float height,
        float u1,
        float v1,
        boolean smooth
    ) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        int filter = smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, 0.0, v1);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0.0, u1, 0.0);
        tessellator.addVertexWithUV(x, y, 0.0, 0.0, 0.0);
        tessellator.draw();
    }

    private static ResourceLocation tileTexture(Tile tile) {
        return new ResourceLocation(SensusNews.MOD_ID, "textures/gui/pages/" + tile.file());
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
