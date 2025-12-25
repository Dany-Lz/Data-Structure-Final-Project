package Tree;

import java.util.List;
import Tree.BinaryTreeNode;
import java.io.Serializable;

abstract public class Tree<E> implements Serializable {

    private static final long serialVersionUID = 1L;
    protected TreeNode<E> root;

    public Tree() {
        root = null;
    }

    public Tree(TreeNode<E> root) {
        this.root = root;
    }

    public TreeNode<E> getRoot() {
        return root;
    }

    public void setRoot(TreeNode<E> root) {
        this.root = root;
    }

    public boolean isEmpty() {
        return root == null;
    }

    public abstract int totalNodes();

    public abstract List<TreeNode<E>> getLeaves();

    public abstract int nodeLevel(TreeNode<E> node);

    public abstract int treeLevel();

    public abstract int treeHeight();

    public abstract boolean nodeIsLeaf(TreeNode<E> node);

    public abstract int nodeDegree(TreeNode<E> node);

    public abstract E deleteNode(BinaryTreeNode<E> node);
}
