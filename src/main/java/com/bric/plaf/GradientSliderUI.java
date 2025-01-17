/*
 * @(#)GradientSliderUI.java
 *
 * $Date: 2014-06-06 20:04:49 +0200 (P, 06 jún. 2014) $
 *
 * Copyright (c) 2011 by Jeremy Wood.
 * All rights reserved.
 *
 * The copyright of this software is owned by Jeremy Wood. 
 * You may not use, copy or modify this software, except in  
 * accordance with the license agreement you entered into with  
 * Jeremy Wood. For details see accompanying license terms.
 * 
 * This software is probably, but not necessarily, discussed here:
 * https://javagraphics.java.net/
 * 
 * That site should also contain the most recent official version
 * of this software.  (See the SVN repository for more details.)
 */
package com.bric.plaf;

import com.bric.swing.GradientSlider;
import com.bric.swing.MultiThumbSlider;

import javax.swing.*;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Math.PI;

/**
 * The UI for the GradientSlider class.
 *
 * There are a few properties you can use to customize the UI
 * of a GradientSlider.  You can set these for each slider
 * by calling:
 * <BR><code>slider.putClientProperty(key,value);</code>
 * <P>Or you can set these globally by calling:
 * <BR><code>UIManager.put(key,value);</code>
 * <P>The available properties are:
 * <P><TABLE summary="GradientSliderUI Client Properties" BORDER="1" CELLPADDING=5>
 * <TR>
 * <TD>Property Name</TD><TD>Default Value</TD><TD>Description</td>
 * </TR>
 * <TR>
 * <TD>GradientSlider.useBevel</TD><TD>"false"</TD><TD>If this is <code>true</code>, then this slider will be painted in a rectangle with a bevel effect around the borders.  If this is <code>false</code>, then this slider will be painted in a rounded rectangle.</td>
 * </TR>
 * <TR>
 * <TD>GradientSlider.showTranslucency</TD><TD>"true"</TD><TD>If this is <code>true</code>, then the slider will reflect the opacity of the colors in the gradient, and paint a checkered background underneath the colors to indicate opacity.  If this is <code>false</code>, then this slider will always paint with completely opaque colors, although the actual colors may be translucent.</td>
 * </TR>
 * <TR>
 * <TD>GradientSlider.colorPickerIncludesOpacity</TD><TD>"true"</TD><TD>This is used when the user double-clicks a color and a ColorPicker dialog is invoked.  (So this value may not have any meaning if you override <code>GradientSlider.doDoubleClick()</code>.)  This controls whether the opacity/alpha controls are available in that dialog.  This does <i>not</i> control whether translucent colors can be used in this slider: translucent colors are always allowed, if the user can enter them.</TD>
 * </TR>
 * <TR>
 * <TD>MultiThumbSlider.indicateComponent</TD><TD>"true"</TD><TD>If this is <code>true</code>, then the thumbs will only paint on this component when the mouse is inside this slider <i>or</i> when this slider as the keyboard focus.</td>
 * </TR>
 * <TR>
 * <TD>MultiThumbSlider.indicateThumb</TD><TD>"true"</TD><TD>If this is <code>true</code>, then the thumb the mouse is over will gently fade into a slightly different color.</td>
 * </TR>
 * </TABLE>
 */
public class GradientSliderUI extends MultiThumbSliderUI {
    private static final int TRIANGLE_SIZE = 8;

    /**
     * The width of this image is the absolute widest the track will
     * ever become.
     */
    private final BufferedImage img = new BufferedImage(1000, 1, BufferedImage.TYPE_INT_ARGB);

    /**
     * A temporary array used for the buffered image
     */
    private final int[] array = new int[img.getWidth()];

    public GradientSliderUI(GradientSlider slider) {
        super(slider);
    }

    @Override
    public int getClickLocationTolerance() {
        return TRIANGLE_SIZE;
    }

    protected void calculateImage() {
        float[] f = slider.getThumbPositions();
        Color[] c = ((GradientSlider) slider).getColors();

        /* make sure we DO have a value at 0 and 1:
         */
        if (f[0] != 0) {
            float[] f2 = new float[f.length + 1];
            System.arraycopy(f, 0, f2, 1, f.length);
            Color[] c2 = new Color[c.length + 1];
            System.arraycopy(c, 0, c2, 1, f.length);
            f = f2;
            c = c2;
            f[0] = 0;
            c[0] = c[1];
        }
        if (f[f.length - 1] != 1) {
            float[] f2 = new float[f.length + 1];
            System.arraycopy(f, 0, f2, 0, f.length);
            Color[] c2 = new Color[c.length + 1];
            System.arraycopy(c, 0, c2, 0, f.length);
            f = f2;
            c = c2;
            f[f.length - 1] = 1;
            c[c.length - 1] = c[c.length - 2];
        }

        /* Now, finally paint */
        int[] argb = new int[c.length];
        for (int a = 0; a < argb.length; a++) {
            argb[a] = ((c[a].getAlpha() & 0xff) << 24) +
                    ((c[a].getRed() & 0xff) << 16) +
                    ((c[a].getGreen() & 0xff) << 8) +
                    (c[a].getBlue() & 0xff);
        }
        int max;
        if (slider.getOrientation() == MultiThumbSlider.HORIZONTAL) {
            max = trackRect.width;
        } else {
            max = trackRect.height;
        }
        if (max <= 0) {
            return;
        }

        boolean alwaysOpaque = getProperty(slider, "GradientSlider.showTranslucency", "true").equals("false");

        float fraction;
        int i1 = 0;
        int i2 = 1;
        int a1 = (argb[0] >> 24) & 0xff;
        int r1 = (argb[0] & 0x00ff0000) >> 16;
        int g1 = (argb[0] & 0x0000ff00) >> 8;
        int b1 = (argb[0] & 0x000000ff);
        int a2 = (argb[1] >> 24) & 0xff;
        int r2 = (argb[1] & 0x00ff0000) >> 16;
        int g2 = (argb[1] & 0x0000ff00) >> 8;
        int b2 = (argb[1] & 0x000000ff);
        for (int z = 0; z < max; z++) {
            fraction = ((float) z) / ((float) (max - 1));
            if (fraction < 1 && fraction >= f[i2]) {
                while (fraction < 1 && fraction >= f[i2]) {
                    i1++;
                    i2++;
                }

                a1 = (argb[i1] >> 24) & 0xff;
                r1 = (argb[i1] & 0x00ff0000) >> 16;
                g1 = (argb[i1] & 0x0000ff00) >> 8;
                b1 = (argb[i1] & 0x000000ff);
                a2 = (argb[i2] >> 24) & 0xff;
                r2 = (argb[i2] & 0x00ff0000) >> 16;
                g2 = (argb[i2] & 0x0000ff00) >> 8;
                b2 = (argb[i2] & 0x000000ff);
            }
            float colorFraction = (fraction - f[i1]) / (f[i2] - f[i1]);
            if (colorFraction > 1) {
                colorFraction = 1;
            }
            if (colorFraction < 0) {
                colorFraction = 0;
            }
            if (alwaysOpaque) {
                a1 = 255;
                a2 = 255;
            }
            array[z] = (((int) (a1 * (1 - colorFraction) + a2 * colorFraction)) << 24) +
                    (((int) (r1 * (1 - colorFraction) + r2 * colorFraction)) << 16) +
                    (((int) (g1 * (1 - colorFraction) + g2 * colorFraction)) << 8) +
                    (int) (b1 * (1 - colorFraction) + b2 * colorFraction);
        }
        img.getRaster().setDataElements(0, 0, max, 1, array);
    }


    @Override
    public Dimension getMinimumSize(JComponent s) {
        Dimension d = super.getMinimumSize(s);
        if (slider.getOrientation() == MultiThumbSlider.HORIZONTAL) {
            d.height += 2;
        } else {
            d.width += 2;
        }
        return d;
    }

    @Override
    public Dimension getPreferredSize(JComponent s) {
        Dimension d = super.getPreferredSize(s);
        if (slider.getOrientation() == MultiThumbSlider.HORIZONTAL) {
            d.height += 2;
        } else {
            d.width += 2;
        }
        return d;
    }

    @Override
    protected Rectangle calculateTrackRect() {
        int w = slider.getWidth();
        int h = slider.getHeight();

        Rectangle r = new Rectangle();

        if (slider.getOrientation() == MultiThumbSlider.HORIZONTAL) {
            r.x = TRIANGLE_SIZE;
            r.y = 3;
            r.height = h - TRIANGLE_SIZE - r.y;
            r.width = w - 2 * TRIANGLE_SIZE;
            if (r.width > img.getWidth()) {
                r.width = img.getWidth();
                r.x = (w - 2 * TRIANGLE_SIZE) / 2 - r.width / 2;
            }
            if (r.height > 2 * DEPTH) {
                r.height = 2 * DEPTH;
                r.y = (h - TRIANGLE_SIZE) / 2 - r.height / 2;
            }
        } else {
            r.x = 3;
            r.y = TRIANGLE_SIZE;
            r.width = w - TRIANGLE_SIZE - r.x;
            r.height = h - 2 * TRIANGLE_SIZE;
            if (r.height > img.getWidth()) {
                r.height = img.getWidth();
                r.y = (h - 2 * TRIANGLE_SIZE) / 2 - r.height / 2;
            }
            if (r.width > 2 * DEPTH) {
                r.width = 2 * DEPTH;
                r.x = (w - TRIANGLE_SIZE) / 2 - r.width / 2;
            }
        }
        return r;
    }

    @Override
    protected synchronized void calculateGeometry() {
        super.calculateGeometry();
        calculateImage();
    }

    static TexturePaint checkerPaint;

    private static void createCheckerPaint() {
        int k = 4;
        BufferedImage bi = new BufferedImage(2 * k, 2 * k, TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, 2 * k, 2 * k);
        g.setColor(Color.lightGray);
        g.fillRect(0, 0, k, k);
        g.fillRect(k, k, k, k);
        checkerPaint = new TexturePaint(bi, new Rectangle(0, 0, bi.getWidth(), bi.getHeight()));
    }

    /**
     * The "frame" includes the trackRect and possible some extra padding.
     * For example, the frame might be the rounded rectangle enclosing the
     * track (if rounded rectangles are turned on)
     *
     * @return
     */
    private Shape getFrame() {

        if (getProperty(slider, "GradientSlider.useBevel", "false").equals("true")) {
            return trackRect;
        }

        if (slider.getOrientation() == MultiThumbSlider.HORIZONTAL) {
            int curve = Math.min(TRIANGLE_SIZE - 2, trackRect.height / 2);
            return new RoundRectangle2D.Float(
                    trackRect.x - curve, trackRect.y,
                    trackRect.width + 2 * curve, trackRect.height,
                    curve * 2, curve * 2);
        } else {
            int curve = Math.min(TRIANGLE_SIZE - 2, trackRect.width / 2);
            return new RoundRectangle2D.Float(
                    trackRect.x, trackRect.y - curve,
                    trackRect.width, trackRect.height + 2 * curve,
                    curve * 2, curve * 2
            );
        }
    }

    @Override
    protected void paintTrack(Graphics2D g) {
        // Laszlo added this to add a more disabled-like look
        if (!slider.isEnabled()) {
            g.setColor(g.getBackground().darker());
            g.draw(getFrame());
            return;
        }

        Composite oldComposite = g.getComposite();
        float alpha = slider.isEnabled() ? 1 : 0.5f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        Shape frame = getFrame();

        boolean alwaysOpaque = getProperty(slider, "GradientSlider.showTranslucency", "true").equals("false");

        if (!alwaysOpaque) {
            if (checkerPaint == null) {
                createCheckerPaint();
            }
            g.setPaint(checkerPaint);
            g.fill(frame);
        }

        TexturePaint tp = new TexturePaint(img, new Rectangle(trackRect.x, 0, img.getWidth(), 1));
        g.setPaint(tp);

        AffineTransform oldTransform = g.getTransform();
        AffineTransform transform = new AffineTransform();
        if (slider.getOrientation() == MultiThumbSlider.VERTICAL) {
            if (slider.isInverted()) {
                transform.rotate(PI / 2, trackRect.x, trackRect.y);
            } else {
                transform.rotate(-PI / 2, trackRect.x, trackRect.y + trackRect.height);
            }
        } else {
            if (slider.isInverted()) {
                //flip horizontal:
                double x1 = trackRect.x;
                double x2 = trackRect.x + trackRect.width;
                //m00*x1+m02 = x2
                //m00*x2+m02 = x1
                double m00 = (x2 - x1) / (x1 - x2);
                double m02 = x1 - m00 * x2;
                transform.setTransform(m00, 0, 0, 1, m02, 0);
            } else {
                //no transform necessary
            }
        }

        g.transform(transform);

        try {
            g.fill(transform.createInverse().createTransformedShape(trackRect));
        } catch (NoninvertibleTransformException e) {
            //this won't happen; unless a width/height
            //is zero somewhere, in which case we have nothing to paint anyway.
        }
        if (oldTransform != null) {
            g.setTransform(oldTransform);
        }
        if (getProperty(slider, "GradientSlider.useBevel", "false").equals("true")) {
            PlafPaintUtils.drawBevel(g, trackRect);
        } else {
            g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
            Shape oldClip = g.getClip();
            int first, last;
            Color[] colors = ((GradientSlider) slider).getColors();
            float[] f = slider.getThumbPositions();
            if ((!slider.isInverted() && slider.getOrientation() == MultiThumbSlider.HORIZONTAL) ||
                    (slider.isInverted() && slider.getOrientation() == MultiThumbSlider.VERTICAL)) {
                first = 0;
                last = colors.length - 1;
                while (f[first] < 0) {
                    first++;
                }
                while (f[last] > 1) {
                    last--;
                }
            } else {
                last = 0;
                first = colors.length - 1;
                while (f[last] < 0) {
                    last++;
                }
                while (f[first] > 1) {
                    first--;
                }
            }
            if (slider.getOrientation() == MultiThumbSlider.HORIZONTAL) {
                g.clip(frame);
                g.setColor(colors[first]);
                g.fillRect(0, 0, trackRect.x, slider.getHeight());
                g.setColor(colors[last]);
                g.fillRect(trackRect.x + trackRect.width, 0,
                        slider.getWidth() - (trackRect.x + trackRect.width), slider.getHeight());
            } else {
                g.clip(frame);
                g.setColor(colors[first]);
                g.fillRect(0, 0, slider.getWidth(), trackRect.y);
                g.setColor(colors[last]);
                g.fillRect(0, trackRect.y + trackRect.height,
                        slider.getWidth(), slider.getHeight() - (trackRect.y + trackRect.height));
            }
            g.setStroke(new BasicStroke(1));
            g.setClip(oldClip);
            g.setColor(new Color(0, 0, 0, 130));
            g.draw(frame);

            g.setColor(new Color(0, 0, 0, 130));
        }

        if (slider.isPaintTicks()) {
            paintTick(g, 0.25f, 2);
            paintTick(g, 0.5f, 2);
            paintTick(g, 0.75f, 2);
            paintTick(g, 0.0f, 2);
            paintTick(g, 1.0f, 2);
        }
        g.setComposite(oldComposite);
    }

    protected void paintTick(Graphics2D g, float f, int d) {
        if (slider.getOrientation() == MultiThumbSlider.HORIZONTAL) {
            int x = (int) (trackRect.x + trackRect.width * f + 0.5f);
            int y = trackRect.y + trackRect.height;
            g.drawLine(x, y, x, y + d);
            y = trackRect.y;
            g.drawLine(x, y, x, y - d);
        } else {
            int y = (int) (trackRect.y + trackRect.height * f + 0.5f);
            int x = trackRect.x + trackRect.width;
            g.drawLine(x, y, x + d, y);
            x = trackRect.x;
            g.drawLine(x, y, x - d, y);
        }
    }

    @Override
    protected void paintFocus(Graphics2D g) {

    }

    static GeneralPath hTriangle = null;
    static GeneralPath vTriangle = null;

    @Override
    protected void paintThumbs(Graphics2D g) {
        if (!slider.isEnabled()) {
            return;
        }

        if (hTriangle == null) {
            hTriangle = new GeneralPath();
            hTriangle.moveTo(0, 0);
            hTriangle.lineTo(TRIANGLE_SIZE, TRIANGLE_SIZE);
            hTriangle.lineTo(-TRIANGLE_SIZE, TRIANGLE_SIZE);
            hTriangle.lineTo(0, 0);
            hTriangle.closePath();
            vTriangle = new GeneralPath();
            vTriangle.moveTo(0, 0);
            vTriangle.lineTo(TRIANGLE_SIZE, TRIANGLE_SIZE);
            vTriangle.lineTo(TRIANGLE_SIZE, -TRIANGLE_SIZE);
            vTriangle.lineTo(0, 0);
            vTriangle.closePath();
        }


        AffineTransform t = new AffineTransform();
        int dx = trackRect.x + trackRect.width;
        int dy = trackRect.y + trackRect.height;
        dy -= trackRect.height / 6;
        dx -= trackRect.width / 6;
        int selected = slider.getSelectedThumb(false);
        float[] f = slider.getThumbPositions();
        int orientation = slider.getOrientation();
        Shape shape;

        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                indication));

        for (int a = 0; a < thumbPositions.length; a++) {
            if (f[a] >= 0 && f[a] <= 1 && a != selected) {
                if (orientation == MultiThumbSlider.HORIZONTAL) {
                    dx = thumbPositions[a];
                    shape = hTriangle;
                } else {
                    dy = thumbPositions[a];
                    shape = vTriangle;
                }
                t.setToTranslation(dx, dy);
                g.transform(t);

                float brightness = Math.max(0, thumbIndications[a] * 0.6f);

                g.setColor(new Color((int) (255 * brightness),
                        (int) (255 * brightness),
                        (int) (255 * brightness)));
                g.fill(shape);
                g.translate(-0.5f, -0.5f);

                g.setColor(new Color(255, 255, 255));

                g.draw(shape);
                g.translate(0.5f, 0.5f);

                t.setToTranslation(-dx, -dy);
                g.transform(t);
            }
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                indication));

        if (selected != -1 && f[selected] >= 0 && f[selected] <= 1) {
            if (orientation == MultiThumbSlider.HORIZONTAL) {
                dx = thumbPositions[selected];
                shape = hTriangle;
            } else {
                dy = thumbPositions[selected];
                shape = vTriangle;
            }
            t.setToTranslation(dx, dy);
            g.transform(t);

            g.setColor(new Color(255, 255, 255));
            g.fill(shape);
            g.translate(-0.5f, -0.5f);

            g.setColor(new Color(0, 0, 0));

            g.draw(shape);
            g.translate(0.5f, 0.5f);

            t.setToTranslation(-dx, -dy);
            g.transform(t);
        }

        g.setComposite(oldComposite);
    }
}
