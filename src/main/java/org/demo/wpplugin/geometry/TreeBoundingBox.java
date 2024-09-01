package org.demo.wpplugin.geometry;

import java.awt.*;
import java.util.Arrays;
import java.util.Iterator;

public class TreeBoundingBox extends AxisAlignedBoundingBox2d {
    final int sumChilds;
    private final AxisAlignedBoundingBox2d leftChild;
    private final AxisAlignedBoundingBox2d rightChild;

    public TreeBoundingBox(AxisAlignedBoundingBox2d leftChild, AxisAlignedBoundingBox2d rightChild) {
        super(Arrays.asList(leftChild.minPoint, leftChild.maxPoint, rightChild.minPoint, rightChild.maxPoint));
        this.leftChild = leftChild;
        this.rightChild = rightChild;
        this.sumChilds =
                (leftChild instanceof TreeBoundingBox ? ((TreeBoundingBox) leftChild).sumChilds : 1) + (rightChild instanceof TreeBoundingBox ? ((TreeBoundingBox) rightChild).sumChilds : 1);
    }

    @Override
    public boolean contains(Point p) {
        if (!super.contains(p)) {
            return false;
        }
        boolean isInChildren = (leftChild.contains(p) || rightChild.contains(p));
        return isInChildren;
    }

    @Override
    public TreeBoundingBox expand(double size) {
        return new TreeBoundingBox(leftChild.expand(size), rightChild.expand(size));
    }

    @Override
    public Iterator<Point> areaIterator() {
        return new Iterator<Point>() {
            final Iterator<Point> leftChildIterator = leftChild.areaIterator();
            final Iterator<Point> rightChildIterator = rightChild.areaIterator();

            @Override
            public boolean hasNext() {
                return leftChildIterator.hasNext() || rightChildIterator.hasNext();
            }

            @Override
            public Point next() {
                if (leftChildIterator.hasNext()) {
                    return leftChildIterator.next();
                } else {
                    return rightChildIterator.next();
                }
            }
        };
    }

    @Override
    public String toString() {
        return "TreeBoundingBox{" +
                super.toString() +
                "childs=" + sumChilds +
                '}';
    }
}