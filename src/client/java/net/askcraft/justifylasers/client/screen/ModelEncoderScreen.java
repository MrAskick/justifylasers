package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.client.render.PrintTextures;
import net.askcraft.justifylasers.client.render.PrintedModelRenderer;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.printing.VoxelGrid;
import net.askcraft.justifylasers.printing.VoxImporter;
import net.askcraft.justifylasers.printing.EncoderDraft;
import net.askcraft.justifylasers.screen.ModelEncoderScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ModelEncoderScreen extends HandledScreen<ModelEncoderScreenHandler> {
    private VoxelGrid.Material[] cells=new VoxelGrid.Material[4096];
    private PrintDesign design;
    private VoxelGrid.Material selected=new VoxelGrid.Material("minecraft:block/quartz_block_side",0xFFFFFF), mappingSource;
    private String selectedTexture=selected.texture(), query="", upload, pending, lastSaved="";
    private List<VoxelGrid.Material> palette=List.of();
    private List<VoxelGrid.Material> colors=List.of();
    private int partIndex, colorScroll;
    private int schematicScale = 1;
    private boolean colorMode=true, overview, importing;
    private static final java.util.concurrent.ExecutorService IMPORTER=java.util.concurrent.Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"JustifyLasers model import");thread.setDaemon(true);return thread;});
    private static final java.util.concurrent.atomic.AtomicBoolean IMPORT_ACTIVE=new java.util.concurrent.atomic.AtomicBoolean();
    private int brush=1,paletteScroll,autosaveTicks;
    private boolean circle,initializing;
    private String localBackup="";
    private int awaitingTicks;
    private int screenTicks, nextDraftTick, nextWriteTick;
    private boolean closePending;
    private String modelName="";
    private Text message=label("hint");
    private Text importWarning, importDetails;
    private record ImportedModel(PrintDesign design, List<net.askcraft.justifylasers.printing.SchematicImporter.SkippedBlock> skipped) {
        private ImportedModel(PrintDesign design) { this(design, List.of()); }
    }
    private List<String> matches=List.of();
    private final ArrayDeque<Snapshot> undo=new ArrayDeque<>(), redo=new ArrayDeque<>();
    private record Snapshot(VoxelGrid.Material[] cells,PrintDesign design,boolean voxelMode,String name,int part) { }
    private boolean voxelMode=true, edited, loaded, painting;
    private int layer,axis=1,scroll,page,uploadPosition,sequence;
    private float yaw=35,pitch=25;
    private TextFieldWidget nameField,search;

    public ModelEncoderScreen(ModelEncoderScreenHandler handler,PlayerInventory inventory,Text title) {
        super(handler,inventory,title); backgroundWidth=320; backgroundHeight=266;
    }
    private static Text label(String key,Object... args) { return Text.translatable("gui.justifylasers.printing."+key,args); }
    @Override protected void init() {
        super.init();initializing=true;
        nameField=addDrawableChild(new TextFieldWidget(textRenderer,x+12,y+9,172,12,label("name")));
        nameField.setPlaceholder(label("name"));
        nameField.setMaxLength(48); nameField.setText(modelName); nameField.setChangedListener(value->{modelName=value;if(!initializing)edited=true;});
        button(190,7,24,"?",()->label("help"),()->open(page==3?0:3));
        iconButton(218,7,24,()->PrintingIcons.Icon.HOLOGRAM,()->label(handler.hologramEnabled()?"hologram_on":"hologram_off"),()->sendButton(3));
        button(246,7,20,"⏻",()->Text.translatable("gui.justifylasers.industry.toggle"),()->sendButton(0));
        button(270,7,20,"S",()->Text.translatable("gui.justifylasers.powered.security"),()->open(page==1?0:1));
        button(294,7,20,"R",()->Text.translatable("gui.justifylasers.powered.redstone"),()->open(page==2?0:2));
        button(12,25,30,null,()->label("new"),()->{saveUndo();cells=new VoxelGrid.Material[4096];design=null;partIndex=0;refreshPalette();voxelMode=true;edited=true;importWarning=null;importDetails=null;message=label("hint");});
        button(46,25,60,null,()->label("paste"),this::paste);
        button(110,25,38,null,()->label("import_file"),this::chooseFile);
        button(152,25,62,null,()->label("copy"),()->{if(design!=null){client.keyboard.setClipboard(named().json());message=label("copied");}});
        button(218,25,92,null,()->label("voxelize"),this::voxelize);
        button(194,46,55,null,()->label("colors"),()->{colorMode=true;open(page);});
        button(253,46,57,null,()->label("textures"),()->{colorMode=false;open(page);});
        search=addDrawableChild(new TextFieldWidget(textRenderer,x+195,y+65,113,12,label("search")));
        search.setPlaceholder(label("search"));
        search.setMaxLength(100); search.setText(query); search.setChangedListener(value->{query=value;filter();}); filter();refreshPalette();
        button(286,113,24,"→",()->label("assign_material"),this::assignMaterial);
        button(12,133,14,"−",()->label("layer"),()->{layer=Math.max(0,layer-1);edited=true;});
        button(60,133,14,"+",()->label("layer"),()->{layer=Math.min(15,layer+1);edited=true;});
        button(78,133,18,null,()->Text.literal("XYZ".substring(axis,axis+1)),()->{axis=(axis+1)%3;edited=true;});
        button(101,133,14,"−",()->label("brush"),()->{brush=Math.max(1,brush-1);edited=true;});
        button(144,133,14,"+",()->label("brush"),()->{brush=Math.min(16,brush+1);edited=true;});
        iconButton(162,133,22,()->circle?PrintingIcons.Icon.CIRCLE:PrintingIcons.Icon.SQUARE,()->label(circle?"circle":"square"),()->{circle=!circle;edited=true;});
        button(188,133,36,null,()->label("fill"),()->{if(voxelMode){saveUndo();for(int v=0;v<16;v++)for(int u=0;u<16;u++)cells[index(u,v)]=selected;rebuild();}});
        iconButton(230,133,22,()->PrintingIcons.Icon.UNDO,()->label("undo"),()->restore(undo,redo));
        iconButton(256,133,22,()->PrintingIcons.Icon.REDO,()->label("redo"),()->restore(redo,undo));
        button(12,153,16,"‹",()->label("previous_part"),()->changePart(-1));
        button(131,153,16,"›",()->label("next_part"),()->changePart(1));
        button(152,153,62,null,()->label(overview?"part_view":"overview"),()->overview=!overview);
        button(247,193,63,null,()->label("write"),this::write);
        button(12,232,60,null,()->label("schematic_scale",schematicScale==1?"1:16":"1:1"),()->schematicScale=schematicScale==1?16:1);
        initializing=false;open(page);loadDraft();loadRecovery();
    }
    private void open(int next){page=next;search.setVisible(page==0&&!colorMode);
        for(var child:children())if(child instanceof ButtonWidget button){int by=button.getY()-y;button.visible=page==0||by<44||by>168;}
    }
    private void iconButton(int bx,int by,int width,java.util.function.Supplier<PrintingIcons.Icon> icon,java.util.function.Supplier<Text> hint,Runnable action){
        addDrawableChild(new ButtonWidget(x+bx,y+by,width,15,hint.get(),b->action.run(),supplier->supplier.get()) {
            public void renderButton(DrawContext c,int mx,int my,float delta){renderWidget(c,mx,my,delta);}
            public void renderWidget(DrawContext c,int mx,int my,float delta){active=upload==null&&!importing;visible=page==0||by<44;setMessage(hint.get());if(!visible)return;TechGui.button(c,getX(),getY(),this.width,height,TechGui.State.of(active,false,isHovered(),isFocused(),false));PrintingIcons.draw(c,icon.get(),getX()+(this.width-16)/2,getY(),active?0xFFBEEBF3:0xFF68848F);setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(hint.get()));}
        });
    }
    private void button(int bx,int by,int width,String text,java.util.function.Supplier<Text> hint,Runnable action) {
        addDrawableChild(new ButtonWidget(x+bx,y+by,width,15,text==null?hint.get():Text.literal(text),b->action.run(),supplier->supplier.get()) {
            public void renderButton(DrawContext c,int mx,int my,float delta){renderWidget(c,mx,my,delta);}
            public void renderWidget(DrawContext c,int mx,int my,float delta) {
                active=upload==null&&!importing;visible=page==0||by<44||by>168;if(!visible)return;
                var state=TechGui.State.of(active,false,isHovered(),isFocused(),false); TechGui.button(c,getX(),getY(),this.width,height,state);
                TechGui.Icon icon="⏻".equals(text)?TechGui.Icon.POWER:"S".equals(text)?TechGui.Icon.SHIELD:"R".equals(text)?TechGui.Icon.REDSTONE:null;
                if(icon!=null)icon.draw(c,getX()+(this.width-11)/2F,getY()+2,11,state);
                else fitted(c,text==null?hint.get():Text.literal(text),getX()+3,getY()+4,this.width-6,active?0xBEEBF3:0x68848F);
                setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(hint.get()));
            }
        });
    }
    private void sendButton(int id){if(client.interactionManager!=null)client.interactionManager.clickButton(handler.syncId,id);}
    private void filter(){String q=query.toLowerCase(Locale.ROOT);matches=PrintTextures.catalog().stream().filter(id->id.contains(q)).toList();scroll=0;}
    private void send(int action,String value){ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId,action,value));}
    private PrintDesign named(){return design.withName(modelName);}
    private EncoderDraft draft(){return new EncoderDraft(design==null?null:named(),modelName,voxelMode,selected,layer,axis,brush,circle,partIndex);}
    private PrintDesign currentPart(){return design==null?null:design.part(partIndex);}
    private void readCells(){var part=currentPart();cells=part==null?null:part.voxelCells();voxelMode=design==null||cells!=null&&!part.hasBlockModels();if(cells==null)cells=new VoxelGrid.Material[4096];refreshPalette();}
    private void refreshPalette(){
        palette=design!=null&&design.assembly()!=null?design.assembly().palette():VoxelGrid.palette(cells);
        var choices=new java.util.LinkedHashSet<VoxelGrid.Material>();for(var material:palette)choices.add(VoxelGrid.Material.color(material.tint()));
        for(int rgb:net.askcraft.justifylasers.printing.PrintPalette.COLORS)choices.add(VoxelGrid.Material.color(rgb));
        colors=List.copyOf(choices);paletteScroll=Math.min(paletteScroll,Math.max(0,palette.size()-7));colorScroll=Math.min(colorScroll,Math.max(0,(colors.size()+6)/7-4));
    }
    private void changePart(int direction){if(design==null)return;partIndex=MathHelper.clamp(partIndex+direction,0,design.partCount()-1);readCells();edited=true;}
    private void loadDraft(){
        if(loaded||edited)return;var saved=handler.draft();
        if(saved!=null){design=saved.design();modelName=saved.name();selected=saved.selected();layer=saved.layer();axis=saved.axis();brush=saved.brush();circle=saved.circle();partIndex=saved.partIndex();readCells();voxelMode=saved.editable();selectedTexture=selected.texture();colorMode=selected.texture().equals(VoxelGrid.WHITE);open(page);
            initializing=true;nameField.setText(modelName);initializing=false;lastSaved=draft().json();loaded=true;message=label("restored");
        }else if(handler.design()!=null){design=handler.design();partIndex=0;readCells();modelName=design.name();initializing=true;nameField.setText(modelName);initializing=false;loaded=true;}
    }
    private boolean saveDraft(){
        if(!edited||upload!=null||importing)return true;
        try{String json=draft().json();backup(json);if(json.equals(lastSaved))return true;
            if(screenTicks<nextDraftTick||handler.uploadCooldown(true)>0)return false;
            send(ModelEncoderScreenHandler.DRAFT_BEGIN,Integer.toString(json.length()));nextDraftTick=screenTicks+ModelEncoderScreenHandler.UPLOAD_INTERVAL;int offset=0,index=0;
            while(offset<json.length()){int end=Math.min(json.length(),offset+ModelEncoderScreenHandler.CHUNK_SIZE);if(end<json.length()&&Character.isHighSurrogate(json.charAt(end-1)))end--;send(ModelEncoderScreenHandler.CHUNK,index+++":"+json.substring(offset,end));offset=end;}
            send(ModelEncoderScreenHandler.DRAFT_COMMIT,"");lastSaved=json;message=label("draft_sent");
        }catch(IllegalArgumentException failure){error(failure);}
        return true;
    }
    private java.nio.file.Path backupPath(){
        String world=client.getCurrentServerEntry()!=null?client.getCurrentServerEntry().address:client.getServer()!=null?client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toString():"local";
        String key=world+"|"+client.world.getRegistryKey().getValue()+"|"+handler.encoderPos()+"|"+client.player.getUuid();
        try{return client.runDirectory.toPath().resolve("justifylasers/encoder-drafts/"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+".json");}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void backup(String json){
        if(json.equals(localBackup))return;
        try{var target=backupPath();java.nio.file.Files.createDirectories(target.getParent());var temporary=target.resolveSibling(target.getFileName()+".tmp");java.nio.file.Files.writeString(temporary,json,java.nio.charset.StandardCharsets.UTF_8);
            try{java.nio.file.Files.move(temporary,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException unsupported){java.nio.file.Files.move(temporary,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}localBackup=json;
        }catch(java.io.IOException failure){message=label("backup_failed");}
    }
    private void loadRecovery(){
        if(edited||!localBackup.isEmpty())return;
        try{var file=backupPath();if(!java.nio.file.Files.isRegularFile(file)||java.nio.file.Files.size(file)>EncoderDraft.MAX_JSON*3L)return;
            String json=java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8);var saved=EncoderDraft.parse(json);localBackup=json;
            if(handler.draft()!=null&&json.equals(handler.draft().json()))return;
            design=saved.design();modelName=saved.name();selected=saved.selected();layer=saved.layer();axis=saved.axis();brush=saved.brush();circle=saved.circle();partIndex=saved.partIndex();readCells();voxelMode=saved.editable();colorMode=selected.texture().equals(VoxelGrid.WHITE);open(page);
            selectedTexture=selected.texture();initializing=true;nameField.setText(modelName);initializing=false;loaded=true;message=label("recovered");
        }catch(java.io.IOException|IllegalArgumentException ignored){ }
    }
    private void write(){
        if(design==null||upload!=null||pending!=null)return;
        if(screenTicks<nextWriteTick||handler.uploadCooldown(false)>0){message=label("upload_wait");return;}
        if(handler.energy()<2000){message=label("need_energy");return;}
        if(handler.getSlot(0).getStack().isEmpty()||handler.getSlot(1).hasStack()){message=label("need_card");return;}
        try{upload=named().json();uploadPosition=0;sequence=0;send(ModelEncoderScreenHandler.BEGIN,Integer.toString(upload.length()));nextWriteTick=screenTicks+ModelEncoderScreenHandler.UPLOAD_INTERVAL;message=label("uploading");}
        catch(IllegalArgumentException failure){error(failure);}
    }
    @Override protected void handledScreenTick(){
        super.handledScreenTick();
        screenTicks++;
        if(closePending){close();return;}
        loadDraft();
        if(upload!=null){
            for(int i=0;i<4&&uploadPosition<upload.length();i++){
                int end=Math.min(upload.length(),uploadPosition+ModelEncoderScreenHandler.CHUNK_SIZE);
                // Do not split a UTF-16 surrogate pair across independent UTF-8 packets.
                if(end<upload.length()&&Character.isHighSurrogate(upload.charAt(end-1)))end--;
                send(ModelEncoderScreenHandler.CHUNK,sequence+++":"+upload.substring(uploadPosition,end));uploadPosition=end;
            }
            if(uploadPosition==upload.length()){send(ModelEncoderScreenHandler.COMMIT,"");pending=upload;awaitingTicks=0;upload=null;message=label("submitted");}
        }
        if(pending!=null){
            var output=net.askcraft.justifylasers.printing.PrintData.read(handler.getSlot(1).getStack());
            if(output!=null&&output.json().equals(pending)){message=label("written");pending=null;}
            else if(++awaitingTicks>100){message=Text.translatable("gui.justifylasers.industry.status."+handler.status().name().toLowerCase(Locale.ROOT));pending=null;}
        }
        if(++autosaveTicks>=20&&upload==null&&pending==null){autosaveTicks=0;saveDraft();}
        if(handler.draftResult()==2)message=label("draft_conflict");
        else if(message.equals(label("draft_sent"))&&handler.draft()!=null&&handler.draft().json().equals(lastSaved))message=label("autosaved");
    }
    @Override public void close(){
        closePending=true;
        if(upload!=null){while(uploadPosition<upload.length()){int end=Math.min(upload.length(),uploadPosition+ModelEncoderScreenHandler.CHUNK_SIZE);if(end<upload.length()&&Character.isHighSurrogate(upload.charAt(end-1)))end--;send(ModelEncoderScreenHandler.CHUNK,sequence+++":"+upload.substring(uploadPosition,end));uploadPosition=end;}send(ModelEncoderScreenHandler.COMMIT,"");upload=null;}
        if(saveDraft())super.close();else message=label("draft_sent");
    }
    @Override public void removed(){if(edited)try{backup(draft().json());}catch(IllegalArgumentException ignored){ }super.removed();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        for(var field:new TextFieldWidget[]{nameField,search})if(field!=null&&field.isVisible()&&field.isFocused()&&key!=org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){field.keyPressed(key,scan,modifiers);return true;}
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Z&&hasControlDown()){restore(undo,redo);return true;}
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Y&&hasControlDown()){restore(redo,undo);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    private void importDesign(PrintDesign imported){saveUndo();design=imported;partIndex=0;modelName=design.name();initializing=true;nameField.setText(modelName);initializing=false;readCells();paletteScroll=0;colorScroll=0;overview=design.assembly()!=null;edited=true;message=label("imported");}
    private void paste(){
        String json=client.keyboard.getClipboard();importAsync(()->PrintTextures.importJson(json));
    }
    private void chooseFile(){
        int scale=schematicScale;
        importWithReportAsync(()->{try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var filters=stack.mallocPointer(3);filters.put(stack.UTF8("*.vox")).put(stack.UTF8("*.schem")).put(stack.UTF8("*.json")).flip();
            String path=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Import model / WorldEdit schematic","",filters,"Models (*.vox, *.schem, *.json)",false);
            return path==null?null:readFile(java.nio.file.Path.of(path),scale);
        }});
    }
    public void importVox(java.nio.file.Path path){
        importAsync(()->readVox(path));
    }
    public void importFile(java.nio.file.Path path){int scale=schematicScale;importWithReportAsync(()->readFile(path,scale));}
    private ImportedModel readFile(java.nio.file.Path path,int scale){
        String name=path.getFileName().toString(), extension=name.toLowerCase(Locale.ROOT);
        if(extension.endsWith(".vox"))return new ImportedModel(readVox(path));
        if(!extension.endsWith(".schem")&&!extension.endsWith(".schematic")&&!extension.endsWith(".json"))throw PrintDesign.invalid("import_format");
        try(var stream=java.nio.file.Files.newInputStream(path)){
            byte[] bytes=stream.readNBytes(net.askcraft.justifylasers.printing.SchematicImporter.MAX_BYTES+1);
            if(bytes.length>net.askcraft.justifylasers.printing.SchematicImporter.MAX_BYTES)throw PrintDesign.invalid("schematic_size");
            if(extension.endsWith(".json"))return new ImportedModel(PrintTextures.importJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
            var schematic=net.askcraft.justifylasers.printing.SchematicImporter.read(bytes);
            var future=new java.util.concurrent.CompletableFuture<net.askcraft.justifylasers.printing.SchematicImporter.ResolvedPalette>();
            // Model/atlas access stays on the render thread; NBT, decompression and assembly encoding do not.
            client.execute(()->{try{
                future.complete(schematic.resolveMaterials(net.askcraft.justifylasers.client.render.SchematicBlockModels::material));
            }catch(RuntimeException failure){future.completeExceptionally(failure);}});
            var resolved=future.join();
            for(var skipped:resolved.skipped())org.slf4j.LoggerFactory.getLogger("justifylasers").warn(
                    "Schematic import skipped {} blocks of {} ({})",skipped.count(),skipped.state(),skipped.reason());
            return new ImportedModel(schematic.model(name.substring(0,name.lastIndexOf('.')),scale,resolved.materials()::get),resolved.skipped());
        }catch(java.io.IOException failure){throw PrintDesign.invalid("import_file");}
    }
    public boolean importing(){return importing;}
    private void importAsync(java.util.function.Supplier<PrintDesign> task){
        importWithReportAsync(()->{var model=task.get();return model==null?null:new ImportedModel(model);});
    }
    private void importWithReportAsync(java.util.function.Supplier<ImportedModel> task){
        if(upload!=null||pending!=null||!IMPORT_ACTIVE.compareAndSet(false,true))return;
        saveDraft();importing=true;message=label("importing");
        java.util.concurrent.CompletableFuture.supplyAsync(task,IMPORTER).whenComplete((model,failure)->{
            IMPORT_ACTIVE.set(false);
            client.execute(()->{importing=false;if(client.currentScreen!=this)return;
                if(failure!=null){Throwable cause=failure;while(cause instanceof java.util.concurrent.CompletionException&&cause.getCause()!=null)cause=cause.getCause();error(cause instanceof RuntimeException runtime?runtime:new IllegalArgumentException("import_file"));}
                else if(model!=null){
                    importDesign(model.design());
                    setImportReport(model.skipped());
                    message=label(design.hasBlockModels()||design.assembly()!=null&&design.assembly().hasBlockModels()?"schematic_imported":"vox_imported");
                }else message=label("hint");
            });
        });
    }
    private void setImportReport(List<net.askcraft.justifylasers.printing.SchematicImporter.SkippedBlock> skipped){
        importWarning=null;importDetails=null;
        if(skipped.isEmpty())return;
        int count=skipped.stream().mapToInt(net.askcraft.justifylasers.printing.SchematicImporter.SkippedBlock::count).sum();
        importWarning=label("schematic_skipped",count);
        var details=label("schematic_skipped_details",count).copy();
        for(var block:skipped.subList(0,Math.min(6,skipped.size()))){
            String id=block.state().split("\\[",2)[0];
            details.append("\n"+id+" × "+block.count());
        }
        if(skipped.size()>6)details.append("\n").append(label("schematic_skipped_more",skipped.size()-6));
        details.append("\n").append(label("schematic_skipped_log"));importDetails=details;
    }
    private static PrintDesign readVox(java.nio.file.Path path){
        try(var stream=java.nio.file.Files.newInputStream(path)){
            String name=path.getFileName().toString();if(!name.toLowerCase(Locale.ROOT).endsWith(".vox"))throw PrintDesign.invalid("vox_format");
            return VoxImporter.read(stream.readNBytes(VoxImporter.MAX_BYTES+1),name.substring(0,name.length()-4));
        }catch(java.io.IOException failure){throw PrintDesign.invalid("vox_file");}
    }
    @Override public void filesDragged(List<java.nio.file.Path> paths){if(paths.size()==1)importFile(paths.get(0));}
    private void error(RuntimeException failure){
        String reason=failure instanceof IllegalArgumentException?failure.getMessage():"format";
        message=label("error."+(reason!=null&&reason.matches("[a-z_]+")?reason:"format"),failure instanceof net.askcraft.justifylasers.printing.PrintBlockState.ImportFailure detail?detail.detail():"");
    }
    private void voxelize(){
        if(design==null||voxelMode)return;saveUndo();cells=new VoxelGrid.Material[4096];
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(var cube:currentPart().elements())if(cube.contains(x+.5,y+.5,z+.5)&&!cube.faces().isEmpty()){
            var face=cube.faces().values().iterator().next();cells[x+16*(z+16*y)]=new VoxelGrid.Material(face.texture(),face.tint());break;
        }
        voxelMode=true;rebuild();message=label("voxelized");
    }
    private void assignMaterial(){if(!voxelMode||mappingSource==null)return;saveUndo();
        try{if(design!=null&&design.assembly()!=null){design=PrintDesign.assembly(modelName,design.assembly().replaceMaterial(mappingSource,selected));readCells();edited=true;}
            else {for(int i=0;i<cells.length;i++)if(mappingSource.equals(cells[i]))cells[i]=selected;rebuild();}mappingSource=selected;
        }catch(IllegalArgumentException failure){error(failure);}
    }
    private Snapshot snapshot(){return new Snapshot(cells.clone(),design,voxelMode,modelName,partIndex);}
    private void saveUndo(){undo.addLast(snapshot());if(undo.size()>32)undo.removeFirst();redo.clear();}
    private void restore(ArrayDeque<Snapshot> source,ArrayDeque<Snapshot> destination){
        if(source.isEmpty()||importing)return;destination.addLast(snapshot());if(destination.size()>32)destination.removeFirst();var old=source.removeLast();cells=old.cells.clone();design=old.design;voxelMode=old.voxelMode;modelName=old.name;partIndex=old.part;nameField.setText(modelName);refreshPalette();edited=true;importWarning=null;importDetails=null;
    }
    private void rebuild(){
        edited=true;
        try{if(design!=null&&design.assembly()!=null){design=PrintDesign.assembly(modelName,design.assembly().replacePart(partIndex,cells));partIndex=Math.min(partIndex,design.partCount()-1);readCells();}
            else design=Arrays.stream(cells).anyMatch(java.util.Objects::nonNull)?PrintDesign.voxels(modelName,cells):null;refreshPalette();message=label("hint");}
        catch(IllegalArgumentException failure){if(!undo.isEmpty()){var before=undo.peekLast();cells=before.cells.clone();design=before.design;voxelMode=before.voxelMode;partIndex=before.part;refreshPalette();}error(failure);}
    }
    private int index(int u,int v){return VoxelGrid.index(axis,layer,u,v);}
    private boolean inside(double mx,double my,int bx,int by,int w,int h){return mx>=x+bx&&my>=y+by&&mx<x+bx+w&&my<y+by+h;}
    private boolean paint(double mx,double my,int button){
        if(!voxelMode||!inside(mx,my,12,50,80,80)||button>1)return false;
        if(VoxelGrid.brush(cells,axis,layer,(int)(mx-x-12)/5,(int)(my-y-50)/5,brush,circle,button==1?null:selected))rebuild();return true;
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(importing)return true;
        if(upload!=null)return super.mouseClicked(mx,my,button);
        if(page==1&&inside(mx,my,12,88,295,22)){if(handler.canManageSecurity())sendButton(2);return true;}
        if(page==2&&inside(mx,my,12,76,295,22)){sendButton(1);return true;}
        if(page==0){
            if(inside(mx,my,12,50,80,80)&&voxelMode&&button<=1){saveUndo();painting=true;return paint(mx,my,button);}
            if(colorMode&&inside(mx,my,195,64,91,52)&&button==0){int at=((int)(my-y-64)/13+colorScroll)*7+(int)(mx-x-195)/13;if(at<colors.size()){selected=colors.get(at);edited=true;}return true;}
            if(!colorMode&&inside(mx,my,195,81,113,32)&&button==0){int row=(int)(my-y-81)/16;if(scroll+row<matches.size()){selectedTexture=matches.get(scroll+row);selected=new VoxelGrid.Material(selectedTexture,0xFFFFFF);edited=true;}return true;}
            if(inside(mx,my,103,114,84,12)&&button==0){int row=(int)(mx-x-103)/12+paletteScroll;if(row<palette.size()){mappingSource=palette.get(row);edited=true;}return true;}
            if(inside(mx,my,101,50,80,62))return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(!importing&&upload==null&&page==0){if(painting&&paint(mx,my,button))return true;if(inside(mx,my,101,50,80,62)){yaw+=(float)dx*2;pitch=MathHelper.clamp(pitch+(float)dy, -80,80);return true;}}
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){painting=false;return super.mouseReleased(mx,my,button);}
    public boolean mouseScrolled(double mx,double my,double amount){return scroll(mx,my,amount);}
    public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){return scroll(mx,my,vertical);}
    private boolean scroll(double mx,double my,double amount){
        if(page!=0||importing)return false;
        if(colorMode&&inside(mx,my,195,64,91,52)){colorScroll=MathHelper.clamp(colorScroll-(int)Math.signum(amount),0,Math.max(0,(colors.size()+6)/7-4));return true;}
        if(!colorMode&&inside(mx,my,195,81,113,32)){scroll=MathHelper.clamp(scroll-(int)Math.signum(amount)*2,0,Math.max(0,matches.size()-2));return true;}
        if(inside(mx,my,103,114,84,12)){paletteScroll=MathHelper.clamp(paletteScroll-(int)Math.signum(amount),0,Math.max(0,palette.size()-7));return true;}
        if(inside(mx,my,12,50,80,80)){layer=MathHelper.clamp(layer+(int)Math.signum(amount),0,15);edited=true;return true;}
        if(inside(mx,my,101,133,83,15)){brush=MathHelper.clamp(brush+(int)Math.signum(amount),1,16);edited=true;return true;}return false;
    }
    @Override protected void drawBackground(DrawContext c,float delta,int mx,int my){
        c.fill(x,y,x+backgroundWidth,y+backgroundHeight,0xEC031722);c.drawBorder(x,y,backgroundWidth,backgroundHeight,0xFF367F95);
        c.fill(x+7,y+44,x+313,y+131,0xA0000A13);c.fill(x+7,y+169,x+313,y+261,0x98000A13);
        for(var slot:handler.slots){c.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xC5266880);c.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xFF031521);}
        if(page==0){
            for(int v=0;v<16;v++)for(int u=0;u<16;u++){
                var material=cells[index(u,v)];int gx=x+12+u*5,gy=y+50+v*5;
                swatch(c,material,gx,gy,4);
            }
            if(colorMode)for(int row=0;row<4;row++)for(int col=0;col<7;col++){int at=(row+colorScroll)*7+col;if(at>=colors.size())continue;var material=colors.get(at);swatch(c,material,x+195+col*13,y+64+row*13,11);if(material.equals(selected))c.drawBorder(x+194+col*13,y+63+row*13,13,13,0xFFEAFBFF);}
            else for(int row=0;row<2&&scroll+row<matches.size();row++){
                String texture=matches.get(scroll+row);int ty=y+81+row*16;
                if(texture.equals(selectedTexture))c.fill(x+193,ty-1,x+310,ty+15,0xDD256275);
                c.drawTexture(PrintTextures.image(texture),x+195,ty,14,14,0,0,16,16,16,16);
                c.drawText(textRenderer,textRenderer.trimToWidth(texture.substring(texture.lastIndexOf('/')+1),94),x+212,ty+4,0xAFCCD5,false);
            }
            for(int i=0;i<7&&paletteScroll+i<palette.size();i++){var material=palette.get(paletteScroll+i);swatch(c,material,x+103+i*12,y+114,10);if(material.equals(mappingSource))c.drawBorder(x+102+i*12,y+113,12,12,0xFFEAFBFF);}
            fitted(c,Text.literal((layer+1)+"/16"),x+29,y+137,29,0xBEEBF3);fitted(c,Text.literal(brush+"×"+brush),x+118,y+137,24,0xBEEBF3);
            PrintedModelRenderer.preview(c,overview?design:currentPart(),x+111,y+49,63,yaw,pitch);
            fitted(c,label("part",partIndex+1,design==null?1:design.partCount()),x+31,y+157,97,0xBEEBF3);
            if(design!=null&&design.assembly()!=null){var offset=design.assembly().offset(partIndex);fitted(c,Text.literal("X "+offset.getX()+"  Y "+offset.getY()+"  Z "+offset.getZ()),x+220,y+157,88,0x90C8D1);}
            if(voxelMode&&inside(mx,my,12,50,80,80)){var preview=new VoxelGrid.Material[4096];VoxelGrid.brush(preview,axis,layer,(mx-x-12)/5,(my-y-50)/5,brush,circle,selected);for(int v=0;v<16;v++)for(int u=0;u<16;u++)if(preview[index(u,v)]!=null)c.drawBorder(x+12+u*5,y+50+v*5,4,4,0xFFE3F8FF);}
        }else if(page==1){
            fitted(c,Text.translatable("gui.justifylasers.powered.owner").copy().append(": "+handler.ownerName()),x+16,y+57,286,0xBEEBF3);
            TechGui.button(c,x+12,y+88,295,22,TechGui.State.of(handler.canManageSecurity(),false,inside(mx,my,12,88,295,22),false,false));
            fitted(c,Text.translatable("gui.justifylasers.powered."+(handler.isPrivate()?"private":"public")),x+16,y+93,286,0x8EE1D4);
        }else if(page==2){
            TechGui.button(c,x+12,y+76,295,22,TechGui.State.of(true,false,inside(mx,my,12,76,295,22),false,false));
            fitted(c,Text.translatable(handler.redstone().translationKey()),x+16,y+80,286,0x8EE1D4);
        }
        else {int ty=y+49;for(var line:textRenderer.wrapLines(label("help_text"),290)){c.drawText(textRenderer,line,x+14,ty,0xACD1DC,false);ty+=10;if(ty>y+120)break;}}
        fitted(c,Text.literal("→"),x+36,y+199,12,0xA5F4EA);
        fitted(c,Text.literal(handler.energy()+" FE"),x+12,y+219,62,0x90C8D1);
        fitted(c,Text.literal("2 000 FE"),x+249,y+214,57,0x90C8D1);
        if(design!=null){var cost=currentPart().cost();fitted(c,Text.literal(cost.polymer()+" mB"),x+249,y+171,57,0xBAA1E3);
            fitted(c,Text.literal(String.format(Locale.ROOT,"%.1f klm",cost.lumens()/1000d)),x+249,y+183,57,0xBAA1E3);
            fitted(c,Text.literal(String.format(Locale.ROOT,"%.1f s",cost.ticks()/20d)),x+249,y+226,57,0x90C8D1);}
        String status=message.getString();
        int statusWidth=importWarning==null?296:198;
        if(textRenderer.getWidth(status)>statusWidth)status=textRenderer.trimToWidth(status,statusWidth-10)+"…";
        c.drawText(textRenderer,status,x+12,y+252,0x9DC2CA,false);
        if(importWarning!=null)fitted(c,importWarning,x+218,y+252,90,0xF5C37C);
    }
    @Override protected void drawForeground(DrawContext c,int mx,int my){ }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        RenderVersion.screenBackground(this,c,mx,my,delta);super.render(c,mx,my,delta);drawMouseoverTooltip(c,mx,my);
        if(importWarning!=null&&inside(mx,my,218,250,90,12))c.drawTooltip(textRenderer,importDetails,mx,my);
        else if(inside(mx,my,10,250,importWarning==null?300:202,12))c.drawTooltip(textRenderer,message,mx,my);
        if(page==0&&!colorMode&&inside(mx,my,195,81,113,32)){int row=(my-y-81)/16;if(scroll+row<matches.size())c.drawTooltip(textRenderer,Text.literal(matches.get(scroll+row)),mx,my);}
    }
    private void swatch(DrawContext c,VoxelGrid.Material material,int tx,int ty,int size){
        if(material==null){c.fill(tx,ty,tx+size,ty+size,0xFF173442);return;}
        if(material.texture().equals(VoxelGrid.WHITE)){c.fill(tx,ty,tx+size,ty+size,0xFF000000|material.tint());return;}
        c.draw();com.mojang.blaze3d.systems.RenderSystem.setShaderColor((material.tint()>>16&255)/255F,(material.tint()>>8&255)/255F,(material.tint()&255)/255F,1);
        c.drawTexture(PrintTextures.image(material.texture()),tx,ty,size,size,0,0,16,16,16,16);c.draw();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
    }
    private void fitted(DrawContext c,Text text,int tx,int ty,int width,int color){
        float scale=Math.min(1,width/(float)Math.max(1,textRenderer.getWidth(text)));c.getMatrices().push();c.getMatrices().translate(tx,ty,0);c.getMatrices().scale(scale,scale,1);c.drawText(textRenderer,text,0,0,color,false);c.getMatrices().pop();
    }
}
