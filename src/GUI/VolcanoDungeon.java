package GUI;

import Characters.Boss;
import Logic.Game;
import Runner.MainScreen;
import com.almasb.fxgl.dsl.FXGL;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class VolcanoDungeon {

    private final StackPane root;
    private final Pane world;
    private final StackPane loadingOverlay;
    private ImageView backgroundView;
    private MediaPlayer music;

    private final ImageView heroView;
    private final double HERO_W = 48;
    private final double HERO_H = 48;
    private final double HERO_SPEED = 180.0;
    private final Set<KeyCode> keys = new HashSet<>();
    private AnimationTimer mover;
    private Rectangle orbNode = null;
    private Rectangle2D orbTrigger = null;
    private Text orbHintText = null;

    private final double VIEW_W = 800;
    private final double VIEW_H = 600;
    private double worldW = VIEW_W;
    private double worldH = VIEW_H;

    private Runnable onExitCallback;
    private Rectangle startRect;
    private Rectangle castleRect;
    private final Game game;

    private ImageView bossView;

    // Sistema de colisiones
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = true; // R para ver/ocultar áreas de trigger

    private InventoryScreen inventory;

    private final List<Rectangle> dungeonTriggerRects = new ArrayList<>();
    private final List<Rectangle> bossTriggerRects = new ArrayList<>();

    // Direcciones del héroe (si quieres usarlas para depuración)
    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }
    private Direction currentDirection = Direction.NONE;

    // Clase interna para obstáculos
    private static class Obstacle {

        final Rectangle2D collisionRect;
        final ObstacleType type;
        final String id;

        Obstacle(Rectangle2D collision, ObstacleType type, String id) {
            this.collisionRect = collision;
            this.type = type;
            this.id = id;
        }
    }

    private enum ObstacleType {
        BLOCK, PLANT, DOOR
    }

    public VolcanoDungeon(Game game) {
        this.game = game;
        root = new StackPane();
        root.setPrefSize(VIEW_W, VIEW_H);

        world = new Pane();
        world.setPrefSize(VIEW_W, VIEW_H);

        loadingOverlay = createLoadingOverlay();
        root.getChildren().addAll(world, loadingOverlay);

        heroView = createHeroView();
        installInputHandlers();
        createMover();

        root.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                clearInputState();
            }
        });
    }

    public StackPane getRoot() {
        return root;
    }

    public Point2D getHeroMapTopLeft() {
        return new Point2D(heroView.getLayoutX(), heroView.getLayoutY());
    }

    public void showWithLoading(Runnable onLoaded, Runnable onExit) {
        this.onExitCallback = onExit;

        Platform.runLater(() -> {
            FXGL.getGameScene().addUINode(root);
            root.requestFocus();
            showLoading(true);

            boolean imageOk = loadBackgroundImage("/Resources/textures/volcanoDungeon/volcanoExterior.png");
            startMapMusic();
            if (!game.getHero().existsCompletedTask(game.searchTask("M004")) && !game.getHero().existsPendingTask(game.searchTask("M004"))) {
                game.getHero().addTasks(game.searchTask("M004"));
            }

            populateCastleObstacles();
            positionHeroAtEntrance();
            createStartRectAtHeroStart();
            createOrbTrigger();
            createCastleRect();
            drawBossDungeon();

            PauseTransition wait = new PauseTransition(Duration.millis(600));
            wait.setOnFinished(e -> {
                showLoading(false);
                fadeInContent();
                startMover();
                if (onLoaded != null) {
                    onLoaded.run();
                }
            });
            wait.play();
        });
    }

    public void hide() {
        Platform.runLater(() -> {
            stopMapMusic();
            stopMover();
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
        });
    }

    public void setHeroPosition(double x, double y) {
        double nx = clamp(x, 0, Math.max(0, worldW - HERO_W));
        double ny = clamp(y, 0, Math.max(0, worldH - HERO_H));
        heroView.setLayoutX(nx);
        heroView.setLayoutY(ny);
        updateCamera();
    }

    // ---------------- internals / UI ----------------
    private StackPane createLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(true);
        Rectangle bg = new Rectangle(VIEW_W, VIEW_H);
        bg.setFill(Color.rgb(0, 0, 0, 0.6));
        Text label = new Text("Loading Volcano Dungeon..");
        label.setStyle("-fx-font-size: 24px; -fx-fill: #e0d090;");
        overlay.getChildren().addAll(bg, label);
        StackPane.setAlignment(label, Pos.CENTER);
        overlay.setVisible(false);
        return overlay;
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
        if (show) {
            loadingOverlay.toFront();
        } else {
            loadingOverlay.toBack();
        }
    }

    private void fadeInContent() {
        FadeTransition ft = new FadeTransition(Duration.millis(400), root);
        ft.setFromValue(0.2);
        ft.setToValue(1.0);
        ft.play();
    }

    private boolean loadBackgroundImage(String path) {
        boolean ret = false;
        try {
            Image img = new Image(getClass().getResourceAsStream(path));
            backgroundView = new ImageView(img);

            // Forzar a llenar toda la ventana (sin mantener proporción)
            backgroundView.setPreserveRatio(false);
            backgroundView.setSmooth(true);
            backgroundView.setFitWidth(VIEW_W);
            backgroundView.setFitHeight(VIEW_H);

            // Ajustar el mundo al tamaño de la vista
            worldW = VIEW_W;
            worldH = VIEW_H;
            world.setPrefSize(worldW, worldH);

            world.getChildren().clear();
            world.getChildren().add(backgroundView);

            if (!world.getChildren().contains(heroView)) {
                world.getChildren().add(heroView);
            } else {
                heroView.toFront();
            }
            ret = true;
        } catch (Throwable t) {
            Text err = new Text("No se pudo cargar la imagen del Volcán.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);
        }
        return ret;
    }

    private boolean startDungeonMusic(String path) {
        boolean started = false;
        try {
            URL res = getClass().getResource(path);
            if (res != null) {
                Media media = new Media(res.toExternalForm());
                stopDungeonMusic();
                music = new MediaPlayer(media);
                music.setCycleCount(MediaPlayer.INDEFINITE);
                music.setVolume(MainScreen.getVolumeSetting());
                music.play();
                started = true;
            }
        } catch (Exception t) {
            started = false;
        }
        return started;
    }

    private void stopDungeonMusic() {
        try {
            if (music != null) {
                music.stop();
                music.dispose();
                music = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private ImageView createHeroView() {
        Image img;
        try {
            img = new Image(getClass().getResourceAsStream(game.getHero().getSpritePath()));
        } catch (Throwable ignored) {
            img = null;
        }
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(HERO_W);
        iv.setFitHeight(HERO_H);
        iv.setMouseTransparent(true);
        return iv;
    }

    // ---------------- colisiones ----------------
    private void populateCastleObstacles() {
        obstacles.clear();

        double[][] COLLISIONS = new double[][]{
            //lava
            {77.677381999999994, 0.0},
            {77.677381999999994, 40.0},
            {77.677381999999994, 80.0},
            {77.677381999999994, 120.0},
            {115.677381999999994, 148.0},
            {150.677381999999994, 188.0},
            {190.677381999999994, 188.0},
            {190.677381999999994, 220.0},
            {190.677381999999994, 260.0},
            {190.677381999999994, 300.0},
            {230.677381999999994, 340.0},
            {270.677381999999994, 380.0},
            {310.677381999999994, 380.0},
            {340.677381999999994, 380.0},
            {340.677381999999994, 420.0},
            {340.677381999999994, 460.0},
            {340.677381999999994, 480.0},
            {300.677381999999994, 480.0},
            {260.677381999999994, 480.0},
            {220.677381999999994, 440.0},
            {180.677381999999994, 400.0},
            {140.677381999999994, 400.0},
            {120.677381999999994, 400.0},
            {80.677381999999994, 440.0},
            {80.677381999999994, 480.0},
            {80.677381999999994, 520.0},
            {80.677381999999994, 560.0},
            {273.29193199999935, 262.86739400000016},
            {548.5359859999999, 390.5738959999999},
            {600.245902, 350.54233600000043},
            {548.5359859999999, 430.5738959999999},
            {548.5359859999999, 470.5738959999999},
            {548.5359859999999, 490.5738959999999},
            {588.5359859999999, 490.5738959999999},
            {625.5359859999999, 490.5738959999999},
            {625.5359859999999, 450.5738959999999},
            {625.5359859999999, 410.5738959999999},
            {663.5359859999999, 370.5738959999999},
            {663.5359859999999, 330.5738959999999},
            {703.5359859999999, 290.5738959999999},
            {743.5359859999999, 250.5738959999999},
            //rocas camino
            {590.9176799999984, 270.4184319999998},
            {554.9176799999984, 227.4184319999998},
            {554.9176799999984, 180.4184319999998},
            {554.9176799999984, 140.4184319999998},
            {554.9176799999984, 100.4184319999998},
            {548.9176799999984, 60.4184319999998},
            {540.9176799999984, 40.4184319999998},
            {350.51289800000006, 215.2297240000001},
            {325.51289800000006, 190.2297240000001},
            {340.51289800000006, 150.2297240000001},
            {345.51289800000006, 110.2297240000001},
            {345.51289800000006, 90.2297240000001},
            {345.51289800000006, 50.2297240000001},
            {345.51289800000006, 10.2297240000001},
            {430.2840279999998, 80.80925999999999},
            {450.2840279999998, 80.80925999999999},
            {430.2840279999998, 130.80925999999999},
            {450.2840279999998, 130.80925999999999},
            {430.2840279999998, 170.80925999999999},
            {450.2840279999998, 170.80925999999999},};

        int idx = 1;
        for (double[] p : COLLISIONS) {
            obstacles.add(new Obstacle(
                    new Rectangle2D(p[0], p[1], 25, 25),
                    ObstacleType.BLOCK,
                    "Collision" + idx
            ));
            idx++;
        }

        if (!(game.getHero().existsCompletedTask(game.searchTask("M004")))) {
            double[][] COLLISIONS2 = new double[][]{
                {428.31, 392.7951659999998},
                {396.7416540000001, 392.7951659999998},
                {367.7265540000001, 392.7951659999998},
                {480.0751739999999, 392.7951659999998}

            };

            for (double[] p : COLLISIONS2) {
                double x = p[0];
                double y = p[1];
                obstacles.add(new Obstacle(
                        new Rectangle2D(x, y, 30, 30),
                        ObstacleType.PLANT,
                        "Collision" + idx
                ));
                idx++;
            }
        }
    }

    // ---------------- movimiento y entradas ----------------
    private void positionHeroAtEntrance() {
        // Ajusta estas coordenadas al punto de entrada real del primer piso
        double startX = 428.31;
        double startY = 552;
        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {
        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }

        // Coordenadas específicas que proporcionaste
        double[] xs = {465.6887279999999, 422.4077639999999, 396.35299799999996};
        double[] ys = {552, 552, 552};

        // Calcular límites mínimos y máximos EXACTOS
        double minX = Arrays.stream(xs).min().getAsDouble();  // 394.983306
        double maxX = Arrays.stream(xs).max().getAsDouble();  // 466.96125599999993
        double minY = Arrays.stream(ys).min().getAsDouble();  // 540.5982060000001
        double maxY = Arrays.stream(ys).max().getAsDouble();  // 543.3095460000001

        // Crear rectángulo EXACTO que cubra solo esos puntos
        double rx = minX;
        double ry = minY;
        double rw = maxX - minX;  // 466.96125599999993 - 394.983306 = 71.97794999999993
        double rh = maxY - minY;  // 543.3095460000001 - 540.5982060000001 = 2.71134

        startRect = new Rectangle(rx, ry, rw, rh);
        startRect.setFill(Color.rgb(0, 120, 255, 0.28));
        startRect.setStroke(Color.rgb(0, 80, 200, 0.9));
        startRect.setMouseTransparent(true);
        startRect.getProperties().put("tag", "exit_area");

        if (!world.getChildren().contains(startRect)) {
            world.getChildren().add(startRect);
        }

        startRect.toBack();
        heroView.toFront();
    }

    private void createCastleRect() {

        if (castleRect != null) {
            world.getChildren().remove(castleRect);
            castleRect = null;
        }

        double[] xs = {0, 0, 0};
        double[] ys = {0.0, 0.0, 0.0};

        // Calcular límites mínimos y máximos
        double minX = Arrays.stream(xs).min().getAsDouble();
        double maxX = Arrays.stream(xs).max().getAsDouble();
        double minY = Arrays.stream(ys).min().getAsDouble();
        double maxY = Arrays.stream(ys).max().getAsDouble();

        // Definir rectángulo que cubra toda la zona de avance
        double rx = minX;
        double ry = minY;
        double rw = (maxX - minX) + HERO_W; // ancho cubriendo todo el rango
        double rh = HERO_H + 20;            // altura suficiente para detectar al héroe

        castleRect = new Rectangle(rx, ry, rw, rh);
        castleRect.setFill(Color.rgb(200, 120, 0, 0.28));
        castleRect.setStroke(Color.rgb(180, 80, 0, 0.9));
        castleRect.setMouseTransparent(true);
        castleRect.getProperties().put("tag", "castle_area");

        if (!world.getChildren().contains(castleRect)) {
            world.getChildren().add(castleRect);
        }
        castleRect.toBack();
        heroView.toFront();
    }

    public Point2D getHeroMapCenter() {
        double cx = heroView.getLayoutX() + heroView.getBoundsInLocal().getWidth() / 2.0;
        double cy = heroView.getLayoutY() + heroView.getBoundsInLocal().getHeight() / 2.0;
        return new Point2D(cx, cy);
    }

    private void installInputHandlers() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode k = ev.getCode();

            if (k == KeyCode.W || k == KeyCode.UP) {
                keys.add(KeyCode.W);
                heroView.setImage(game.getHero().getSpriteForDirection("Up"));
            }
            if (k == KeyCode.S || k == KeyCode.DOWN) {
                keys.add(KeyCode.S);
                heroView.setImage(game.getHero().getSpriteForDirection("Down"));
            }
            if (k == KeyCode.A || k == KeyCode.LEFT) {
                keys.add(KeyCode.A);
                heroView.setImage(game.getHero().getSpriteForDirection("Left"));
            }
            if (k == KeyCode.D || k == KeyCode.RIGHT) {
                keys.add(KeyCode.D);
                heroView.setImage(game.getHero().getSpriteForDirection("Right"));
            }

            if (k == KeyCode.P) {
                System.out.println("(" + heroView.getLayoutX() + ", " + heroView.getLayoutY() + ")");

            }

            if (k == KeyCode.I || k == KeyCode.ADD || k == KeyCode.PLUS) {
                clearInputState();
                openInventory();
            }
            if (k == KeyCode.B) {
                clearInputState();
                openDebugCombat();
            }
            if (k == KeyCode.R) {
                debugEnabled = !debugEnabled;
                if (debugEnabled) {
                    drawDebugObstacles();
                } else {
                    world.getChildren().removeIf(n -> "obstacle_debug".equals(n.getProperties().get("tag")));
                }

            }

            if (k == KeyCode.ENTER) {

                if (bossView != null) {
                    checkBossTriggers();
                }
                if (checkOrbTrigger()) {
                    game.completeMainM002();
                    collectOrb();
                    showBottomDialogRPG("Item Obtained", "You have received the Fire Orb", null);
                }
                checkExitTrigger();

            }

            ev.consume();
        });

        root.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
            KeyCode k = ev.getCode();
            if (k == KeyCode.W || k == KeyCode.UP) {
                keys.remove(KeyCode.W);
            }
            if (k == KeyCode.S || k == KeyCode.DOWN) {
                keys.remove(KeyCode.S);
            }
            if (k == KeyCode.A || k == KeyCode.LEFT) {
                keys.remove(KeyCode.A);
            }
            if (k == KeyCode.D || k == KeyCode.RIGHT) {
                keys.remove(KeyCode.D);
            }
            ev.consume();
        });

        root.setFocusTraversable(true);
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(root::requestFocus);
            } else {
                clearInputState();
            }
        });
    }

    private void createMover() {
        mover = new AnimationTimer() {
            private long last = -1;

            @Override
            public void handle(long now) {
                if (last < 0) {
                    last = now;
                }
                double dt = (now - last) / 1e9;
                last = now;

                if (root.getScene() == null || !root.isFocused()) {
                    clearInputState();
                } else {
                    updateAndMove(dt);
                }
            }
        };
    }

    public void startMover() {
        if (mover != null) {
            mover.start();
        }
    }

    public void stopMover() {
        if (mover != null) {
            mover.stop();
        }
    }

    private void updateAndMove(double dt) {
        double vx = 0;
        double vy = 0;
        if (keys.contains(KeyCode.A)) {
            vx -= HERO_SPEED;
        }
        if (keys.contains(KeyCode.D)) {
            vx += HERO_SPEED;
        }
        if (keys.contains(KeyCode.W)) {
            vy -= HERO_SPEED;
        }
        if (keys.contains(KeyCode.S)) {
            vy += HERO_SPEED;
        }

        boolean shouldMove = !(vx == 0 && vy == 0);

        if (shouldMove) {
            moveHero(vx * dt, vy * dt);
        }
    }

    private void moveHero(double dx, double dy) {
        double curX = heroView.getLayoutX();
        double curY = heroView.getLayoutY();

        double proposedX = clamp(curX + dx, 0, Math.max(0, worldW - HERO_W));
        double proposedY = clamp(curY + dy, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, HERO_W, HERO_H);
        boolean collision = false;

        // Primer bucle sin break
        int i = 0;
        int obstacleCount = obstacles.size();
        while (i < obstacleCount && !collision) {
            Obstacle ob = obstacles.get(i);
            if (heroRect.intersects(ob.collisionRect)) {
                collision = true;
            }
            i++;
        }

        if (!collision) {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        } else {
            // Intento separar ejes X/Y para movimiento "slide"
            Rectangle2D heroRectX = new Rectangle2D(proposedX, curY, HERO_W, HERO_H);
            Rectangle2D heroRectY = new Rectangle2D(curX, proposedY, HERO_W, HERO_H);

            boolean canMoveX = true;
            boolean canMoveY = true;

            // Segundo bucle sin break
            i = 0;
            boolean stopChecking = false;
            while (i < obstacleCount && !stopChecking) {
                Obstacle ob = obstacles.get(i);

                if (canMoveX && heroRectX.intersects(ob.collisionRect)) {
                    canMoveX = false;
                }
                if (canMoveY && heroRectY.intersects(ob.collisionRect)) {
                    canMoveY = false;
                }

                if (!canMoveX && !canMoveY) {
                    stopChecking = true;
                }

                i++;
            }

            if (canMoveX) {
                heroView.setLayoutX(proposedX);
            }
            if (canMoveY) {
                heroView.setLayoutY(proposedY);
            }
        }

        updateCamera();
    }

    private void updateCamera() {
        double heroCenterX = heroView.getLayoutX() + HERO_W / 2.0;
        double heroCenterY = heroView.getLayoutY() + HERO_H / 2.0;

        double targetTx = VIEW_W / 2.0 - heroCenterX;
        double targetTy = VIEW_H / 2.0 - heroCenterY;

        double minTx = Math.min(0, VIEW_W - worldW);
        double maxTx = 0;
        double minTy = Math.min(0, VIEW_H - worldH);
        double maxTy = 0;

        double tx = clamp(targetTx, minTx, maxTx);
        double ty = clamp(targetTy, minTy, maxTy);

        world.setTranslateX(tx);
        world.setTranslateY(ty);
    }

    private static double clamp(double v, double lo, double hi) {
        double result = v;
        if (v < lo) {
            result = lo;
        } else if (v > hi) {
            result = hi;
        }
        return result;
    }

    private void clearInputState() {
        keys.clear();
    }

    public void startMapMusic() {
        try {
            stopMapMusic();
            URL res = getClass().getResource("/Resources/music/volcanoDungeon.mp3");
            boolean hasRes = res != null;
            if (hasRes) {
                Media media = new Media(res.toExternalForm());
                music = new MediaPlayer(media);
                music.setCycleCount(MediaPlayer.INDEFINITE);
                music.setVolume(MainScreen.getVolumeSetting());
                music.play();

                AudioManager.register(music);
            }
        } catch (Throwable ignored) {
        }
    }

    public void stopMapMusic() {
        try {
            boolean exists = music != null;
            if (exists) {
                AudioManager.unregister(music);
                music.stop();
                music.dispose();
                music = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawDebugObstacles() {

        if (startRect != null) {
            startRect.setFill(debugEnabled ? Color.rgb(0, 120, 255, 0.42) : Color.rgb(0, 120, 255, 0.28));
        }
        if (castleRect != null) {
            castleRect.setFill(debugEnabled ? Color.rgb(200, 120, 0, 0.42) : Color.rgb(200, 120, 0, 0.28));
        }
        heroView.toFront();
    }

    private void openInventory() {
        stopMover();

        try {
            if (music != null) {
                music.pause();
            }
        } catch (Throwable ignored) {
        }

        inventory = new InventoryScreen(game, this);

        inventory.setOnClose(() -> {
            Platform.runLater(() -> {
                try {
                    FXGL.getGameScene().removeUINode(inventory.getRoot());
                } catch (Throwable ignored) {
                }
                startMover();
                try {
                    if (music != null) {
                        music.play();
                    }
                } catch (Throwable ignored) {
                }
                root.requestFocus();
            });
        });

        inventory.show();
        Platform.runLater(() -> {
            try {
                inventory.getRoot().requestFocus();
            } catch (Throwable ignored) {
            }
        });
    }

    private void openDebugCombat() {
        String bg = "/Resources/textures/Battle/VolcanoDungeonBattle.png";
        stopMapMusic();

        GUI.CombatScreen cs = new GUI.CombatScreen(game, bg, "Sky", game.getHero(), false, null);
        cs.setBattleMusicPath("/Resources/music/fieldBattle.mp3");

        cs.setOnExit(() -> {
            Platform.runLater(() -> {
                try {
                    FXGL.getGameScene().removeUINode(cs.root);
                } catch (Throwable ignored) {
                }
                try {
                    FXGL.getGameScene().addUINode(root);
                } catch (Throwable ignored) {
                }
                root.requestFocus();
                startMapMusic();
            });
        });

        Platform.runLater(() -> {
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
            cs.show();
        });
    }

    // Trigger de salida: volver al mapa anterior
    private void checkExitTrigger() {
        Rectangle2D heroRect = new Rectangle2D(
                heroView.getLayoutX(),
                heroView.getLayoutY(),
                HERO_W,
                HERO_H
        );

        if (startRect != null) {
            Rectangle2D startArea = new Rectangle2D(
                    startRect.getX(),
                    startRect.getY(),
                    startRect.getWidth(),
                    startRect.getHeight()
            );

            if (heroRect.intersects(startArea)) {
                clearInputState();

                if (onExitCallback != null) {
                    hide();
                    onExitCallback.run(); // vuelve al mapa anterior
                } else {
                    hide();
                }
            }
        }
    }

    //TODO LO RELACIONADO AL MANEJO DEL BOSS
    public void drawBossDungeon() {
        if (!game.getHero().existsCompletedTask(game.searchTask("M004"))) {
            createBossTriggerRects();

            boolean skipCreate = false;
            if (bossView != null) {
                if (!world.getChildren().contains(bossView)) {
                    world.getChildren().add(bossView);
                }
                bossView.toFront();
                skipCreate = true;
            }

            if (!skipCreate) {
                try {
                    Image img = new Image(getClass().getResourceAsStream("/Resources/sprites/Monsters/volcanoBoss00.png"));
                    bossView = new ImageView(img);

                    bossView.setPreserveRatio(true);
                    bossView.setFitWidth(200);
                    bossView.setFitHeight(200);
                    bossView.setMouseTransparent(true);

                    bossView.setLayoutX(344.87427599999984);
                    bossView.setLayoutY(259.01579999999984);

                    bossView.getProperties().put("tag", "volcano_boss");

                    if (!world.getChildren().contains(bossView)) {
                        world.getChildren().add(bossView);
                    }
                    bossView.toFront();

                } catch (Exception t) {
                    System.err.println("No se pudo cargar la imagen del boss: " + t.getMessage());
                }
            }

        } else {
            if (bossView != null) {
                try {
                    world.getChildren().remove(bossView);
                } catch (Exception ignored) {
                }
                bossView = null;
            }
        }
    }

    private void createBossTriggerRects() {
        if (!game.getHero().existsCompletedTask(game.searchTask("M004"))) {
            for (Rectangle r : bossTriggerRects) {
                try {
                    world.getChildren().remove(r);
                } catch (Throwable ignored) {
                }
            }
            bossTriggerRects.clear();

            double[][] TRIGGERS = new double[][]{
                {373.65856199999996, 373.49356800000015},
                {402.45651, 373.49356800000015},
                {437.0306939999999, 373.49356800000015},
                {465.9549839999999, 373.49356800000015},
                {497.75021999999996, 373.49356800000015}
            };

            for (int i = 0; i < TRIGGERS.length; i++) {
                double x = TRIGGERS[i][0];
                double y = TRIGGERS[i][1];
                double w = HERO_W + 8;
                double h = HERO_H + 8;
                Rectangle r = new Rectangle(x - 4, y - 4, w, h);
                r.setFill(Color.color(0, 0, 0, 0.0));
                r.setStroke(null);
                r.setMouseTransparent(true);
                r.getProperties().put("tag", "boss_trigger");
                r.getProperties().put("id", "bossTRigger" + (i + 1));
                bossTriggerRects.add(r);
                if (!world.getChildren().contains(r)) {
                    world.getChildren().add(r);
                }
            }
            heroView.toFront();
        }
    }

    public void redrawRoomAfterBoss() {
        Platform.runLater(() -> {
            try {
                if (bossView != null) {
                    try {
                        world.getChildren().remove(bossView);
                    } catch (Throwable ignored) {
                    }
                    bossView = null;
                }

                try {
                    for (Rectangle r : bossTriggerRects) {
                        world.getChildren().remove(r);
                    }
                    bossTriggerRects.clear();
                } catch (Throwable ignored) {
                }

                obstacles.clear();
                populateCastleObstacles();
                if (!world.getChildren().contains(heroView)) {
                    world.getChildren().add(heroView);
                }
                heroView.toFront();

                checkExitTrigger();

                updateCamera();

                FadeTransition ft = new FadeTransition(Duration.millis(260), root);
                ft.setFromValue(0.95);
                ft.setToValue(1.0);
                ft.play();

            } catch (Throwable t) {
                System.err.println("Error al redibujar la sala tras boss: " + t.getMessage());
            }
        });
    }

    private void checkBossTriggers() {
        boolean combat = false;

        if (bossView != null) {
            double hx = heroView.getLayoutX();
            double hy = heroView.getLayoutY();
            Rectangle2D heroRect = new Rectangle2D(hx, hy, HERO_W, HERO_H);

            for (Rectangle trigger : bossTriggerRects) {
                Rectangle2D tr = new Rectangle2D(trigger.getX(), trigger.getY(), trigger.getWidth(), trigger.getHeight());
                if (heroRect.intersects(tr)) {
                    combat = true;
                }
            }
        }

        if (combat) {
            battleAgainstBoss((Boss) game.getCharacters().get(17));

        }
    }

    private void battleAgainstBoss(Boss boss) {
        String bg = "/Resources/textures/Battle/VolcanoDungeonBattle.png";
        stopMapMusic();

        CombatScreen cs = new GUI.CombatScreen(game, bg, "Volcano", game.getHero(), true, boss);

        cs.setBattleMusicPath("/Resources/music/bossBattle2.mp3");

        cs.setOnExit(() -> {
            Platform.runLater(() -> {
                try {
                    FXGL.getGameScene().removeUINode(cs.root);
                } catch (Throwable ignored) {
                }
                try {
                    FXGL.getGameScene().addUINode(root);
                } catch (Throwable ignored) {
                }
                startMapMusic();
                root.requestFocus();
            });
        });

        Platform.runLater(() -> {
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
            cs.show();
        });

        game.completeMainM004();
        redrawRoomAfterBoss();

    }

    public void createOrbTrigger() {
        if (game.getHero().existsCompletedTask(game.searchTask("M002"))) {
            if (orbNode != null) {
                try {
                    world.getChildren().remove(orbNode);
                } catch (Throwable ignored) {
                }
                orbNode = null;
            }
            orbTrigger = null;
        } else {
            double x = 434.6619119999998;
            double y = 195.846294;
            orbTrigger = new Rectangle2D(x - 4, y - 4, HERO_W + 8, HERO_H + 8);
            if (orbNode == null) {
                Rectangle r = new Rectangle(orbTrigger.getWidth(), orbTrigger.getHeight());
                r.setLayoutX(orbTrigger.getMinX());
                r.setLayoutY(orbTrigger.getMinY());
                r.setFill(Color.TRANSPARENT);
                r.setStroke(null);
                r.setMouseTransparent(true);
                orbNode = r;
                if (!world.getChildren().contains(orbNode)) {
                    world.getChildren().add(orbNode);
                }
                orbNode.toFront();
                heroView.toFront();

            } else {
                orbNode.setLayoutX(orbTrigger.getMinX());
                orbNode.setLayoutY(orbTrigger.getMinY());
            }
        }
    }

    public boolean checkOrbTrigger() {
        boolean intersects = false;
        if (orbTrigger != null) {
            Rectangle2D heroRect = new Rectangle2D(heroView.getLayoutX(), heroView.getLayoutY(), HERO_W, HERO_H);
            intersects = heroRect.intersects(orbTrigger);
            if (intersects) {
                if (orbHintText == null) {
                    orbHintText = new Text("Press ENTER to pick up");
                    orbHintText.setStyle("-fx-font-size: 12px; -fx-fill: #fffacd; -fx-stroke: #00000055;");
                    orbHintText.setMouseTransparent(true);
                    world.getChildren().add(orbHintText);
                }
                orbHintText.setLayoutX(heroView.getLayoutX());
                orbHintText.setLayoutY(heroView.getLayoutY() - 10);
                if (!world.getChildren().contains(orbHintText)) {
                    world.getChildren().add(orbHintText);
                }
                orbHintText.toFront();
                heroView.toFront();
            } else {
                if (orbHintText != null) {
                    try {
                        world.getChildren().remove(orbHintText);
                    } catch (Throwable ignored) {
                    }
                    orbHintText = null;
                }
            }
        } else {
            if (orbHintText != null) {
                try {
                    world.getChildren().remove(orbHintText);
                } catch (Throwable ignored) {
                }
                orbHintText = null;
            }
        }
        return intersects;
    }

    public void collectOrb() {
        if (orbNode != null) {
            try {
                world.getChildren().remove(orbNode);
            } catch (Throwable ignored) {
            }
            orbNode = null;
        }
        orbTrigger = null;
        if (orbHintText != null) {
            try {
                world.getChildren().remove(orbHintText);
            } catch (Throwable ignored) {
            }
            orbHintText = null;
        }
    }

    private void showBottomDialogRPG(String title, String message, String iconResourcePath) {
        Platform.runLater(() -> {
            boolean foundExisting = false;
            StackPane existingOverlay = null;
            Button existingOkBtn = null;

            for (Node child : root.getChildren()) {
                Object flag = child.getProperties().get("rpgDialog");
                if (Boolean.TRUE.equals(flag) && child instanceof StackPane) {
                    existingOverlay = (StackPane) child;
                    Node db = existingOverlay.getChildren().isEmpty() ? null : existingOverlay.getChildren().get(0);
                    if (db instanceof HBox) {
                        HBox dialogBox = (HBox) db;
                        for (Node n : dialogBox.getChildren()) {
                            if (n instanceof VBox) {
                                VBox texts = (VBox) n;
                                if (texts.getChildren().size() >= 2 && texts.getChildren().get(1) instanceof Text) {
                                    Text tMsg = (Text) texts.getChildren().get(1);
                                    tMsg.setText(message);
                                }
                                if (texts.getChildren().size() >= 1 && texts.getChildren().get(0) instanceof Text) {
                                    Text tTitle = (Text) texts.getChildren().get(0);
                                    tTitle.setText(title);
                                }
                            }
                            if (n instanceof Button) {
                                existingOkBtn = (Button) n;
                            }
                        }
                    }
                    foundExisting = true;
                }
            }

            if (foundExisting && existingOverlay != null) {
                StackPane overlayRef = existingOverlay;
                Button okRef = existingOkBtn;
                Platform.runLater(() -> {
                    overlayRef.requestFocus();
                    if (okRef != null) {
                        okRef.requestFocus();
                    }
                });
            } else {
                stopMover();
                root.getProperties().put("dialogOpen", true);

                StackPane modalOverlay = new StackPane();
                modalOverlay.getProperties().put("rpgDialog", true);
                modalOverlay.setPrefSize(VIEW_W, VIEW_H);
                modalOverlay.setStyle("-fx-background-color: transparent;");
                modalOverlay.setPickOnBounds(true);
                modalOverlay.setFocusTraversable(true);

                HBox dialogBox = new HBox(10);
                dialogBox.setMinHeight(72);
                dialogBox.setMaxHeight(140);
                dialogBox.setMaxWidth(420);
                dialogBox.setPrefWidth(420);
                dialogBox.setStyle(
                        "-fx-background-color: rgba(0,0,0,0.88);"
                        + "-fx-padding: 10 12 10 12;"
                        + "-fx-background-radius: 6;"
                        + "-fx-border-radius: 6;"
                        + "-fx-border-color: rgba(255,255,255,0.06);"
                        + "-fx-border-width: 1;"
                );
                dialogBox.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.7)));
                dialogBox.setAlignment(Pos.CENTER_LEFT);

                ImageView iconView = null;
                if (iconResourcePath != null) {
                    try {
                        Image icon = new Image(getClass().getResourceAsStream(iconResourcePath));
                        iconView = new ImageView(icon);
                        iconView.setFitWidth(44);
                        iconView.setFitHeight(44);
                        iconView.setPreserveRatio(true);
                    } catch (Throwable ignored) {
                        iconView = null;
                    }
                }

                VBox texts = new VBox(3);
                Text tTitle = new Text(title);
                tTitle.setStyle("-fx-font-size: 13px; -fx-fill: #f5f5f5; -fx-font-weight: 700;");
                Text tMsg = new Text(message);
                tMsg.setWrappingWidth(420 - 140);
                tMsg.setStyle("-fx-font-size: 12px; -fx-fill: #e6e6e6;");
                texts.getChildren().addAll(tTitle, tMsg);

                Button okBtn = new Button("Ok");
                okBtn.setDefaultButton(true);
                okBtn.setStyle(
                        "-fx-background-color: linear-gradient(#444444, #222222);"
                        + "-fx-text-fill: #ffffff;"
                        + "-fx-font-weight: 600;"
                        + "-fx-background-radius: 6;"
                        + "-fx-padding: 6 10 6 10;"
                );
                okBtn.setOnAction(e -> fadeOutAndRemove(modalOverlay));

                if (iconView != null) {
                    dialogBox.getChildren().addAll(iconView, texts, okBtn);
                } else {
                    dialogBox.getChildren().addAll(texts, okBtn);
                }

                StackPane.setAlignment(dialogBox, Pos.BOTTOM_CENTER);
                StackPane.setMargin(dialogBox, new Insets(0, 20, 12, 20));
                modalOverlay.getChildren().add(dialogBox);

                root.getChildren().add(modalOverlay);

                modalOverlay.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, ev -> {
                    Bounds b = dialogBox.localToScene(dialogBox.getBoundsInLocal());
                    if (!b.contains(ev.getSceneX(), ev.getSceneY())) {
                        ev.consume();
                    }
                });

                TranslateTransition tt = new TranslateTransition(Duration.millis(220), dialogBox);
                tt.setFromY(28);
                tt.setToY(0);
                tt.play();

                FadeTransition ftIn = new FadeTransition(Duration.millis(160), dialogBox);
                ftIn.setFromValue(0.0);
                ftIn.setToValue(1.0);
                ftIn.play();

                Platform.runLater(() -> {
                    modalOverlay.requestFocus();
                    okBtn.requestFocus();
                });

                Platform.runLater(() -> {
                    javafx.scene.Scene scene = root.getScene();
                    if (scene != null) {
                        javafx.event.EventHandler<KeyEvent> sceneHandler = ev -> {
                            if (Boolean.TRUE.equals(root.getProperties().get("dialogOpen"))) {
                                if (ev.getCode() == KeyCode.ENTER || ev.getCode() == KeyCode.ESCAPE) {
                                    ev.consume();
                                    Platform.runLater(() -> {
                                        try {
                                            okBtn.fire();
                                        } catch (Throwable ignored) {
                                        }
                                    });
                                } else {
                                    ev.consume();
                                }
                            }
                        };
                        modalOverlay.getProperties().put("sceneKeyHandler", sceneHandler);
                        scene.addEventFilter(KeyEvent.KEY_PRESSED, sceneHandler);
                    }
                });

                modalOverlay.getProperties().put("onRemoved", (Runnable) () -> {
                    startMover();
                    root.getProperties().put("dialogOpen", false);
                });
            }
        });
    }

    private void fadeOutAndRemove(StackPane modalOverlay) {
        final Runnable[] resumeArr = new Runnable[1];
        if (modalOverlay != null) {
            Node dialogBox = modalOverlay.getChildren().isEmpty() ? null : modalOverlay.getChildren().get(0);
            try {
                Object o = modalOverlay.getProperties().get("onRemoved");
                if (o instanceof Runnable) {
                    resumeArr[0] = (Runnable) o;
                }
            } catch (Throwable ignored) {
            }

            try {
                Object handlerObj = modalOverlay.getProperties().remove("sceneKeyHandler");
                if (handlerObj instanceof javafx.event.EventHandler) {
                    javafx.scene.Scene scene = root.getScene();
                    if (scene != null) {
                        @SuppressWarnings("unchecked")
                        javafx.event.EventHandler<KeyEvent> h = (javafx.event.EventHandler<KeyEvent>) handlerObj;
                        scene.removeEventFilter(KeyEvent.KEY_PRESSED, h);
                    }
                }
            } catch (Throwable ignored) {
            }

            if (dialogBox != null) {
                FadeTransition ftOut = new FadeTransition(Duration.millis(140), dialogBox);
                ftOut.setFromValue(1.0);
                ftOut.setToValue(0.0);
                ftOut.setOnFinished(ev -> {
                    root.getChildren().remove(modalOverlay);
                    if (resumeArr[0] != null) {
                        resumeArr[0].run();
                    }
                });
                ftOut.play();
            } else {
                root.getChildren().remove(modalOverlay);
                if (resumeArr[0] != null) {
                    resumeArr[0].run();
                }
            }
        } else {
            try {
                Object o = root.getProperties().get("onRemovedFallback");
                if (o instanceof Runnable) {
                    ((Runnable) o).run();
                }
            } catch (Throwable ignored) {
            }
        }
    }

}
