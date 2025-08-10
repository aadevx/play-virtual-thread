package play.libs;

import jj.play.ns.nl.captcha.gimpy.GimpyRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;

public class FishEyeGimpyRendererCustom implements GimpyRenderer {
    private final Color _hColor;
    private final Color _vColor;

    public FishEyeGimpyRendererCustom() {
        this(Color.BLACK, Color.BLACK);
    }

    public FishEyeGimpyRendererCustom(Color var1, Color var2) {
        this._hColor = var1;
        this._vColor = var2;
    }

    public void gimp(BufferedImage var1) {
        int var2 = var1.getHeight();
        int var3 = var1.getWidth();
        int var4 = var2 / 7;
        int var5 = var3 / 7;
        int var6 = var2 / (var4 + 1);
        int var7 = var3 / (var5 + 1);
        Graphics2D var8 = (Graphics2D)var1.getGraphics();

        int var9;
        for(var9 = var6; var9 < var2; var9 += var6) {
            var8.setColor(this._hColor);
            var8.drawLine(0, var9, var3, var9);
        }

        for(var9 = var7; var9 < var3; var9 += var7) {
            var8.setColor(this._vColor);
            var8.drawLine(var9, 0, var9, var2);
        }

        int[] var25 = new int[var2 * var3];
        int var10 = 0;

        for(int var11 = 0; var11 < var3; ++var11) {
            for(int var12 = 0; var12 < var2; ++var12) {
                var25[var10] = var1.getRGB(var11, var12);
                ++var10;
            }
        }

        double var13 = (double)this.ranInt(var3 / 4, var3 / 3);
        int var15 = var1.getWidth() / 2;
        int var16 = var1.getHeight() / 2;

        for(int var17 = 0; var17 < var1.getWidth(); ++var17) {
            for(int var18 = 0; var18 < var1.getHeight(); ++var18) {
                int var19 = var17 - var15;
                int var20 = var18 - var16;
                double var21 = Math.sqrt((double)(var19 * var19 + var20 * var20));
                if (var21 < var13) {
                    int var23 = var15 + (int)((var21 / var13) * var13 / var21 * (double)(var17 - var15));
                    int var24 = var16 + (int)((var21 / var13) * var13 / var21 * (double)(var18 - var16));
                    var1.setRGB(var17, var18, var25[var23 * var2 + var24]);
                }
            }
        }

        var8.dispose();
    }

    private int ranInt(int var1, int var2) {
        double var3 = Math.random();
        return (int)((double)var1 + (double)(var2 - var1 + 1) * var3);
    }

}
