package Tree;

import java.util.Iterator; 

public interface ITreeIterator<E> extends Iterator<E> { 
	BinaryTreeNode<E>nextNode(); 
}