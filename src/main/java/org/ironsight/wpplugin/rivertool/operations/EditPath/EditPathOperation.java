package org.ironsight.wpplugin.rivertool.operations.EditPath;

import javafx.geometry.Point2D;
import org.ironsight.wpplugin.rivertool.ArrayUtility;
import org.ironsight.wpplugin.rivertool.Gui.Heightmap3dApp;
import org.ironsight.wpplugin.rivertool.Gui.OperationOptionsPanel;
import org.ironsight.wpplugin.rivertool.Gui.OptionsLabel;
import org.ironsight.wpplugin.rivertool.HalfWaySubdivider;
import org.ironsight.wpplugin.rivertool.RepeatedTask;
import org.ironsight.wpplugin.rivertool.Subdivide;
import org.ironsight.wpplugin.rivertool.geometry.HeightDimension;
import org.ironsight.wpplugin.rivertool.geometry.PaintDimension;
import org.ironsight.wpplugin.rivertool.layers.PathPreviewLayer;
import org.ironsight.wpplugin.rivertool.layers.renderers.DemoLayerRenderer;
import org.ironsight.wpplugin.rivertool.operations.ContinuousCurve;
import org.ironsight.wpplugin.rivertool.operations.River.RiverHandleInformation;
import org.ironsight.wpplugin.rivertool.pathing.*;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.brushes.Brush;
import org.pepsoft.worldpainter.colourschemes.HardcodedColourScheme;
import org.pepsoft.worldpainter.operations.*;
import org.pepsoft.worldpainter.painting.Paint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

/**
 * For any operation that is intended to be applied to the dimension in a
 * particular location as indicated by the user
 * by clicking or dragging with a mouse or pressing down on a tablet, it
 * makes sense to subclass
 * {@link MouseOrTabletOperation}, which automatically sets that up for you.
 *
 * <p>For more general kinds of operations you are free to subclass
 * {@link AbstractOperation} instead, or even just
 * implement {@link Operation} directly.
 *
 * <p>There are also more specific base classes you can use:
 *
 * <ul>
 *     <li>{@link AbstractBrushOperation} - for operations that need access
 *     to the currently selected brush and
 *     intensity setting.
 *     <li>{@link RadiusOperation} - for operations that perform an action in
 *     the shape of the brush.
 *     <li>{@link AbstractPaintOperation} - for operations that apply the
 *     currently selected paint in the shape of the
 *     brush.
 * </ul>
 *
 * <p><strong>Note</strong> that for now WorldPainter only supports
 * operations that
 */
public class EditPathOperation extends MouseOrTabletOperation implements PaintOperation, // Implement this if you
// need access to the currently selected paint; note that some base
        // classes already provide this
        BrushOperation // Implement this if you need access to the currently
        // selected brush; note that some base
        // classes already provide this
{
    public static final int COLOR_NONE = 0;
    public static final int COLOR_HANDLE = DemoLayerRenderer.Cyan;
    public static final int COLOR_CURVE = DemoLayerRenderer.BLUE;
    public static final int COLOR_SELECTED = DemoLayerRenderer.Orange;

    public static final int SIZE_SELECTED = 15;
    public static final int SIZE_DOT = 0;
    public static final int SIZE_MEDIUM_CROSS = 10;
    /**
     * The globally unique ID of the operation. It's up to you what to use
     * here. It is not visible to the user. It can
     * be a FQDN or package and class name, like here, or you could use a
     * UUID. As long as it is globally unique.
     */
    static final String ID = "orig.ironsight.wpplugin.rivertool.BezierPathTool.v1";
    /**
     * Human-readable short name of the operation.
     */
    static final String NAME = "Edit Path Operation";
    /**
     * Human-readable description of the operation. This is used e.g. in the
     * tooltip of the operation selection button.
     */
    static final String DESCRIPTION = "<html>Draw smooth, connected curves " + "with C1 continuity.<br>left click: " +
            "add " + "new" + " point " + "after selected<br>right click: delete selected<br>ctrl+click: " + "select " +
            "this " + "handle<br>shift+click: move " + "selected" + " " + "handle here</html>";
    //update path
    public static int PATH_ID = 1;
    private final EditPathOptions options = new EditPathOptions();
    private final LinkedList<ToolHistoryState> history = new LinkedList<>();
    private final HeightDimension dim;
    EditPathOptionsPanel eOptionsPanel;
    int resolution3d = 2;
    RepeatedTask task;
    private Brush brush;
    private Paint paint;
    private boolean shiftDown = false;
    private boolean altDown = false;
    private boolean ctrlDown = false;
    private StandardOptionsPanel panelContainer;
    private ToolHistoryState currentState;
    private boolean keyListening;

    public EditPathOperation() {
        // Using this constructor will create a "single shot" operation. The
        // tick() method below will only be invoked
        // once for every time the user clicks the mouse or presses on the
        // tablet:
        super(NAME, DESCRIPTION, ID);
        // Using this constructor instead will create a continues operation.
        // The tick() method will be invoked once
        // every "delay" ms while the user has the mouse button down or
        // continues pressing on the tablet. The "first"
        // parameter will be true for the first invocation per mouse button
        // press and false for every subsequent
        // invocation:
        // super(NAME, DESCRIPTION, delay, ID);
        int selectedPathId = PathManager.instance.getAnyValidId();
        Path p = PathManager.instance.getPathBy(selectedPathId);
        this.currentState = new ToolHistoryState(
                p,
                new IndexSelection(new boolean[p.amountHandles()], p.amountHandles(), 0)
                , selectedPathId
        );
        //Worldpainter Pen has a severe bug deep down that breaks
        // shift/alt/control after a button in the settings
        // pannel was used.
        //this shitty listener circumvents the bug
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (!EditPathOperation.super.isActive()) return false;
            shiftDown = e.isShiftDown();
            altDown = e.isAltDown();
            ctrlDown = e.isControlDown();

            return false;
        });

        this.dim = new HeightDimension() {
            @Override
            public float getHeight(int x, int y) {
                return getDimension().getHeightAt(x, y);
            }

            @Override
            public void setHeight(int x, int y, float z) {
                getDimension().setHeightAt(x, y, z);
            }
        };
    }

    /**
     * draws this path onto the map
     *
     * @param path
     */
    static void DrawPathLayer(Path path, ContinuousCurve curve, PaintDimension dim) throws IllegalAccessException {
        Path clone = path.clone();
        //nothing
        if (path.type == PointInterpreter.PointType.RIVER_2D) {
            RiverHandleInformation.DrawRiverPath(path, curve, dim);
        }
        assert clone.equals(path) : "something mutated the path";
    }

    public void addKeyListenerToComponent(JComponent component) {
        if (keyListening)
            return;
        keyListening = true;


        JFrame wpApp = (JFrame) SwingUtilities.getWindowAncestor(component);
        {
            KeyStroke keyStroke = KeyStroke.getKeyStroke((char) KeyEvent.VK_DELETE);
            wpApp.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
                    put(keyStroke, "delete");
            wpApp.getRootPane().getActionMap().put("delete", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    userDoDeleteAction();
                    redrawSelectedPathLayer();
                }
            });
        }

        {
            KeyStroke controlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_MASK);
            wpApp.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
                    put(controlA, "select_all");
            wpApp.getRootPane().getActionMap().put("select_all", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (currentState.indexSelection.getSelectedIdcs(false).length == getSelectedPath().amountHandles())  //all are selected
                        currentState.indexSelection.deselectAll();
                    else    //not all are selected
                        currentState.indexSelection.selectAll();
                    redrawSelectedPathLayer();
                }
            });
        }

        {
            wpApp.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
                    put(KeyStroke.getKeyStroke("ENTER"), "submit");
            wpApp.getRootPane().getActionMap().put("submit", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    currentState.indexSelection.invertHandleSelection(currentState.indexSelection.getCursorHandleIdx());
                    redrawSelectedPathLayer();
                }
            });
        }
    }

    private void show3dAction() {
        if (task != null)
            return;
        task = new RepeatedTask();

        Runnable runner = new Runnable() {
            @Override
            public void run() {
                try {
                    Point selected = PointUtils.getPoint2D(getCursorHandle());
                    //Create heightmap
                    float[][] heightmap = new float[256][];
                    float[][] waterMap = new float[256][];
                    Heightmap3dApp.Texture[][] blockMap = new Heightmap3dApp.Texture[256][];
                    BufferedImage texture = Heightmap3dApp.texture256;
                    for (int y = -128; y < 128; y++) {
                        heightmap[y + 128] = new float[256];
                        waterMap[y + 128] = new float[256];
                        blockMap[y + 128] = new Heightmap3dApp.Texture[256];
                        for (int x = -128; x < 128; x++) {
                            Point thisP = new Point(selected.x + x * resolution3d, selected.y + y * resolution3d);


                            float height = getDimension().getHeightAt(thisP) / resolution3d;
                            heightmap[y + 128][x + 128] = height;
                            ColourScheme scheme = new HardcodedColourScheme();
                            int color = getDimension().getTerrainAt(thisP.x, thisP.y)
                                    .getColour(0,
                                            thisP.x, thisP.y, 62, (int) 62,
                                            Configuration.DEFAULT_PLATFORM,
                                            scheme);
                            texture.setRGB(x+128,y+128,color);

                            float waterHeight = (float) getDimension().getWaterLevelAt(thisP.x, thisP.y) / resolution3d;
                            waterMap[y + 128][x + 128] = waterHeight;

                            Terrain t = getDimension().getTerrainAt(thisP.x, thisP.y);
                            Heightmap3dApp.Texture tex;
                            if (t == null) t = Terrain.GRASS;
                            switch (t) {

                                case GRASS:
                                case BARE_GRASS:
                                    tex = Heightmap3dApp.Texture.GRASS;
                                    break;
                                case GRAVEL:
                                    tex = Heightmap3dApp.Texture.GRAVEL;
                                    break;
                                case SAND:
                                case DESERT:
                                case BEACHES:
                                case BARE_BEACHES:
                                case SANDSTONE:
                                case END_STONE:
                                    tex = Heightmap3dApp.Texture.SAND;
                                    break;
                                case ROCK:
                                case STONE:
                                case COBBLESTONE:
                                case MOSSY_COBBLESTONE:
                                case DIORITE:
                                case ANDESITE:
                                case BASALT:
                                case DEEPSLATE:
                                    tex = Heightmap3dApp.Texture.ROCK;
                                    break;
                                case DEEP_SNOW:
                                case SNOW:
                                    tex = Heightmap3dApp.Texture.SNOW;
                                    break;
                                case DIRT:
                                    tex = Heightmap3dApp.Texture.DIRT;
                                    break;
                                case WATER:
                                    tex = Heightmap3dApp.Texture.WATER;
                                    break;
                                default:
                                    tex = Heightmap3dApp.Texture.ROCK;
                            }
                            blockMap[y + 128][x + 128] = tex;
                        }
                    }
                    Heightmap3dApp.heightMap = heightmap;
                    Heightmap3dApp.waterMap = waterMap;
                    Heightmap3dApp.blockmap = blockMap;
                    Heightmap3dApp.setHeightMap = point -> {
                        getDimension().setHeightAt((int) point.getX(), (int) point.getY(), (float) point.getZ());
                    };
                    Heightmap3dApp.setWaterHeight = point -> {
                        getDimension().setWaterLevelAt((int) point.getX(), (int) point.getY(), (int) point.getZ());
                    };
                    Heightmap3dApp.globalOffset = new Point2D(selected.x - 128, selected.y - 128);
                    Heightmap3dApp.main();
                } catch (Exception ignored) {

                }

            }
        };

        task.startTask(runner);


    }

    float[] getCursorHandle() {
        int selectedPointIdx = currentState.indexSelection.getCursorHandleIdx();
        if (selectedPointIdx == -1) return null;
        if (selectedPointIdx < 0 || selectedPointIdx > getSelectedPath().amountHandles() - 1) return null;
        return getSelectedPath().handleByIndex(selectedPointIdx);
    }

    Path getSelectedPath() {
        return currentState.path;
    }

    /**
     * automatically advance the path downhill until it doesnt find any lower
     * point.
     */
    private boolean addHandleDownhill(HeightDimension dim) {
        Point selected = PointUtils.getPoint2D(getCursorHandle());
        float selectedZ = getDimension().getHeightAt(selected);

        Point pMin = null;
        for (int radius = 1; radius < 25; radius++) {
            pMin = PointUtils.getLowestAtRadius(radius, selected, dim);
            if (getDimension().getHeightAt(pMin) < selectedZ) break;
        }
        if (getDimension().getHeightAt(pMin) >= selectedZ) return false; //tested all radii, didnt find lower point

        Path p = getSelectedPath();
        assert pMin != null;

        float[] newHandle = RiverHandleInformation.riverInformation(pMin.x, pMin.y);
        p = p.insertPointAfter(getCursorHandle(), newHandle);
        int[] newToOldMapping = Path.getMappingFromTo(p, getSelectedPath());
        overwriteSelectedPath(p, newToOldMapping);
        try {
            setSelectedPointIdx(p.getClosestHandleIdxTo(newHandle));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private void overwriteSelectedPath(Path p, int[] newToOldMapping) {
        history.addFirst(currentState);

        assert newToOldMapping.length == p.amountHandles();
        IndexSelection newSel = IndexSelection.translateToNew(currentState.indexSelection, newToOldMapping);

        this.currentState = new ToolHistoryState(p, newSel,
                currentState.pathId);
        try {
            PathManager.instance.setPathBy(getSelectedPathId(), p);
        } catch (AssertionError err) {
            System.err.println(err);
        }
    }

    void setSelectedPointIdx(int selectedPointIdx) {
        currentState.indexSelection.setCursorHandleIdx(selectedPointIdx);
    }

    int getSelectedPathId() {
        return currentState.pathId;
    }

    private void selectPathById(int pathId) {
        history.addFirst(currentState);
        Path p = PathManager.instance.getPathBy(pathId);
        currentState = new ToolHistoryState(p, new IndexSelection(p.amountHandles()), pathId);
    }

    private void undo() {
        if (history.isEmpty())
            return;
        ToolHistoryState previousPath = history.pop();
        try {
            currentState = previousPath;
            PathManager.instance.setPathBy(previousPath.pathId, previousPath.path);
        } catch (AssertionError err) {
            System.err.println(err);
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

    @Override
    public JPanel getOptionsPanel() {
        panelContainer = new EditPathOptionsPanelContainer(getName(), "a " + "description");
        return panelContainer;
    }

    @Override
    protected void activate() throws PropertyVetoException {
        super.activate();
        altDown = false;
        ctrlDown = false;
        shiftDown = false;
        addKeyListenerToComponent(this.getView());

        redrawSelectedPathLayer();
    }

    /**
     * Perform the operation. For single shot operations this is invoked once
     * per mouse-down. For continuous operations
     * this is invoked once per {@code delay} ms while the mouse button is
     * down, with the first invocation having
     * {@code first} be {@code true} and subsequent invocations having it be
     * {@code false}.
     *
     * @param centreX      The x coordinate where the operation should be
     *                     applied, in world coordinates.
     * @param centreY      The y coordinate where the operation should be
     *                     applied, in world coordinates.
     * @param inverse      Whether to perform the "inverse" operation instead
     *                     of the regular operation, if applicable
     *                     . If the
     *                     operation has no inverse it should just apply the
     *                     normal operation.
     * @param first        Whether this is the first tick of a continuous
     *                     operation. For a one shot operation this
     *                     will always
     *                     be {@code true}.
     * @param dynamicLevel The dynamic level (from 0.0f to 1.0f inclusive) to
     *                     apply in addition to the {@code level}
     *                     property, for instance due to a pressure sensitive
     *                     stylus being used. In other words,
     *                     <strong>not</strong> the total level at which to
     *                     apply the operation! Operations are free to
     *                     ignore this if it is not applicable. If the
     *                     operation is being applied through a means which
     *                     doesn't provide a dynamic level (for instance the
     *                     mouse), this will be <em>exactly</em>
     *                     {@code 1.0f}.
     */
    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        this.getView().requestFocus();
        //  Perform the operation. In addition to the parameters you have the
        //  following methods available:
        // * getDimension() - obtain the dimension on which to perform the
        // operation
        // * getLevel() - obtain the current brush intensity setting as a
        // float between 0.0 and 1.0
        // * altDown - whether the Alt key is currently pressed - NOTE:
        // this is already in use to indicate whether
        //                 the operation should be inverted, so should
        //                 probably not be overloaded
        // * ctrlDown - whether any of the Ctrl, Windows or Command keys
        // are currently pressed
        // * shiftDown - whether the Shift key is currently pressed
        // In addition you have the following fields in this class:
        // * brush - the currently selected brush
        // * paint - the currently selected paint
        final Path path = getSelectedPath();
        EditPathOperation.PATH_ID = getSelectedPathId();

        float[] userClickedCoord = RiverHandleInformation.riverInformation(centreX, centreY);
        if (path.type == PointInterpreter.PointType.RIVER_2D)
            RiverHandleInformation.setValue(userClickedCoord, RiverHandleInformation.RiverInformation.WATER_Z,
                    getDimension().getHeightAt(centreX, centreY));

        altDown = isAltDown();
        ctrlDown = isCtrlDown();
        shiftDown = isShiftDown();

        try {
            if (inverse) {
                //rightclick
                if (shiftDown) {

                } else if (ctrlDown) {
                    if (path.amountHandles() != 0)
                        userDoDeleteAction();

                } else {
                    if (path.amountHandles() != 0)
                        userDoShiftPoint(userClickedCoord);
                }
            } else {
                //leftclick
                if (shiftDown) {
                    if (path.amountHandles() != 0)
                        userDoSelectAllFromCursor(userClickedCoord, path);
                } else if (ctrlDown) {
                    userDoAddNewPoint(path, userClickedCoord);

                } else {
                    if (path.amountHandles() != 0)
                        userDoSelectPosition(userClickedCoord, path);
                }

            }

            assert getSelectedPath().amountHandles() == 0 || getCursorHandle() != null;


            assert getSelectedPath() == PathManager.instance.getPathBy(getSelectedPathId()) : "unsuccessfull " +
                    "setting " + "path in manager";
        } catch (Exception e) {
            System.out.println("Exception after user edit-path-action");
            System.out.println(e);
        }
        redrawSelectedPathLayer();

        if (this.eOptionsPanel != null) this.eOptionsPanel.onOptionsReconfigured();
    }

    private void userDoDeleteAction() {
        Path path = getSelectedPath();
        int oldCursor = currentState.indexSelection.getCursorHandleIdx();
        ArrayList<float[]> remaining = new ArrayList<>();
        for (int i = 0; i < path.amountHandles(); i++) {
            if (!currentState.indexSelection.isHandleSelected(i, true))
                remaining.add(path.handleByIndex(i));
        }
        Path newPath = new Path(remaining, path.type);
        int[] mapping = Path.getMappingFromTo(newPath, path);
        overwriteSelectedPath(newPath, mapping);

        //find a new index closest to the deleted cursor
        int[] oldToNew = Path.getMappingFromTo(path, newPath);
        int newCursor = -1;
        //walk forwards from old cursor
        for (int idx = oldCursor; idx < oldToNew.length; idx++) {
            if (oldToNew[idx] != -1) {
                newCursor = oldToNew[idx];
                break;
            }
        }
        if (newCursor == -1)    //walk backwards form old cursor if necessary
            for (int idx = oldCursor; idx >= 0; idx--) {
                if (oldToNew[idx] != -1) {
                    newCursor = oldToNew[idx];
                    break;
                }
            }

        setSelectedPointIdx(newCursor);
    }

    private void userDoAddNewPoint(Path path, float[] userClickedCoord) {
        Path p = (path.amountHandles() == 0) ? path.addPoint(userClickedCoord) :
                path.insertPointAfter(getCursorHandle(), userClickedCoord);
        int[] newToOldMapping = Path.getMappingFromTo(p, getSelectedPath());
        overwriteSelectedPath(p, newToOldMapping);
        setSelectedPointIdx(getSelectedPath().indexOfPosition(userClickedCoord));
    }

    private void userDoShiftPoint(float[] userClickedCoord) {
        float[] cursor = getCursorHandle();
        float diffX = userClickedCoord[0] - cursor[0];
        float diffY = userClickedCoord[1] - cursor[1];
        MapPointAction a = new MapPointAction() {
            @Override
            public float[] map(float[] point, int index) {
                float[] out = point.clone();
                if (currentState.indexSelection.isHandleSelected(index, true)) {
                    out[0] += diffX;
                    out[1] += diffY;
                }
                return out;
            }
        };


        Path shifted = getSelectedPath().mapPoints(a);
        overwriteSelectedPath(shifted, Path.getOneToOneMapping(shifted));
    }

    private void userDoSelectAllFromCursor(float[] userClickedCoord, Path path) {
        int newIdx = getHandleNear(userClickedCoord, path);
        if (newIdx != -1) {
            currentState.indexSelection.selectAllBetweenCursorAnd(newIdx);
            setSelectedPointIdx(newIdx);
        }
    }

    private void userDoClearPath(Path path) {
        overwriteSelectedPath(path.newEmpty(), new int[0]);
        setSelectedPointIdx(-1);
    }

    private void userDoSelectPosition(float[] userClickedCoord, Path path) {
        int idx = getHandleNear(userClickedCoord, path);
        if (currentState.indexSelection.getCursorHandleIdx() == idx) {
            //clicked cursor pos again
            currentState.indexSelection.invertHandleSelection(idx);
        } else {
            if (idx != -1)
                setSelectedPointIdx(idx);
        }

    }

    private int getHandleNear(float[] userClickedCoord, Path path) {
        //SELECT POINT
        try {
            if (path.amountHandles() != 0) {
                int clostestIdx = path.getClosestHandleIdxTo(userClickedCoord);
                float[] closest = path.handleByIndex(clostestIdx);
                //dont allow very far away clicks
                if (PointUtils.getPositionalDistance(closest, userClickedCoord,
                        RiverHandleInformation.PositionSize.SIZE_2_D.value) < 50) {
                    return clostestIdx;
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    void redrawSelectedPathLayer() {
        this.getView().requestFocus();
        this.getDimension().setEventsInhibited(true);
        //erase old
        this.getDimension().clearLayerData(PathPreviewLayer.INSTANCE);
        PaintDimension paintDim = new PaintDimension() {
            @Override
            public int getValue(int x, int y) {
                return getDimension().getLayerValueAt(PathPreviewLayer.INSTANCE, x, y);
            }

            @Override
            public void setValue(int x, int y, int v) {
                getDimension().setLayerValueAt(PathPreviewLayer.INSTANCE, x, y, v);
            }
        };

        for (int selectedIdx : currentState.indexSelection.getSelectedIdcs(false)) {
            float[] handle = getSelectedPath().handleByIndex(selectedIdx);
            PointUtils.markPoint(PointUtils.getPoint2D(handle), COLOR_SELECTED, SIZE_MEDIUM_CROSS, paintDim);
        }

        try {
            //redraw new
            DrawPathLayer(getSelectedPath().clone(), ContinuousCurve.fromPath(getSelectedPath(), dim), paintDim
            );
            if (getCursorHandle() != null)
                PointUtils.drawCircle(PointUtils.getPoint2D(getCursorHandle()), COLOR_SELECTED, SIZE_SELECTED,
                        paintDim, false);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        } finally {
            this.getDimension().setEventsInhibited(false);
        }
    }


    private static class EditPathOptions {
        float subdivisions = 1;
        float subdivisionRange = .5f;
    }

    private class ToolHistoryState {
        final Path path;
        IndexSelection indexSelection;
        int pathId;

        public ToolHistoryState(Path path, IndexSelection indexSelection, int pathId) {
            this.path = path;
            this.indexSelection = indexSelection;
            this.pathId = pathId;
        }
    }

    private class EditPathOptionsPanelContainer extends StandardOptionsPanel {
        public EditPathOptionsPanelContainer(String name, String description) {
            super(name, description);
        }

        @Override
        protected void addAdditionalComponents(GridBagConstraints constraints) {
            JLabel desc = new JLabel(getDescription());
            add(desc, constraints);
            eOptionsPanel = new EditPathOptionsPanel(options);
            add(eOptionsPanel, constraints);
        }
    }

    private class EditPathOptionsPanel extends OperationOptionsPanel<EditPathOptions> {
        public EditPathOptionsPanel(EditPathOptions editPathOptions) {
            super(editPathOptions);
        }

        @Override
        protected ArrayList<OptionsLabel> addComponents(EditPathOptions editPathOptions,
                                                        Runnable onOptionsReconfigured) {
            ArrayList<OptionsLabel> inputs = new ArrayList<>();

            //select path dropdown
            Collection<PathManager.NamedId> availablePaths = PathManager.instance.allPathNamedIds();

            JComboBox<Object> comboBox = new JComboBox<>(availablePaths.toArray());
            comboBox.setSelectedItem(PathManager.instance.getPathName(getSelectedPathId()));
            comboBox.addActionListener(e -> {
                selectPathById(((PathManager.NamedId) comboBox.getSelectedItem()).id);
                setSelectedPointIdx(getSelectedPath().amountHandles() == 0 ? -1 :
                        getSelectedPath().amountHandles() - 1);
                redrawSelectedPathLayer();
                onOptionsReconfigured.run();
            });
            JLabel comboBoxLabel = new JLabel("Selected path");
            inputs.add(() -> new JComponent[]{comboBoxLabel, comboBox});

            // ADD BUTTON
            // Create a JButton with text
            JButton button = new JButton("Add empty path");
            // Add an ActionListener to handle button clicks
            button.addActionListener(e -> {
                float[][] handles = new float[][]{RiverHandleInformation.riverInformation(0, 0),
                        RiverHandleInformation.riverInformation(10, 10, 1, 2, 3, 4, 5),
                        RiverHandleInformation.riverInformation(20, 20)};
                selectPathById(PathManager.instance.addPath(new Path(Arrays.asList(handles),
                        getSelectedPath().type)));
                setSelectedPointIdx(0);
                redrawSelectedPathLayer();
                onOptionsReconfigured.run();
            });
            inputs.add(() -> new JComponent[]{button});


            // Create a JTextField for text input
            final JTextField textField = new JTextField(20);

            // Create a JButton to trigger an action
            JButton submitNameChangeButton = new JButton("Change Name");
            textField.setText(PathManager.instance.getPathName(getSelectedPathId()).name);
            // Add ActionListener to handle button click
            submitNameChangeButton.addActionListener(e -> {
                // Get the text from the text field and display it in the label
                String inputText = textField.getText();
                PathManager.instance.nameExistingPath(getSelectedPathId(), inputText);
                onOptionsReconfigured.run();
            });

            {          // FLOW DOWNHILL BUTTON
                JButton myButton = new JButton("flow downhill");
                myButton.addActionListener(e -> {
                    //run downhill 25 handles max or until we hit a hole with
                    // no escape
                    for (int i = 0; i < 25; i++) {
                        boolean didFindSth = addHandleDownhill(dim);
                        if (!didFindSth) break;
                    }
                    redrawSelectedPathLayer();
                });
                inputs.add(() -> new JComponent[]{myButton});
            }


            {

                // SHOW 3d BUTTON
                JButton myButton = new JButton("show 3d");
                myButton.addActionListener(e -> {
                    //run downhill 25 handles max or until we hit a hole with
                    // no escape
                    show3dAction();
                });
                inputs.add(() -> new JComponent[]{myButton});

                SpinnerNumberModel model = new SpinnerNumberModel(1f * resolution3d, 1, 10, 1f);

                OptionsLabel l = OptionsLabel.numericInput("3d resolution 1:x", "displays " + "area in 1:resolution",
                        model,
                        newValue -> {
                            resolution3d = newValue.intValue();
                        }, EditPathOperation.this::show3dAction);
                inputs.add(l);
            }


            if (getCursorHandle() != null) {
                if (getSelectedPath().type == PointInterpreter.PointType.RIVER_2D) {
                    OptionsLabel[] riverInputs = RiverHandleInformation.Editor(getCursorHandle(), point -> {
                        try {
                            Path newPath = getSelectedPath().overwriteHandle(getCursorHandle(), point);
                            overwriteSelectedPath(newPath, Path.getOneToOneMapping(newPath));
                        } catch (Exception ex) {
                            System.err.println(ex.getMessage());
                        }
                        redrawSelectedPathLayer();
                    }, onOptionsReconfigured);

                    inputs.addAll(Arrays.asList(riverInputs));
                }
            }

            HeightDimension dim = new HeightDimension() {
                @Override
                public float getHeight(int x, int y) {
                    return getDimension().getHeightAt(x, y);
                }

                @Override
                public void setHeight(int x, int y, float z) {

                }
            };

            {
                JButton button1 = new JButton("Edit water height");
                button1.addActionListener(e -> {
                    JFrame c = (JFrame) SwingUtilities.getWindowAncestor(this.getParent());
                    JDialog dialog = RiverHandleInformation.riverRadiusEditor(c, getSelectedPath(),
                            currentState.indexSelection.getCursorHandleIdx(),
                            p -> overwriteSelectedPath(p, Path.getOneToOneMapping(p)), dim);
                    dialog.setVisible(true);
                    onOptionsReconfigured();
                    redrawSelectedPathLayer();
                });
                inputs.add(() -> new JComponent[]{button1});
            }

            {   // subdivide segment
                {
                    JButton subdivideButton = new JButton("subdivide segment");
                    subdivideButton.addActionListener(e -> {
                        Path p = getSelectedPath();
                        ArrayList<float[]> pathHandles = p.getHandles();
                        int newSelectedIdx = currentState.indexSelection.getCursorHandleIdx();
                        int indexOffset = 0;
                        ArrayList<float[]> flatHandles = ArrayUtility.transposeMatrix(pathHandles);
                        Subdivide divider = new HalfWaySubdivider(options.subdivisionRange, options.subdivisionRange,
                                true);

                        //subdivide all marked segments
                        for (int oldSelectedIdx : currentState.indexSelection.getSelectedIdcs(true)) {
                            int selectedIdx = indexOffset + oldSelectedIdx;
                            if (selectedIdx < 0 || selectedIdx + 1 >= flatHandles.get(0).length)
                                continue;
                            ArrayList<float[]> newFlats = Subdivide.subdivide(flatHandles.get(0), flatHandles.get(1),
                                    selectedIdx, (int) options.subdivisions, divider);

                            //FIXME carry over the old values
                            for (int i = 2; i < p.type.size; i++) {
                                float[] filler = new float[newFlats.get(0).length];
                                Arrays.fill(filler, RiverHandleInformation.INHERIT_VALUE);
                                newFlats.add(filler);
                            }

                            int amountNewHandles = newFlats.get(0).length - flatHandles.get(0).length;
                            assert amountNewHandles >= 0;
                            indexOffset += amountNewHandles;
                            flatHandles = newFlats;
                        }

                        //write back new handles as selected path
                        Path newPath = new Path(ArrayUtility.transposeMatrix(flatHandles), p.type);
                        int[] newToOldMap = Path.getMappingFromTo(newPath, p);
                        overwriteSelectedPath(newPath, newToOldMap);
                        for (int i = 0; i < newToOldMap.length; i++) {
                            if (newToOldMap[i] == -1) {
                                //new point, add to selection
                                currentState.indexSelection.setHandleSelection(i, true);
                            }
                        }


                        onOptionsReconfigured();
                        redrawSelectedPathLayer();
                    });
                    inputs.add(() -> new JComponent[]{subdivideButton});
                }

                {
                    SpinnerNumberModel model = new SpinnerNumberModel(options.subdivisions, 1f, 5f, 1f);
                    OptionsLabel label = OptionsLabel.numericInput("subdivisions", "how often to subdivide", model,
                            f -> {
                                options.subdivisions = f;
                            }, onOptionsReconfigured);
                    inputs.add(label);
                }
                {
                    SpinnerNumberModel model = new SpinnerNumberModel(options.subdivisionRange * 200f, 1, 100, 1f);
                    OptionsLabel label = OptionsLabel.numericInput("subdivide range %",
                            "how far a new random point is allowed to be placed from the curve",
                            model, f -> {
                                options.subdivisionRange = f / 200f;
                            }, onOptionsReconfigured);
                    inputs.add(label);
                }
            }
            {
                JButton undoButton = new JButton("undo");
                undoButton.addActionListener(e -> {
                            undo();
                            onOptionsReconfigured();
                            redrawSelectedPathLayer();
                        }
                );
                inputs.add(() -> new JComponent[]{undoButton});
            }

            return inputs;
        }
    }
}