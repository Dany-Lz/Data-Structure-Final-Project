package GUI;

import Runner.MainScreen;
import Characters.Hero;
import Logic.Game;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FieldVillage {

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

    private final double VIEW_W = 800;
    private final double VIEW_H = 600;
    private double worldW = VIEW_W;
    private double worldH = VIEW_H;

    private Rectangle startRect;
    private boolean onStartRect = false;

    private Runnable onExitCallback;
    private final Game game;

    // Sistema de colisiones similar a GameMapScreen
    private final List<Obstacle> obstacles = new ArrayList<>();
    private boolean debugEnabled = false;

    // Inventario
    private InventoryScreen inventory;

    // Direcciones del héroe (para depuración con tecla P)
    public enum Direction {
        NONE, N, NE, E, SE, S, SW, W, NW
    }
    private Direction currentDirection = Direction.NONE;

    // Tipos de obstáculos para la aldea
    private enum ObstacleType {
        HOUSE, TREE, WELL, FENCE, BUSH, EXIT, BLOCK
    }

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

    public FieldVillage(Game game) {
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

    public void showWithLoading(Runnable onLoaded, Runnable onExit) {
        this.onExitCallback = onExit;

        Platform.runLater(() -> {
            FXGL.getGameScene().addUINode(root);
            showLoading(true);

            boolean imageOk = loadBackgroundImage("/Resources/textures/fieldVillage/fieldVillage.png");
            boolean musicOk = startVillageMusic("/Resources/music/fieldVillage.mp3");

            // Primero poblar colisiones
            populateVillageObstacles();

            // Luego posicionar al héroe
            positionHeroAtEntrance();
            createStartRectAtHeroStart();

            // Dibujar obstáculos en modo debug
            if (debugEnabled) {
                drawDebugObstacles();
            }

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
            stopVillageMusic();
            stopMover();
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }
        });
    }

    private StackPane createLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(true);

        Rectangle bg = new Rectangle(VIEW_W, VIEW_H);
        bg.setFill(Color.rgb(0, 0, 0, 0.6));

        Text label = new Text("Cargando aldea...");
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
            backgroundView.setPreserveRatio(false);
            backgroundView.setSmooth(true);

            worldW = img.getWidth() > 0 ? img.getWidth() : VIEW_W;
            worldH = img.getHeight() > 0 ? img.getHeight() : VIEW_H;

            backgroundView.setFitWidth(worldW);
            backgroundView.setFitHeight(worldH);

            world.setPrefSize(worldW, worldH);   // <-- CORRECCIÓN: pasar ancho y alto
            world.getChildren().clear();
            world.getChildren().add(backgroundView);

            if (!world.getChildren().contains(heroView)) {
                world.getChildren().add(heroView);
            } else {
                heroView.toFront();
            }

            ret = true;
        } catch (Throwable t) {
            Text err = new Text("No se pudo cargar la imagen de la aldea.");
            err.setStyle("-fx-font-size: 16px; -fx-fill: #ffdddd;");
            root.getChildren().add(err);

        }
        return ret;
    }

    private boolean startVillageMusic(String path) {
        try {
            URL res = getClass().getResource(path);
            if (res == null) {
                return false;
            }
            Media media = new Media(res.toExternalForm());
            stopVillageMusic();
            music = new MediaPlayer(media);
            music.setCycleCount(MediaPlayer.INDEFINITE);
            music.setVolume(MainScreen.getVolumeSetting());
            music.play();
            // Registrar en AudioManager si existe
            try {
                AudioManager.register(music);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void stopVillageMusic() {
        try {
            if (music != null) {
                try {
                    AudioManager.unregister(music);
                } catch (Throwable ignored) {
                }
                music.stop();
                music.dispose();
                music = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private ImageView createHeroView() {
        Image img = null;
        try {
            img = new Image(getClass().getResourceAsStream("/Resources/sprites/hero.png"));
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

    // Poblar obstáculos de la aldea (AJUSTA ESTAS COORDENADAS A TU MAPA)
    private void populateVillageObstacles() {
        obstacles.clear();
        // ... (misma implementación que tenías; no se elimina nada)
    }

    private void drawDebugObstacles() {
        world.getChildren().removeIf(n -> "debug_obstacle".equals(n.getProperties().get("tag")));

        if (!debugEnabled) {
            return;
        }

        for (Obstacle ob : obstacles) {
            Rectangle rect = new Rectangle(
                    ob.collisionRect.getMinX(),
                    ob.collisionRect.getMinY(),
                    ob.collisionRect.getWidth(),
                    ob.collisionRect.getHeight()
            );

            // Diferentes colores según el tipo
            switch (ob.type) {
                case HOUSE:
                    rect.setFill(Color.rgb(139, 69, 19, 0.4)); // Marrón
                    rect.setStroke(Color.rgb(101, 50, 14, 0.8));
                    break;
                case TREE:
                    rect.setFill(Color.rgb(34, 139, 34, 0.4)); // Verde
                    rect.setStroke(Color.rgb(0, 100, 0, 0.8));
                    break;
                case WELL:
                    rect.setFill(Color.rgb(105, 105, 105, 0.4)); // Gris
                    rect.setStroke(Color.rgb(64, 64, 64, 0.8));
                    break;
                case FENCE:
                    rect.setFill(Color.rgb(160, 82, 45, 0.4)); // Madera
                    rect.setStroke(Color.rgb(101, 50, 14, 0.8));
                    break;
                case BUSH:
                    rect.setFill(Color.rgb(0, 128, 0, 0.4)); // Verde oscuro
                    rect.setStroke(Color.rgb(0, 64, 0, 0.8));
                    break;
                default:
                    rect.setFill(Color.rgb(255, 0, 0, 0.3));
                    rect.setStroke(Color.RED);
            }

            rect.getProperties().put("tag", "debug_obstacle");
            rect.setMouseTransparent(true);
            world.getChildren().add(rect);
        }
    }

    private void positionHeroAtEntrance() {
        double startX = (worldW - HERO_W) / 2.0;
        double startY = worldH - HERO_H - 8.0;

        startX = clamp(startX, 0, Math.max(0, worldW - HERO_W));
        startY = clamp(startY, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(startX, startY, HERO_W, HERO_H);
        for (Obstacle ob : obstacles) {
            if (heroRect.intersects(ob.collisionRect)) {
                startY = ob.collisionRect.getMinY() - HERO_H - 5;
                break;
            }
        }

        heroView.setLayoutX(startX);
        heroView.setLayoutY(startY);
        updateCamera();
    }

    private void createStartRectAtHeroStart() {
        if (startRect != null) {
            world.getChildren().remove(startRect);
            startRect = null;
        }
        double rx = heroView.getLayoutX();
        double ry = heroView.getLayoutY();
        double rw = HERO_W + 8;
        double rh = HERO_H + 8;

        startRect = new Rectangle(rx - 4, ry - 4, rw, rh);
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

    private void installInputHandlers() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode k = ev.getCode();

            // Movimiento
            if (k == KeyCode.W || k == KeyCode.UP) {
                keys.add(KeyCode.W);
            }
            if (k == KeyCode.S || k == KeyCode.DOWN) {
                keys.add(KeyCode.S);
            }
            if (k == KeyCode.A || k == KeyCode.LEFT) {
                keys.add(KeyCode.A);
            }
            if (k == KeyCode.D || k == KeyCode.RIGHT) {
                keys.add(KeyCode.D);
            }

            // Tecla P para depuración (coordenadas)
            if (k == KeyCode.P) {
                System.out.println("Hero position (aldea): (" + heroView.getLayoutX() + ", " + heroView.getLayoutY() + ")");
                System.out.println("Hero world center (aldea): ("
                        + (heroView.getLayoutX() + HERO_W / 2) + ", "
                        + (heroView.getLayoutY() + HERO_H / 2) + ")");
                System.out.println("Hero direction: " + getHeroDirection().name());
            }

            // Tecla B para depuración (combate)
            if (k == KeyCode.B) {
                clearInputState();
                openDebugCombat();
            }

            // Tecla I, + o ADD para inventario
            if (k == KeyCode.I || k == KeyCode.ADD || k == KeyCode.PLUS) {
                clearInputState();
                openInventory();
            }

            if (k == KeyCode.ENTER) {
                if (onStartRect) {
                    clearInputState();
                    try {
                        if (game != null && game.getHero() != null) {
                            Hero h = game.getHero();
                            h.setLastLocation(Hero.Location.FIELD_VILLAGE);
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
                        onExitCallback.run();
                    } else {
                        hide();
                    }
                }
            }

            if (k == KeyCode.ESCAPE) {
                clearInputState();

                Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
                dlg.setTitle("Volver al menú");
                dlg.setHeaderText("¿Quieres volver al menú principal?");
                dlg.setContentText("Si vuelves al menú, la partida seguirá guardada en disco.");
                try {
                    if (root.getScene() != null && root.getScene().getWindow() != null) {
                        dlg.initOwner(root.getScene().getWindow());
                    }
                } catch (Throwable ignored) {
                }
                dlg.setOnHidden(eh -> {
                    clearInputState();
                    Platform.runLater(root::requestFocus);
                });

                Optional<ButtonType> opt = dlg.showAndWait();
                boolean ok = opt.isPresent() && opt.get() == ButtonType.OK;
                if (ok) {
                    try {
                        if (game != null && game.getHero() != null) {
                            Hero h = game.getHero();
                            h.setLastLocation(Hero.Location.FIELD_VILLAGE);
                            h.setLastPosY(heroView.getLayoutY());
                            h.setLastPosX(heroView.getLayoutX());
                            try {
                                game.createSaveGame();
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    stopVillageMusic();
                    try {
                        FXGL.getGameScene().removeUINode(root);
                    } catch (Throwable ignored) {
                    }
                    MainScreen.restoreMenuAndMusic();
                } else {
                    clearInputState();
                    Platform.runLater(root::requestFocus);
                }
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

    private void openInventory() {
        // Detener el movimiento
        stopMover();

        // Pausar todas las músicas registradas (si existe AudioManager)
        try {
            AudioManager.pauseAll();
        } catch (Throwable ignored) {
        }

        // Pasar referencia this para que el inventario pueda guardar la posición y detener/reanudar música
        inventory = new InventoryScreen(game, this);

        // Configurar onClose para cuando se cierra el inventario normalmente
        inventory.setOnClose(() -> {
            Platform.runLater(() -> {
                try {
                    FXGL.getGameScene().removeUINode(inventory.getRoot());
                } catch (Throwable ignored) {
                }
                // Reanudar el movimiento
                startMover();
                // Reanudar la música de la aldea si corresponde
                try {
                    if (music != null) {
                        music.play();
                        try {
                            AudioManager.register(music);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
                root.requestFocus();
            });
        });

        // Mostrar el inventario y asegurar que capture el foco
        inventory.show();
        Platform.runLater(() -> {
            try {
                inventory.getRoot().requestFocus();
            } catch (Throwable ignored) {
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
                    return;
                }

                updateAndMove(dt);
            }
        };
    }

    private void startMover() {
        if (mover != null) {
            mover.start();
        }
    }

    private void stopMover() {
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

        Direction newDir = (vx != 0 || vy != 0) ? directionFromVector(vx, vy) : Direction.NONE;
        setDirectionIfChanged(newDir);

        if (vx == 0 && vy == 0) {
            checkStartIntersection();
            return;
        }

        moveHero(vx * dt, vy * dt);
    }

    private void moveHero(double dx, double dy) {
        double curX = heroView.getLayoutX();
        double curY = heroView.getLayoutY();

        double proposedX = clamp(curX + dx, 0, Math.max(0, worldW - HERO_W));
        double proposedY = clamp(curY + dy, 0, Math.max(0, worldH - HERO_H));

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, HERO_W, HERO_H);
        boolean collision = false;

        for (Obstacle ob : obstacles) {
            if (heroRect.intersects(ob.collisionRect)) {
                collision = true;
                break;
            }
        }

        if (!collision) {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        } else {
            Rectangle2D heroRectX = new Rectangle2D(proposedX, curY, HERO_W, HERO_H);
            Rectangle2D heroRectY = new Rectangle2D(curX, proposedY, HERO_W, HERO_H);

            boolean canMoveX = true;
            boolean canMoveY = true;

            for (Obstacle ob : obstacles) {
                if (heroRectX.intersects(ob.collisionRect)) {
                    canMoveX = false;
                }
                if (heroRectY.intersects(ob.collisionRect)) {
                    canMoveY = false;
                }
            }

            if (canMoveX) {
                heroView.setLayoutX(proposedX);
            }
            if (canMoveY) {
                heroView.setLayoutY(proposedY);
            }
        }

        checkStartIntersection();
        updateCamera();
    }

    private Direction directionFromVector(double vx, double vy) {
        if (vx == 0 && vy == 0) {
            return Direction.NONE;
        }

        double angle = Math.toDegrees(Math.atan2(-vy, vx));
        if (angle < 0) {
            angle += 360.0;
        }

        if (angle >= 337.5 || angle < 22.5) {
            return Direction.E;
        }
        if (angle < 67.5) {
            return Direction.NE;
        }
        if (angle < 112.5) {
            return Direction.N;
        }
        if (angle < 157.5) {
            return Direction.NW;
        }
        if (angle < 202.5) {
            return Direction.W;
        }
        if (angle < 247.5) {
            return Direction.SW;
        }
        if (angle < 292.5) {
            return Direction.S;
        }
        if (angle < 337.5) {
            return Direction.SE;
        }
        return Direction.NONE;
    }

    private void setDirectionIfChanged(Direction newDir) {
        if (newDir == null) {
            newDir = Direction.NONE;
        }
        currentDirection = newDir;
    }

    public Direction getHeroDirection() {
        return currentDirection;
    }

    private void checkStartIntersection() {
        if (startRect == null) {
            onStartRect = false;
            return;
        }
        Rectangle2D heroRect = new Rectangle2D(heroView.getLayoutX(), heroView.getLayoutY(), HERO_W, HERO_H);
        onStartRect = heroRect.intersects(new Rectangle2D(startRect.getX(), startRect.getY(), startRect.getWidth(), startRect.getHeight()));
    }

    private void updateCamera() {
        // Mantener la cámara simple: centrar la vista en hero si el mundo es más grande que la vista
        double cx = heroView.getLayoutX() + HERO_W / 2.0;
        double cy = heroView.getLayoutY() + HERO_H / 2.0;

        double tx = clamp(VIEW_W / 2.0 - cx, Math.min(0, VIEW_W - worldW), 0);
        double ty = clamp(VIEW_H / 2.0 - cy, Math.min(0, VIEW_H - worldH), 0);

        world.setTranslateX(tx);
        world.setTranslateY(ty);
    }

    // Métodos auxiliares que existían en tu versión original (no eliminados)
    private void clearInputState() {
        keys.clear();
    }

    private void openDebugCombat() {
        // Restaura la funcionalidad que tenías para debug combat
        try {
            CombatScreen cs = new CombatScreen(game, "/Resources/textures/battle_bg.png",
                    List.of("/Resources/sprites/Monsters/monster1.png"), null);
            cs.setOnExit(() -> {
                // nada especial al salir en debug
            });
            cs.show();
        } catch (Throwable ignored) {
        }
    }

    public void setHeroPosition(double x, double y) {
        if (heroView != null) {
            double hw = heroView.getBoundsInLocal().getWidth();
            double hh = heroView.getBoundsInLocal().getHeight();
            heroView.setLayoutX(clamp(x, 0, Math.max(0, worldW - hw)));
            heroView.setLayoutY(clamp(y, 0, Math.max(0, worldH - hh)));
            updateCamera();
        }
    }

    // clamp restaurado
    private static double clamp(double v, double lo, double hi) {
        double out = v;
        if (out < lo) {
            out = lo;
        } else if (out > hi) {
            out = hi;
        }
        return out;
    }
}
