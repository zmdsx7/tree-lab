package com.treelab;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class UserService {
    public record User(long id, String name, String email) {}
    private record Account(User user, String hash) {}
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    public UserService(JdbcTemplate db, PasswordEncoder passwords) {
        this.db=db;this.passwords=passwords;
        dummyHash=passwords.encode(UUID.randomUUID().toString());
        db.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
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
        try { db.update("INSERT INTO users(name,email,password_hash) VALUES (?,?,?)",name.trim(),normalized,passwords.encode(password)); }
        catch(DataAccessException ex){
            // SQLite may report UNIQUE violations as UncategorizedSQLException.
            if(db.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,normalized)>0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"อีเมลนี้ถูกใช้งานแล้ว");
            throw ex;
        }
        return find(normalized);
    }
    public User login(String email,String password) {
        String normalized=email(email);
        if(password==null || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"อีเมลหรือรหัสผ่านไม่ถูกต้อง");
        List<Account> rows=db.query("SELECT id,name,email,password_hash FROM users WHERE email=?",(rs,i)->new Account(new User(rs.getLong(1),rs.getString(2),rs.getString(3)),rs.getString(4)),normalized);
        boolean valid=passwords.matches(password,rows.isEmpty()?dummyHash:rows.get(0).hash());
        if(rows.isEmpty() || !valid)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"อีเมลหรือรหัสผ่านไม่ถูกต้อง");
        return rows.get(0).user();
    }
    public User find(String email) {
        return db.queryForObject("SELECT id,name,email FROM users WHERE email=?",(rs,i)->new User(rs.getLong(1),rs.getString(2),rs.getString(3)),email);
    }
}
