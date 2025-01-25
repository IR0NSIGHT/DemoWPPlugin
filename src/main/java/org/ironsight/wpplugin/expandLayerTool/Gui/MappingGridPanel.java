package org.ironsight.wpplugin.expandLayerTool.Gui;

import org.ironsight.wpplugin.expandLayerTool.operations.LayerMapping;
import org.pepsoft.worldpainter.layers.Annotations;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class MappingGridPanel extends JPanel implements IMappingEditor {
    private final List<Line> lines = new ArrayList<>();  // List to store lines
    private final int pointHitBoxRadius = 5;
    private final int pixelSizeX = 500;
    private final int pixelSizeY = 500;
    private final float GRID_X_SCALE;
    private final float GRID_Y_SCALE;
    private boolean drag;
    private LayerMapping.MappingPoint selected;
    private LayerMapping mapping;
    private Consumer<LayerMapping> onUpdate = f -> {
    };
    private Consumer<Integer> onSelect = f -> {
    };

    public MappingGridPanel(LayerMapping mapping) {
        this.GRID_X_SCALE = pixelSizeX / ((float) mapping.input.getMaxValue() - mapping.input.getMinValue());
        this.GRID_Y_SCALE = pixelSizeY / ((float) mapping.output.getMaxValue() - mapping.output.getMinValue());

        this.setPreferredSize(new Dimension(pixelSizeX, pixelSizeY));
        setBorder(BorderFactory.createEmptyBorder(100, 100, 100, 100));

        setMapping(mapping);
        init();

    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Grid Panel");


        LayerMapping mapper = new LayerMapping(new LayerMapping.SlopeProvider(),
                new LayerMapping.NibbleLayerSetter(Annotations.INSTANCE),
                new LayerMapping.MappingPoint[]{new LayerMapping.MappingPoint(20, 2),
                        new LayerMapping.MappingPoint(50, 3), new LayerMapping.MappingPoint(70, 7),});
        MappingGridPanel gridPanel = new MappingGridPanel(mapper);
        gridPanel.setOnUpdate(f -> {
        });

        // Add the outer panel to the frame
        frame.add(gridPanel);

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void init() {
        this.setLayout(new BorderLayout());
        JPanel grid = new JPanel() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                paintGrid(g);
            }
        };
        grid.setSize(new Dimension(pixelSizeX, pixelSizeY));


        // Add a MouseListener to detect clicks inside the panel
        MappingGridPanel panel = this;
        grid.addMouseListener(new MouseAdapter() {
            int gridXDragStart, gridYDragStart;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    Point grid = pixelToGrid(e.getX(), e.getY());
                    gridXDragStart = grid.x;
                    gridYDragStart = grid.y;
                    //update selected
                    boolean hit = selectPointNear(gridXDragStart, gridYDragStart, pointHitBoxRadius);
                    if (!hit) selected = null;
                    drag = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                drag = false;
                // Get the pixel coordinates of the click
                int pixelX = e.getX();
                int pixelY = e.getY();
                Point pressed = pixelToGrid(pixelX, pixelY);

                // Convert the pixel coordinates to grid coordinates
                int gridX = pressed.x;
                int gridY = pressed.y;

                int MINIMAL_DRAG_DISTANCE = 3;
                boolean dragged = distanceBetween(gridX, gridY, gridXDragStart, gridYDragStart) > MINIMAL_DRAG_DISTANCE;
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (dragged && selected != null) {  // REPOSITION POINT

                    } else {    // INSERT POINT
                        // Call the callback with the grid coordinates
                        boolean hit = selectPointNear(gridX, gridY, pointHitBoxRadius);
                        if (!hit) {
                            // INSERT NEW POINT
                            LayerMapping.MappingPoint[] newPoints = Arrays.copyOf(panel.mapping.getMappingPoints(),
                                    panel.mapping.getMappingPoints().length + 1);
                            newPoints[newPoints.length - 1] = new LayerMapping.MappingPoint(gridX, gridY);
                            setMapping(mapping.withNewPoints(newPoints));
                        } else {
                            //implicitly set selected point, but dont do anything with it
                        }
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    //update selected
                    boolean hit = selectPointNear(gridX, gridY, pointHitBoxRadius);
                    if (!hit) selected = null;

                    if (selected == null || panel.mapping.getMappingPoints().length <= 1) return;
                    LayerMapping.MappingPoint[] newPoints =
                            new LayerMapping.MappingPoint[panel.mapping.getMappingPoints().length - 1];
                    int i = 0;
                    for (LayerMapping.MappingPoint p : panel.mapping.getMappingPoints()) {
                        if (p.equals(selected)) continue;
                        newPoints[i++] = p;
                    }
                    selected = null;
                    setMapping(mapping.withNewPoints(newPoints));
                }
            }
        });

        grid.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // Get the pixel coordinates of the click
                Point pressed = pixelToGrid(e.getX(), e.getY());
                int gridX = pressed.x;
                int gridY = pressed.y;

                if (drag) {
                    if (selected != null) {
                        LayerMapping.MappingPoint[] newPoints =
                                new LayerMapping.MappingPoint[panel.mapping.getMappingPoints().length];
                        int i = 0;
                        for (LayerMapping.MappingPoint p : panel.mapping.getMappingPoints()) {
                            if (p.equals(selected)) {
                                newPoints[i] = new LayerMapping.MappingPoint(gridX, gridY);
                                selected = newPoints[i++];
                            } else newPoints[i++] = p;
                        }
                        setMapping(mapping.withNewPoints(newPoints));
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // Optional: Handle mouse movement when not dragging
            }
        });

        this.setLayout(null);
        int pad = 50;
        grid.setBounds(pad, 0, pixelSizeX + 1 , pixelSizeY + 1);

        JPanel left = new JPanel(new BorderLayout());
        left.setBounds(0,0,pad, pixelSizeY);
        {
            JLabel leftText = new JLabel("" + mapping.output.getMinValue());
            leftText.setHorizontalAlignment(SwingConstants.CENTER);
            leftText.setVerticalAlignment(SwingConstants.CENTER);
            left.add(leftText, BorderLayout.SOUTH);
        }
        {
            JLabel leftText = new JLabel("" + mapping.output.getMaxValue());
            leftText.setHorizontalAlignment(SwingConstants.CENTER);
            leftText.setVerticalAlignment(SwingConstants.CENTER);
            left.add(leftText, BorderLayout.NORTH);
        }
        {
            JLabel leftText = new JLabel(mapping.output.getName());
            leftText.setHorizontalAlignment(SwingConstants.CENTER);
            leftText.setVerticalAlignment(SwingConstants.CENTER);
            left.add(leftText, BorderLayout.CENTER);
        }


        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBounds(pad,pixelSizeY,pixelSizeX + 1, pad);
        {
            JLabel leftText = new JLabel("" + mapping.input.getMinValue());
            leftText.setHorizontalAlignment(SwingConstants.CENTER);
            leftText.setVerticalAlignment(SwingConstants.CENTER);
            bottom.add(leftText, BorderLayout.WEST);
        }
        {
            JLabel leftText = new JLabel("" + mapping.input.getMaxValue());
            leftText.setHorizontalAlignment(SwingConstants.CENTER);
            leftText.setVerticalAlignment(SwingConstants.CENTER);
            bottom.add(leftText, BorderLayout.EAST);
        }
        {
            JLabel leftText = new JLabel(mapping.input.getName());
            leftText.setHorizontalAlignment(SwingConstants.CENTER);
            leftText.setVerticalAlignment(SwingConstants.CENTER);
            bottom.add(leftText, BorderLayout.CENTER);
        }



        this.add(grid, BorderLayout.CENTER);
        this.add(bottom, BorderLayout.SOUTH);
        this.add(left, BorderLayout.WEST);
        this.setPreferredSize( new Dimension(pixelSizeX + pad, pixelSizeY + pad));
    }

    private Point pixelToGrid(int pixelX, int pixelY) {
        // Convert the pixel coordinates to grid coordinates
        int gridXPressed = Math.round(pixelX / GRID_X_SCALE);
        int gridYPressed = Math.round((pixelSizeY - pixelY) / GRID_Y_SCALE); // Flip the Y-axis
        return new Point(gridXPressed, gridYPressed);
    }

    private Point gridToPixel(int gridX, int gridY) {
        int pixelX = Math.round(gridX * GRID_X_SCALE);
        int pixelY = Math.round(pixelSizeY - (gridY * GRID_Y_SCALE));

        return new Point(pixelX, pixelY);
    }

    @Override
    public void setMapping(LayerMapping mapping) {
        this.mapping = mapping;
        clearLines();
        {
            LayerMapping.MappingPoint a = mapping.getMappingPoints()[0];
            addLine(mapping.input.getMinValue(), a.output, a.input, a.output);

            LayerMapping.MappingPoint b = mapping.getMappingPoints()[mapping.getMappingPoints().length - 1];
            addLine(b.input, b.output, mapping.input.getMaxValue(), b.output);
        }
        for (int i = 0; i < mapping.getMappingPoints().length - 1; i++) {
            LayerMapping.MappingPoint a = mapping.getMappingPoints()[i];
            LayerMapping.MappingPoint b = mapping.getMappingPoints()[i + 1];

            addLine(a.input, a.output, b.input, b.output);
        }

        if (onUpdate != null) onUpdate.accept(mapping);
        this.repaint();
    }

    // Callback method to handle the click event (pass the grid coordinates)
    private boolean selectPointNear(int x, int y, int maxDist) {
        LayerMapping.MappingPoint closest = null;
        int selectedIdx = -1;
        int i = 0;
        int distTotalSq = Integer.MAX_VALUE;


        for (LayerMapping.MappingPoint p : mapping.getMappingPoints()) {
            int distX = Math.abs(p.input - x);
            int distY = Math.abs(p.output - y);
            int distSq = distX * distX + distY * distY;
            if (closest == null || distSq < distTotalSq) {
                closest = p;
                distTotalSq = distSq;
                selectedIdx = i;
            }
            i++;
        }
        if (distTotalSq > (maxDist * maxDist)) return false;
        selected = closest;
        this.onSelect.accept(selectedIdx);
        this.repaint();
        return true;
    }

    private float distanceBetween(int x1, int y1, int x2, int y2) {
        float dX = x1 - x2;
        float dY = y1 - y2;
        return (float) Math.sqrt(dX * dX + dY * dY);
    }

    public void clearLines() {
        lines.clear();
    }

    /**
     * Adds a line to be drawn on the grid from (x, y) to (x1, y1).
     *
     * @param x  The x-coordinate of the starting point (0 to 100).
     * @param y  The y-coordinate of the starting point (0 to 100).
     * @param x1 The x-coordinate of the ending point (0 to 100).
     * @param y1 The y-coordinate of the ending point (0 to 100).
     */
    public void addLine(int x, int y, int x1, int y1) {
        lines.add(new Line(x, y, x1, y1));
        repaint();  // Request a repaint to update the display
    }

    @Override
    public void setOnUpdate(Consumer<LayerMapping> onUpdate) {
        this.onUpdate = onUpdate;
    }

    @Override
    public void setOnSelect(Consumer<Integer> onSelect) {
        this.onSelect = onSelect;
    }

    @Override
    public void setSelected(Integer selectedPointIdx) {
        this.selected = mapping.getMappingPoints()[selectedPointIdx];
        this.repaint();
    }

    private void paintGrid(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw grid
        g2d.setColor(Color.LIGHT_GRAY);

        int rangeX = mapping.input.getMaxValue() - mapping.input.getMinValue();
        int rangeY = mapping.output.getMaxValue() - mapping.output.getMinValue();
        for (int i = 0; i <= rangeX; i += 1) {
            Point start = gridToPixel(i, mapping.output.getMinValue());
            Point end = gridToPixel(i, mapping.output.getMaxValue());
            g2d.drawLine(start.x, start.y, end.x, end.y);
        }

        for (int i = 0; i <= rangeY; i += 1) {
            Point start = gridToPixel(mapping.input.getMinValue(), i);
            Point end = gridToPixel(mapping.input.getMaxValue(), i);
            g2d.drawLine(start.x, start.y, end.x, end.y);
        }

        // Draw lines from the list
        g2d.setColor(Color.RED);
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4, 10}, 0));
        for (Line line : lines) {
            Point start = gridToPixel(line.x, line.y);
            Point end = gridToPixel(line.x1, line.y1);

            int x1 = start.x;
            int y1 = start.y;  // Flip the y-axis
            int x2 = end.x;
            int y2 = end.y;
            g2d.drawLine(x1, y1, x2, y2);
        }

        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
        for (LayerMapping.MappingPoint p : mapping.getMappingPoints()) {
            int radius = 10;  // Circle radius
            Point start = gridToPixel(p.input, p.output);

            int x1 = start.x;
            int y1 = start.y;

            if (selected != null && selected.equals(p)) {
                int width = radius * 4;
                g2d.drawRect(x1 - width / 2, y1 - width / 2, width, width);
            }
            g2d.fillOval(x1 - radius / 2, y1 - radius / 2, radius, radius);


        }

        // Draw circles at points
        g2d.setColor(Color.RED);
    }

    // Helper class to represent a line
    private static class Line {
        int x, y, x1, y1;

        Line(int x, int y, int x1, int y1) {
            this.x = x;
            this.y = y;
            this.x1 = x1;
            this.y1 = y1;
        }
    }
}
