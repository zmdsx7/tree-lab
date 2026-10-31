package com.treelab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;

import org.bson.Document;
import org.bson.types.ObjectId;

import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.DeleteResult;

class AuthIntegrationTest {

    MongoTemplate database() {
        MongoTemplate db = mock(MongoTemplate.class);

        when(db.indexOps(anyString()))
            .thenReturn(mock(IndexOperations.class));

        return db;
    }

    @Test
    void registrationNormalizesEmailAndHashesPassword() {
        MongoTemplate db = database();
        var encoder = new BCryptPasswordEncoder();

        UserService users = new UserService(db, encoder);

        Document stored = new Document("_id", new ObjectId())
            .append("name", "Test")
            .append("email", "test@example.com");

        when(db.findOne(any(Query.class), eq(Document.class), eq("users")))
            .thenReturn(stored);

        users.register(" Test ", " TEST@Example.com ", "password123");

        ArgumentCaptor<Document> saved =
            ArgumentCaptor.forClass(Document.class);

        verify(db).insert(saved.capture(), eq("users"));

        assertEquals(
            "test@example.com",
            saved.getValue().getString("email")
        );

        assertTrue(
            encoder.matches(
                "password123",
                saved.getValue().getString("password_hash")
            )
        );

        assertFalse(saved.getValue().containsKey("password"));
    }

    @Test
    void duplicateAndIncorrectCredentialsAreRejected() {
        MongoTemplate db = database();
        var encoder = new BCryptPasswordEncoder();

        UserService users = new UserService(db, encoder);

        when(db.insert(any(Document.class), eq("users")))
            .thenThrow(new DuplicateKeyException("duplicate"));

        assertEquals(
            409,
            assertThrows(
                ResponseStatusException.class,
                () -> users.register(
                    "Test",
                    "test@example.com",
                    "password123"
                )
            ).getStatusCode().value()
        );

        assertEquals(
            401,
            assertThrows(
                ResponseStatusException.class,
                () -> users.login(
                    "test@example.com",
                    "badpassword"
                )
            ).getStatusCode().value()
        );

        Document stored = new Document("_id", new ObjectId())
            .append("name", "Test")
            .append("email", "test@example.com")
            .append("password_hash", encoder.encode("password123"));

        when(db.findOne(any(Query.class), eq(Document.class), eq("users")))
            .thenReturn(stored);

        assertEquals(
            "test@example.com",
            users.login("test@example.com", "password123").email()
        );

        assertThrows(
            ResponseStatusException.class,
            () -> users.login("test@example.com", "wrong")
        );
    }

    @Test
    void historyAlwaysFiltersByOwnerAndRejectsMissingRecords() throws Exception {
        MongoTemplate db = database();

        UserService users = mock(UserService.class);

        when(users.find("a@example.com"))
            .thenReturn(
                new UserService.User(
                    "owner-a",
                    "A",
                    "a@example.com"
                )
            );

        TreeController controller = new TreeController(
            db,
            new ObjectMapper(),
            users
        );

        var auth = UsernamePasswordAuthenticationToken.authenticated(
            "a@example.com",
            null,
            java.util.List.of()
        );

        String id = new ObjectId().toHexString();

        assertEquals(
            404,
            assertThrows(
                ResponseStatusException.class,
                () -> controller.load(id, auth)
            ).getStatusCode().value()
        );

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        verify(db).findOne(
            query.capture(),
            eq(Document.class),
            eq("history")
        );

        assertEquals(
            "owner-a",
            query.getValue().getQueryObject().getString("user_id")
        );

        when(db.remove(any(Query.class), eq("history")))
            .thenReturn(DeleteResult.acknowledged(0));

        assertThrows(
            ResponseStatusException.class,
            () -> controller.remove(id, auth)
        );

        verify(db).remove(query.capture(), eq("history"));

        assertEquals(
            "owner-a",
            query.getValue().getQueryObject().getString("user_id")
        );

        assertThrows(
            ResponseStatusException.class,
            () -> controller.load("invalid", auth)
        );
    }
}