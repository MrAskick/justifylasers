import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Compares decoded runtime PNGs between two releases, independent of their PNG/ZIP encoding. */
public final class VerifyTextureArchive {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java tools/VerifyTextureArchive.java <before.jar> <after.jar>");
        try (var before = new ZipFile(args[0]); var after = new ZipFile(args[1])) {
            var oldNames = textures(before);
            var newNames = textures(after);
            int unchanged = 0, removed = 0;
            for (String name : oldNames) {
                if (!newNames.contains(name)) { removed++; continue; }
                BufferedImage a = read(before, name), b = read(after, name);
                int width = a.getWidth(), height = a.getHeight();
                if (width != b.getWidth() || height != b.getHeight()
                        || !Arrays.equals(a.getRGB(0, 0, width, height, null, 0, width), b.getRGB(0, 0, width, height, null, 0, width)))
                    throw new AssertionError("Texture pixels changed: " + name);
                unchanged++;
            }
            System.out.printf("Verified %d pixel-identical textures; %d removed, %d added.%n", unchanged, removed, newNames.size() - unchanged);
        }
    }

    private static TreeSet<String> textures(ZipFile archive) {
        var names = new TreeSet<String>();
        archive.stream().filter(entry -> entry.getName().startsWith("assets/justifylasers/") && entry.getName().endsWith(".png"))
                .forEach(entry -> names.add(entry.getName()));
        return names;
    }

    private static BufferedImage read(ZipFile archive, String name) throws IOException {
        try (var stream = archive.getInputStream(archive.getEntry(name))) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IOException("Unreadable image: " + name);
            return image;
        }
    }
}
