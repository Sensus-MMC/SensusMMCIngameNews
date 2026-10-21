package sensusmmc.news;

import net.minecraft.resources.ResourceLocation;

public final class ResourceLocations {
    private ResourceLocations() {
    }

    public static ResourceLocation of(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
