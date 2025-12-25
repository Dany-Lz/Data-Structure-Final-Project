package Misc;

import java.util.ArrayList;
import Items.Item;
import java.io.Serializable;

public class Task implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String info;
    private String id;
    private boolean state;
    private boolean mainQuest;
    private ArrayList<Item> rewards;
    private int money;
}
