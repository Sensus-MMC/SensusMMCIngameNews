package sensusmmc.news.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import sensusmmc.news.RemotePagePack;
import sensusmmc.news.ResourceLocations;
import sensusmmc.news.SensusNews;

public final class RemotePageTextures {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<String, ResourceLocation>();

    private RemotePageTextures() {
    }

    public static ResourceLocation get(String file) {
        ResourceLocation cached = CACHE.get(file);
        if (cached != null) return cached;
        byte[] bytes = RemotePagePack.tile(file);
        if (bytes == null) return null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            String path = "remote/" + file.toLowerCase(Locale.ROOT).replace('/', '_').replace('.', '_');
            ResourceLocation id = ResourceLocations.of(SensusNews.MOD_ID, path);
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.setFilter(true, false);
            CACHE.put(file, id);
            return id;
        } catch (IOException error) {
            return null;
        }
    }
}
