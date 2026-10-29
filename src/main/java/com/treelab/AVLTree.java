package com.treelab;
public class AVLTree extends BinarySearchTree {
    private Node unbalanced(Node n){if(n==null)return null;Node x=unbalanced(n.left);if(x!=null)return x;x=unbalanced(n.right);if(x!=null)return x;return Math.abs(balance(n))>1?n:null;}
    private void replace(Node old,Node replacement){if(root==old){root=replacement;return;}Node p=root;while(p!=null){if(p.left==old){p.left=replacement;return;}if(p.right==old){p.right=replacement;return;}p=old.value<p.value?p.left:p.right;}throw new IllegalStateException("node not found");}
    private void left(Node n){Node x=n.right;n.right=x.left;x.left=n;replace(n,x);log("หมุนซ้ายที่ "+n.value+" โดย "+x.value+" ขึ้นมาแทน");}
    private void right(Node n){Node x=n.left;n.left=x.right;x.right=n;replace(n,x);log("หมุนขวาที่ "+n.value+" โดย "+x.value+" ขึ้นมาแทน");}
    @Override protected void afterChange(){Node n;while((n=unbalanced(root))!=null){int b=balance(n);if(b>1){if(balance(n.left)<0){log("โหนด "+n.value+" ไม่สมดุล (LR) ต้องหมุน 2 ครั้ง");left(n.left);right(n);}else{log("โหนด "+n.value+" ไม่สมดุล (LL) หมุนขวา");right(n);}}else{if(balance(n.right)>0){log("โหนด "+n.value+" ไม่สมดุล (RL) ต้องหมุน 2 ครั้ง");right(n.right);left(n);}else{log("โหนด "+n.value+" ไม่สมดุล (RR) หมุนซ้าย");left(n);}}}log("ตรวจสอบแล้ว ต้นไม้ AVL สมดุล (Balance Factor อยู่ระหว่าง -1 ถึง 1)");}
}
