package dev.chatads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DbConnectionTest {

    @Test
    void jdbcUrlPassesThroughWithSslMode() {
        DbConnection db = DbConnection.parse(
                "jdbc:postgresql://ep-cool-123-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require", "owner", "pw");
        assertEquals("jdbc:postgresql://ep-cool-123-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require", db.jdbcUrl());
        assertEquals("owner", db.user());
        assertEquals("pw", db.password());
    }

    @Test
    void postgresUrlBecomesJdbcWithCredentialsFromTheUrl() {
        DbConnection db = DbConnection.parse(
                "postgresql://neondb_owner:p%40ss%2Fword@ep-cool-123-pooler.aws.neon.tech/neondb?sslmode=require&channel_binding=require",
                "", "");
        assertEquals("jdbc:postgresql://ep-cool-123-pooler.aws.neon.tech/neondb?sslmode=require", db.jdbcUrl());
        assertEquals("neondb_owner", db.user());
        assertEquals("p@ss/word", db.password());
    }

    @Test
    void explicitUserAndPasswordWinOverTheUrl() {
        DbConnection db = DbConnection.parse("postgres://a:b@db.example.com:6543/app?sslmode=require", "owner", "from-env");
        assertEquals("jdbc:postgresql://db.example.com:6543/app?sslmode=require", db.jdbcUrl());
        assertEquals("owner", db.user());
        assertEquals("from-env", db.password());
    }

    @Test
    void urlWithoutQueryOrCredentialsKeepsTheEnvValues() {
        DbConnection db = DbConnection.parse("postgresql://localhost/chatads", "chatads", "pw");
        assertEquals("jdbc:postgresql://localhost/chatads", db.jdbcUrl());
        assertEquals("chatads", db.user());
    }

    @Test
    void otherSchemesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DbConnection.parse("mysql://host/db", "u", "p"));
    }
}
