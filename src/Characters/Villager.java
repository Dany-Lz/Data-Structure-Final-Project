package Characters;

import Items.*;

public class Villager extends NPC {

    private boolean event;
    private boolean gift;
    private Item giftItem;

    public Villager(boolean event, boolean gift, Item giftItem, String name, String sprite) {
        super(name, sprite);
        this.event = event;
        this.gift = gift;
        this.giftItem = giftItem;
    }

}
