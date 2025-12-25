package Items;

public class Potion extends Item {

    private int healing;

    public Potion(String info, String name, String id, int healing) {
        super(info, name, id);
        setHealing(healing);

    }

    public int getHealing() {
        return healing;
    }

    public void setHealing(int healing) {
        this.healing = healing;
    }
    
    

}
