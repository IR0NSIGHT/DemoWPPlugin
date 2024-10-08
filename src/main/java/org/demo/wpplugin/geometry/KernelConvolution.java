package org.demo.wpplugin.geometry;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class KernelConvolution {
    final HashMap<Point, Float> xSmoothedPoints;
    final int radius;
    final HeightDimension dimension;
    final HashSet<Point> points;
    final Collection<Point> corePoints;

    public KernelConvolution(Collection<Point> points, int radius, HeightDimension dimension) {
        this.radius = radius;
        this.xSmoothedPoints = new HashMap<>(points.size());
        this.dimension = dimension;
        this.points = new HashSet<>(points);
        this.corePoints = points;
        for (Point point : points) {
            for (int y = -radius; y <= radius; y++) {
                this.points.add(new Point(point.x, point.y + y));
            }
        }
    }

    /**
     * Generates a Gaussian curve as a float array.
     *
     * @param size      Number of points in the curve
     * @param maxHeight Maximum height of the curve (A in the Gaussian function)
     * @param mean      The mean (center) of the curve (μ in the Gaussian function)
     * @param stdDev    The standard deviation (width) of the curve (σ in the Gaussian function)
     * @return A float array representing the Gaussian curve
     */
    public static float[] generateGaussianCurve(int size, float maxHeight, float mean, float stdDev) {
        float[] curve = new float[size];

        // Generate the Gaussian curve
        for (int i = 0; i < size; i++) {
            // Calculate the x value for each point
            float x = i;

            // Apply the Gaussian formula
            float value = (float) (maxHeight * Math.exp(-Math.pow(x - mean, 2) / (2 * Math.pow(stdDev, 2))));

            // Store the value in the curve array
            curve[i] = value;
        }

        return curve;
    }

    public static float[] calculateGradient(float[] input) {
        int kernelWidth = 2;
        float[] kernel = new float[kernelWidth * 2 + 1];
        for (int i = 0; i < kernelWidth; i++) {
            kernel[i] = -1f / (2 * (kernelWidth - i));
            kernel[kernel.length - 1 - i] = -kernel[i];
        }
        kernel[kernelWidth] = 0f;

        //Normalize kernel
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] = kernel[i] / kernelWidth;
        }

        float[] gradients = new float[input.length];

        if (input.length < kernelWidth * 2 + 1) {
            //its pointless because its not wide enough to use the kernel even once
            return gradients;
        }

        for (int i = kernelWidth; i < input.length - kernelWidth; i++) {
            float gradient = 0;
            for (int j = -kernelWidth; j < kernelWidth + 1; j++) {
                int inputIdx = i + j;
                float kernelI = kernel[j + kernelWidth];
                float inputI = input[inputIdx];
                gradient += kernelI * inputI;
            }

            gradients[i] = gradient;
        }
        //spread first couple and last couple gradients to fill unset ones
        for (int i = 0; i < kernelWidth; i++) {
            gradients[i] = gradients[kernelWidth];  //use first calculated gradients in all before
        }
        for (int i = gradients.length - 1; i > gradients.length - kernelWidth - 1; i--) {
            gradients[i] = gradients[gradients.length - 1 - kernelWidth];
        }
        return gradients;
    }

    public void smoothAverage() {
        float[] kernel = new float[2 * radius + 1];
        float sum = kernel.length;
        Arrays.fill(kernel, 1);

        smoothPoints(kernel, sum);
    }

    public void smoothGauss() {
        float[] kernel = radius <= 0 ? new float[]{1} : generateGaussianCurve(2 * radius + 1, 10, radius, radius / 2f);
        float sum = 0;

        for (int i = 0; i < kernel.length; i++) {
            kernel[i] = (float) Math.sqrt(kernel[i]);
        }
        for (int i = 0; i < kernel.length; i++) {
            for (int j = 0; j < kernel.length; j++)
                sum += kernel[i] * kernel[j];
        }
        smoothPoints(kernel, sum);
    }

    public void smoothPoints(float[] kernel, float kernelSum) {
        //smooth in x dir, store locally to not influence other points being calculated
        for (Point curvePoint : points) {
            float sum = 0;
            for (int x = 0; x < kernel.length; x++) {
                float factor = kernel[x];
                int xPos = curvePoint.x + x - radius;
                int yPos = curvePoint.y;
                sum += dimension.getHeight(xPos, yPos) * factor;
            }
            xSmoothedPoints.put(curvePoint, sum);
        }

        //smooth in y dir using precalculated smoothed x values
        for (Point curvePoint : corePoints) {
            float sum = 0;
            for (int y = 0; y < kernel.length; y++) {
                float factor = kernel[y];
                sum += xSmoothedPoints.get(new Point(curvePoint.x, curvePoint.y + y - radius)) * factor;
            }
            sum /= kernelSum;
            dimension.setHeight(curvePoint.x, curvePoint.y, sum);
        }
    }
}
