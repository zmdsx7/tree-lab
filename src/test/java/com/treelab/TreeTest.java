package com.treelab;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class TreeTest {
 @Test void rotations(){for(int[] input:new int[][]{{30,20,10},{10,20,30},{30,10,20},{10,30,20}}){AVLTree t=new AVLTree();for(int x:input)t.insert(x);assertEquals(20,t.getTree().value());assertEquals(List.of(10,20,30),t.traversals().get("inorder"));assertTrue(t.getSteps().stream().anyMatch(s->s.text().startsWith("หมุน")));}}
 @Test void deletion(){for(BinarySearchTree t:List.of(new BinarySearchTree(),new AVLTree())){for(int x:new int[]{50,30,70,20,40,60,80})t.insert(x);t.delete(30);assertEquals(List.of(20,40,50,60,70,80),t.traversals().get("inorder"));t.delete(20);t.delete(70);assertEquals(List.of(40,50,60,80),t.traversals().get("inorder"));}}
 @Test void avlDeletionBalances(){AVLTree t=new AVLTree();for(int i=0;i<100;i++)t.insert(i);for(int i=0;i<90;i++){t.delete(i);check(t.getTree());}}
 void check(BinarySearchTree.View n){if(n==null)return;assertTrue(Math.abs(n.balance())<=1);check(n.left());check(n.right());}
}
