package Tree;

import java.io.Serializable;

abstract public class TreeNode<E> implements Serializable {

    private static final long serialVersionUID = 1L;
    protected E info;

    public TreeNode() {
        this.info = null;
    }

    public TreeNode(E info) {
        this.info = info;
    }
}
