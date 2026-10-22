package sensusmmc.news.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.TextureUtil;

import sensusmmc.news.RemotePagePack;

public final class RemotePageTextures {
    private static final Map<String, Integer> CACHE = new HashMap<String, Integer>();

    private RemotePageTextures() {
    }

    public static Integer get(String file) {
        Integer cached = CACHE.get(file);
        if (cached != null) return cached;
        byte[] bytes = RemotePagePack.tile(file);
        if (bytes == null) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return null;
            int texture = TextureUtil.glGenTextures();
            TextureUtil.uploadTextureImageAllocate(texture, image, true, false);
            CACHE.put(file, texture);
            return texture;
        } catch (IOException error) {
            return null;
        }
    }
}
