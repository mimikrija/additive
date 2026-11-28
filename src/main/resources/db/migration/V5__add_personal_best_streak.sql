ALTER TABLE children
    ADD COLUMN current_correct_run BIGINT NOT NULL DEFAULT 0 CHECK (current_correct_run >= 0),
    ADD COLUMN best_correct_streak BIGINT NOT NULL DEFAULT 0 CHECK (best_correct_streak >= current_correct_run);

UPDATE children
SET current_correct_run = correct_streak, best_correct_streak = correct_streak
WHERE correct_streak > 0;

WITH numbered_attempts AS (
    SELECT problems.child_id, attempts.correct,
        -- A counted correct answer closes its problem, so it comes after any counted wrong answers for that problem.
        COUNT(*) FILTER (WHERE NOT attempts.correct) OVER (
            PARTITION BY problems.child_id
            ORDER BY problems.sequence_number, attempts.correct, attempts.id
        ) AS run_number
    FROM attempts
    JOIN problems ON problems.id = attempts.problem_id
    WHERE attempts.counted
), runs AS (
    SELECT child_id, run_number, COUNT(*) FILTER (WHERE correct) AS run_length
    FROM numbered_attempts
    GROUP BY child_id, run_number
), streaks AS (
    SELECT DISTINCT ON (child_id) child_id, run_length AS current_run,
        MAX(run_length) OVER (PARTITION BY child_id) AS best_streak
    FROM runs
    ORDER BY child_id, run_number DESC
)
UPDATE children
SET current_correct_run = streaks.current_run, best_correct_streak = streaks.best_streak
FROM streaks
WHERE children.id = streaks.child_id;
