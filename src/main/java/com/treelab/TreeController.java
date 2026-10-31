package com.treelab;

import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class TreeController {

    private final MongoTemplate db;
    private final ObjectMapper json;
    private final UserService users;

    public TreeController(MongoTemplate db, ObjectMapper json, UserService users) {
        this.db = db;
        this.json = json;
        this.users = users;

        db.indexOps("history").ensureIndex(
            new org.springframework.data.mongodb.core.index.Index()
                .on("user_id", Sort.Direction.ASC)
                .on("created_at", Sort.Direction.DESC)
        );
    }

    private String owner(Authentication auth) {
        return users.find(auth.getName()).id();
    }

    private Query owned(String id, Authentication auth) {
        if (!ObjectId.isValid(id)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "ไม่พบประวัติ"
            );
        }

        return Query.query(
            Criteria.where("_id").is(new ObjectId(id))
                .and("user_id").is(owner(auth))
        );
    }

    public record Action(String type, Integer value) {}

    public record Request(String kind, List<Action> actions) {}

    public record Save(String name, Request request) {}

    private void validate(Request r) {
        if (r == null
                || !("bst".equals(r.kind()) || "avl".equals(r.kind()))
                || r.actions() == null
                || r.actions().size() > 200) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "ข้อมูลไม่ถูกต้อง หรือมีมากกว่า 200 รายการ"
            );
        }

        for (Action a : r.actions()) {
            if (a == null
                    || a.value() == null
                    || !List.of("insert", "delete", "search").contains(a.type())) {

                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "คำสั่งไม่ถูกต้อง"
                );
            }
        }
    }

    @PostMapping("/calculate")
    public Map<String, Object> calculate(@RequestBody Request r) {
        validate(r);

        BinarySearchTree t = r.kind().equals("avl")
                ? new AVLTree()
                : new BinarySearchTree();

        for (Action a : r.actions()) {
            switch (a.type()) {
                case "insert" -> t.insert(a.value());
                case "delete" -> t.delete(a.value());
                case "search" -> t.search(a.value());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();

        out.put("tree", t.getTree());
        out.put("steps", t.getSteps());
        out.put("traversals", t.traversals());

        return out;
    }

    @PostMapping("/history")
    public Map<String, Object> save(
            @RequestBody Save s,
            Authentication auth) throws Exception {

        validate(s.request());

        String name = s.name() == null
                ? "ต้นไม้ของฉัน"
                : s.name().trim();

        if (name.isEmpty()) {
            name = "ต้นไม้ของฉัน";
        }

        if (name.length() > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "ชื่อยาวเกินไป"
            );
        }

        db.insert(
            new Document("name", name)
                .append("data", json.writeValueAsString(s.request()))
                .append("user_id", owner(auth))
                .append("created_at", new Date()),
            "history"
        );

        return Map.of("ok", true);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(Authentication auth) {
        return db.find(
            Query.query(
                Criteria.where("user_id").is(owner(auth))
            )
            .with(Sort.by(Sort.Direction.DESC, "created_at", "_id"))
            .limit(100),
            Document.class,
            "history"
        )
        .stream()
        .map(d -> Map.<String, Object>of(
            "id", d.getObjectId("_id").toHexString(),
            "name", d.getString("name"),
            "created_at", d.getDate("created_at").toInstant().toString()
        ))
        .toList();
    }

    @GetMapping("/history/{id}")
    public Request load(
            @PathVariable String id,
            Authentication auth) throws Exception {

        Document d = db.findOne(owned(id, auth), Document.class, "history");

        if (d == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "ไม่พบประวัติ"
            );
        }

        return json.readValue(d.getString("data"), Request.class);
    }

    @DeleteMapping("/history/{id}")
    public Map<String, Object> remove(
            @PathVariable String id,
            Authentication auth) {

        if (db.remove(owned(id, auth), "history").getDeletedCount() == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "ไม่พบประวัติ"
            );
        }

        return Map.of("ok", true);
    }
}