package org.demo.wpplugin.operations.River;

import org.demo.wpplugin.geometry.HeightDimension;
import org.demo.wpplugin.geometry.PaintDimension;
import org.demo.wpplugin.layers.renderers.DemoLayerRenderer;
import org.demo.wpplugin.operations.OptionsLabel;
import org.demo.wpplugin.pathing.Path;
import org.demo.wpplugin.pathing.PointInterpreter;
import org.demo.wpplugin.pathing.PointUtils;
import org.pepsoft.worldpainter.App;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

import static org.demo.wpplugin.operations.ApplyPath.ApplyRiverOperation.angleOf;
import static org.demo.wpplugin.operations.EditPath.EditPathOperation.*;
import static org.demo.wpplugin.operations.OptionsLabel.numericInput;
import static org.demo.wpplugin.operations.River.RiverHandleInformation.RiverInformation.*;
import static org.demo.wpplugin.pathing.PointInterpreter.PointType.RIVER_2D;
import static org.demo.wpplugin.pathing.PointUtils.getPoint2D;
import static org.demo.wpplugin.pathing.PointUtils.markLine;

public class RiverHandleInformation {
    public static final float INHERIT_VALUE = -1;

    public static float getValue(float[] point, RiverInformation information) {
        return point[PositionSize.SIZE_2_D.value + information.idx];
    }

    public static float[] setValue(float[] point, RiverInformation information, float value) {
        float[] out = point.clone();
        out[PositionSize.SIZE_2_D.value + information.idx] = value;
        return out;
    }

    public static float[] riverInformation(int x, int y, float riverRadius, float riverDepth, float beachRadius,
                                           float transitionRadius, float waterZ) {
        float[] out = new float[RIVER_2D.size];
        out[0] = x;
        out[1] = y;
        out = setValue(out, RiverInformation.RIVER_RADIUS, riverRadius);
        out = setValue(out, RiverInformation.RIVER_DEPTH, riverDepth);
        out = setValue(out, RiverInformation.BEACH_RADIUS, beachRadius);
        out = setValue(out, RiverInformation.TRANSITION_RADIUS, transitionRadius);
        out = setValue(out, RiverInformation.WATER_Z, waterZ);
        return out;
    }

    /**
     * @param x
     * @param y
     * @return a handle with given position and the rest of meta values set to INHERIT
     */
    public static float[] riverInformation(int x, int y) {
        return riverInformation(x, y, INHERIT_VALUE, INHERIT_VALUE, INHERIT_VALUE, INHERIT_VALUE, INHERIT_VALUE);
    }

    public static float[] positionInformation(float x, float y, PointInterpreter.PointType type) {
        float[] point = new float[type.size];
        point[0] = x;
        point[1] = y;
        return point;
    }


    public static BufferedImage toImage(HeightDimension dim, int width, int height) {

        // Create a BufferedImage with width, height and type (TYPE_INT_RGB is common for RGB images)
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Loop over every pixel and manipulate it (here we're creating a gradient)
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Example: creating a color gradient from black to blue
                int z = Math.round(dim.getHeight(x, y));

                Color color = z == 0 ? new Color(255, 0, 0) : new Color(z, z, z);

                // Set pixel color at (x, y)
                image.setRGB(x, height - 1 - y, color.getRGB());
            }
        }

        return image;
    }


    public static boolean validateRiver2D(ArrayList<float[]> handles) {
        float lastWaterZ = Float.MAX_VALUE;
        for (float[] handle : handles) {
            if (handle.length != RIVER_2D.size) {
                return false;
            }
            for (RiverInformation information : RiverInformation.values()) {
                float value = getValue(handle, information);
                if (value != INHERIT_VALUE && (value < information.min || value > information.max)) {
                    return false;
                }
            }

            if (getValue(handle, WATER_Z) != INHERIT_VALUE) {
                if (lastWaterZ < getValue(handle, WATER_Z)) {
                    //the river can only flow downhill, not uphill
                    return false;
                }

                lastWaterZ = getValue(handle, WATER_Z);
            }

        }
        return true;
    }

    public static HeightDimension curve1D(ArrayList<float[]> curve,
                                          RiverHandleInformation.RiverInformation riverInformation) {
        HeightDimension dim = new HeightDimension() {
            final HashMap<Point, Float> heightMap = new HashMap<>();

            @Override
            public float getHeight(int x, int y) {
                return heightMap.getOrDefault(new Point(x, y), 0f);
            }

            @Override
            public void setHeight(int x, int y, float z) {
                heightMap.put(new Point(x, y), z);
            }
        };
        for (int i = 0; i < curve.size(); i++) {
            float value = getValue(curve.get(i), riverInformation);
            for (int z = 0; z < value; z++)
                dim.setHeight(i, z, 255);
        }
        return dim;
    }

    public static JDialog riverRadiusEditor(Path path) {
        JFrame parent = App.getInstance();
        JDialog dialog = new JDialog(parent, "Dialog Title", true); // Modal
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Only close the dialog

        // Create a custom panel that will display the image
        ArrayList<float[]> curve = path.continousCurve();

   /*     // Draw gridlines every x=100
        for (int x = 0; x < curveImg.getWidth(); x += 100) {
            for (int y = 0; y < curveImg.getHeight(); y += 2) {
                curveImg.setRGB(x, y, Color.BLACK.getRGB());
            }
        }

        // Horizontal gridlines
        for (int y = curveImg.getHeight() - 1; y > 0; y -= 100) {
            for (int x = 0; x < curveImg.getWidth(); x += 2) {
                curveImg.setRGB(x, y, Color.BLACK.getRGB());
            }
        }
*/
        // Calculate dialog size as a percentage of the screen size
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int dialogWidth = (int) (screenSize.width * 0.5); // 50% of screen width
        int dialogHeight = (int) (screenSize.height * 0.5); // 50% of screen height

        float scale = dialogWidth * 1f / curve.size();
        int resizedWidth = dialogWidth;
        int resizedHeight = 255;
        BufferedImage resizedImage = new BufferedImage(resizedWidth, resizedHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.translate(0, resizedHeight);  // Move the origin to the bottom-left
        graphics2D.scale(1, -1);  // Flip the y-axis
        graphics2D.setColor(Color.WHITE);
        for (int i = 1; i < curve.size(); i++) {
            float aZ = getValue(curve.get(i - 1), WATER_Z);
            float bZ = getValue(curve.get(i), WATER_Z);


            graphics2D.drawLine(i - 1, (int) aZ, i, (int) bZ);
        }

        float[] dashPattern = {10, 5};
        graphics2D.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashPattern, 0));
        int[] handleToCurve = path.handleToCurveIdx(true);
        for (Integer i : handleToCurve) {
            graphics2D.drawLine(i, 0, i, 30);

        }
        graphics2D.dispose();


        // Create an ImageIcon from the BufferedImage
        ImageIcon imageIcon = new ImageIcon(resizedImage);

        // Create a JLabel to display the image
        JPanel imageLabel = new PathHistogram(path, WATER_Z);
        imageLabel.setSize(dialogWidth, dialogHeight);
        // Add the JLabel to the dialog
        dialog.add(imageLabel);

        // Get the screen dimensions


        // Set the dialog size
        dialog.setSize(dialogWidth, dialogHeight);

        // Optionally, center the dialog on the screen
        dialog.setLocationRelativeTo(parent);

        // Pack the dialog to ensure proper layout
        dialog.pack();

        return dialog;
    }

    public static OptionsLabel[] Editor(float[] point, Consumer<float[]> onSubmitCallback, Runnable onChanged) {
        OptionsLabel[] options = new OptionsLabel[RiverInformation.values().length];
        int i = 0;
        for (RiverInformation information : RiverInformation.values()) {
            SpinnerNumberModel model = new SpinnerNumberModel(getValue(point, information), INHERIT_VALUE,
                    information.max, 1f);

            options[i++] = numericInput(information.displayName,
                    information.toolTip,
                    model,
                    newValue -> {
                        onSubmitCallback.accept(setValue(point, information, newValue));
                    },
                    onChanged
            );
        }
        return options;
    }

    public static void DrawRiverPath(Path path, PaintDimension dim, int selectedIdx) throws IllegalAccessException {
        if (path.type != RIVER_2D)
            throw new IllegalArgumentException("path is not river: " + path.type);
        if (path.amountHandles() < 4) {

        } else {
            ArrayList<float[]> curve = path.continousCurve(true);
            int[] curveIdxHandles = path.handleToCurveIdx(true);

            int startIdx = curveIdxHandles[Math.min(Math.max(0, selectedIdx - 2), curveIdxHandles.length - 1)];
            int endIdx = curveIdxHandles[Math.min(Math.max(0, selectedIdx + 2), curveIdxHandles.length - 1)];

            float[] riverPosition_X = new float[curve.size()];
            float[] riverPosition_Y = new float[curve.size()];
            for (int i = 0; i < curve.size(); i++) {
                float[] p = curve.get(i);
                riverPosition_X[i] = p[0];
                riverPosition_Y[i] = p[1];
            }


            for (int i = 1; i < curve.size() - 1; i++) {
                float[] p = curve.get(i);
                int color = startIdx < i && i < endIdx ? DemoLayerRenderer.Dark_Cyan : COLOR_CURVE;
                PointUtils.markPoint(getPoint2D(p), COLOR_CURVE, SIZE_DOT, dim);

                for (RiverInformation info : new RiverInformation[]{RIVER_RADIUS, BEACH_RADIUS, TRANSITION_RADIUS}) {
                    float radius = getValue(p, info);
                    Point curvePointP = getPoint2D(p);

                    int tangentX = Math.round(curve.get(i + 1)[0] - curve.get(i - 1)[0]);
                    int tangentY = Math.round(curve.get(i + 1)[1] - curve.get(i - 1)[1]);
                    double tangentAngle = angleOf(tangentX, tangentY);

                    int x = (int) Math.round(radius * Math.cos(tangentAngle + Math.toRadians(90)));
                    int y = (int) Math.round(radius * Math.sin(tangentAngle + Math.toRadians(90)));
                    dim.setValue(curvePointP.x + x, curvePointP.y + y, color);

                    x = (int) Math.round(radius * Math.cos(tangentAngle + Math.toRadians(-90)));
                    y = (int) Math.round(radius * Math.sin(tangentAngle + Math.toRadians(-90)));
                    dim.setValue(curvePointP.x + x, curvePointP.y + y, color);
                    color = color + 1 % 15;
                }
            }
        }


        if (path.amountHandles() > 1) {
            markLine(getPoint2D(path.handleByIndex(0)), getPoint2D(path.handleByIndex(1)), COLOR_HANDLE, dim);
            markLine(getPoint2D(path.getTail()), getPoint2D(path.getPreviousPoint(path.getTail())), COLOR_HANDLE, dim);
        }

        for (int i = 0; i < path.amountHandles(); i++) {
            float[] handle = path.handleByIndex(i);
            int size = SIZE_MEDIUM_CROSS;
            PointUtils.markPoint(getPoint2D(handle), DemoLayerRenderer.Yellow, size,
                    dim);

            //RIVER RADIUS
            if (!(getValue(handle, RIVER_RADIUS) == INHERIT_VALUE))
                PointUtils.drawCircle(getPoint2D(handle), getValue(handle, RIVER_RADIUS), dim,
                        getValue(handle, RIVER_RADIUS) == RiverHandleInformation.INHERIT_VALUE);

        }
    }

    public enum RiverInformation {
        RIVER_RADIUS(0, "river radius", "radius of the river ", 0, 1000),
        RIVER_DEPTH(1, "river depth", "depth of the river ", 0, 1000),
        BEACH_RADIUS(2, "beach radius", "radius of the beach ", 0, 1000),
        TRANSITION_RADIUS(3, "transition radius", "radius of the transition blending with original terrain ", 0, 1000),
        WATER_Z(4, "water level", "water level position on z axis", 0, 1000);
        public final int idx;
        public final String displayName;
        public final String toolTip;
        public final float min;
        public final float max;

        RiverInformation(int idx, String displayName, String toolTip, float min, float max) {
            this.min = min;
            this.max = max;
            this.displayName = displayName;
            this.toolTip = toolTip;
            this.idx = idx;
        }
    }

    public enum PositionSize {
        SIZE_1_D(1),
        SIZE_2_D(2),
        SIZE_3_D(3);
        public final int value;

        PositionSize(int idx) {
            this.value = idx;
        }
    }

    private static class PathHistogram extends JPanel {
        private final Path path;
        private final RiverInformation information;

        PathHistogram(Path path, RiverHandleInformation.RiverInformation riverInformation) {
            super(new BorderLayout());
            this.path = path;
            this.information = riverInformation;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            ArrayList<float[]> curve = path.continousCurve(true);

            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(Color.BLACK);
            g2d.drawRect(0, 0, getWidth(), getHeight());
            g2d.translate(0, getHeight());  // Move the origin to the bottom-left
            g2d.scale(1, -1);  // Flip the y-axis
            float scale = getWidth() * 1f / curve.size();
            g2d.scale(scale, scale);
            g2d.setColor(Color.BLACK);
            for (int i = 1; i < curve.size(); i++) {
                float aZ = getValue(curve.get(i - 1), WATER_Z);
                float bZ = getValue(curve.get(i), WATER_Z);
                g2d.drawLine(i - 1, (int) aZ, i, (int) bZ);
            }

            float[] dashPattern = {10, 5};
            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashPattern, 0));
            int[] handleToCurve = path.handleToCurveIdx(true);
            for (Integer i : handleToCurve) {
                g2d.drawLine(i, 0, i, 30);

            }
        }

        @Override
        public Dimension getPreferredSize() {
            // Get screen dimensions
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

            // Use a fraction of the screen size, e.g., 70% width and 40% height
            int width = (int) (screenSize.width * 0.7);
            int height = (int) (screenSize.height * 0.4);

            // Return the dynamically calculated size
            return new Dimension(width, height);
        }
    }
}
