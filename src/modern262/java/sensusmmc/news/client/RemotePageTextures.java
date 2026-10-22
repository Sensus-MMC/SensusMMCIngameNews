package sensusmmc.news.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import sensusmmc.news.RemotePagePack;
import sensusmmc.news.ResourceLocations;
import sensusmmc.news.SensusNews;

public final class RemotePageTextures {
    private static final Map<String, Identifier> CACHE = new HashMap<String, Identifier>();

    private RemotePageTextures() {
    }

    public static Identifier get(String file) {
        Identifier cached = CACHE.get(file);
        if (cached != null) return cached;
        byte[] bytes = RemotePagePack.tile(file);
        if (bytes == null) return null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            String path = "remote/" + file.toLowerCase(Locale.ROOT).replace('/', '_').replace('.', '_');
            Identifier id = ResourceLocations.of(SensusNews.MOD_ID, path);
            DynamicTexture texture = new DynamicTexture(() -> "Sensus page " + path, image);
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(id, texture);
            CACHE.put(file, id);
            return id;
        } catch (IOException error) {
            return null;
        }
    }
}
