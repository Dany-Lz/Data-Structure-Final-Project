package GUI;

import Logic.Game;
import Characters.Hero;
import Items.*;
import Misc.Classes;
import Runner.MainScreen;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javafx.application.Platform;

public class InventoryScreen {

    private final Game game;
    private final Object mapScreen; // puede ser GameMapScreen, FieldVillage u otro proveedor
    private StackPane root = null;
    private final TabPane tabPane;
    private Runnable onClose;
    private boolean isVisible = false;

    // Sistema de mensajes temporales (toast)
    private StackPane toastContainer;
    private boolean showingToast = false;

    private EventHandler<KeyEvent> sceneKeyFilter = null;
    private EventHandler<MouseEvent> sceneMouseFilter = null;
    private Parent sceneRootRef = null;

    public InventoryScreen(Game game, Object mapScreen) {
        this.game = game;
        this.mapScreen = mapScreen;

        root = new StackPane();
        root.setPrefSize(800, 600);

        // Fondo oscuro semitransparente
        Rectangle bg = new Rectangle(800, 600);
        bg.setFill(Color.rgb(0, 0, 0, 0.85));
        root.getChildren().add(bg);

        // Contenedor principal
        VBox mainContainer = new VBox();
        mainContainer.setAlignment(Pos.TOP_CENTER);
        mainContainer.setPrefSize(750, 550);
        mainContainer.setStyle("-fx-background-color: rgba(10, 10, 20, 0.95); "
                + "-fx-background-radius: 10; "
                + "-fx-border-color: #2a2a3a; "
                + "-fx-border-width: 2; "
                + "-fx-border-radius: 10;");

        // Título
        Label title = new Label("INVENTORY");
        title.setFont(Font.font("System Bold", 32));
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-effect: dropshadow(gaussian, #000000, 8, 0.5, 0, 2);");
        title.setPadding(new Insets(15, 0, 15, 0));

        // TabPane para las pestañas
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(730, 450);
        tabPane.setStyle("-fx-background-color: transparent; "
                + "-fx-border-color: #333344; "
                + "-fx-border-width: 1;");

        // Crear las pestañas
        Tab statusTab = createStatusTab();
        Tab weaponsArmorTab = createWeaponsArmorTab();
        Tab waresTab = createWaresTab();
        Tab keyItemsTab = createKeyItemsTab();
        Tab settingsTab = createSettingsTab();

        tabPane.getTabs().addAll(statusTab, weaponsArmorTab, waresTab, keyItemsTab, settingsTab);

        // Botón para cerrar
        Button closeButton = new Button("Close");
        closeButton.setFont(Font.font("System Bold", 14));
        closeButton.setPrefSize(180, 40);
        closeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #3a7bd5, #00d2ff); "
                + "-fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 6; -fx-cursor: hand;");
        closeButton.setOnAction(e -> close());

        // Evento de teclado para cerrar (capturado por el root)
        root.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case I:
                case ADD:
                case PLUS:
                case ESCAPE:
                case SUBTRACT:
                case MINUS:
                    close();
                    break;
                default:
                    break;
            }
        });

        mainContainer.getChildren().addAll(title, tabPane, closeButton);
        VBox.setMargin(title, new Insets(0, 0, 10, 0));
        VBox.setMargin(tabPane, new Insets(0, 0, 20, 0));
        VBox.setMargin(closeButton, new Insets(0, 0, 20, 0));

        root.getChildren().add(mainContainer);
        StackPane.setAlignment(mainContainer, Pos.CENTER);

        toastContainer = new StackPane();
        toastContainer.setMouseTransparent(true);
        toastContainer.setPickOnBounds(false);
        root.getChildren().add(toastContainer);

        root.setFocusTraversable(true);
    }

    private Tab createStatusTab() {
        Tab tab = new Tab("Status");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(25);
        grid.setVgap(15);
        grid.setAlignment(Pos.TOP_CENTER);

        Hero hero = game.getHero();
        int row = 0;

        // ===== LEFT COLUMN =====
        // Hero icon
        ImageView heroIcon = new ImageView(hero.getImage());
        heroIcon.setFitWidth(120);
        heroIcon.setFitHeight(120);
        heroIcon.setStyle("-fx-effect: dropshadow(gaussian, #000000, 10, 0.5, 0, 0);");

        GridPane.setConstraints(heroIcon, 0, row, 1, 3);
        grid.getChildren().add(heroIcon);

        // Name
        Label nameTitle = createTitleLabel("Name:");
        Label nameValue = createValueLabel(hero.getName());
        GridPane.setConstraints(nameTitle, 1, row);
        GridPane.setConstraints(nameValue, 2, row);
        grid.getChildren().addAll(nameTitle, nameValue);
        row++;

        // Level
        Label levelTitle = createTitleLabel("Level:");
        Label levelValue = createValueLabel(String.valueOf(hero.getLevel()));
        GridPane.setConstraints(levelTitle, 1, row);
        GridPane.setConstraints(levelValue, 2, row);
        grid.getChildren().addAll(levelTitle, levelValue);
        row++;

        // HP Section
        Label hpTitle = createTitleLabel("HP:");
        GridPane.setConstraints(hpTitle, 1, row);
        grid.getChildren().add(hpTitle);
        row++;

        // HP Bar with numbers in center
        HBox hpContainer = new HBox();
        hpContainer.setAlignment(Pos.CENTER);
        hpContainer.setPrefWidth(250);
        hpContainer.setPrefHeight(30);

        StackPane hpBarContainer = new StackPane();
        hpBarContainer.setPrefWidth(250);
        hpBarContainer.setPrefHeight(30);

        // Background bar
        Rectangle hpBg = new Rectangle(250, 25);
        hpBg.setFill(Color.rgb(40, 40, 40));
        hpBg.setArcWidth(10);
        hpBg.setArcHeight(10);

        // HP bar
        double hpPercent = (double) hero.getActualLife() / hero.getLife();
        Rectangle hpBar = new Rectangle(250 * hpPercent, 25);
        if (hpPercent < 0.3) {
            hpBar.setFill(Color.rgb(220, 60, 60)); // Red
        } else if (hpPercent < 0.6) {
            hpBar.setFill(Color.rgb(220, 180, 60)); // Yellow
        } else {
            hpBar.setFill(Color.rgb(60, 220, 60)); // Green
        }
        hpBar.setArcWidth(10);
        hpBar.setArcHeight(10);

        // HP text
        Label hpText = new Label(hero.getActualLife() + " / " + hero.getLife());
        hpText.setFont(Font.font("System Bold", 14));
        hpText.setTextFill(Color.WHITE);
        hpText.setStyle("-fx-effect: dropshadow(gaussian, #000000, 2, 0, 0, 1);");

        hpBarContainer.getChildren().addAll(hpBg, hpBar, hpText);
        hpContainer.getChildren().add(hpBarContainer);

        GridPane.setConstraints(hpContainer, 1, row, 2, 1);
        grid.getChildren().add(hpContainer);
        row++;

        // Attack
        Label attackTitle = createTitleLabel("Attack:");
        int totalAttack = hero.getAttack() + (hero.getActualWeapon() != null ? hero.getActualWeapon().getAttack() : 0);
        Label attackValue = createValueLabel(hero.getAttack() + " + "
                + (hero.getActualWeapon() != null ? hero.getActualWeapon().getAttack() : 0)
                + " = " + totalAttack);
        GridPane.setConstraints(attackTitle, 1, row);
        GridPane.setConstraints(attackValue, 2, row);
        grid.getChildren().addAll(attackTitle, attackValue);
        row++;

        // Defense
        Label defenseTitle = createTitleLabel("Defense:");
        int totalDefense = hero.getDefense() + (hero.getArmor() != null ? hero.getArmor().getDefense() : 0);
        Label defenseValue = createValueLabel(hero.getDefense() + " + "
                + (hero.getArmor() != null ? hero.getArmor().getDefense() : 0)
                + " = " + totalDefense);
        GridPane.setConstraints(defenseTitle, 1, row);
        GridPane.setConstraints(defenseValue, 2, row);
        grid.getChildren().addAll(defenseTitle, defenseValue);
        row++;

        // ===== RIGHT COLUMN =====
        int rightCol = 3;
        int rightRow = 0;

        // Magic
        Label magicTitle = createTitleLabel("Magic:");
        Label magicValue = createValueLabel(String.valueOf(hero.getMagic()));
        GridPane.setConstraints(magicTitle, rightCol, rightRow);
        GridPane.setConstraints(magicValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(magicTitle, magicValue);
        rightRow++;

        // Experience (actual / max)
        Label expTitle = createTitleLabel("Experience:");
        Label expValue = createValueLabel(hero.getExpActual() + " / " + hero.getExpMax());
        GridPane.setConstraints(expTitle, rightCol, rightRow);
        GridPane.setConstraints(expValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(expTitle, expValue);
        rightRow++;

        // Money
        Label Money = createTitleLabel("Money:");
        int money = hero.getMoney();
        String ammount = String.valueOf(money);
        Label MoneyAmmount = createValueLabel(ammount);
        GridPane.setConstraints(Money, rightCol, rightRow);
        GridPane.setConstraints(MoneyAmmount, rightCol + 1, rightRow);
        grid.getChildren().addAll(Money, MoneyAmmount);
        rightRow++;
        // Current Weapon
        Label weaponTitle = createTitleLabel("Weapon:");
        Weapon actualWeapon = hero.getActualWeapon();
        String weaponName = actualWeapon != null ? actualWeapon.getName() : "None";
        String weaponDurability = actualWeapon != null ? " (Durability: " + actualWeapon.getLifeSpan() + ")" : "";
        Label weaponValue = createValueLabel(weaponName + weaponDurability);
        weaponValue.setWrapText(true);
        weaponValue.setMaxWidth(250);
        GridPane.setConstraints(weaponTitle, rightCol, rightRow);
        GridPane.setConstraints(weaponValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(weaponTitle, weaponValue);
        rightRow++;

        // Armor
        Label armorTitle = createTitleLabel("Armor:");
        Armor armor = hero.getArmor();
        String armorName = armor != null ? armor.getName() : "None";
        String armorDefense = armor != null ? " (+" + armor.getDefense() + " def)" : "";
        Label armorValue = createValueLabel(armorName + armorDefense);
        GridPane.setConstraints(armorTitle, rightCol, rightRow);
        GridPane.setConstraints(armorValue, rightCol + 1, rightRow);
        grid.getChildren().addAll(armorTitle, armorValue);
        rightRow++;

        // Skill Tree
        Label skillTreeTitle = new Label("SKILL TREE");
        skillTreeTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #aaddff;");
        GridPane.setConstraints(skillTreeTitle, 0, 6, 2, 1);
        grid.getChildren().add(skillTreeTitle);

        TextArea skillTreeArea = new TextArea();
        skillTreeArea.setEditable(false);
        skillTreeArea.setPrefRowCount(6);
        skillTreeArea.setPrefColumnCount(50);
        skillTreeArea.setWrapText(true);
        skillTreeArea.setText(getSkillTreeAsString());
        skillTreeArea.setStyle("-fx-control-inner-background: #0a0a14; "
                + "-fx-text-fill: #aaddff; "
                + "-fx-font-family: 'Consolas', monospace; "
                + "-fx-font-size: 12px; "
                + "-fx-border-color: #333344;");
        GridPane.setConstraints(skillTreeArea, 0, 7, 4, 2);
        grid.getChildren().add(skillTreeArea);

        scrollPane.setContent(grid);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createWeaponsArmorTab() {
        Tab tab = new Tab("Weapons/Armor");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: transparent;");

        Hero hero = game.getHero();

        // Weapons section
        Label weaponsTitle = new Label("AVAILABLE WEAPONS");
        weaponsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffaa44;");
        weaponsTitle.setPadding(new Insets(0, 0, 10, 0));

        VBox weaponsList = new VBox(5);
        if (game.getHeroWeapons().isEmpty()) {
            Label noWeapons = new Label("No weapons available.");
            noWeapons.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            weaponsList.getChildren().add(noWeapons);
        } else {
            for (Weapon w : game.getHeroWeapons()) {
                HBox weaponRow = createItemRow(w.getName(),
                        "Attack: " + w.getAttack()
                        + " | Durability: " + w.getLifeSpan()
                        + (w == hero.getActualWeapon() ? " (EQUIPPED)" : ""),
                        w == hero.getActualWeapon());
                weaponsList.getChildren().add(weaponRow);
            }
        }

        // Armor section
        Label armorTitle = new Label("ARMOR");
        armorTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #44aaff;");
        armorTitle.setPadding(new Insets(20, 0, 10, 0));

        VBox armorList = new VBox(5);
        Armor armor = hero.getArmor();
        if (armor != null) {
            HBox armorRow = createItemRow(armor.getName(),
                    "Defense: " + armor.getDefense()
                    + " | Effect: " + armor.getEffect(),
                    true);
            armorRow.setStyle("-fx-background-color: rgba(68, 170, 255, 0.1); -fx-background-radius: 5;");
            armorList.getChildren().add(armorRow);
        } else {
            Label noArmor = new Label("No armor equipped.");
            noArmor.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            armorList.getChildren().add(noArmor);
        }

        content.getChildren().addAll(weaponsTitle, weaponsList, armorTitle, armorList);
        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createWaresTab() {
        Tab tab = new Tab("Consumables");
        tab.setStyle("-fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: transparent;");

        Hero hero = game.getHero();

        Label waresTitle = new Label("HEALING ITEMS");
        waresTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #44ff44;");
        waresTitle.setPadding(new Insets(0, 0, 10, 0));

        VBox waresList = new VBox(5);
        waresList.setStyle("-fx-background-color: transparent;");

        // Get consumables from game (not hero's items)
        int healingItems = 0;
        ArrayList<Item> allItems = game.getItems();
        for (Item item : allItems) {
            if (item instanceof Wares) {
                healingItems++;
                Wares ware = (Wares) item;
                HBox wareRow = createItemRow(ware.getName(),
                        "Healing: " + ware.getHealing()
                        + " | ID: " + ware.getId(),
                        false);
                wareRow.setStyle("-fx-background-color: rgba(68, 255, 68, 0.1); -fx-background-radius: 5;");
                waresList.getChildren().add(wareRow);
            }
        }

        if (healingItems == 0) {
            Label noWares = new Label("No healing items available.");
            noWares.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
            waresList.getChildren().add(noWares);
        }

        content.getChildren().addAll(waresTitle, waresList);
        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createKeyItemsTab() {
        Tab tab = new Tab("Key Items");
        tab.setStyle("-fx-font-weight: bold;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(40));
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: transparent;");

        Label label = new Label("KEY ITEMS");
        label.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ff44ff;");

        Label message = new Label("No key items at the moment.");
        message.setStyle("-fx-font-size: 14px; -fx-text-fill: #aaaaaa; -fx-font-style: italic;");

        content.getChildren().addAll(label, message);
        tab.setContent(content);
        return tab;
    }

    private Tab createSettingsTab() {
        Tab tab = new Tab("Settings");
        tab.setStyle("-fx-font-weight: bold;");

        VBox content = new VBox(25);
        content.setPadding(new Insets(40));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle("-fx-background-color: transparent;");

        // Save game button
        VBox saveSection = new VBox(10);
        saveSection.setAlignment(Pos.CENTER);
        Label saveLabel = new Label("Save Game");
        saveLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffff44;");

        Button saveButton = new Button("💾 SAVE");
        saveButton.setFont(Font.font("System Bold", 16));
        saveButton.setPrefWidth(250);
        saveButton.setPrefHeight(50);
        saveButton.setStyle("-fx-background-color: linear-gradient(to bottom, #ffd54f, #ffb300); "
                + "-fx-text-fill: black; -fx-font-weight: bold; "
                + "-fx-background-radius: 8; -fx-effect: dropshadow(gaussian, #333, 5, 0, 0, 2); "
                + "-fx-cursor: hand;");
        saveButton.setOnAction(e -> {
            try {
                Point2D pos = tryGetHeroTopLeftFromProvider();

                if (pos != null) {
                    Hero h = game.getHero();
                    if (h != null) {
                        String clsName = mapScreen != null ? mapScreen.getClass().getSimpleName() : "";

                        switch (clsName) {
                            case "FieldVillage" ->
                                h.setLastLocation(Hero.Location.FIELD_VILLAGE);
                            case "GameMapScreen" ->
                                h.setLastLocation(Hero.Location.MAP);
                            case "ForestHouse" ->
                                h.setLastLocation(Hero.Location.FOREST_HOUSE);
                            default -> {
                                h.setLastLocation(Hero.Location.MAP);
                            }
                        }

                        h.setLastPosX(pos.getX());
                        h.setLastPosY(pos.getY());
                    }
                }

                boolean saved = game.createSaveGame();

                if (saved) {
                    showToast("Game saved successfully!", 1200);
                } else {
                    showToast("Error saving game.", 1200);
                }

                PauseTransition pt = new PauseTransition(Duration.millis(350));
                pt.setOnFinished(ev -> {
                    try {
                        close();
                    } catch (Throwable ex) {
                        try {
                            FXGL.getGameScene().removeUINode(root);
                        } catch (Throwable ignored) {
                        }
                        if (onClose != null) {
                            try {
                                onClose.run();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
                pt.play();

            } catch (Throwable ex) {
                try {
                    showToast("Error saving game.", 1200);
                } catch (Throwable ignored) {
                }
            }
        });

        saveSection.getChildren().addAll(saveLabel, saveButton);

        // Volume slider
        VBox volumeSection = new VBox(10);
        volumeSection.setAlignment(Pos.CENTER);
        Label volumeLabel = new Label("VOLUME");
        volumeLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #44aaff;");

        HBox volumeControls = new HBox(15);
        volumeControls.setAlignment(Pos.CENTER);

        Slider volumeSlider = new Slider(0, 100, Math.round(MainScreen.getVolumeSetting() * 100));
        volumeSlider.setPrefWidth(200);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            //   MainScreen.setVolumeSetting(newVal.doubleValue() / 100.0);
        });

        Label volumeValue = new Label(Math.round(volumeSlider.getValue()) + "%");
        volumeValue.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            volumeValue.setText(Math.round(newVal.doubleValue()) + "%");
        });

        volumeControls.getChildren().addAll(volumeSlider, volumeValue);
        volumeSection.getChildren().addAll(volumeLabel, volumeControls);

        // Exit to menu button - AHORA CIERRA TODO COMPLETAMENTE Y DETIENE MÚSICA DE MAPA (si mapScreen != null)
        VBox exitSection = new VBox(10);
        exitSection.setAlignment(Pos.CENTER);
        Label exitLabel = new Label("Exit to Main Menu");
        exitLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ff6b6b;");

        Button exitButton = new Button("🚪 EXIT TO MAIN MENU");
        exitButton.setFont(Font.font("System Bold", 16));
        exitButton.setPrefWidth(250);
        exitButton.setPrefHeight(50);
        exitButton.setStyle("-fx-background-color: linear-gradient(to bottom, #ff6b6b, #c44569); "
                + "-fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 8; -fx-effect: dropshadow(gaussian, #333, 5, 0, 0, 2); "
                + "-fx-cursor: hand;");
        exitButton.setOnAction(e -> {
            // 1. Quitar filtros globales del inventario si están instalados
            try {
                Parent currentRoot = FXGL.getGameScene().getRoot();
                if (sceneRootRef != null) {
                    try {
                        sceneRootRef.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    } catch (Throwable ignored) {
                    }
                    try {
                        sceneRootRef.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    } catch (Throwable ignored) {
                    }
                }
                if (currentRoot != null && currentRoot != sceneRootRef) {
                    try {
                        currentRoot.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    } catch (Throwable ignored) {
                    }
                    try {
                        currentRoot.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }

            // 2. Cerrar el inventario UI si está presente
            try {
                FXGL.getGameScene().removeUINode(root);
            } catch (Throwable ignored) {
            }

            // 3. Intentar detener la música del mapa si existe (invocación segura por reflexión)
            try {
                if (mapScreen != null) {
                    try {
                        java.lang.reflect.Method m = mapScreen.getClass().getMethod("stopMapMusic");
                        m.invoke(mapScreen);
                    } catch (NoSuchMethodException nsme) {
                        try {
                            java.lang.reflect.Method mv = mapScreen.getClass().getMethod("stopVillageMusic");
                            mv.invoke(mapScreen);
                        } catch (Throwable ignored2) {
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }

            // 4. Detener y limpiar toda la música registrada (AudioManager)
            try {
                AudioManager.stopAll();
            } catch (Throwable ignored) {
            }

            // 5. Limpiar UI y restaurar menú y música principal
            try {
                FXGL.getGameScene().clearUINodes();
            } catch (Throwable ignored) {
            }

            MainScreen.restoreMenuAndMusic();
        });

        exitSection.getChildren().addAll(exitLabel, exitButton);

        content.getChildren().addAll(saveSection, volumeSection, exitSection);
        tab.setContent(content);
        return tab;
    }

    private HBox createItemRow(String name, String details, boolean equipped) {
        HBox row = new HBox(15);
        row.setPadding(new Insets(8, 15, 8, 15));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.05); "
                + "-fx-background-radius: 5; "
                + "-fx-border-color: rgba(255, 255, 255, 0.1); "
                + "-fx-border-radius: 5;");

        // Item name
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; "
                + (equipped ? "-fx-text-fill: #ffff44;" : "-fx-text-fill: white;"));
        nameLabel.setMinWidth(150);

        // Details
        Label detailsLabel = new Label(details);
        detailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaaaaa;");
        detailsLabel.setWrapText(true);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Equipped indicator
        if (equipped) {
            Label equippedLabel = new Label("EQUIPPED");
            equippedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #ffff44;");
            row.getChildren().addAll(nameLabel, detailsLabel, spacer, equippedLabel);
        } else {
            row.getChildren().addAll(nameLabel, detailsLabel, spacer);
        }

        return row;
    }

    private void showToast(String message, int durationMs) {
        if (showingToast) {
            toastContainer.getChildren().clear();
        }

        showingToast = true;

        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.85); "
                + "-fx-text-fill: white; "
                + "-fx-padding: 12 20 12 20; "
                + "-fx-background-radius: 6; "
                + "-fx-font-size: 13px; "
                + "-fx-font-weight: bold;");

        StackPane toastPane = new StackPane(toastLabel);
        toastPane.setMouseTransparent(true);
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
        toastLabel.setTranslateY(-50);

        toastContainer.getChildren().add(toastPane);

        // Fade in
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toastPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        // Pause
        PauseTransition pause = new PauseTransition(Duration.millis(durationMs));

        // Fade out
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), toastPane);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            toastContainer.getChildren().remove(toastPane);
            showingToast = false;
        });

        // Animation sequence
        SequentialTransition sequence = new SequentialTransition(fadeIn, pause, fadeOut);
        sequence.play();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void show() {
        isVisible = true;

        // Añadir a la escena
        FXGL.getGameScene().addUINode(root);

        // Pausar todas las músicas registradas (AudioManager)
        try {
            AudioManager.pauseAll();
        } catch (Throwable ignored) {
        }

        // Instalar filtros globales para bloquear inputs a la UI subyacente
        try {
            // obtener referencia actual del root de la escena (si cambia, siempre usamos el actual)
            sceneRootRef = FXGL.getGameScene().getRoot();
            if (sceneRootRef != null) {
                // crear filtros si no existen
                sceneKeyFilter = ev -> {
                    Object tgt = ev.getTarget();
                    if (tgt instanceof Node) {
                        if (!isNodeDescendantOfRoot((Node) tgt)) {
                            ev.consume();
                        }
                    }
                };
                sceneMouseFilter = ev -> {
                    Object tgt = ev.getTarget();
                    if (tgt instanceof Node) {
                        if (!isNodeDescendantOfRoot((Node) tgt)) {
                            ev.consume();
                        }
                    }
                };

                // registrar en el root actual
                sceneRootRef.addEventFilter(KeyEvent.ANY, sceneKeyFilter);
                sceneRootRef.addEventFilter(MouseEvent.ANY, sceneMouseFilter);
            }
        } catch (Throwable ignored) {
        }

        // Request focus on inventory root
        javafx.application.Platform.runLater(() -> root.requestFocus());
    }

    public void close() {
        isVisible = false;

        // Quitar filtros globales de forma robusta: intentar en el root guardado y en el root actual
        try {
            Parent currentRoot = FXGL.getGameScene().getRoot();
            if (sceneRootRef != null) {
                try {
                    if (sceneKeyFilter != null) {
                        sceneRootRef.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (sceneMouseFilter != null) {
                        sceneRootRef.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    }
                } catch (Throwable ignored) {
                }
            }
            // además intentar quitar del root actual por si cambió
            if (currentRoot != null && currentRoot != sceneRootRef) {
                try {
                    if (sceneKeyFilter != null) {
                        currentRoot.removeEventFilter(KeyEvent.ANY, sceneKeyFilter);
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (sceneMouseFilter != null) {
                        currentRoot.removeEventFilter(MouseEvent.ANY, sceneMouseFilter);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        } finally {
            sceneKeyFilter = null;
            sceneMouseFilter = null;
            sceneRootRef = null;
        }

        // Quitar UI
        try {
            FXGL.getGameScene().removeUINode(root);
        } catch (Throwable ignored) {
        }

        // Reanudar todas las músicas registradas
        try {
            AudioManager.resumeAll();
        } catch (Throwable ignored) {
        }

        // Asegurar que la pantalla que abrió el inventario recupere foco y entrada.
        try {
            if (mapScreen != null) {
                // intentar dar foco al root del mapScreen si tiene método requestFocus o getRoot
                try {
                    // si tiene getRoot() que devuelva Parent
                    java.lang.reflect.Method m = mapScreen.getClass().getMethod("getRoot");
                    Object r = m.invoke(mapScreen);
                    if (r instanceof Node) {
                        Platform.runLater(() -> ((Node) r).requestFocus());
                    }
                } catch (NoSuchMethodException nsme) {
                    // si no tiene getRoot, intentar setHeroPosition o requestFocus directo
                    try {
                        java.lang.reflect.Method m2 = mapScreen.getClass().getMethod("requestFocus");
                        m2.invoke(mapScreen);
                    } catch (Throwable ignored) {
                    }
                } catch (Throwable ignored) {
                }
            } else {
                // fallback: pedir foco al scene root
                Parent sceneRoot = FXGL.getGameScene().getRoot();
                if (sceneRoot != null) {
                    Platform.runLater(() -> sceneRoot.requestFocus());
                }
            }
        } catch (Throwable ignored) {
        }

        // Ejecutar callback onClose si existe (por ejemplo para reanudar mover)
        if (onClose != null) {
            onClose.run();
        }

    }

    public void toggle() {
        if (isVisible) {
            close();
        } else {
            show();
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public StackPane getRoot() {
        return root;
    }

    // ---------------- Helpers ----------------
    private Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #aaddff;");
        label.setMinWidth(100);
        return label;
    }

    private Label createValueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");
        label.setMinWidth(150);
        return label;
    }

    private String getSkillTreeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════╗\n");
        sb.append("║           SKILL TREE                 ║\n");
        sb.append("╚══════════════════════════════════════╝\n\n");
        sb.append("┌── Warrior\n");
        sb.append("│   ├── Swordsman\n");
        sb.append("│   │   ├── Claymore User\n");
        sb.append("│   │   └── Saber User\n");
        sb.append("│   ├── Spearman\n");
        sb.append("│   │   ├── Halberd User\n");
        sb.append("│   │   └── Pike User\n");
        sb.append("│   └── Gunner\n");
        sb.append("│       ├── Shotgun User\n");
        sb.append("│       └── Rifle User\n");
        sb.append("└──────────────────────────────────────\n");

        return sb.toString();
    }

    private Point2D tryGetHeroTopLeftFromProvider() {
        if (mapScreen == null) {
            return null;
        }
        try {
            Method m = mapScreen.getClass().getMethod("getHeroMapTopLeft");
            Object res = m.invoke(mapScreen);
            if (res instanceof Point2D) {
                return (Point2D) res;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isNodeDescendantOfRoot(Node node) {
        if (node == null) {
            return false;
        }
        Node cur = node;
        while (cur != null) {
            if (cur == root) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }
}
