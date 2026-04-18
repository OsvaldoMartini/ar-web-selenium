-- =====================================================================
-- V001__init_schema.sql  (MS Access / UCanAccess dialect)
-- Source:   PerformInitializer.initializeMainDatabaseAccess(dbFile)  (lines 255-480)
-- Notes:    - Access uses MEMO instead of TEXT, AUTOINCREMENT instead of IDENTITY.
--           - FKs MUST be added with ALTER TABLE ... ADD CONSTRAINT — inline
--             REFERENCES is not respected by the UCanAccess driver.
--           - Every statement below must be sent separately (one per JDBC call).
--           - SQLite/Postgres share a CASCADE semantic that Access matches via
--             "ON DELETE CASCADE" — verified with UCanAccess 5.x.
-- =====================================================================

CREATE TABLE home_banking (
    id              AUTOINCREMENT PRIMARY KEY,
    url             MEMO,
    name            MEMO,
    priority        MEMO,
    search_config   MEMO,
    options_config  MEMO,
    cookies         MEMO,
    driver_session  MEMO,
    username        MEMO,
    password        MEMO
);

CREATE TABLE home_url (
    id              AUTOINCREMENT PRIMARY KEY,
    url             MEMO,
    home_banking_id LONG
);
ALTER TABLE home_url
    ADD CONSTRAINT fk_home_url_home_banking
    FOREIGN KEY (home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE;

CREATE TABLE bot_job (
    id              AUTOINCREMENT PRIMARY KEY,
    name            MEMO,                -- UNIQUE emulated via index below
    description     MEMO,
    priority        MEMO,
    active          LONG NOT NULL,
    home_banking_id LONG,
    home_url_id     LONG
);
CREATE UNIQUE INDEX ux_bot_job_name ON bot_job (name);
ALTER TABLE bot_job ADD CONSTRAINT fk_bot_job_home_banking
    FOREIGN KEY (home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE;
ALTER TABLE bot_job ADD CONSTRAINT fk_bot_job_home_url
    FOREIGN KEY (home_url_id)     REFERENCES home_url(id)     ON DELETE CASCADE;

CREATE TABLE block (
    id                  AUTOINCREMENT PRIMARY KEY,
    block_order_number  LONG NOT NULL,
    name                MEMO NOT NULL,
    description         MEMO,
    type_id             LONG,
    export_file         MEMO,
    active              LONG NOT NULL,
    wait                LONG,
    bot_job_id          LONG
);
ALTER TABLE block ADD CONSTRAINT fk_block_bot_job
    FOREIGN KEY (bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE;

CREATE TABLE instruction (
    id                          AUTOINCREMENT PRIMARY KEY,
    instruction_order_number    LONG NOT NULL,
    actions                     MEMO,
    name                        MEMO,
    xpath                       MEMO,
    coordinates                 MEMO,
    force_coordinates           LONG,
    iframe_xpath                MEMO,
    tag_name                    MEMO,
    shadow_host                 MEMO,
    shadow_root                 MEMO,
    css_selector                MEMO,
    description                 MEMO,
    operation                   MEMO,
    optional                    LONG,
    block_marked                LONG,
    default_value               MEMO,
    action_custom_max_wait_sec  LONG,
    on_hold_seconds             LONG,
    codified                    LONG,
    export_to_abr               LONG,
    active                      LONG NOT NULL,
    block_id                    LONG,
    variable_id                 LONG,
    parent_block_id             LONG,
    parent_id                   LONG,
    bot_job_id                  LONG
);
ALTER TABLE instruction ADD CONSTRAINT fk_instruction_block
    FOREIGN KEY (block_id)        REFERENCES block(id)   ON DELETE CASCADE;
ALTER TABLE instruction ADD CONSTRAINT fk_instruction_parent_block
    FOREIGN KEY (parent_block_id) REFERENCES block(id)   ON DELETE CASCADE;
ALTER TABLE instruction ADD CONSTRAINT fk_instruction_bot_job
    FOREIGN KEY (bot_job_id)      REFERENCES bot_job(id) ON DELETE CASCADE;

CREATE TABLE reference (
    id              AUTOINCREMENT PRIMARY KEY,
    reference_type  MEMO,
    value           MEMO,
    instruction_id  LONG NOT NULL,
    bot_job_id      LONG
);
ALTER TABLE reference ADD CONSTRAINT fk_reference_instruction
    FOREIGN KEY (instruction_id) REFERENCES instruction(id) ON DELETE CASCADE;
ALTER TABLE reference ADD CONSTRAINT fk_reference_bot_job
    FOREIGN KEY (bot_job_id)     REFERENCES bot_job(id)     ON DELETE CASCADE;

CREATE TABLE variable (
    id              AUTOINCREMENT PRIMARY KEY,
    type            MEMO,
    name            MEMO,
    value           MEMO,
    local_format    MEMO,
    delimiter       MEMO,
    instruction_id  LONG,
    bot_job_id      LONG
);
ALTER TABLE variable ADD CONSTRAINT fk_variable_instruction
    FOREIGN KEY (instruction_id) REFERENCES instruction(id) ON DELETE CASCADE;
ALTER TABLE variable ADD CONSTRAINT fk_variable_bot_job
    FOREIGN KEY (bot_job_id)     REFERENCES bot_job(id)     ON DELETE CASCADE;

CREATE TABLE component_block (
    id                  AUTOINCREMENT PRIMARY KEY,
    home_banking_id     LONG,
    block_order_number  LONG NOT NULL,
    name                MEMO NOT NULL,
    description         MEMO,
    type_id             LONG,
    export_file         MEMO,
    active              LONG,
    wait                LONG
);
ALTER TABLE component_block ADD CONSTRAINT fk_component_block_home_banking
    FOREIGN KEY (home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE;

CREATE TABLE component_instruction (
    id                          AUTOINCREMENT PRIMARY KEY,
    instruction_order_number    LONG NOT NULL,
    actions                     MEMO,
    name                        MEMO,
    xpath                       MEMO,
    coordinates                 MEMO,
    force_coordinates           LONG,
    iframe_xpath                MEMO,
    tag_name                    MEMO,
    shadow_host                 MEMO,
    shadow_root                 MEMO,
    css_selector                MEMO,
    description                 MEMO,
    operation                   MEMO,
    optional                    LONG,
    block_marked                LONG,
    default_value               MEMO,
    action_custom_max_wait_sec  LONG,
    on_hold_seconds             LONG,
    codified                    LONG,
    export_to_abr               LONG,
    active                      LONG NOT NULL,
    block_id                    LONG,
    variable_id                 LONG,
    parent_block_id             LONG,
    parent_id                   LONG,
    home_banking_id             LONG
);
ALTER TABLE component_instruction ADD CONSTRAINT fk_cinst_cblock
    FOREIGN KEY (block_id)        REFERENCES component_block(id) ON DELETE CASCADE;
ALTER TABLE component_instruction ADD CONSTRAINT fk_cinst_parent_cblock
    FOREIGN KEY (parent_block_id) REFERENCES component_block(id) ON DELETE CASCADE;
ALTER TABLE component_instruction ADD CONSTRAINT fk_cinst_home_banking
    FOREIGN KEY (home_banking_id) REFERENCES home_banking(id)    ON DELETE CASCADE;

CREATE TABLE component_reference (
    id              AUTOINCREMENT PRIMARY KEY,
    reference_type  MEMO,
    value           MEMO,
    instruction_id  LONG NOT NULL,
    home_banking_id LONG
);
ALTER TABLE component_reference ADD CONSTRAINT fk_cref_cinst
    FOREIGN KEY (instruction_id)  REFERENCES component_instruction(id) ON DELETE CASCADE;
ALTER TABLE component_reference ADD CONSTRAINT fk_cref_home_banking
    FOREIGN KEY (home_banking_id) REFERENCES home_banking(id)          ON DELETE CASCADE;

CREATE TABLE component_variable (
    id              AUTOINCREMENT PRIMARY KEY,
    type            MEMO,
    name            MEMO,
    value           MEMO,
    local_format    MEMO,
    delimiter       MEMO,
    instruction_id  LONG,
    home_banking_id LONG
);
ALTER TABLE component_variable ADD CONSTRAINT fk_cvar_cinst
    FOREIGN KEY (instruction_id)  REFERENCES component_instruction(id) ON DELETE CASCADE;
ALTER TABLE component_variable ADD CONSTRAINT fk_cvar_home_banking
    FOREIGN KEY (home_banking_id) REFERENCES home_banking(id)          ON DELETE CASCADE;
