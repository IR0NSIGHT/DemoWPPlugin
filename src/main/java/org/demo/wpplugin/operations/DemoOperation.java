package org.demo.wpplugin.operations;

import org.demo.wpplugin.layers.DemoLayer;
import org.pepsoft.worldpainter.brushes.Brush;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.operations.*;
import org.pepsoft.worldpainter.painting.Paint;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

import static org.demo.wpplugin.PointUtils.*;

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
public class DemoOperation extends MouseOrTabletOperation implements
        PaintOperation, // Implement this if you need access to the currently selected paint; note that some base classes already provide this
        BrushOperation // Implement this if you need access to the currently selected brush; note that some base classes already provide this
{
    public DemoOperation() {
        // Using this constructor will create a "single shot" operation. The tick() method below will only be invoked
        // once for every time the user clicks the mouse or presses on the tablet:
        super(NAME, DESCRIPTION, ID);
        // Using this constructor instead will create a continues operation. The tick() method will be invoked once
        // every "delay" ms while the user has the mouse button down or continues pressing on the tablet. The "first"
        // parameter will be true for the first invocation per mouse button press and false for every subsequent
        // invocation:
        // super(NAME, DESCRIPTION, delay, ID);
    }

    private Collection<Point> path = new ArrayList<>();

    public static Collection<Point> calculateCubicBezier(Point startPoint, Point handle0, Point handle1, Point endPoint, int numPoints) {
        Collection<Point> points = new ArrayList<>();
        for (int i = 0; i <= numPoints; i++) {
            double t = (double) i / numPoints;
            double x = Math.pow(1 - t, 3) * startPoint.x
                    + 3 * Math.pow(1 - t, 2) * t * handle0.x
                    + 3 * (1 - t) * Math.pow(t, 2) * handle1.x
                    + Math.pow(t, 3) * endPoint.x;
            double y = Math.pow(1 - t, 3) * startPoint.y
                    + 3 * Math.pow(1 - t, 2) * t * handle0.y
                    + 3 * (1 - t) * Math.pow(t, 2) * handle1.y
                    + Math.pow(t, 3) * endPoint.y;
            points.add(new Point((int) Math.round(x), (int) Math.round(y)));
        }
        return points;
    }

    public static double calculatePathLength(Point[] path) {
        if (path == null || path.length < 2) {
            throw new IllegalArgumentException("Path must contain at least two points.");
        }
        double totalLength = 0.0;
        for (int i = 1; i < path.length; i++) {
            totalLength += path[i - 1].distance(path[i]);
        }
        return totalLength;
    }

    void DrawPathLayer(Point[] path) {
        DemoLayer layer = DemoLayer.INSTANCE;
        for (Point p : path) {
            markPoint(p, layer, 15, 10);
        }

        for (int i = 0; i < path.length - 3; i++) {
            for (Point p : getSplineFor(path[i], path[i + 1], path[i + 2], path[i + 3]))
                markPoint(p, DemoLayer.INSTANCE, 15, 2);
        }
    }

    /**
     * get spline connecting B and C. A and D are the points before and after BC in the path
     *
     * @param A
     * @param B
     * @param C
     * @param D
     */
    Collection<Point> getSplineFor(Point A, Point B, Point C, Point D) {
        float factor = (float) B.distance(C) / 2f;
        Point handle1 = add(multiply(normalize(divide(subtract(C, A), 2)), factor), B); // (AC)/2+B
        Point handle2 = subtract(C, multiply(normalize(divide(subtract(D, B), 2)), factor)); //(C-(BD/2))
        Collection<Point> path = calculateCubicBezier(B, handle1, handle2, C, 50);
        double length = calculatePathLength(path.toArray(new Point[0]));
        float metersBetweenPoints = 10;
        path = calculateCubicBezier(B, handle1, handle2, C, (int) (length / metersBetweenPoints));
        return path;
    }

    void markPoint(Point p, Layer layer, int value, int size) {
        for (int i = -size; i <= size; i++) {
            getDimension().setLayerValueAt(layer, p.x + i, p.y - i, value);
            getDimension().setLayerValueAt(layer, p.x + i, p.y + i, value);
        }
    }

    /**
     * Perform the operation. For single shot operations this is invoked once per mouse-down. For continuous operations
     * this is invoked once per {@code delay} ms while the mouse button is down, with the first invocation having
     * {@code first} be {@code true} and subsequent invocations having it be {@code false}.
     *
     * @param centreX The x coordinate where the operation should be applied, in world coordinates.
     * @param centreY The y coordinate where the operation should be applied, in world coordinates.
     * @param inverse Whether to perform the "inverse" operation instead of the regular operation, if applicable. If the
     *                operation has no inverse it should just apply the normal operation.
     * @param first Whether this is the first tick of a continuous operation. For a one shot operation this will always
     *              be {@code true}.
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

        if (inverse) {
            path.clear();
            getDimension().clearLayerData(DemoLayer.INSTANCE);
        } else {
            Point next = new Point(centreX, centreY);
            path.add(next);
            System.out.println("path = " + path);
            DrawPathLayer(path.toArray(new Point[0]));
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

    private Brush brush;
    private Paint paint;

    /**
     * The globally unique ID of the operation. It's up to you what to use here. It is not visible to the user. It can
     * be a FQDN or package and class name, like here, or you could use a UUID. As long as it is globally unique.
     */
    static final String ID = "org.demo.wpplugin.DemoOperation.v1";

    /**
     * Human-readable short name of the operation.
     */
    static final String NAME = "Demo Operation";

    /**
     * Human-readable description of the operation. This is used e.g. in the tooltip of the operation selection button.
     */
    static final String DESCRIPTION = "A demonstration of creating a custom operation plugin for WorldPainter";
}