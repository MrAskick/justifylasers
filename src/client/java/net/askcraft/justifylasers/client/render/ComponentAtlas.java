package net.askcraft.justifylasers.client.render;

import com.google.gson.JsonParser;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.laser.LaserColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class ComponentAtlas {
    record Uv(float u, float v) { }

    record Region(int x, int y, int width, int height, int size) {
        Uv uv(double u, double v) {
            return new Uv((float) ((x + 0.5 + clamp(u) * (width - 1)) / size),
                    (float) ((y + 0.5 + clamp(v) * (height - 1)) / size));
        }

        Uv project(Vec3d point, Vec3d normal, Vec3d min, Vec3d max, boolean trim, boolean alignLongAxis) {
            double u, v, w, h;
            if (Math.abs(normal.y) > Math.max(Math.abs(normal.x), Math.abs(normal.z))) {
                u = fraction(point.x, min.x, max.x);
                v = fraction(point.z, min.z, max.z);
                if (normal.y < 0) v = 1 - v;
                w = max.x - min.x; h = max.z - min.z;
            } else if (Math.abs(normal.x) > Math.abs(normal.z)) {
                u = fraction(point.z, min.z, max.z);
                if (normal.x < 0) u = 1 - u;
                v = 1 - fraction(point.y, min.y, max.y);
                w = max.z - min.z; h = max.y - min.y;
            } else {
                u = fraction(point.x, min.x, max.x);
                if (normal.z > 0) u = 1 - u;
                v = 1 - fraction(point.y, min.y, max.y);
                w = max.x - min.x; h = max.y - min.y;
            }
            if (alignLongAxis) {
                if (width > height && h > w || height > width && w > h) {
                    double previous = u; u = v; v = 1 - previous;
                    double previousWidth = w; w = h; h = previousWidth;
                }
            }
            if (trim) {
                // Crop excess metal grain; light strips keep their full colored border.
                double aspect = Math.max(0.001, w) / Math.max(0.001, h);
                double textureAspect = (double) width / height;
                if (textureAspect > aspect) u = 0.5 + (u - 0.5) * aspect / textureAspect;
                else v = 0.5 + (v - 0.5) * textureAspect / aspect;
            }
            return uv(u, v);
        }

        boolean contains(Uv uv) {
            return uv.u * size >= x && uv.u * size <= x + width
                    && uv.v * size >= y && uv.v * size <= y + height;
        }
    }

    record Skin(Region front, Region back, Region left, Region right, Region top, Region bottom, boolean trim, boolean alignLongAxis) {
        Skin(Region front, Region back, Region left, Region right, Region top, Region bottom, boolean trim) {
            this(front, back, left, right, top, bottom, trim, trim);
        }
        static Skin all(Region region, boolean trim) { return new Skin(region, region, region, region, region, region, trim); }
        Skin front(Region region) { return new Skin(region, back, left, right, top, bottom, false); }
        Skin back(Region region) { return new Skin(front, region, left, right, top, bottom, false); }
        Skin sides(Region a, Region b) { return new Skin(front, back, a, b, top, bottom, false); }
        Skin top(Region region) { return new Skin(front, back, left, right, region, bottom, false); }
        Skin bottom(Region region) { return new Skin(front, back, left, right, top, region, false); }
        Region face(Vec3d normal) {
            if (Math.abs(normal.y) > Math.max(Math.abs(normal.x), Math.abs(normal.z))) return normal.y > 0 ? top : bottom;
            if (Math.abs(normal.x) > Math.abs(normal.z)) return normal.x > 0 ? right : left;
            return normal.z < 0 ? front : back;
        }
        Uv project(Vec3d point, Vec3d normal, Vec3d min, Vec3d max) { return face(normal).project(point, normal, min, max, trim, alignLongAxis); }
    }

    private static final Map<String, ComponentAtlas> CACHE = new HashMap<>();
    private final Map<String, Region> regions;
    private final int size;

    private ComponentAtlas(Map<String, Region> regions, int size) { this.regions = Map.copyOf(regions); this.size = size; }

    static ComponentAtlas load(String kind, String variant) {
        return CACHE.computeIfAbsent(kind + "/" + variant, path -> {
            String resource = "/assets/justifylasers/textures/component/" + path + ".json";
            try (var stream = ComponentAtlas.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Missing component UV layout: " + resource);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                int size = json.get("size").getAsInt();
                Map<String, Region> regions = new LinkedHashMap<>();
                json.getAsJsonObject("regions").entrySet().forEach(entry -> {
                    var rect = entry.getValue().getAsJsonArray();
                    regions.put(entry.getKey(), new Region(rect.get(0).getAsInt(), rect.get(1).getAsInt(),
                            rect.get(2).getAsInt(), rect.get(3).getAsInt(), size));
                });
                return new ComponentAtlas(regions, size);
            } catch (IOException exception) { throw new IllegalStateException("Cannot read component UV layout: " + resource, exception); }
        });
    }

    Region region(String name) {
        Region region = regions.get(name);
        if (region == null) throw new IllegalArgumentException("Unknown component region: " + name);
        return region;
    }
    Region regionOr(String name, String fallback) { return regions.getOrDefault(name, region(fallback)); }
    Skin surface(String name) { return Skin.all(region(name), false); }
    Skin trim(String name) { return Skin.all(region(name), true); }
    Skin strip(String name) {
        var region = region(name);
        return new Skin(region, region, region, region, region, region, false, true);
    }
    Map<String, Region> regions() { return regions; }
    int size() { return size; }

    static Identifier texture(String kind, int color) { return texture(kind, LaserColor.nearest(color).asString()); }
    static Identifier texture(String kind, String variant) { return JustifyLasers.id("textures/component/" + kind + "/" + variant + ".png"); }
    private static double fraction(double value, double min, double max) { return (value - min) / Math.max(0.00001, max - min); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
