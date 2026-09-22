package net.askcraft.justifylasers.printing;

import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PrintData {
    public static final String KEY = "PrintDesign";
    private static final Map<String, PrintDesign> CACHE = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, PrintDesign> eldest) { return size() > 24; }
    };

    public static PrintDesign read(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(ModIndustry.MODEL_SCHEMATIC) && !stack.isOf(ModIndustry.PRINTED_MODEL.asItem())) return null;
        return read(getText(GameVersion.itemData(stack),KEY,PrintDesign.MAX_DOCUMENT_JSON));
    }
    public static PrintDesign read(String json) {
        if (json.isBlank() || json.length() > PrintDesign.MAX_DOCUMENT_JSON) return null;
        synchronized (CACHE) { if (CACHE.containsKey(json)) return CACHE.get(json); }
        PrintDesign model;
        try { model = PrintDesign.parse(json); } catch (IllegalArgumentException failure) { model = null; }
        synchronized (CACHE) { CACHE.put(json, model); }
        return model;
    }
    public static ItemStack schematic(PrintDesign model) { return write(new ItemStack(ModIndustry.MODEL_SCHEMATIC), model); }
    public static ItemStack printed(PrintDesign model) { return write(new ItemStack(ModIndustry.PRINTED_MODEL), model); }
    public static ItemStack write(ItemStack stack, PrintDesign model) {
        var data = GameVersion.itemData(stack); putText(data,KEY,model.json()); GameVersion.setItemData(stack, data); return stack;
    }
    public static void putText(net.minecraft.nbt.NbtCompound nbt,String key,String text) {
        if(text.length()<=20_000){nbt.putString(key,text);return;}
        var chunks=new net.minecraft.nbt.NbtList();
        for(int at=0;at<text.length();){int end=Math.min(text.length(),at+20_000);if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;chunks.add(net.minecraft.nbt.NbtString.of(text.substring(at,end)));at=end;}
        nbt.put(key,chunks);
    }
    public static String getText(net.minecraft.nbt.NbtCompound nbt,String key,int limit) {
        if(nbt.contains(key,8)){String value=nbt.getString(key);return value.length()<=limit?value:"";}
        if(!nbt.contains(key,9))return "";
        var chunks=nbt.getList(key,8);if(chunks.size()>(limit+19_999)/20_000)return "";var text=new StringBuilder();
        for(int i=0;i<chunks.size();i++){String value=chunks.getString(i);if(value.length()>20_000||text.length()+value.length()>limit)return "";text.append(value);}return text.toString();
    }
    private PrintData() { }
}
