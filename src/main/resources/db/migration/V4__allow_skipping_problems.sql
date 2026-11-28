ALTER TABLE problems
    ADD COLUMN skipped BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT problem_cannot_be_solved_and_skipped CHECK (NOT (solved AND skipped));

DROP INDEX one_unsolved_problem_per_child;
CREATE UNIQUE INDEX one_open_problem_per_child ON problems (child_id) WHERE NOT solved AND NOT skipped;

CREATE INDEX attempts_by_problem ON attempts (problem_id);

UPDATE children SET wrong_streak = 5 WHERE level = 'LEVEL_1' AND wrong_streak > 5;
