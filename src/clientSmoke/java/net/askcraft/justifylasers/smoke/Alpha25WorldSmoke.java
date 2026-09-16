package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserComponentBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.compat.RecipeNavigation;
import net.askcraft.justifylasers.client.screen.IndustrialMachineScreen;
import net.askcraft.justifylasers.client.screen.SolarConcentratorScreen;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.slf4j.LoggerFactory;

final class Alpha25WorldSmoke {
    private static final boolean THERMAL = Boolean.getBoolean("justifylasers.smokeAlpha26");
    private static final BlockPos SMALL = new BlockPos(802,2,802), GROWER = SMALL.east(4);
    private static final BlockPos GENERATOR = new BlockPos(806,2,798);
    private static final BlockPos ORIGIN = new BlockPos(797,2,810), LARGE = ORIGIN.add(1,0,-1), RECEIVER = LARGE.north(3);
    private static final BlockPos EAST_RECEIVER = ORIGIN.add(6,0,1);

    static void tick(MinecraftClient client, int frame) {
        if (frame == 10) language(client,"en_us");
        if (frame == 30) client.getServer().execute(() -> scene(client));
        if (frame == 100) {
            check(client,true); capture(client,"small-noon");
            camera(client,new Vec3d(805,2,796.3),Vec3d.ofCenter(GENERATOR));
            if(THERMAL)client.getServer().execute(() -> {
                // Thermal progression is tested with real fuel ticks in GameTests; load a hot display fixture here.
                var generator=(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(GENERATOR);
                SmokeWorldAccess.warmGenerator(generator);
            });
        }
        if (frame == 140) { capture(client,"fuel-display"); open(client,GENERATOR); }
        if (frame == 165) {
            if (!(client.currentScreen instanceof IndustrialMachineScreen screen) || screen.getScreenHandler().temperature() <= 20
                    || screen.getScreenHandler().rate() <= 0 || (THERMAL ? screen.getScreenHandler().rate()!=128 || screen.getScreenHandler().efficiency()!=95 : screen.getScreenHandler().rate()>=128))
                throw new AssertionError("Generator GUI must synchronize gradual warm-up and dynamic output");
            capture(client,"generator-menu"); client.player.closeHandledScreen();
            camera(client,THERMAL?new Vec3d(796,7,809):new Vec3d(793.5,4.1,806),new Vec3d(798.5,3.4,811.5));
        }
        if(THERMAL && frame==190){capture(client,"large-panel-grid");camera(client,new Vec3d(793.5,4.1,806),new Vec3d(798.5,3.4,811.5));}
        if (frame == 205) { capture(client,"large-noon"); open(client,LARGE); }
        if (frame == 230) {
            if (!(client.currentScreen instanceof SolarConcentratorScreen screen) || screen.getScreenHandler().luminousFlux() < 470_000)
                throw new AssertionError("Large concentrator GUI not synchronized");
            capture(client,"large-menu"); client.player.closeHandledScreen();
            client.getServer().execute(() -> ((SolarConcentratorBlockEntity)client.getServer().getOverworld().getBlockEntity(LARGE)).selectOutput(Direction.EAST));
            camera(client,new Vec3d(805.5,3.9,806),new Vec3d(798.5,3.4,811.5));
        }
        if (frame == 270) {
            client.getServer().submit(() -> {
                var world=client.getServer().getOverworld();
                if (((LaserOpticBlockEntity)world.getBlockEntity(RECEIVER)).lastFlux()!=0
                        || ((LaserOpticBlockEntity)world.getBlockEntity(EAST_RECEIVER)).lastFlux()<470_000)
                    throw new AssertionError("Configurable output did not reroute the optical budget");
            }).join();
            capture(client,"large-east-output");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(1000));
            if(THERMAL)camera(client,new Vec3d(793.5,4.6,807),new Vec3d(798.5,4,811.5));
            else camera(client,new Vec3d(800,3,799.7),Vec3d.ofCenter(SMALL).add(0,.15,0));
        }
        if(THERMAL && frame==295){capture(client,"large-morning-tracking");camera(client,new Vec3d(800,3,799.7),Vec3d.ofCenter(SMALL).add(0,.15,0));}
        if (frame == 310) {
            capture(client,"small-morning");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(18000));
        }
        if (frame == 350) {
            check(client,false); capture(client,"small-night");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(6000));
            open(client,GROWER);
        }
        if (frame == 385) {
            if (!(client.currentScreen instanceof IndustrialMachineScreen screen) || screen.getScreenHandler().lightFlux()<15000)
                throw new AssertionError("Grower GUI must show optical input");
            capture(client,"grower-menu");
            int x=(screen.width-320)/2, y=(screen.height-234)/2;
            screen.mouseClicked(x+185,y+76,0);
            if (Platform.isModLoaded("jei") && client.currentScreen instanceof IndustrialMachineScreen)
                throw new AssertionError("Recipe arrow did not open JEI");
        }
        if (frame == 410) {
            if (Platform.isModLoaded("jei")) { Alpha24JeiSmoke.verifyMachine("crystal_growth_chamber",1); capture(client,"grower-jei-lm"); }
            client.player.closeHandledScreen(); open(client,GROWER);
        }
        if (frame == 440) {
            if (!(client.currentScreen instanceof IndustrialMachineScreen screen)) throw new AssertionError("Grower did not reopen");
            screen.mouseClicked((screen.width-320)/2+24,(screen.height-234)/2+140,0);
            if (Platform.isModLoaded("jei") && client.currentScreen instanceof IndustrialMachineScreen)
                throw new AssertionError("Relocated Help button is not clickable");
        }
        if (frame == 465) {
            if (Platform.isModLoaded("jei")) { Alpha24JeiSmoke.solar(false); }
        }
        if (frame == 490) {
            if (Platform.isModLoaded("jei")) capture(client,"new-construction");
            client.player.closeHandledScreen(); language(client,"ru_ru");
        }
        if (frame == 545) open(client,GROWER);
        if (frame == 570) {
            capture(client,"grower-ru"); client.player.closeHandledScreen(); open(client,SMALL);
        }
        if (frame == 605) {
            if (!(client.currentScreen instanceof SolarConcentratorScreen screen) || !screen.getScreenHandler().small())
                throw new AssertionError("Small concentrator uses its own title and preview");
            capture(client,"small-ru"); client.player.closeHandledScreen();
            client.getServer().submit(() -> {
                var generator=(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(GENERATOR);
                if (ExtraterrestrialTabletItem.charge(generator.getStack(6))<=0) throw new AssertionError("Native charging slot did not charge tablet");
                return true;
            }).join();
            LoggerFactory.getLogger("justifylasers-client-smoke").info("{}_WORLD_SMOKE_PASSED solar=true lmGrowth=true charging=true output=true gui=true languages=en,ru jei={} shader={}",THERMAL?"ALPHA26":"ALPHA25",Platform.isModLoaded("jei"),IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void scene(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        if(world.getBlockState(SMALL).isOf(ModIndustry.SMALL_SOLAR_CONCENTRATOR)
                && !(world.getBlockEntity(SMALL) instanceof SolarConcentratorBlockEntity))
            throw new AssertionError("Saved small collector did not restore its solar entity");
        world.setTimeOfDay(6000); world.setWeather(0,0,false,false);
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,client.getServer());
        for(int x=792;x<=812;x++)for(int z=795;z<=816;z++){
            world.setBlockState(new BlockPos(x,1,z),Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for(int y=2;y<=10;y++)world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
        }
        var player=player(client); player.changeGameMode(GameMode.CREATIVE); player.getInventory().clear();
        player.getAbilities().flying=true; player.sendAbilitiesUpdate();
        world.setBlockState(SMALL,ModIndustry.SMALL_SOLAR_CONCENTRATOR.getDefaultState().with(LaserComponentBlock.FACING,Direction.EAST));
        ((SolarConcentratorBlockEntity)world.getBlockEntity(SMALL)).initializeOwner(player);
        for(var pos:ChamberStructure.positions(GROWER))world.setBlockState(pos,ModIndustry.MACHINES.get(MachineKind.CRYSTAL_GROWER).getDefaultState());
        var grower=(IndustrialMachineBlockEntity)world.getBlockEntity(GROWER); grower.initializeOwner(player);
        if(!ChamberStructure.form(grower))throw new AssertionError("Grower cannot form");
        grower.setStack(0,new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL,4));grower.setStack(1,new ItemStack(Items.QUARTZ,8));grower.fillWater(4000,false);
        world.setBlockState(GENERATOR,ModIndustry.MACHINES.get(MachineKind.FUEL_GENERATOR).getDefaultState());
        var generator=(IndustrialMachineBlockEntity)world.getBlockEntity(GENERATOR); generator.initializeOwner(player);
        generator.setStack(0,new ItemStack(Items.COAL,8));generator.setStack(6,new ItemStack(ModIndustry.EXTRATERRESTRIAL_TABLET));
        for(var cell:SolarStructure.BODY)world.setBlockState(ORIGIN.add(cell.offset()),ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState());
        for(var cell:SolarStructure.RESONATORS)world.setBlockState(ORIGIN.add(cell.offset()),
                ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState().with(LaserComponentBlock.FACING,SolarStructure.resonatorFacing(cell)));
        world.setBlockState(LARGE,ModIndustry.COMPONENT_BLOCKS.get("optical_resonator").getDefaultState().with(LaserComponentBlock.FACING,Direction.NORTH));
        var large=(SolarConcentratorBlockEntity)world.getBlockEntity(LARGE);large.initializeOwner(player);
        if(!SolarStructure.form(large))throw new AssertionError("Large collector cannot form");
        world.setBlockState(RECEIVER,ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING,Direction.SOUTH));
        world.setBlockState(EAST_RECEIVER,ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING,Direction.WEST));
        world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(792,2,795,813,11,817),entity->true)
                .forEach(net.minecraft.entity.Entity::discard);
        camera(client,new Vec3d(800.5,2.6,799),new Vec3d(804,2.6,802.6));
    }
    private static void check(MinecraftClient client,boolean day){
        client.getServer().submit(() -> {
            var world=client.getServer().getOverworld();
            var small=(SolarConcentratorBlockEntity)world.getBlockEntity(SMALL);
            var grower=(IndustrialMachineBlockEntity)world.getBlockEntity(GROWER);
            if(small.isBeamActive()!=day || (grower.lightFlux()>0)!=day || grower.energy().stored()!=0)
                throw new AssertionError("Native solar/LM state incorrect: "+small.status()+" / "+grower.lightFlux());
            return true;
        }).join();
    }
    private static void open(MinecraftClient client,BlockPos pos){
        client.getServer().execute(() -> {
            player(client).teleport(client.getServer().getOverworld(),pos.getX()-1,pos.getY(),pos.getZ()-1,0,0);
            Platform.openScreen(player(client),(LaserScreenFactory)client.getServer().getOverworld().getBlockEntity(pos));
        });
    }
    private static void camera(MinecraftClient client,Vec3d position,Vec3d target){
        client.getServer().execute(() -> {
            Vec3d direction=target.subtract(position.add(0,1.62,0));
            float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
            player(client).teleport(client.getServer().getOverworld(),position.x,position.y,position.z,yaw,pitch);
        });
    }
    private static ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void language(MinecraftClient client,String locale){client.getLanguageManager().setLanguage(locale);client.options.language=locale;client.reloadResources();}
    private static void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(client.runDirectory,(THERMAL?"alpha26-":"alpha25-")+name+".png",client.getFramebuffer(),text -> {});}
    private Alpha25WorldSmoke(){ }
}
