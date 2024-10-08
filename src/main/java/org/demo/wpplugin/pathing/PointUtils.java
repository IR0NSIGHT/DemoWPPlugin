package org.demo.wpplugin.pathing;

import org.demo.wpplugin.geometry.AxisAlignedBoundingBox2d;
import org.demo.wpplugin.geometry.PaintDimension;
import org.demo.wpplugin.operations.River.RiverHandleInformation;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PointUtils {

    public static Rectangle pointExtent(Rectangle extent) {
        Rectangle rect = new Rectangle(extent);
        rect.setBounds(extent.x * 128, extent.y * 128, extent.width * 128, extent.height * 128);
        return rect;
    }

    // Method to add two points
    public static Point add(Point p1, Point p2) {
        return new Point(p1.x + p2.x, p1.y + p2.y);
    }

    // Method to subtract two points
    public static Point subtract(Point p1, Point p2) {
        return new Point(p1.x - p2.x, p1.y - p2.y);
    }

    // Method to multiply a point by a factor
    public static Point multiply(Point p, float factor) {
        return new Point(Math.round(p.x * factor), Math.round(p.y * factor));
    }

    // Method to divide a point by a factor
    public static Point divide(Point p, float factor) {
        if (factor == 0) {
            throw new IllegalArgumentException("Division by zero is not allowed.");
        }
        return new Point(Math.round(p.x / factor), Math.round(p.y / factor));
    }

    // Method to normalize a point
    public static Point normalize(Point p) {
        float magnitude = (float) Math.sqrt(p.x * p.x + p.y * p.y);
        if (magnitude == 0) {
            throw new IllegalArgumentException("Cannot normalize a point at the origin (0,0).");
        }
        return divide(p, magnitude);
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

    public static ArrayList<AxisAlignedBoundingBox2d> toBoundingBoxes(ArrayList<Point> curve, int boxSizeFactor,
                                                                      double radius) {
        ArrayList<AxisAlignedBoundingBox2d> bbxs = new ArrayList<>(curve.size() / boxSizeFactor + 1);
        for (int i = 0; i < curve.size(); i += boxSizeFactor) {
            int boxId = i / boxSizeFactor;
            List<Point> subcurve = curve.subList(i, Math.min(i + boxSizeFactor, curve.size()));
            AxisAlignedBoundingBox2d box = new AxisAlignedBoundingBox2d(subcurve, boxId).expand(radius);
            bbxs.add(box);
            assert box.id == boxId;
        }
        return bbxs;
    }

    public static void drawCircle(Point center, float radius, PaintDimension dimension, boolean dotted) {
        for (int theta = 0; theta < 360; theta += 10) {
            int x = (int) Math.round(radius * Math.cos(theta));
            int y = (int) Math.round(radius * Math.sin(theta));
            dimension.setValue(center.x + x, center.y + y, 15);
        }
    }

    /**
     * draws an X on the map in given color and size
     *
     * @param p
     * @param color
     * @param size, 0 size = single dot on map
     */
    public static void markPoint(Point p, int color, int size, PaintDimension dim) {
        for (int i = -size; i <= size; i++) {
            dim.setValue(p.x + i, p.y - i, color);
            dim.setValue(p.x + i, p.y + i, color);
        }
    }

    public static void markLine(Point p0, Point p1, int color, PaintDimension dim) {
        double length = p0.distance(p1);
        for (double i = 0; i <= length; i++) {
            double factor = i / length;
            Point inter = new Point((int) (p0.x * factor + p1.x * (1 - factor)),
                    (int) (p0.y * factor + p1.y * (1 - factor)));
            dim.setValue(inter.x, inter.y, color);
        }
    }

    static float getPositionalLength(float[] pointA, int positionDigits) {
        //euclidian distance of B and C positions
        float dist = 0;
        for (int i = 0; i < positionDigits; i++) {
            float distI = pointA[i];
            dist += distI * distI;
        }
        dist = (float) Math.sqrt(dist);
        return dist;
    }

    public static float getPositionalDistance(float[] pointA, float[] pointB, int positionDigits) {
        assert pointA != null;
        assert pointB != null;
        //euclidian distance of B and C positions
        float dist = 0;
        for (int i = 0; i < positionDigits; i++) {
            float distI = pointA[i] - pointB[i];
            dist += distI * distI;
        }
        dist = (float) Math.sqrt(dist);
        assert !Float.isNaN(dist) : "distance between " + Arrays.toString(pointA) + " and " + Arrays.toString(pointB);
        return dist;
    }

    public static boolean arePositionalsEqual(float[] pointA, float[] pointB, int positionDigits) {
        return getPoint2D(pointA).equals(getPoint2D(pointB));
    }

    public static Point getPoint2D(float[] nVector) {
        if (nVector == null)
            return null;
        return new Point(Math.round(nVector[0]), Math.round(nVector[1]));
    }

    public static ArrayList<Point> point2DfromNVectorArr(ArrayList<float[]> points) {
        ArrayList<Point> out = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            out.add(getPoint2D(points.get(i)));
        }
        return out;
    }

    public static float[] setPosition2D(float[] point, float[] position) {
        float[] out = point.clone();
        if (RiverHandleInformation.PositionSize.SIZE_2_D.value >= 0)
            System.arraycopy(position, 0, out, 0, RiverHandleInformation.PositionSize.SIZE_2_D.value);
        return out;
    }
}
