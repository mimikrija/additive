CREATE TABLE children (
    id UUID PRIMARY KEY
);

CREATE TABLE icecreams (
    child_id UUID NOT NULL REFERENCES children (id),
    operation TEXT NOT NULL CHECK (operation IN ('ADDITION', 'SUBTRACTION')),
    first_operand INTEGER NOT NULL CHECK (first_operand >= 0),
    second_operand INTEGER NOT NULL CHECK (second_operand >= 0),
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (child_id, operation, first_operand, second_operand)
);
