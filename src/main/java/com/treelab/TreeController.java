package com.treelab;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController @RequestMapping("/api")
public class TreeController {
    private final JdbcTemplate db; private final ObjectMapper json;
    public TreeController(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;db.execute("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, data TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP, user_id INTEGER REFERENCES users(id))");
        boolean owned = db.queryForList("PRAGMA table_info(history)").stream().anyMatch(column -> "user_id".equals(column.get("name")));
        if (!owned) db.execute("ALTER TABLE history ADD COLUMN user_id INTEGER REFERENCES users(id)");
        db.execute("CREATE INDEX IF NOT EXISTS history_owner_idx ON history(user_id, id)");}
    private long owner(Authentication auth){return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,auth.getName());}
    public record Action(String type,Integer value){}
    public record Request(String kind,List<Action> actions){}
    public record Save(String name,Request request){}
    private void validate(Request r){if(r==null || !("bst".equals(r.kind())||"avl".equals(r.kind())) || r.actions()==null || r.actions().size()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ข้อมูลไม่ถูกต้อง หรือมีมากกว่า 200 รายการ");for(Action a:r.actions())if(a==null||a.value()==null||!List.of("insert","delete","search").contains(a.type()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"คำสั่งไม่ถูกต้อง");}
    @PostMapping("/calculate") public Map<String,Object> calculate(@RequestBody Request r){validate(r);BinarySearchTree t=r.kind().equals("avl")?new AVLTree():new BinarySearchTree();for(Action a:r.actions()){switch(a.type()){case "insert"->t.insert(a.value());case "delete"->t.delete(a.value());case "search"->t.search(a.value());}}Map<String,Object> out=new LinkedHashMap<>();out.put("tree",t.getTree());out.put("steps",t.getSteps());out.put("traversals",t.traversals());return out;}
    @PostMapping("/history") public Map<String,Object> save(@RequestBody Save s, Authentication auth)throws Exception{validate(s.request());String name=s.name()==null?"ต้นไม้ของฉัน":s.name().trim();if(name.isEmpty())name="ต้นไม้ของฉัน";if(name.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ชื่อยาวเกินไป");db.update("INSERT INTO history(name,data,user_id) VALUES (?,?,?)",name,json.writeValueAsString(s.request()), owner(auth));return Map.of("ok",true);}
    @GetMapping("/history") public List<Map<String,Object>> history(Authentication auth){return db.queryForList("SELECT id,name,created_at FROM history WHERE user_id=? ORDER BY id DESC LIMIT 100",owner(auth));}
    @GetMapping("/history/{id}") public Request load(@PathVariable long id, Authentication auth)throws Exception{List<String> rows=db.query("SELECT data FROM history WHERE id=? AND user_id=?",(rs,i)->rs.getString(1),id,owner(auth));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return json.readValue(rows.get(0),Request.class);}
    @DeleteMapping("/history/{id}") public Map<String,Object> remove(@PathVariable long id, Authentication auth){int removed=db.update("DELETE FROM history WHERE id=? AND user_id=?",id,owner(auth));if(removed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"ไม่พบประวัติ");return Map.of("ok",true);}
}
