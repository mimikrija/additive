ALTER TABLE children
    ADD COLUMN level TEXT NOT NULL DEFAULT 'LEVEL_1'
        CHECK (level IN ('LEVEL_1', 'LEVEL_2', 'LEVEL_3', 'LEVEL_4')),
    ADD COLUMN correct_streak INTEGER NOT NULL DEFAULT 0 CHECK (correct_streak >= 0),
    ADD COLUMN wrong_streak INTEGER NOT NULL DEFAULT 0 CHECK (wrong_streak >= 0);
