package Items;

public abstract class Armor extends Item {
    protected int defense;
    protected String effect;

    public int getDefense() {
        return defense;
    }

    public void setDefense(int defense) {
        this.defense = defense;
    }
    
    
    
}
