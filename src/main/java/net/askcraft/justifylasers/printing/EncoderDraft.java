package net.askcraft.justifylasers.printing;

import com.google.gson.JsonObject;

public record EncoderDraft(PrintDesign design, String name, boolean editable, VoxelGrid.Material selected,
                           int layer, int axis, int brush, boolean circle, int partIndex) {
    // Editor metadata must not make a valid maximum-size model impossible to save.
    public static final int MAX_JSON = PrintDesign.MAX_DOCUMENT_JSON + 1024;
    public EncoderDraft(PrintDesign design,String name,boolean editable,VoxelGrid.Material selected,int layer,int axis,int brush,boolean circle){this(design,name,editable,selected,layer,axis,brush,circle,0);}
    private static final java.util.Map<String,EncoderDraft> CACHE=new java.util.LinkedHashMap<>(16,.75F,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,EncoderDraft> entry){return size()>16;}
    };
    public EncoderDraft {
        if(name==null || name.length()>48 || layer<0 || layer>15 || axis<0 || axis>2 || brush<1 || brush>16 || selected==null)
            throw PrintDesign.invalid("format");
        if(name.chars().anyMatch(c -> Character.isISOControl(c) || c == 0xA7))throw PrintDesign.invalid("name");
        if(partIndex<0||partIndex>=(design==null?1:design.partCount()))throw PrintDesign.invalid("assembly");
        if(editable && design!=null && design.assembly()==null && design.voxelCells()==null)throw PrintDesign.invalid("format");
    }
    public String json() {
        var root=new JsonObject();if(design!=null)root.add("model",PrintDesign.readObject(design.json()));
        root.addProperty("name",name);root.addProperty("editable",editable);root.addProperty("texture",selected.texture());root.addProperty("tint",selected.tint());
        root.addProperty("layer",layer);root.addProperty("axis",axis);root.addProperty("brush",brush);root.addProperty("circle",circle);
        root.addProperty("part",partIndex);
        String json=root.toString();if(json.length()>MAX_JSON)throw PrintDesign.invalid("size");return json;
    }
    public static synchronized EncoderDraft parse(String json) {
        var cached=CACHE.get(json);if(cached!=null)return cached;
        try {
            var root=PrintDesign.readObject(json,MAX_JSON);
            var draft=new EncoderDraft(root.has("model")?PrintDesign.parse(root.get("model").toString()):null,root.get("name").getAsString(),
                    root.get("editable").getAsBoolean(),new VoxelGrid.Material(root.get("texture").getAsString(),integer(root,"tint")),
                    integer(root,"layer"),integer(root,"axis"),integer(root,"brush"),root.get("circle").getAsBoolean(),root.has("part")?integer(root,"part"):0);
            CACHE.put(json,draft);return draft;
        } catch(IllegalArgumentException failure){throw failure;}catch(RuntimeException failure){throw PrintDesign.invalid("format");}
    }
    private static int integer(JsonObject root,String key){double n=root.get(key).getAsDouble();if(!Double.isFinite(n)||n!=Math.rint(n)||Math.abs(n)>0xFFFFFF)throw PrintDesign.invalid("number");return(int)n;}
}
