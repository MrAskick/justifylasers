import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Lossless PNG packing for checked-in runtime assets; no external image tools are required. */
public final class OptimizeTextures {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java tools/OptimizeTextures.java <source directory> <destination directory>");
        Path source = Path.of(args[0]).toAbsolutePath().normalize();
        Path destination = Path.of(args[1]).toAbsolutePath().normalize();
        long before = 0, after = 0;
        int count = 0;
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path target = destination.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                if (!file.toString().endsWith(".png")) {
                    if (!file.equals(target)) Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }
                byte[] original = Files.readAllBytes(file);
                BufferedImage image = ImageIO.read(file.toFile());
                if (image == null) throw new IOException("Unreadable PNG: " + file);
                byte[] packed = encode(image);
                if (packed.length >= original.length) packed = original;
                BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(packed));
                int w = image.getWidth(), h = image.getHeight();
                if (decoded.getWidth() != w || decoded.getHeight() != h
                        || !Arrays.equals(image.getRGB(0, 0, w, h, null, 0, w), decoded.getRGB(0, 0, w, h, null, 0, w))) {
                    throw new IOException("Lossless verification failed: " + file);
                }
                if (!file.equals(target) || packed != original) Files.write(target, packed);
                before += original.length;
                after += packed.length;
                count++;
            }
        }
        System.out.printf("Packed %d PNGs: %,d -> %,d bytes (%.1f%% smaller); every decoded RGBA pixel verified.%n",
                count, before, after, before == 0 ? 0 : 100.0 * (before - after) / before);
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        int w = image.getWidth(), h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        boolean opaque = true;
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        for (int pixel : pixels) {
            opaque &= pixel >>> 24 == 255;
            if (palette != null && !palette.containsKey(pixel)) {
                if (palette.size() == 256) palette = null;
                else palette.put(pixel, palette.size());
            }
        }
        // Keep RGB color space even for neutral textures; grayscale PNG decoders may apply a different transfer curve.
        int type = palette != null ? 3 : opaque ? 2 : 6;
        int channels = type == 6 ? 4 : type == 2 ? 3 : 1;
        int stride = w * channels;
        byte[] raster = new byte[stride * h];
        int offset = 0;
        for (int pixel : pixels) {
            if (palette != null) raster[offset++] = palette.get(pixel).byteValue();
            else {
                raster[offset++] = (byte) (pixel >> 16);
                raster[offset++] = (byte) (pixel >> 8); raster[offset++] = (byte) pixel;
                if (!opaque) raster[offset++] = (byte) (pixel >>> 24);
            }
        }
        byte[] plain = compress(raster, stride, h, channels, false);
        byte[] filtered = compress(raster, stride, h, channels, true);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(png);
        out.writeLong(0x89504E470D0A1A0AL);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream ihdr = new DataOutputStream(header);
        ihdr.writeInt(w); ihdr.writeInt(h); ihdr.writeByte(8); ihdr.writeByte(type);
        ihdr.writeByte(0); ihdr.writeByte(0); ihdr.writeByte(0);
        chunk(out, "IHDR", header.toByteArray());
        if (palette != null) {
            byte[] colors = new byte[palette.size() * 3], alpha = new byte[palette.size()];
            for (var entry : palette.entrySet()) {
                int i = entry.getValue(), p = entry.getKey();
                colors[i * 3] = (byte) (p >> 16); colors[i * 3 + 1] = (byte) (p >> 8); colors[i * 3 + 2] = (byte) p;
                alpha[i] = (byte) (p >>> 24);
            }
            chunk(out, "PLTE", colors);
            if (!opaque) chunk(out, "tRNS", alpha);
        }
        chunk(out, "IDAT", filtered.length < plain.length ? filtered : plain);
        chunk(out, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static byte[] compress(byte[] raster, int stride, int height, int channels, boolean adaptive) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(9);
        try (DeflaterOutputStream out = new DeflaterOutputStream(result, deflater)) {
            byte[][] rows = new byte[5][stride];
            for (int y = 0; y < height; y++) {
                int start = y * stride, best = 0;
                long bestScore = Long.MAX_VALUE;
                for (int filter = 0; filter <= (adaptive ? 4 : 0); filter++) {
                    long score = 0;
                    for (int x = 0; x < stride; x++) {
                        int raw = raster[start + x] & 255;
                        int left = x < channels ? 0 : raster[start + x - channels] & 255;
                        int above = y == 0 ? 0 : raster[start + x - stride] & 255;
                        int corner = y == 0 || x < channels ? 0 : raster[start + x - stride - channels] & 255;
                        int prediction = switch (filter) {
                            case 1 -> left;
                            case 2 -> above;
                            case 3 -> (left + above) / 2;
                            case 4 -> paeth(left, above, corner);
                            default -> 0;
                        };
                        rows[filter][x] = (byte) (raw - prediction);
                        score += Math.abs((int) rows[filter][x]);
                    }
                    if (score < bestScore) { best = filter; bestScore = score; }
                }
                out.write(best); out.write(rows[best]);
            }
        } finally { deflater.end(); }
        return result.toByteArray();
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c, da = Math.abs(p - a), db = Math.abs(p - b), dc = Math.abs(p - c);
        return da <= db && da <= dc ? a : db <= dc ? b : c;
    }

    private static void chunk(DataOutputStream out, String name, byte[] bytes) throws IOException {
        byte[] type = name.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32(); crc.update(type); crc.update(bytes);
        out.writeInt(bytes.length); out.write(type); out.write(bytes); out.writeInt((int) crc.getValue());
    }
}
