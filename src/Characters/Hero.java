package Characters;

import java.util.LinkedList;
import Items.*;
import cu.edu.cujae.ceis.tree.general.GeneralTree;
import Misc.*;
import javax.swing.Icon;

public class Hero {

    private String name;
    private Icon sprite;
    private double attack;
    private double magic;
    private double defense;
    private int level;
    private LinkedList<Item> items;
    private LinkedList<Weapon> weapons;
    private Weapon actualWeapon;
    private GeneralTree<Classes> unlockedClasses;
    private Classes actualClass;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getAttack() {
        return attack;
    }

    public void setAttack(double attack) {
        this.attack = attack;
    }

    public double getMagic() {
        return magic;
    }

    public void setMagic(double magic) {
        this.magic = magic;
    }

    public double getDefense() {
        return defense;
    }

    public void setDefense(double defense) {
        this.defense = defense;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public LinkedList<Item> getItems() {
        return items;
    }

    public void setItems(LinkedList<Item> items) {
        this.items = items;
    }

    public LinkedList<Weapon> getWeapons() {
        return weapons;
    }

    public void setWeapons(LinkedList<Weapon> weapons) {
        this.weapons = weapons;
    }

    public Weapon getActualWeapon() {
        return actualWeapon;
    }

    public void setActualWeapon(Weapon actualWeapon) {
        this.actualWeapon = actualWeapon;
    }

    public Hero(String name) {
        this.name = name;
        sprite = null;
        this.attack = 5;
        this.magic = 20;
        this.defense = 8;
        this.level = 1;
        items = new LinkedList<>();
        weapons = new LinkedList<>();
        actualWeapon = null;
        unlockedClasses = new GeneralTree<>();
        this.actualClass = null;
    }

}
