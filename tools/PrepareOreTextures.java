import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Converts selected ore albedos to the runtime 64px material budget. */
public final class PrepareOreTextures {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: source.png destination.png");
        var source = ImageIO.read(Path.of(args[0]).toFile());
        var output = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            output.setRGB(x, y, source.getRGB((int)((x + .5) * source.getWidth() / 64), (int)((y + .5) * source.getHeight() / 64)));
        Path target = Path.of(args[1]);
        Files.createDirectories(target.getParent());
        ImageIO.write(output, "png", target.toFile());
    }
}
