-- V2__alter_questions_to_text.sql
-- Konvertiert questions.question und questions.answer von varchar(...) nach text.
-- USING-Klausel stellt sicher, dass vorhandene Daten korrekt konvertiert werden.

BEGIN;

-- Prüfen ob die Spalte existiert, dann ändern. (sichere Variante)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'questions' AND column_name = 'question'
  ) THEN
    -- Nur dann ausführen
ALTER TABLE questions
ALTER COLUMN question TYPE text USING question::text;
END IF;
END
$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'questions' AND column_name = 'answer'
  ) THEN
ALTER TABLE questions
ALTER COLUMN answer TYPE text USING answer::text;
END IF;
END
$$;

COMMIT;
