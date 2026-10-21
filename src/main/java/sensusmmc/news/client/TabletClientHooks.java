package sensusmmc.news.client;

public final class TabletClientHooks {
    private static Runnable opener = () -> {
    };

    private TabletClientHooks() {
    }

    public static void setOpener(Runnable value) {
        opener = value == null ? () -> {
        } : value;
    }

    public static void open() {
        opener.run();
    }
}
