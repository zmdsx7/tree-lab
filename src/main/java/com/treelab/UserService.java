package com.treelab;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class UserService {
    public record User(String id, String name, String email) {}
    private record Account(User user, String hash) {}
    private final MongoTemplate db;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    public UserService(MongoTemplate db, PasswordEncoder passwords) {
        this.db=db;this.passwords=passwords;
        dummyHash=passwords.encode(UUID.randomUUID().toString());
        db.indexOps("users").ensureIndex(new org.springframework.data.mongodb.core.index.Index().on("email", Sort.Direction.ASC).unique());
    }
    private String email(String value) {
        if(value==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"กรุณากรอกอีเมล");
        String email=value.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"รูปแบบอีเมลไม่ถูกต้อง");
        return email;
    }
    private void password(String value) {
        if(value==null || value.length()<8 || value.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร และไม่เกิน 72 ไบต์");
    }
    public User register(String name,String email,String password) {
        String normalized=email(email);password(password);
        if(name==null || name.trim().isEmpty() || name.trim().length()>80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ชื่อต้องมี 1–80 ตัวอักษร");
        Document account=new Document("_id",new ObjectId()).append("name",name.trim()).append("email",normalized)
            .append("password_hash",passwords.encode(password)).append("created_at",new Date());
        try { db.insert(account,"users"); }
        catch(DuplicateKeyException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"อีเมลนี้ถูกใช้งานแล้ว");}
        return find(normalized);
    }
    public User login(String email,String password) {
        String normalized=email(email);
        if(password==null || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"อีเมลหรือรหัสผ่านไม่ถูกต้อง");
        Document account=db.findOne(Query.query(Criteria.where("email").is(normalized)),Document.class,"users");
        boolean valid=passwords.matches(password,account==null?dummyHash:account.getString("password_hash"));
        if(account==null || !valid)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"อีเมลหรือรหัสผ่านไม่ถูกต้อง");
        return user(account);
    }
    private User user(Document d){return new User(d.getObjectId("_id").toHexString(),d.getString("name"),d.getString("email"));}

    public User find(String email) {
        Document account=db.findOne(Query.query(Criteria.where("email").is(email)),Document.class,"users");
        if(account==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"กรุณาเข้าสู่ระบบใหม่");
        return user(account);
    }
}
