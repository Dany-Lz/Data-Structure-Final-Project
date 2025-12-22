
package Items;

public class KeyItem extends Item{
    private boolean used;

    public KeyItem(String info, String name, String id) {
        super(info, name, id);
        used = false;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
    
    
    
}
