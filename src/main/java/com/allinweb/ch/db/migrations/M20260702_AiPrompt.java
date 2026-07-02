package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import lombok.extern.slf4j.Slf4j;

/**
 * GEN FLOW phase 1 — provider-agnostic AI prompt storage.
 *
 * <p>Creates {@code ai_prompt (id, name UNIQUE, content, active, updated_at)} and seeds the
 * {@code GEN_FLOW} prompt template used by the GEN FLOW button to generate surface-navigation
 * test blocks. The template contains the placeholders {@code {{BLOCK_NAME}}},
 * {@code {{ELEMENTS_JSON}}}, {@code {{MAX_BLOCKS}}} and {@code {{JSON_SCHEMA}}} that
 * {@code com.allinweb.ch.ai.GenFlowService} substitutes at runtime. The element inventory is
 * never stored in the DB — only in the composed prompt file — so the stored template stays
 * small (Access MEMO/LONGCHAR safe).
 */
@Slf4j
public class M20260702_AiPrompt implements Migration {

    private static final String NAME = "2026-07-02__ai_prompt";
    private static final String TABLE = "ai_prompt";

    /** Seeded provider-agnostic GEN FLOW template (works with Claude Code, Together, OpenAI). */
    public static final String GEN_FLOW_PROMPT =
            """
            You are generating a SURFACE NAVIGATION TEST for a web page.

            CONTEXT
            - Source block: "{{BLOCK_NAME}}"
            - The JSON array below lists the interactive elements found on the page (links, buttons, inputs).
              Each entry has: name, clientNamed, tagName, actions, xpath, cssSelector.

            ELEMENTS:
            {{ELEMENTS_JSON}}

            TASK
            Create a navigation test plan that visits every link and button, one small navigation unit ("block") per target:
            1. For each link or button: one block with a CLICK step on that element, followed by one BACK step to return to the original page.
            2. If a click clearly does NOT navigate away (expand/collapse, same-page tab, anchor "#"), you may omit the BACK step.
            3. For plain text inputs you MAY add an INSERT step with a short synthetic value (e.g. "test", "123", "test@example.com").
            4. NAVIGATION ONLY: never fill passwords or login fields, never submit forms, never click submit/save/delete/confirm/logout-style buttons, no destructive actions.
            5. Produce at most {{MAX_BLOCKS}} blocks; if there are more targets, keep the most important navigation ones.

            STRICT RULES
            - Use ONLY elements listed in ELEMENTS. Copy "name", "xpath" and "cssSelector" EXACTLY as given. Never invent elements or locators.
            - "action" must be exactly one of: CLICK, INSERT, BACK.
            - BACK steps have no element fields. INSERT steps must include "value".
            - Give each block a short descriptive "name" (max 40 chars).

            OUTPUT
            Respond with ONLY one JSON object matching this schema - no markdown fences, no commentary:
            {{JSON_SCHEMA}}
            """;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        createAiPromptTable(conn, dialect);
        seedGenFlowPrompt(conn);
    }

    private void createAiPromptTable(Connection conn, String dialect) throws SQLException {
        if (tableExists(conn, TABLE)) {
            log.info("{} — table {} already present, skipping create", NAME, TABLE);
            return;
        }
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "name VARCHAR(100) NOT NULL UNIQUE, "
                        + "content TEXT, "
                        + "active INTEGER DEFAULT 1, "
                        + "updated_at TIMESTAMP"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id BIGINT IDENTITY(1,1) PRIMARY KEY, "
                        + "name NVARCHAR(100) NOT NULL UNIQUE, "
                        + "content NVARCHAR(MAX), "
                        + "active INT DEFAULT 1, "
                        + "updated_at DATETIME"
                        + ")";
                break;
            case "TEXT": // SQLite
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "name TEXT NOT NULL UNIQUE, "
                        + "content TEXT, "
                        + "active INTEGER DEFAULT 1, "
                        + "updated_at TEXT"
                        + ")";
                break;
            default: // Access (UCanAccess) — LONGCHAR = MEMO, needed for the multi-KB template
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id COUNTER PRIMARY KEY, "
                        + "name VARCHAR(100) NOT NULL UNIQUE, "
                        + "content LONGCHAR, "
                        + "active LONG, "
                        + "updated_at DATETIME"
                        + ")";
                break;
        }
        try (Statement st = conn.createStatement()) {
            log.info("{} — exec: {}", NAME, ddl);
            st.executeUpdate(ddl);
        }
    }

    private void seedGenFlowPrompt(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM " + TABLE + " WHERE name = ?")) {
            ps.setString(1, "GEN_FLOW");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    log.info("{} — GEN_FLOW prompt already seeded", NAME);
                    return;
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + TABLE + " (name, content, active, updated_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, "GEN_FLOW");
            ps.setString(2, GEN_FLOW_PROMPT);
            ps.setInt(3, 1);
            ps.setString(4, new Timestamp(System.currentTimeMillis()).toString());
            ps.executeUpdate();
            log.info("{} — seeded GEN_FLOW prompt ({} chars)", NAME, GEN_FLOW_PROMPT.length());
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        for (String tn : new String[] {tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
            try (ResultSet rs = md.getTables(null, null, tn, new String[] {"TABLE"})) {
                if (rs.next()) return true;
            }
        }
        return false;
    }
}
