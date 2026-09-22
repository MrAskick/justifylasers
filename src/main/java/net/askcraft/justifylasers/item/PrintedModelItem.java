package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.printing.PrintData;
import net.askcraft.justifylasers.printing.PrintPart;
import net.askcraft.justifylasers.block.PrintedModelBlock;
import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public final class PrintedModelItem extends BlockItem {
    public PrintedModelItem(Block block, Settings settings) { super(block, settings); }
    @Override public ActionResult place(ItemPlacementContext context) {
        var design=PrintData.read(context.getStack());
        if(design==null||design.assembly()!=null)return ActionResult.FAIL;
        var part=design.partInfo();var clicked=net.minecraft.util.math.BlockPos.ofFloored(context.getHitPos().subtract(net.minecraft.util.math.Vec3d.of(context.getSide().getVector()).multiply(.001)));
        if(part!=null&&context.getPlayer()!=null&&!context.getPlayer().isSneaking()
                &&context.getWorld().getBlockEntity(clicked) instanceof PrintedModelBlockEntity existing&&existing.design()!=null){
            var other=existing.design().partInfo();
            if(other!=null&&part.project().equals(other.project())){
                var facing=existing.getCachedState().get(PrintedModelBlock.FACING);
                var target=clicked.subtract(PrintPart.rotate(other.offset(),facing)).add(PrintPart.rotate(part.offset(),facing));
                // Snapping never extends normal placement reach or bypasses replacement/collision checks.
                if(target.equals(clicked)||context.getPlayer().getEyePos().squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(target))>25)return ActionResult.FAIL;
                var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(target),context.getSide(),target,false);
                var aligned=new ItemPlacementContext(context.getWorld(),context.getPlayer(),context.getHand(),context.getStack(),hit){
                    @Override public net.minecraft.util.math.Direction getHorizontalPlayerFacing(){return facing.getOpposite();}
                    @Override public net.minecraft.util.math.BlockPos getBlockPos(){return target;}
                };
                if(!context.getWorld().getBlockState(target).canReplace(aligned))return ActionResult.FAIL;
                return super.place(aligned);
            }
        }
        return super.place(context);
    }
    @Override public Text getName(ItemStack stack) {
        var model = PrintData.read(stack);
        var name=model == null || model.name().isBlank() ? super.getName(stack) : Text.literal(model.name());
        return model!=null&&model.partInfo()!=null?name.copy().append(" ["+(model.partInfo().index()+1)+"/"+model.partInfo().count()+"]"):name;
    }
}
