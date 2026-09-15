package net.askcraft.justifylasersbridge;

final class LoaderBridge {
    static Object plugin(String suffix) {
        ClassLoader classes = LoaderBridge.class.getClassLoader();
        String loader;
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader", false, classes);
            loader = "neoforge";
        } catch (ClassNotFoundException ignored) {
            loader = "forge";
        }
        String name = String.join(".", "net", "askcraft", "justifylasers", "loader", loader, suffix);
        try {
            return Class.forName(name, true, classes).getConstructor().newInstance();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot initialize Justify Lasers compatibility plugin: " + name, error);
        }
    }
    private LoaderBridge() { }
}
