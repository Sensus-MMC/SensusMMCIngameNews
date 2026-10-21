package sensusmmc.news;

import net.minecraft.resources.Identifier;

public final class ResourceLocations {
    private ResourceLocations() {
    }

    public static Identifier of(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
