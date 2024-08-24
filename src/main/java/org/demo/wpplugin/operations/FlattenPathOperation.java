package org.demo.wpplugin.operations;

import org.demo.wpplugin.Path;
import org.demo.wpplugin.PathManager;
import org.demo.wpplugin.layers.PathPreviewLayer;
import org.pepsoft.worldpainter.brushes.Brush;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.operations.*;
import org.pepsoft.worldpainter.painting.Paint;
import org.pepsoft.worldpainter.selection.SelectionBlock;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

import static org.demo.wpplugin.CubicBezierSpline.getCubicBezierHandles;
import static org.demo.wpplugin.operations.AddPointOperation.PATH_ID;

/**
 * For any operation that is intended to be applied to the dimension in a particular location as indicated by the user
 * by clicking or dragging with a mouse or pressing down on a tablet, it makes sense to subclass
 * {@link MouseOrTabletOperation}, which automatically sets that up for you.
 *
 * <p>For more general kinds of operations you are free to subclass {@link AbstractOperation} instead, or even just
 * implement {@link Operation} directly.
 *
 * <p>There are also more specific base classes you can use:
 *
 * <ul>
 *     <li>{@link AbstractBrushOperation} - for operations that need access to the currently selected brush and
 *     intensity setting.
 *     <li>{@link RadiusOperation} - for operations that perform an action in the shape of the brush.
 *     <li>{@link AbstractPaintOperation} - for operations that apply the currently selected paint in the shape of the
 *     brush.
 * </ul>
 *
 * <p><strong>Note</strong> that for now WorldPainter only supports operations that
 */
public class FlattenPathOperation extends MouseOrTabletOperation implements
        PaintOperation, // Implement this if you need access to the currently selected paint; note that some base classes already provide this
        BrushOperation // Implement this if you need access to the currently selected brush; note that some base classes already provide this
{

    /**
     * The globally unique ID of the operation. It's up to you what to use here. It is not visible to the user. It can
     * be a FQDN or package and class name, like here, or you could use a UUID. As long as it is globally unique.
     */
    static final String ID = "org.demo.wpplugin.FlattenPathOperation.v1";
    /**
     * Human-readable short name of the operation.
     */
    static final String NAME = "Flatten Path Tool";
    /**
     * Human-readable description of the operation. This is used e.g. in the tooltip of the operation selection button.
     */
    static final String DESCRIPTION = "Make the path wide and flat, similar to a road";

    private Brush brush;
    private Paint paint;



    public FlattenPathOperation() {
        // Using this constructor will create a "single shot" operation. The tick() method below will only be invoked
        // once for every time the user clicks the mouse or presses on the tablet:
        super(NAME, DESCRIPTION, ID);
        // Using this constructor instead will create a continues operation. The tick() method will be invoked once
        // every "delay" ms while the user has the mouse button down or continues pressing on the tablet. The "first"
        // parameter will be true for the first invocation per mouse button press and false for every subsequent
        // invocation:
        // super(NAME, DESCRIPTION, delay, ID);
    }


    /**
     * Perform the operation. For single shot operations this is invoked once per mouse-down. For continuous operations
     * this is invoked once per {@code delay} ms while the mouse button is down, with the first invocation having
     * {@code first} be {@code true} and subsequent invocations having it be {@code false}.
     *
     * @param centreX      The x coordinate where the operation should be applied, in world coordinates.
     * @param centreY      The y coordinate where the operation should be applied, in world coordinates.
     * @param inverse      Whether to perform the "inverse" operation instead of the regular operation, if applicable. If the
     *                     operation has no inverse it should just apply the normal operation.
     * @param first        Whether this is the first tick of a continuous operation. For a one shot operation this will always
     *                     be {@code true}.
     * @param dynamicLevel The dynamic level (from 0.0f to 1.0f inclusive) to apply in addition to the {@code level}
     *                     property, for instance due to a pressure sensitive stylus being used. In other words,
     *                     <strong>not</strong> the total level at which to apply the operation! Operations are free to
     *                     ignore this if it is not applicable. If the operation is being applied through a means which
     *                     doesn't provide a dynamic level (for instance the mouse), this will be <em>exactly</em>
     *                     {@code 1.0f}.
     */
    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        //  Perform the operation. In addition to the parameters you have the following methods available:
        // * getDimension() - obtain the dimension on which to perform the operation
        // * getLevel() - obtain the current brush intensity setting as a float between 0.0 and 1.0
        // * isAltDown() - whether the Alt key is currently pressed - NOTE: this is already in use to indicate whether
        //                 the operation should be inverted, so should probably not be overloaded
        // * isCtrlDown() - whether any of the Ctrl, Windows or Command keys are currently pressed
        // * isShiftDown() - whether the Shift key is currently pressed
        // In addition you have the following fields in this class:
        // * brush - the currently selected brush
        // * paint - the currently selected paint
        this.getDimension().setEventsInhibited(true);
        int pathWidth = 3;
        int transitionDist = 0;
        int totalRadius = pathWidth + transitionDist;
        float maxHeightDiff = 0.5f;
        Path path = PathManager.instance.getPathBy(PATH_ID);

        HashSet<Point> seen = new HashSet<>();
        ArrayList<Point> curve = path.continousCurve(point -> !getDimension().getExtent().contains(point));
        LinkedList<Point> edge = new LinkedList<>();
        int totalRadiusSq = totalRadius*totalRadius;
        //collect all points within rough radius
        for (Point p: curve) {
            for (int x = -totalRadius; x<totalRadius; x++) {
                for (int y = -totalRadius; y<totalRadius; y++) {
                    Point edgePoint = new Point(p.x+x, p.y+y);
                    if (edgePoint.distanceSq(p) > totalRadiusSq)
                        continue;
                    if (seen.contains(edgePoint))
                        continue;
                    seen.add(edgePoint);
                    edge.add(edgePoint);
                }
            }
        }

        float[] curveHeights = new float[curve.size()];
        int INVALID_HEIGHT = Integer.MIN_VALUE;
        float lastHeight = INVALID_HEIGHT;
        for (int i = 0; i < curveHeights.length; i++) {
            float curvePointHeight = getDimension().getHeightAt(curve.get(i));

            float targetHeight = curvePointHeight;
            if (lastHeight != INVALID_HEIGHT) {
                if (targetHeight > maxHeightDiff + lastHeight)
                    targetHeight = maxHeightDiff + lastHeight;
                if (targetHeight < lastHeight - maxHeightDiff)
                    targetHeight = lastHeight - maxHeightDiff;
            }
            lastHeight = targetHeight;
            curveHeights[i] = targetHeight;
        }


        for (Point e: edge) {
            //set to same height //TODO smooth transition
            int curveIdx = getClosestPointIndexOnCurveTo(curve, e);

            getDimension().setHeightAt(e, curveHeights[curveIdx]);
        }


        this.getDimension().setEventsInhibited(false);
    }

    private int getClosestPointIndexOnCurveTo(ArrayList<Point> curve, Point nearby) {
        assert !curve.isEmpty();
        Point closest = null;
        int closestIdx = -1;
        double minDistSq = Double.MAX_VALUE;
        int i = 0;
        for (Point p: curve) {
            double thisDistSq = p.distanceSq(nearby);
            if (thisDistSq < minDistSq) {
                closest = p;
                minDistSq = thisDistSq;
                closestIdx = i;
            }
            i++;
        }
        return closestIdx;
    }

    private void applyAsSelection(Path path) {
        Layer select = SelectionBlock.INSTANCE;
        for (Point p : path.continousCurve(point -> !getDimension().getExtent().contains(point))) {
            getDimension().setBitLayerValueAt(select, p.x, p.y, true);
        }
    }

    @Override
    public Brush getBrush() {
        return brush;
    }

    @Override
    public void setBrush(Brush brush) {
        this.brush = brush;
    }

    @Override
    public Paint getPaint() {
        return paint;
    }

    @Override
    public void setPaint(Paint paint) {
        this.paint = paint;
    }
}