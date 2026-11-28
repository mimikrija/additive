ALTER TABLE children ADD COLUMN token UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;

CREATE TABLE problems (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL REFERENCES children (id),
    sequence_number BIGINT NOT NULL CHECK (sequence_number > 0),
    level TEXT NOT NULL CHECK (level IN ('LEVEL_1', 'LEVEL_2', 'LEVEL_3', 'LEVEL_4')),
    operation TEXT NOT NULL CHECK (operation IN ('ADDITION', 'SUBTRACTION')),
    first_operand INTEGER NOT NULL CHECK (first_operand >= 0),
    second_operand INTEGER NOT NULL CHECK (second_operand >= 0),
    answer INTEGER NOT NULL CHECK (answer >= 0),
    solved BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (child_id, sequence_number)
);

CREATE UNIQUE INDEX one_unsolved_problem_per_child ON problems (child_id) WHERE NOT solved;

CREATE TABLE attempts (
    id UUID PRIMARY KEY,
    problem_id UUID NOT NULL REFERENCES problems (id),
    submitted_answer INTEGER NOT NULL CHECK (submitted_answer >= 0),
    correct BOOLEAN NOT NULL,
    counted BOOLEAN NOT NULL,
    rewarded BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
