package net.askcraft.justifylasers.client.screen;

import net.minecraft.client.gui.DrawContext;

final class PrintingIcons {
    enum Icon { UNDO, REDO, SINGLE, REPEAT, PLAY, STOP, HOLOGRAM, SQUARE, CIRCLE }
    static void draw(DrawContext c,Icon icon,int x,int y,int color) {
        if(icon==Icon.UNDO||icon==Icon.REDO||icon==Icon.REPEAT) {
            for(int i=0;i<25;i++){double a=Math.toRadians(40+i*11);int px=(int)Math.round(Math.cos(a)*5),py=(int)Math.round(Math.sin(a)*5);if(icon==Icon.REDO)px=-px;c.fill(x+7+px,y+7+py,x+9+px,y+9+py,color);}
            int tip=icon==Icon.REDO?3:11;
            for(int i=0;i<4;i++)c.fill(x+tip-i,y+10-i,x+tip+1+i,y+11-i,color);
            if(icon==Icon.REPEAT)for(int i=0;i<4;i++)c.fill(x+4-i,y+4+i,x+5+i,y+5+i,color);
        } else if(icon==Icon.PLAY)for(int i=0;i<7;i++)c.fill(x+4+i,y+2+i,x+5+i,y+15-i,color);
        else if(icon==Icon.STOP)c.fill(x+3,y+3,x+13,y+13,color);
        else if(icon==Icon.SINGLE){c.drawBorder(x+2,y+2,12,12,color);c.fill(x+7,y+5,x+9,y+11,color);}
        else if(icon==Icon.SQUARE)c.drawBorder(x+2,y+2,12,12,color);
        else if(icon==Icon.CIRCLE)for(int i=0;i<32;i++){double a=i*Math.PI/16;int px=(int)Math.round(7+Math.cos(a)*5),py=(int)Math.round(7+Math.sin(a)*5);c.fill(x+px,y+py,x+px+2,y+py+2,color);}
        else {c.fill(x+2,y+13,x+14,y+15,color);c.drawBorder(x+5,y+1,7,7,color);for(int i=0;i<3;i++){c.fill(x+4-i,y+8+i,x+5-i,y+9+i,color);c.fill(x+11+i,y+8+i,x+12+i,y+9+i,color);}}
    }
    private PrintingIcons() { }
}
