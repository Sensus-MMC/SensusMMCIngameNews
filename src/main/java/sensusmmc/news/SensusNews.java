package sensusmmc.news;

public final class SensusNews {
    public static final String MOD_ID = "sensusmmc";
    public static final String SITE_URL = "https://sensusmmc.news";

    private SensusNews() {
    }

    public static boolean isAllowedUrl(String url) {
        return url != null && url.startsWith("https://");
    }
}
