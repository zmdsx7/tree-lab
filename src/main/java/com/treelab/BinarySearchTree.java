package com.treelab;

import java.util.*;

public class BinarySearchTree extends Tree {

    protected static class Node {
        int value;
        Node left, right;

        Node(int v) {
            value = v;
        }
    }

    public record View(int value, int height, int balance, View left, View right) {}

    public record Step(String text, View tree) {}

    protected Node root;

    protected final List<Step> steps = new ArrayList<>();

    protected int height(Node n) {
        return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right));
    }

    protected int balance(Node n) {
        return n == null ? 0 : height(n.left) - height(n.right);
    }

    protected View view(Node n) {
        return n == null ? null : new View(
            n.value,
            height(n),
            balance(n),
            view(n.left),
            view(n.right)
        );
    }

    protected void log(String text) {
        steps.add(new Step(text, view(root)));
    }

    public View getTree() {
        return view(root);
    }

    public List<Step> getSteps() {
        return steps;
    }

    @Override
    public void insert(int v) {
        if (root == null) {
            root = new Node(v);
            log("ใส่ " + v + " เป็นราก");
            return;
        }

        Node n = root;

        while (true) {
            if (v == n.value) {
                log(v + " ซ้ำกับโหนดที่มีอยู่ จึงไม่เพิ่ม");
                return;
            }

            boolean left = v < n.value;

            log(v + (left ? " < " : " > ") + n.value
                + " ไปทาง" + (left ? "ซ้าย" : "ขวา"));

            Node next = left ? n.left : n.right;

            if (next == null) {
                if (left) n.left = new Node(v);
                else n.right = new Node(v);

                log("เพิ่ม " + v + " ทาง"
                    + (left ? "ซ้าย" : "ขวา") + "ของ " + n.value);

                break;
            }

            n = next;
        }

        afterChange();
    }

    @Override
    public void delete(int v) {
        Node parent = null, n = root;

        while (n != null && n.value != v) {
            log("ค้นหา " + v + " เทียบกับ " + n.value
                + " ไปทาง" + (v < n.value ? "ซ้าย" : "ขวา"));

            parent = n;
            n = v < n.value ? n.left : n.right;
        }

        if (n == null) {
            log("ไม่พบ " + v + " จึงไม่มีการลบ");
            return;
        }

        log("พบโหนด " + v);

        if (n.left != null && n.right != null) {
            Node sp = n, s = n.right;

            log("โหนดมีลูกสองฝั่ง หาค่าต่ำสุดในต้นไม้ย่อยด้านขวา");

            while (s.left != null) {
                sp = s;
                s = s.left;

                log("ไปทางซ้ายเพื่อหาค่าที่น้อยที่สุด: " + s.value);
            }

            int replacement = s.value;

            if (sp == n) sp.right = s.right;
            else sp.left = s.right;

            n.value = replacement;

            log("แทน " + v + " ด้วย " + replacement
                + " และนำโหนดเดิมของ " + replacement + " ออก");

        } else {
            Node child = n.left != null ? n.left : n.right;

            if (parent == null) root = child;
            else if (parent.left == n) parent.left = child;
            else parent.right = child;

            log("ลบ " + v + (child == null
                ? " ซึ่งไม่มีลูก"
                : " แล้วให้ลูก " + child.value + " ขึ้นมาแทน"));
        }

        afterChange();
    }

    @Override
    public void search(int v) {
        Node n = root;

        while (n != null) {
            log("เปรียบเทียบ " + v + " กับ " + n.value);

            if (v == n.value) {
                log("พบ " + v);
                return;
            }

            n = v < n.value ? n.left : n.right;
        }

        log("ไม่พบ " + v);
    }

    protected void afterChange() {}

    public Map<String, List<Integer>> traversals() {
        Map<String, List<Integer>> m = new LinkedHashMap<>();

        for (String k : List.of("preorder", "inorder", "postorder")) {
            List<Integer> a = new ArrayList<>();

            walk(root, k, a);
            m.put(k, a);
        }

        return m;
    }

    private void walk(Node n, String k, List<Integer> a) {
        if (n == null) return;

        if (k.equals("preorder")) a.add(n.value);

        walk(n.left, k, a);

        if (k.equals("inorder")) a.add(n.value);

        walk(n.right, k, a);

        if (k.equals("postorder")) a.add(n.value);
    }
}