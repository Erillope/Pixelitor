/*
 * Copyright 2022 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.layers;

import pixelitor.Composition;
import pixelitor.Views;
import pixelitor.compactions.Crop;
import pixelitor.compactions.Flip;
import pixelitor.history.PixelitorEdit;
import pixelitor.tools.Tool;
import pixelitor.tools.Tools;
import pixelitor.tools.shapes.StyledShape;
import pixelitor.tools.transform.TransformBox;
import pixelitor.utils.ImageUtils;
import pixelitor.utils.QuadrantAngle;
import pixelitor.utils.debug.DebugNode;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.concurrent.CompletableFuture;

import static pixelitor.Composition.LayerAdder.Position.ABOVE_ACTIVE;
import static pixelitor.layers.LayerButtonLayout.thumbSize;

public class ShapesLayer extends ContentLayer {
    @Serial
    private static final long serialVersionUID = 1L;

    private static int count;

    private StyledShape styledShape;

    // The box is also stored here because recreating
    // it from the styled shape is currently not possible.
    private TransformBox transformBox;

    private transient BufferedImage cachedImage;

    public ShapesLayer(Composition comp, String name) {
        super(comp, name);
    }

    public static void createNew() {
        var comp = Views.getActiveComp();
        var layer = new ShapesLayer(comp, "shape layer " + (++count));
        new Composition.LayerAdder(comp)
            .atPosition(ABOVE_ACTIVE)
            .withHistory("Add Shape Layer")
            .add(layer);
        Tools.startAndSelect(Tools.SHAPES);
    }

    @Override
    public void edit() {
        Tools.startAndSelect(Tools.SHAPES);
    }

    @Override
    protected Layer createTypeSpecificDuplicate(String duplicateName) {
        var duplicate = new ShapesLayer(comp, duplicateName);
        if (styledShape != null) {
            duplicate.setStyledShape(styledShape.clone());
            if (transformBox != null) {
                duplicate.transformBox = transformBox.copy(duplicate.styledShape, comp.getView());
            }
        }
        return duplicate;
    }

    @Override
    public void paintLayerOnGraphics(Graphics2D g, boolean firstVisibleLayer) {
        if (styledShape != null) {
            // the custom blending modes don't work with gradients
            boolean useCachedImage = g.getComposite().getClass() != AlphaComposite.class
                                     && styledShape.hasBlendingIssue();
            if (useCachedImage) {
                if (cachedImage == null) {
                    int width = comp.getCanvasWidth();
                    int height = comp.getCanvasHeight();
                    cachedImage = ImageUtils.createSysCompatibleImage(width, height);
                    Graphics2D imgG = cachedImage.createGraphics();
                    styledShape.paint(imgG);
                    imgG.dispose();
                }
                g.drawImage(cachedImage, 0, 0, null);
            } else {
                styledShape.paint(g);
            }
        }
    }

    @Override
    protected BufferedImage applyOnImage(BufferedImage src) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BufferedImage createIconThumbnail() {
        BufferedImage img = ImageUtils.createSysCompatibleImage(thumbSize, thumbSize);
        Graphics2D g2 = img.createGraphics();

        if (styledShape == null) {
            thumbCheckerBoardPainter.paint(g2, null, thumbSize, thumbSize);
        } else {
            styledShape.paintIconThumbnail(g2, thumbSize);
        }

        g2.dispose();
        return img;
    }

    @Override
    public CompletableFuture<Void> resize(Dimension newSize) {
        transform(comp.getCanvas().createImTransformToSize(newSize));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void crop(Rectangle2D cropRect, boolean deleteCropped, boolean allowGrowing) {
        transform(Crop.createCanvasTransform(cropRect));
    }

    @Override
    public void flip(Flip.Direction direction) {
        transform(direction.createCanvasTransform(comp.getCanvas()));
    }

    @Override
    public void rotate(QuadrantAngle angle) {
        transform(angle.createCanvasTransform(comp.getCanvas()));
    }

    @Override
    public void enlargeCanvas(int north, int east, int south, int west) {
        transform(AffineTransform.getTranslateInstance(west, north));
    }

    private void transform(AffineTransform at) {
        if (hasShape()) {
            if (transformBox != null) {
                // the box will also transform the shape
                transformBox.imCoordsChanged(at, comp);
            } else {
                // This case should never happen, because an
                // initialized shape should always have a box.
                // Implemented here, but not for the Move Tool support.
                styledShape.resetTransform();
                styledShape.imTransform(at);
                System.out.println("ShapesLayer::transform: transforming only the shape");
            }
        }
    }

    private boolean hasShape() {
        return styledShape != null && styledShape.isInitialized();
    }

    public StyledShape getStyledShape() {
        return styledShape;
    }

    public TransformBox getTransformBox() {
        return transformBox;
    }

    public void setTransformBox(TransformBox transformBox) {
        this.transformBox = transformBox;
    }

    // When the mouse is pressed, only the reference is set.
    // Later, when the mouse is released, the history and
    // the icon image will also be handled
    public void setStyledShape(StyledShape styledShape) {
        assert styledShape != null;
        this.styledShape = styledShape;
        styledShape.setChangeListener(() -> cachedImage = null);
    }

    @Override
    public Rectangle getEffectiveBoundingBox() {
        return comp.getCanvasBounds();
    }

    @Override
    public Rectangle getContentBounds() {
        // by returning null, the move tool shows no outline
        return null;
    }

    @Override
    public int getPixelAtPoint(Point p) {
        return 0;
    }

    @Override
    public void startMovement() {
        super.startMovement();
        if (hasShape() && transformBox != null) {
            transformBox.startMovement();
        }
    }

    @Override
    public void moveWhileDragging(double relImX, double relImY) {
        super.moveWhileDragging(relImX, relImY);
        if (hasShape() && transformBox != null) {
            transformBox.moveWhileDragging(relImX, relImY);
        }
    }

    @Override
    public PixelitorEdit endMovement() {
        PixelitorEdit edit = super.endMovement();
        if (hasShape() && transformBox != null) {
            transformBox.endMovement();
            updateIconImage();
        }
        return edit;
    }

    @Override
    PixelitorEdit createMovementEdit(int oldTx, int oldTy) {
        if (hasShape() && transformBox != null) {
            return transformBox.createMovementEdit(comp, "Move Shape Layer");
        }
        return null;
    }

    @Override
    public Tool getPreferredTool() {
        return Tools.SHAPES;
    }

    @Override
    public String getTypeString() {
        return "Shape Layer";
    }

    public boolean checkConsistency() {
        if (styledShape != null) {
            return styledShape.checkConsistency();
        }
        return true;
    }

    @Override
    public DebugNode createDebugNode(String descr) {
        DebugNode node = super.createDebugNode(descr);

        if (styledShape == null) {
            node.addString("styledShape", "null");
        } else {
            node.add(styledShape.createDebugNode());
        }

        if (transformBox == null) {
            node.addString("transformBox", "null");
        } else {
            node.add(transformBox.createDebugNode());
        }

        return node;
    }
}