package org.ironsight.wpplugin.rivertool.operations.ApplyPath;

import org.ironsight.wpplugin.rivertool.Gui.OperationOptionsPanel;
import org.ironsight.wpplugin.rivertool.geometry.HeightDimension;
import org.ironsight.wpplugin.rivertool.operations.ContinuousCurve;
import org.ironsight.wpplugin.rivertool.operations.EditPath.EditPathOperation;
import org.ironsight.wpplugin.rivertool.Gui.OptionsLabel;
import org.ironsight.wpplugin.rivertool.operations.River.RiverHandleInformation;
import org.ironsight.wpplugin.rivertool.pathing.Path;
import org.ironsight.wpplugin.rivertool.pathing.PathGeometryHelper;
import org.ironsight.wpplugin.rivertool.pathing.PathManager;
import org.ironsight.wpplugin.rivertool.pathing.RingFinder;
import org.pepsoft.worldpainter.operations.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.function.Function;

import static java.lang.Math.max;
import static java.lang.Math.min;

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
public class ApplyRiverOperation extends MouseOrTabletOperation {
    /**
     * The globally unique ID of the operation. It's up to you what to use here. It is not visible to the user. It can
     * be a FQDN or package and class name, like here, or you could use a UUID. As long as it is globally unique.
     */
    static final String ID = "orig.ironsight.wpplugin.rivertool.applyRiverOperation.v1";
    /**
     * Human-readable short name of the operation.
     */
    static final String NAME = "Apply River Operation";
    /**
     * Human-readable description of the operation. This is used e.g. in the tooltip of the operation selection button.
     */
    static final String DESCRIPTION =
            "<html>Apply river to this world<br>Last selected path gets applied into the " + "world.<br>Potentially " +
                    "slow and expensive</html>";
    private final ApplyPathOptions options = new ApplyPathOptions(0, 1);
    private final StandardOptionsPanel optionsPanel = new StandardOptionsPanel(getName(), getDescription()) {
        @Override
        protected void addAdditionalComponents(GridBagConstraints constraints) {
            add(new ApplyPathOptionsPanel(options), constraints);
        }
    };

    public ApplyRiverOperation() {
        super(NAME, DESCRIPTION, ID);
    }

    public static double angleOf(int x, int y) {
        return Math.atan(1f * y / x);
    }

    /**
     * return v1 if point = min, v2 if point = max, linear interpolate otherwise
     *
     * @param point
     * @param min
     * @param max
     * @param v1
     * @param v2
     * @return
     */
    private static float modifyValue(float point, float min, float max, float v1, float v2) {
        float y = getCubicInterpolation(point, min, max);
        return (1 - y) * v1 + (y) * v2;
    }

    /**
     * @param point
     * @param min
     * @param max
     * @return 1 if point = max, 0 if point = min, else linear interpolate
     */
    private static float getCubicInterpolation(float point, float min, float max) {
        float x = (point - min);
        float width = max - min;
        return x / width;
    }

    public static void applyRiverPath(Path path, ApplyPathOptions options, HeightDimension dimension,
                                      HeightDimension waterMap) {
        ContinuousCurve curve = ContinuousCurve.fromPath(path, dimension);
        float randomPercent = (float) options.randomFluctuate / 100f;
        float[] randomEdge = randomEdge(curve.curveLength());

        double fluctuationSpeed = options.fluctuationSpeed;
        fluctuationSpeed = max(1, fluctuationSpeed);    //no divide by zero

        float[] maxHandleValues = getMaxValues(path, path.type.size);

        double totalSearchRadius = RiverHandleInformation.getValue(maxHandleValues, RiverHandleInformation.RiverInformation.RIVER_RADIUS) + RiverHandleInformation.getValue(maxHandleValues, RiverHandleInformation.RiverInformation.BEACH_RADIUS);
        PathGeometryHelper helper = new PathGeometryHelper(path, new ArrayList<>(Arrays.asList(curve.getPositions2d())),
                totalSearchRadius);
        HashMap<Point, Collection<Point>> parentage = helper.getParentage();

        HashMap<Point, Float> finalHeightmap = new HashMap<>();

        Function<float[], Float> riverDepthByDistance = (depthAndDist) -> {
            float a, b, c;
            float depth = depthAndDist[0];
            float width = depthAndDist[2];
            float x = depthAndDist[1];

            a = -depth / (width * width);
            b = 0;
            c = depth;
            return a * (x * x) + b * x + c;
        };
        float[] waterZs = Path.interpolateWaterZ(curve, dimension);
        for (int curveIdx = 0; curveIdx < curve.curveLength(); curveIdx++) {
            Point curvePoint = curve.getPositions2d()[curveIdx];
            Collection<Point> nearby = parentage.get(curvePoint);

            float randomFluxAtIdx = randomEdge[(int) ((curveIdx) / fluctuationSpeed)];
            double riverRadius = curve.getInfo(RiverHandleInformation.RiverInformation.RIVER_RADIUS, curveIdx) * (1 + randomFluxAtIdx * randomPercent);
            double beachRadius = curve.getInfo(RiverHandleInformation.RiverInformation.BEACH_RADIUS, curveIdx);
            float waterHeight = waterZs[curveIdx];

            // Transition, becuase the gauss smoother can only do one value


            //FIXME: parentage is problematic: clostest point doenst guarentee to be the right parent on rivers that
            // grow fast and are very curvy
            // -> point in the transition layer chose the wrong parent. instead we need to test for the closest point
            // on outermost beach layer!
            for (Point point : nearby) {
                double distance = point.distance(curvePoint);
                if (distance < riverRadius) {
                    float[] params = new float[]{
                            -curve.getInfo(RiverHandleInformation.RiverInformation.RIVER_DEPTH, curveIdx) - 0.1f,
                            (float) distance,
                            (float) riverRadius};
                    float riverBedHeight = riverDepthByDistance.apply(params);
                    finalHeightmap.put(point, waterHeight + riverBedHeight);
                    waterMap.setHeight(point.x, point.y, waterHeight);
                } else if (distance - riverRadius <= beachRadius) {
                    finalHeightmap.put(point, waterHeight);
                    waterMap.setHeight(point.x, point.y, waterHeight);
                }
            }
        }


        float maxTransition = (RiverHandleInformation.getValue(maxHandleValues, RiverHandleInformation.RiverInformation.TRANSITION_RADIUS));

        //add transitions rings
        int amountRings = Math.round(maxTransition);
        RingFinder ringFinder = new RingFinder(finalHeightmap, amountRings, dimension);
        for (int i = 1; i < amountRings; i++) {
            for (Point p : ringFinder.ring(i).keySet()) {
                float beachHeight = ringFinder.ring(i).get(p);
                float t = (1f * i) / (amountRings - 1);
                float interpolate = (1 - t) * beachHeight + (t) * dimension.getHeight(p.x, p.y);
                dimension.setHeight(p.x, p.y, interpolate);
            }
        }

        //apply beach and river heights
        for (Point p : finalHeightmap.keySet()) {
            dimension.setHeight(p.x, p.y, finalHeightmap.get(p));
        }
    }

    public static float[] randomEdge(int length) {
        Random rand = new Random(420);
        float[] randomEdge = new float[length];
        float randomWidth = 0;
        for (int i = 0; i < randomEdge.length; i++) {
            randomWidth += ((rand.nextBoolean() ? 1f : -1f) * rand.nextFloat() * 0.3f);
            randomWidth = max(randomWidth, -1);
            randomWidth = min(randomWidth, 1);
            randomEdge[i] = randomWidth;
        }
        return randomEdge;
    }

    /**
     * collect all max values into a single point
     * out[n] = Max(handles[all i][n])
     *
     * @param handles
     * @return
     */
    public static float[] getMaxValues(Iterable<float[]> handles, int handleSize) {
        float[] maxHandleValues = new float[handleSize];
        for (float[] handle : handles) {
            for (int n = RiverHandleInformation.PositionSize.SIZE_2_D.value; n < handle.length; n++) {
                maxHandleValues[n] = max(maxHandleValues[n], handle[n]);
            }
        }
        return maxHandleValues;
    }

    @Override
    public JPanel getOptionsPanel() {
        return optionsPanel;
    }

    /**
     * Perform the operation. For single shot operations this is invoked once per mouse-down. For continuous operations
     * this is invoked once per {@code delay} ms while the mouse button is down, with the first invocation having
     * {@code first} be {@code true} and subsequent invocations having it be {@code false}.
     *
     * @param centreX      The x coordinate where the operation should be applied, in world coordinates.
     * @param centreY      The y coordinate where the operation should be applied, in world coordinates.
     * @param inverse      Whether to perform the "inverse" operation instead of the regular operation, if applicable
     *                     . If the
     *                     operation has no inverse it should just apply the normal operation.
     * @param first        Whether this is the first tick of a continuous operation. For a one shot operation this
     *                     will always
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
        try {
            Path path = PathManager.instance.getPathBy(EditPathOperation.PATH_ID);
            assert path != null : "Pathmanager delivered null path";
            HeightDimension dim = new HeightDimension() {
                @Override
                public float getHeight(int x, int y) {
                    return getDimension().getHeightAt(x, y);
                }

                @Override
                public void setHeight(int x, int y, float z) {
                    getDimension().setHeightAt(x, y, z);
                }
            };
            HeightDimension water = new HeightDimension() {
                @Override
                public float getHeight(int x, int y) {
                    return getDimension().getWaterLevelAt(x, y);
                }

                @Override
                public void setHeight(int x, int y, float z) {
                    getDimension().setWaterLevelAt(x, y, Math.round(z));
                }
            };
            applyRiverPath(path, options, dim, water);
        } catch (Exception ex) {
            System.err.println(ex);
        } finally {
            this.getDimension().setEventsInhibited(false);
        }
    }

    private static class ApplyPathOptionsPanel extends OperationOptionsPanel<ApplyPathOptions> {
        public ApplyPathOptionsPanel(ApplyPathOptions panelOptions) {
            super(panelOptions);
        }

        @Override
        protected ArrayList<OptionsLabel> addComponents(ApplyPathOptions options, Runnable onOptionsReconfigured) {
            ArrayList<OptionsLabel> inputs = new ArrayList<>();

            inputs.add(OptionsLabel.numericInput("random width", "each step the rivers radius will randomly increase or decrease. " +
                            "It will stay within +/- percent " + "of the normal width.",
                    new SpinnerNumberModel(options.randomFluctuate, 0, 100, 1f),
                    w -> options.randomFluctuate = w.intValue(), onOptionsReconfigured));

            inputs.add(OptionsLabel.numericInput("fluctuation speed", "how fast the random fluctuation appears. low number = less " +
                            "extreme change", new SpinnerNumberModel(options.fluctuationSpeed, 0, 100, 1f),
                    w -> options.fluctuationSpeed = w.intValue(), onOptionsReconfigured));

            return inputs;
        }
    }
}