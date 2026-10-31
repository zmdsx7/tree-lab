package com.treelab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class AuthIntegrationTest {
    static final String URL;
    static {
        try {
            URL="jdbc:sqlite:"+Files.createTempFile("tree-auth-tests-", ".db");
            try(Connection c=DriverManager.getConnection(URL);Statement s=c.createStatement()) {
                s.execute("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,data TEXT NOT NULL,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
                s.execute("INSERT INTO history(name,data) VALUES ('legacy','{}')");
            }
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->URL);}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    String credentials(String name,String email,String password)throws Exception{return json.writeValueAsString(java.util.Map.of("name",name,"email",email,"password",password));}
    MockHttpSession register(String name,String email)throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(credentials(name,email,"StrongPass123")))
            .andExpect(status().isOk()).andExpect(jsonPath("user.name").value(name)).andReturn().getRequest().getSession(false);
    }
    @Test void authenticationAndOwnership()throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("user").isEmpty());
        mvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
        String tree="{\"kind\":\"avl\",\"actions\":[{\"type\":\"insert\",\"value\":30},{\"type\":\"insert\",\"value\":20},{\"type\":\"insert\",\"value\":10}]}";
        mvc.perform(post("/api/calculate").with(csrf()).contentType("application/json").content(tree)).andExpect(status().isOk()).andExpect(jsonPath("tree.value").value(20));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(credentials("A","a@example.com","StrongPass123"))).andExpect(status().isForbidden());
        MockHttpSession a=register("Alice","Alice@example.com");
        mvc.perform(get("/api/auth/me").session(a)).andExpect(jsonPath("user.email").value("alice@example.com"));
        mvc.perform(get("/api/history").session(a)).andExpect(content().json("[]"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM history WHERE user_id IS NULL",Integer.class));
        String hash=db.queryForObject("SELECT password_hash FROM users WHERE email='alice@example.com'",String.class);
        assertNotEquals("StrongPass123",hash);assertTrue(hash.startsWith("$2"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(credentials("Alice","ALICE@example.com","StrongPass123"))).andExpect(status().isConflict());
        mvc.perform(post("/api/history").session(a).with(csrf()).contentType("application/json").content("{\"name\":\"Private\",\"request\":"+tree+"}")).andExpect(status().isOk());
        long id=db.queryForObject("SELECT id FROM history WHERE name='Private'",Long.class);
        MockHttpSession b=register("Bob","bob@example.com");
        mvc.perform(get("/api/history").session(b)).andExpect(content().json("[]"));
        mvc.perform(get("/api/history/"+id).session(b)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/history/"+id).session(b).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/history/"+id).session(a)).andExpect(status().isOk()).andExpect(jsonPath("kind").value("avl"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(credentials("","alice@example.com","wrongpass"))).andExpect(status().isUnauthorized());
        MockHttpSession login=(MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(credentials("","alice@example.com","StrongPass123"))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/history").session(login)).andExpect(jsonPath("$[0].name").value("Private"));
        mvc.perform(post("/api/auth/logout").session(login).with(csrf())).andExpect(status().isOk());
        assertTrue(login.isInvalid());
        mvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/history/"+id).session(a).with(csrf())).andExpect(status().isOk());
    }
    @Test void realCsrfAndSessionRotation()throws Exception {
        MvcResult tokenResult=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session=(MockHttpSession)tokenResult.getRequest().getSession(false);
        String previousId=session.getId();
        String token=json.readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/register").session(session).header("X-CSRF-TOKEN",token).contentType("application/json")
            .content(credentials("Carol","carol@example.com","StrongPass123"))).andExpect(status().isOk());
        assertNotEquals(previousId,session.getId());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",token)).andExpect(status().isForbidden());
        MvcResult refreshed=mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        String fresh=json.readTree(refreshed.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",fresh)).andExpect(status().isOk());
        assertTrue(session.isInvalid());
    }
    @Test void invalidInputs()throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(credentials("A","not-email","StrongPass123"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(credentials("A","short@example.com","123"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(credentials("A","long@example.com","ก".repeat(30)))).andExpect(status().isBadRequest());
    }
}
