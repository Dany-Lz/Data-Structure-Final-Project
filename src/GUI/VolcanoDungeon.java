package GUI;

import Characters.Boss;
import Characters.Hero;
import Logic.Game;
import Misc.Task;
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
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
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
    private boolean debugEnabled = false; // R para ver/ocultar áreas de trigger

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
        BLOCK, PLANT
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
            {450.2840279999998, 170.80925999999999},
        };

        int idx = 1;
        for (double[] p : COLLISIONS) {
            obstacles.add(new Obstacle(
                    new Rectangle2D(p[0], p[1], 25, 25),
                    ObstacleType.BLOCK,
                    "SkyCollision" + idx
            ));
            idx++;
        }

        if (!(game.getHero().existsCompletedTask(game.searchTask("M004")))) {
            double[][] COLLISIONS2 = new double[][]{
                {1250.0244400000004, 345.49560000000133},
                {1192.1942379999996, 345.49560000000133},
                {1124.338071999999, 345.49560000000133},
                {1069.9219839999992, 345.49560000000133},
                {1015.4302239999992, 345.49560000000133},
                {966.936135999999, 345.49560000000133},
                {912.9653319999989, 345.49560000000133},
                {858.2888739999987, 345.49560000000133},};

            for (double[] p : COLLISIONS2) {
                double x = p[0];
                double y = p[1];
                obstacles.add(new Obstacle(
                        new Rectangle2D(x, y, 30, 30),
                        ObstacleType.PLANT,
                        "Colision" + idx
                ));
                idx++;
            }
        }
    }

    // ---------------- movimiento y entradas ----------------
    private void positionHeroAtEntrance() {
        // Ajusta estas coordenadas al punto de entrada real del primer piso
        double startX = 428.31;
        double startY =552;
        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {

        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }

        // Coordenadas iniciales del héroe
        double[] xs = {121.40199400000095, 69.70930600000092, 175.1709820000011};
        double[] ys = {0.0, 0.0, 0.0};

        // Calcular límites mínimos y máximos
        double minX = Arrays.stream(xs).min().getAsDouble();
        double maxX = Arrays.stream(xs).max().getAsDouble();
        double minY = Arrays.stream(ys).min().getAsDouble();
        double maxY = Arrays.stream(ys).max().getAsDouble();

        // Definir rectángulo que cubra toda la zona inicial
        double rx = minX;
        double ry = minY - HERO_H; // un poco hacia arriba para cubrir al héroe
        double rw = (maxX - minX) + HERO_W;
        double rh = HERO_H + 20;   // altura suficiente

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

        double[] xs = {746.5826560000006, 702.1915240000005, 663.7390240000004};
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

                boolean proceed = true;
                if (root.getScene() == null || !root.isFocused()) {
                    clearInputState();
                    proceed = false;
                }

                if (proceed) {
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
        boolean proceed = true;
        if (heroView == null) {
            proceed = false;
        }

        double curX = 0, curY = 0;
        if (proceed) {
            curX = heroView.getLayoutX();
            curY = heroView.getLayoutY();
        }

        double proposedX = curX;
        double proposedY = curY;
        if (proceed) {
            proposedX = clamp(curX + dx, 0, Math.max(0, worldW - HERO_W));
            proposedY = clamp(curY + dy, 0, Math.max(0, worldH - HERO_H));
        }

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, HERO_W, HERO_H);
        boolean collision = false;

        if (proceed && obstacles != null) {
            for (Obstacle ob : obstacles) {
                if (ob == null || ob.collisionRect == null) {
                    continue;
                }
                if (heroRect.intersects(ob.collisionRect)) {
                    collision = true;
                    break;
                }
            }
        }

        if (proceed && !collision) {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        } else if (proceed) {

            Rectangle2D heroRectX = new Rectangle2D(proposedX, curY, HERO_W, HERO_H);
            Rectangle2D heroRectY = new Rectangle2D(curX, proposedY, HERO_W, HERO_H);

            boolean canMoveX = true;
            boolean canMoveY = true;

            if (obstacles != null) {
                for (Obstacle ob : obstacles) {
                    if (ob == null || ob.collisionRect == null) {
                        continue;
                    }
                    if (heroRectX.intersects(ob.collisionRect)) {
                        canMoveX = false;
                    }
                    if (heroRectY.intersects(ob.collisionRect)) {
                        canMoveY = false;
                    }
                    if (!canMoveX && !canMoveY) {
                        break;
                    }
                }
            }

            if (canMoveX) {
                heroView.setLayoutX(proposedX);
            }
            if (canMoveY) {
                heroView.setLayoutY(proposedY);
            }
        }

        if (proceed) {
            updateCamera();
        }
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
                try {
                    if (game != null && game.getHero() != null) {
                        Hero h = game.getHero();
                        h.setLastLocation(Hero.Location.SKY_DUNGEON);
                        h.setLastPosX(heroView.getLayoutX());
                        h.setLastPosY(heroView.getLayoutY());
                        try {
                            game.createSaveGame();
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }

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
        if (!game.getHero().existsCompletedTask(game.getTasks().get(0))) {
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

                    bossView.setLayoutX(277.4965360000014);
                    bossView.setLayoutY(87.53349600000011);

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
        if (!game.getHero().existsCompletedTask(game.getTasks().get(3))) {
            for (Rectangle r : bossTriggerRects) {
                try {
                    world.getChildren().remove(r);
                } catch (Throwable ignored) {
                }
            }
            bossTriggerRects.clear();

            double[][] TRIGGERS = new double[][]{
                {1176.5769699999987, 348.5237580000012},
                {1117.6297599999987, 348.5237580000012},
                {1083.1165059999987, 348.5237580000012},
                {1051.7367999999985, 348.5237580000012},
                {1017.1041339999991, 348.5237580000012},};

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

        CombatScreen cs = new GUI.CombatScreen(game, bg, "Sky", game.getHero(), true, boss);

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
}
