-- =====================================================================
-- V001__init_schema.sql  (SQLite dialect)
-- Source:   PerformInitializer.initializeMainDatabaseSQLite(...)  (lines 482-670)
-- Notes:    - INTEGER PRIMARY KEY AUTOINCREMENT is SQLite's rowid alias.
--           - SQLite tolerates forward FK references only with inline syntax
--             and only when PRAGMA foreign_keys = ON.
--           - FIX: line 634 of the legacy code references block(id) for
--             component_instruction.parent_block_id — that is a bug
--             (cross-tree FK). The migration corrects it to component_block.
-- =====================================================================

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS home_banking (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    url             TEXT,
    name            TEXT,
    priority        TEXT,
    search_config   TEXT,
    options_config  TEXT,
    cookies         TEXT,
    driver_session  TEXT,
    username        TEXT,
    password        TEXT
);

CREATE TABLE IF NOT EXISTS home_url (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    url             TEXT,
    home_banking_id INTEGER,
    FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bot_job (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT UNIQUE,
    description     TEXT,
    priority        TEXT,
    active          INTEGER NOT NULL,
    home_banking_id INTEGER,
    home_url_id     INTEGER,
    FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE,
    FOREIGN KEY(home_url_id)     REFERENCES home_url(id)     ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS block (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    block_order_number  INTEGER NOT NULL,
    name                TEXT NOT NULL,
    description         TEXT,
    type_id             INTEGER,
    export_file         TEXT,
    active              INTEGER NOT NULL,
    wait                INTEGER,
    bot_job_id          INTEGER,
    FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS instruction (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    instruction_order_number    INTEGER NOT NULL,
    actions                     TEXT,
    name                        TEXT,
    xpath                       TEXT,
    coordinates                 TEXT,
    force_coordinates           INTEGER,
    iframe_xpath                TEXT,
    tag_name                    TEXT,
    shadow_host                 TEXT,
    shadow_root                 TEXT,
    css_selector                TEXT,
    description                 TEXT,
    operation                   TEXT,
    optional                    INTEGER,
    block_marked                INTEGER,
    default_value               TEXT,
    action_custom_max_wait_sec  INTEGER,
    on_hold_seconds             INTEGER,
    codified                    INTEGER,
    export_to_abr               INTEGER,
    active                      INTEGER NOT NULL,
    block_id                    INTEGER,
    variable_id                 INTEGER,
    parent_block_id             INTEGER,
    parent_id                   INTEGER,
    bot_job_id                  INTEGER,
    FOREIGN KEY(block_id)        REFERENCES block(id)   ON DELETE CASCADE,
    FOREIGN KEY(parent_block_id) REFERENCES block(id)   ON DELETE CASCADE,
    FOREIGN KEY(bot_job_id)      REFERENCES bot_job(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS reference (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    reference_type  TEXT,
    value           TEXT,
    instruction_id  INTEGER NOT NULL,
    bot_job_id      INTEGER,
    FOREIGN KEY(instruction_id) REFERENCES instruction(id) ON DELETE CASCADE,
    FOREIGN KEY(bot_job_id)     REFERENCES bot_job(id)     ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS variable (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    type            TEXT,
    name            TEXT,
    value           TEXT,
    local_format    TEXT,
    delimiter       TEXT,
    instruction_id  INTEGER,
    bot_job_id      INTEGER,
    FOREIGN KEY(instruction_id) REFERENCES instruction(id) ON DELETE CASCADE,
    FOREIGN KEY(bot_job_id)     REFERENCES bot_job(id)     ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS component_block (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    home_banking_id     INTEGER,
    block_order_number  INTEGER NOT NULL,
    name                TEXT NOT NULL,
    description         TEXT,
    type_id             INTEGER,
    export_file         TEXT,
    active              INTEGER,
    wait                INTEGER,
    FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS component_instruction (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    instruction_order_number    INTEGER NOT NULL,
    actions                     TEXT,
    name                        TEXT,
    xpath                       TEXT,
    coordinates                 TEXT,
    force_coordinates           INTEGER,
    iframe_xpath                TEXT,
    tag_name                    TEXT,
    shadow_host                 TEXT,
    shadow_root                 TEXT,
    css_selector                TEXT,
    description                 TEXT,
    operation                   TEXT,
    optional                    INTEGER,
    block_marked                INTEGER,
    default_value               TEXT,
    action_custom_max_wait_sec  INTEGER,
    on_hold_seconds             INTEGER,
    codified                    INTEGER,
    export_to_abr               INTEGER,
    active                      INTEGER NOT NULL,
    block_id                    INTEGER,
    variable_id                 INTEGER,
    parent_block_id             INTEGER,
    parent_id                   INTEGER,
    home_banking_id             INTEGER,
    -- FIX: parent_block_id now references component_block, not block
    FOREIGN KEY(block_id)        REFERENCES component_block(id) ON DELETE CASCADE,
    FOREIGN KEY(parent_block_id) REFERENCES component_block(id) ON DELETE CASCADE,
    FOREIGN KEY(home_banking_id) REFERENCES home_banking(id)    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS component_reference (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    reference_type  TEXT,
    value           TEXT,
    instruction_id  INTEGER NOT NULL,
    home_banking_id INTEGER,
    FOREIGN KEY(instruction_id)  REFERENCES component_instruction(id) ON DELETE CASCADE,
    FOREIGN KEY(home_banking_id) REFERENCES home_banking(id)          ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS component_variable (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    type            TEXT,
    name            TEXT,
    value           TEXT,
    local_format    TEXT,
    delimiter       TEXT,
    instruction_id  INTEGER,
    home_banking_id INTEGER,
    FOREIGN KEY(instruction_id)  REFERENCES component_instruction(id) ON DELETE CASCADE,
    FOREIGN KEY(home_banking_id) REFERENCES home_banking(id)          ON DELETE CASCADE
);
