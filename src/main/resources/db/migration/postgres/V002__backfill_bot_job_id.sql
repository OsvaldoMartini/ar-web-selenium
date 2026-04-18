-- =====================================================================
-- V002__backfill_bot_job_id.sql
-- Replaces:  PerformDataBase.migrationScriptsv2_1f()    (line 3347, dead code)
-- Behaviour: propagate bot_job_id from block -> instruction / reference /
--            complex_instruction, and set active=TRUE where it was left null.
-- Idempotent: repeat runs produce no change because the UPDATE WHERE
--            clauses restrict to rows still missing bot_job_id / active.
-- =====================================================================

-- 1) instructions inherit bot_job_id from their parent block
UPDATE instruction i
SET    bot_job_id = b.bot_job_id
FROM   block b
WHERE  i.block_id = b.id
  AND  i.bot_job_id IS NULL;

-- 2) references inherit bot_job_id from their parent instruction
UPDATE reference r
SET    bot_job_id = i.bot_job_id
FROM   instruction i
WHERE  r.instruction_id = i.id
  AND  r.bot_job_id IS NULL;

-- 3) variables inherit bot_job_id from their parent instruction
UPDATE variable v
SET    bot_job_id = i.bot_job_id
FROM   instruction i
WHERE  v.instruction_id = i.id
  AND  v.bot_job_id IS NULL;

-- 4) default "active" flag for rows created before the column was NOT NULL
UPDATE instruction SET active = 1 WHERE active IS NULL;
UPDATE block       SET active = 1 WHERE active IS NULL;
