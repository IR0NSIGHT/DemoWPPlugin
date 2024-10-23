package org.demo.wpplugin.Gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * @author afsal villan
 * @version 1.0
 * <p>
 * http://www.genuinecoder.com
 */
public class Heightmap3dApp extends Application {
    public static Heightmap3dApp instance;
    public static float[][] heightMap = DefaultHeightMap.loadFloatArrayFromFile("default_heightmap.txt");
    public static int SIZEFACTOR = 100;
    private static boolean isJavaFXRunning = false;
    private final float[] quadUp = new float[]{0, 0, 0,//left
            100, 0, 0,   //right
            100, 0, 100,  //deep right
            0, 0, 100 //deep left
    };
    private final float[] quadXPos = new float[]{0, 0, 0,//left
            0, 0, 100,   //right
            0, 100, 100,  //deep right
            0, 100, 0 //deep left
    };
    private final float[] quadXNeg = new float[]{100, 0, 100,//left
            100, 0, 0,   //right
            100, 100, 0,  //deep right
            100, 100, 100 //deep left
    };
    private final float[] quadZNeg = new float[]{0, 0, 100, 100, 0, 100, 100, 100, 100, 0, 100, 100};
    private final float[] quadZPos = new float[]{0, 0, 0, 0, 100, 0, 100, 100, 0, 100, 0, 0};
    Rotate lightRotY = new Rotate(0, Rotate.Y_AXIS);
    Rotate worldRotX = new Rotate(0, Rotate.X_AXIS);
    Rotate worldRotY = new Rotate(0, Rotate.Y_AXIS);
    //
// The handleMouse() method is used in the MoleculeSampleApp application to
// handle the different 3D camera views.
// This method is used in the Getting Started with JavaFX 3D Graphics tutorial.
//
    double mousePosX;
    double mousePosY;
    double mouseOldX;
    double mouseOldY;
    double mouseDeltaX;
    double mouseDeltaY;
    double SHIFT_MULTIPLIER = 5f;
    double CONTROL_MULTIPLIER = 10f;
    double ROTATION_SPEED = 0.1f;
    double CAMERA_MOVE_SPEED = 100f;
    Rotate camYRot = new Rotate();
    Rotate camXRot = new Rotate();
    Rotate worldYRot = new Rotate();
    private Group root;
    private Stage primaryStage;

    public static void main(String... args) {
        if (instance == null) startJavaFX();
        else {
            instance.reloadScene();
        }
    }

    public static void startJavaFX() {
        if (!isJavaFXRunning) {
            // Start JavaFX runtime in a background thread
            new Thread(() -> Application.launch(Heightmap3dApp.class)).start();
            isJavaFXRunning = true;
        }
    }

    // Static method to open a new stage
    public void reloadScene() {
        Platform.runLater(() -> {
            try {
                DefaultHeightMap.saveFloatArrayToFile(heightMap, "default_heightmap.txt");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            root.getChildren().clear(); // Clear existing nodes
            Group world = createEnvironment();
            root.getChildren().add(world);
        });

    }

    private Group createEnvironment() {
        Group group = new Group();

        Box ground = new Box();
        ground.setHeight(1);
        ground.setWidth(5000);
        ground.setDepth(5000);
        ground.setTranslateY(0.5);
        PhongMaterial m = new PhongMaterial();
        m.setDiffuseColor(Color.DARKGREEN);
        ground.setMaterial(m);

        Box edge = new Box();
        edge.setHeight(25 * 100);
        edge.setWidth(100);
        edge.setDepth(100);
        edge.setTranslateY(-edge.getHeight() / 2f);

        group.getChildren().addAll(ground, edge);

        m.setDiffuseColor(Color.DARKGREEN);
        ground.setMaterial(m);

        float ambientStrenght = 0.3f;
        Color ambientColor = Color.WHITE.deriveColor(0, 1, ambientStrenght, 1);
        AmbientLight ambientLight = new AmbientLight(ambientColor);
        group.getChildren().add(ambientLight);

        Group lightAnchor = new Group();
        PointLight l = new PointLight();
        Color directionalColor = Color.WHITE.deriveColor(0, 1, 1 - ambientStrenght, 1);
        l.setColor(directionalColor);
        l.setTranslateX(100000);
        l.setTranslateY(-100000);
        l.setTranslateZ(100000);
        lightAnchor.getChildren().add(l);
        lightAnchor.getTransforms().add(lightRotY);

        group.getChildren().add(lightAnchor);


        group.getChildren().add(createHeightmapMesh());
        return group;
    }

    private MeshView createHeightmapMesh() {
        TriangleMesh mesh = new TriangleMesh();
        for (int z = 0; z < heightMap.length; z++)
            for (int x = 0; x < heightMap[0].length; x++) {
                float y = Math.round(heightMap[z][x]);
                Point3D center = new Point3D(x * 100, -y * 100, z * 100);
                Block blockType;
                if (y < 62) blockType = Block.WATER;    //water
                else if (y > 120) blockType = Block.ROCK;    //rock
                else blockType = Block.GRASS;   //grass
                addFace(center, mesh, Dir.UP, blockType);
                addFace(center, mesh, Dir.XNEG, blockType);
                addFace(center, mesh, Dir.XPOS, blockType);
                addFace(center, mesh, Dir.ZNEG, blockType);
                addFace(center, mesh, Dir.ZPOS, blockType);
            }

        // Load the PNG image as a texture
        Image textureImage = new Image("file:main_color_texture_worldpainter.png");

        // Create a PhongMaterial and set the texture map
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(textureImage);  // Apply the PNG texture as the diffuse map

        //  material.setDiffuseColor(Color.W);
        //  material.setSpecularColor(Color.LIGHTGREEN);
        //  material.setSpecularPower(30);  // Moderate shininess

        MeshView meshView = new MeshView(mesh);
        meshView.setDrawMode(javafx.scene.shape.DrawMode.FILL); // Render filled triangles
        meshView.setMaterial(material);
        meshView.setTranslateX(-heightMap.length / 2 * 100);
        meshView.setTranslateZ(-heightMap.length / 2 * 100);

        return meshView;
    }

    private void addFace(Point3D position, TriangleMesh view, Dir dir, Block block) {
        FaceDef face = new FaceDef();
        switch (dir) {
            case UP:
                face.verts = quadUp.clone();
                break;
            case XPOS:
                face.verts = quadXPos.clone();
                break;
            case XNEG:
                face.verts = quadXNeg.clone();
                break;
            case ZNEG:
                face.verts = quadZNeg.clone();
                break;
            case ZPOS:
                face.verts = quadZPos.clone();
                break;
            default:
                face.verts = new float[3 * 4];
                break;
        }
        for (int i = 0; i < face.verts.length; i += 3)
            face.verts[i] += (float) position.getX();
        for (int i = 1; i < face.verts.length; i += 3)
            face.verts[i] += face.verts[i] == 0 ? (float) position.getY() : 0;
        for (int i = 2; i < face.verts.length; i += 3)
            face.verts[i] += (float) position.getZ();

        float texPos;

        switch (block) {
            case ROCK:
                texPos = 1;

                break;
            case GRASS:
                if (dir == Dir.UP) texPos = 0;

                else texPos = 3f;
                break;
            case WATER:
                texPos = 2f;
                break;
            default:
                texPos = 0f;
                break;
        }
        float startPosTex = texPos / 4f + 0.01f;
        float endPosTex = texPos / 4f + 1 / 4f - 0.01f;
        face.texCoords = new float[]{startPosTex, startPosTex, endPosTex, startPosTex, endPosTex, endPosTex, startPosTex, endPosTex,};
        face.faces = new int[]{0, 0, 1, 1, 2, 2,

                2, 2, 3, 3, 0, 0};

        //vertices are absolute and stay untouched
        //tex coords are absolute and stay untouched
        //face indices must be shifted
        for (int i = 0; i < face.faces.length; i++) {
            face.faces[i] += view.getPoints().size() / 3;
        }

        view.getPoints().addAll(face.verts);
        view.getTexCoords().addAll(face.texCoords);
        view.getFaces().addAll(face.faces);

    }

    public static void printFloatArray(float[][] array) {
        StringBuilder sb = new StringBuilder();
        sb.append("float[][] array = {\n");

        for (int i = 0; i < array.length; i++) {
            sb.append("    {");
            for (int j = 0; j < array[i].length; j++) {
                sb.append(array[i][j]);
                if (j < array[i].length - 1) {
                    sb.append("f, ");
                }
            }
            sb.append("}");
            if (i < array.length - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("};");

        // Print the constructed syntax
        System.out.println(sb);
    }

    @Override
    public void init() {
        // Set the flag to true when the application is initialized
        instance = this;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        root = new Group();
        this.reloadScene();
        this.primaryStage = primaryStage;
        Scene scene = new Scene(root, SIZEFACTOR, SIZEFACTOR, true);
        scene.setFill(Color.LIGHTBLUE);
        primaryStage.setScene(scene);
        primaryStage.setWidth(16 * SIZEFACTOR);
        primaryStage.setHeight(9 * SIZEFACTOR);

        Camera camera = new PerspectiveCamera();
        camera.setFarClip(2000);
        camera.setNearClip(1);

        int camDist = 10000;
        camera.setTranslateX(-8 * SIZEFACTOR);
        camera.setTranslateY(-2000);
        camera.setTranslateZ(-camDist);

        scene.setCamera(camera);

        handleMouse(scene, scene.getRoot(), camera);

        root.getTransforms().addAll(worldRotY, worldRotX);


        primaryStage.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            double mod = 1f;
            if (event.isShiftDown()) mod += SHIFT_MULTIPLIER;
            if (event.isControlDown()) mod += CONTROL_MULTIPLIER;
            mod *= CAMERA_MOVE_SPEED;
            switch (event.getCode()) {
                case LEFT:
                    worldRotY.setAngle(worldRotY.getAngle() + 10);
                    break;
                case RIGHT:
                    worldRotY.setAngle(worldRotY.getAngle() - 10);
                    break;
                case UP:
                    worldRotX.setAngle(worldRotX.getAngle() + 10);
                    break;
                case DOWN:
                    worldRotX.setAngle(worldRotX.getAngle() - 10);
                case W: //w/s is for z
                    moveNodeRelative(camera, mod, LocalDir.FORWARD);
                    break;
                case S:
                    moveNodeRelative(camera, -mod, LocalDir.FORWARD);
                    break;
                case A:// a/d is x axis
                    moveNodeRelative(camera, -mod, LocalDir.RIGHT);
                    break;
                case D:
                    moveNodeRelative(camera, mod, LocalDir.RIGHT);
                    break;
            }
        });

        primaryStage.show();
    }

    private void handleMouse(Scene scene, final Node root, final Camera camera) {

        scene.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent me) {
                mousePosX = me.getSceneX();
                mousePosY = me.getSceneY();
                mouseOldX = me.getSceneX();
                mouseOldY = me.getSceneY();
            }
        });
        scene.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent me) {
                mouseOldX = mousePosX;
                mouseOldY = mousePosY;
                mousePosX = me.getSceneX();
                mousePosY = me.getSceneY();
                mouseDeltaX = (mousePosX - mouseOldX);
                mouseDeltaY = (mousePosY - mouseOldY);

                double modifier = 1.0;

                if (me.isControlDown()) {
                    modifier = CONTROL_MULTIPLIER;
                }
                if (me.isShiftDown()) {
                    modifier = SHIFT_MULTIPLIER;
                }
                if (me.isPrimaryButtonDown()) {

                    if (me.isShiftDown()) {
                        //rotate world
                        rotateRootAroundYAxis(mouseDeltaX * modifier * ROTATION_SPEED);
                    } else if (me.isControlDown())
                        //rotate light
                        lightRotY.setAngle(lightRotY.getAngle() + mouseDeltaX * modifier * ROTATION_SPEED);
                    else {
                        //pan camera
                        rotateCameraAroundYAxis(camera, mouseDeltaX * modifier * ROTATION_SPEED);
                        rotateCameraAroundXAxis(camera, -mouseDeltaY * modifier * ROTATION_SPEED);
                    }
                } else if (me.isSecondaryButtonDown()) {
                    Point3D f = getNodeDir(camera, LocalDir.FORWARD);
                    Point3D fPlane = new Point3D(f.getX(), 0, f.getZ());
                    fPlane = fPlane.multiply(mouseDeltaY * 0.1f * modifier * CAMERA_MOVE_SPEED);

                    f = getNodeDir(camera, LocalDir.RIGHT);
                    f = new Point3D(f.getX(), 0, f.getZ());
                    f = f.multiply(mouseDeltaX * 0.1f * modifier * CAMERA_MOVE_SPEED);

                    fPlane = fPlane.add(f);
                    fPlane = fPlane.add(camera.getTranslateX(), camera.getTranslateY(), camera.getTranslateZ());

                    camera.setTranslateX(fPlane.getX());
                    camera.setTranslateY(fPlane.getY());
                    camera.setTranslateZ(fPlane.getZ());

                } else if (me.isMiddleButtonDown()) {
                    camera.setTranslateY(camera.getTranslateY() + mouseDeltaY * 0.1f * modifier * CAMERA_MOVE_SPEED);
                }

            }
        }); // setOnMouseDragged
    } //handleMouse

    private void moveNodeRelative(Node node, double distance, LocalDir dir) {
        Point3D forward = getNodeDir(node, dir);
        // Update the camera's position based on the forward vector
        node.setTranslateX(node.getTranslateX() + forward.getX() * distance);
        node.setTranslateY(node.getTranslateY() + forward.getY() * distance);
        node.setTranslateZ(node.getTranslateZ() + forward.getZ() * distance);
    }

    private void rotateRootAroundYAxis(double angleInDegrees) {
        //double total = root.getRotate() + angleInDegrees
        //root.setRotate(total);
        worldRotY.setAxis(Rotate.Y_AXIS);
        double total = (worldRotY.getAngle() + angleInDegrees) % 360;
        worldRotY.setAngle(total);
        // No need to clear and re-add the transform every time
        if (!root.getTransforms().contains(worldRotY)) {
            root.getTransforms().add(worldRotY);  // Add the transform if not already added
        }
    }

    private void rotateCameraAroundYAxis(Camera camera, double angleInDegrees) {
        // Create a Rotate transform
        camYRot.setAxis(Rotate.Y_AXIS);
        camYRot.setAngle(camYRot.getAngle() + angleInDegrees);
        // Apply the rotation to the camera
        camera.getTransforms().clear();
        camera.getTransforms().addAll(camYRot, camXRot);
    }

    private void rotateCameraAroundXAxis(Camera camera, double angleInDegrees) {
        // Create a Rotate transform
        Rotate rot = camXRot;
        rot.setAxis(Rotate.X_AXIS);
        rot.setAngle(rot.getAngle() + angleInDegrees);
        // Apply the rotation to the camera
        camera.getTransforms().clear();
        camera.getTransforms().addAll(camYRot, camXRot);
    }

    private Point3D getNodeDir(Node n, LocalDir dir) {
        Point3D local = null;
        switch (dir) {
            case UP:
                local = new Point3D(0, 1, 0);
                break;
            case FORWARD:
                local = new Point3D(0, 0, 1);
                break;

            case RIGHT:
                local = new Point3D(1, 0, 0);
                break;
            default:
                throw new IllegalArgumentException();
        }
        Point3D forward = n.getLocalToSceneTransform().transform(local);
        forward = forward.subtract(n.getTranslateX(), n.getTranslateY(), n.getTranslateZ());
        return forward;
    }

    enum Block {
        GRASS, ROCK, WATER
    }


    enum LocalDir {
        FORWARD, UP, RIGHT
    }

    enum Dir {
        UP, XPOS, XNEG, ZPOS, ZNEG
    }

    private static class FaceDef {
        float[] verts;
        float[] texCoords;
        int[] faces;
    }
}