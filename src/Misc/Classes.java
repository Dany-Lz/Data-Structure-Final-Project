package Misc;

import java.io.Serializable;

public abstract class Classes implements Serializable {

    private static final long serialVersionUID = 1L;
    protected String description;
    protected boolean unlocked;
    protected String id;

    public Classes(String description, boolean unlocked, String id) {
        setDescription(description);
        setUnlocked(unlocked);
        setId(id);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }
}
