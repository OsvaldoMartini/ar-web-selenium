-- =====================================================================
-- V001__init_schema.sql  (Postgres dialect)
-- Source:   PerformInitializer.initializeMainDatabasePostgres()  (lines 42-237)
-- Purpose:  Bootstrap an empty ar_web database with the canonical schema.
-- Safety:   Uses CREATE TABLE (no DROP). Migration runner guarantees this
--           file only runs on an empty schema.
-- =====================================================================

-- home_banking  (root of the ownership hierarchy)
CREATE TABLE home_banking (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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

-- home_url
CREATE TABLE home_url (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    url             TEXT,
    home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE
);

-- bot_job
CREATE TABLE bot_job (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT UNIQUE,
    description     TEXT,
    priority        TEXT,
    active          INTEGER NOT NULL,
    home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE,
    home_url_id     INTEGER REFERENCES home_url(id)     ON DELETE CASCADE
);

-- block  (regular tree)
CREATE TABLE block (
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    block_order_number  INTEGER NOT NULL,
    name                TEXT NOT NULL,
    description         TEXT,
    type_id             INTEGER,
    export_file         TEXT,
    active              INTEGER NOT NULL,
    wait                INTEGER,
    bot_job_id          INTEGER REFERENCES bot_job(id) ON DELETE CASCADE
);

-- instruction  (regular tree)
CREATE TABLE instruction (
    id                          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    block_id                    INTEGER REFERENCES block(id)   ON DELETE CASCADE,
    variable_id                 INTEGER,                                   -- deferred FK; see bottom
    parent_block_id             INTEGER REFERENCES block(id)   ON DELETE CASCADE,
    parent_id                   INTEGER,                                   -- self-reference; set after insert
    bot_job_id                  INTEGER REFERENCES bot_job(id) ON DELETE CASCADE
);

-- reference
CREATE TABLE reference (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_type  TEXT,
    value           TEXT,
    instruction_id  INTEGER NOT NULL REFERENCES instruction(id) ON DELETE CASCADE,
    bot_job_id      INTEGER REFERENCES bot_job(id) ON DELETE CASCADE
);

-- variable
CREATE TABLE variable (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            TEXT,
    name            TEXT,
    value           TEXT,
    local_format    TEXT,
    delimiter       TEXT,
    instruction_id  INTEGER REFERENCES instruction(id) ON DELETE CASCADE,
    bot_job_id      INTEGER REFERENCES bot_job(id)     ON DELETE CASCADE
);

-- component_block  (component tree — parallels block)
CREATE TABLE component_block (
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    home_banking_id     INTEGER REFERENCES home_banking(id) ON DELETE CASCADE,
    block_order_number  INTEGER NOT NULL,
    name                TEXT NOT NULL,
    description         TEXT,
    type_id             INTEGER,
    export_file         TEXT,
    active              INTEGER,
    wait                INTEGER
);

-- component_instruction  (parallels instruction)
CREATE TABLE component_instruction (
    id                          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    block_id                    INTEGER REFERENCES component_block(id) ON DELETE CASCADE,
    variable_id                 INTEGER,                                    -- deferred FK
    parent_block_id             INTEGER REFERENCES component_block(id) ON DELETE CASCADE,
    parent_id                   INTEGER,                                    -- self-reference
    home_banking_id             INTEGER REFERENCES home_banking(id) ON DELETE CASCADE
);

-- component_reference
CREATE TABLE component_reference (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_type  TEXT,
    value           TEXT,
    instruction_id  INTEGER NOT NULL REFERENCES component_instruction(id) ON DELETE CASCADE,
    home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE
);

-- component_variable
CREATE TABLE component_variable (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            TEXT,
    name            TEXT,
    value           TEXT,
    local_format    TEXT,
    delimiter       TEXT,
    instruction_id  INTEGER REFERENCES component_instruction(id) ON DELETE CASCADE,
    home_banking_id INTEGER REFERENCES home_banking(id)         ON DELETE CASCADE
);

-- Deferred FKs (variable / component_variable are created AFTER instruction/component_instruction).
-- They enforce the row created in V001__init to match the existing behaviour; the
-- cross-FK instruction.variable_id -> variable.id stays intentionally off, mirroring
-- the original code (see the commented-out ALTER in PerformInitializer line 219-229).
ALTER TABLE variable
    ADD CONSTRAINT fk_variable_instruction
    FOREIGN KEY (instruction_id) REFERENCES instruction(id) ON DELETE CASCADE;

ALTER TABLE component_variable
    ADD CONSTRAINT fk_component_variable_instruction
    FOREIGN KEY (instruction_id) REFERENCES component_instruction(id) ON DELETE CASCADE;
