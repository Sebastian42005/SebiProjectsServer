-- Erweitert die bestehende app_user-Constraint, sodass neben ADMIN auch USER erlaubt ist.

BEGIN;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_name = 'app_user'
  ) THEN
    ALTER TABLE app_user
      DROP CONSTRAINT IF EXISTS app_user_role_check;

    ALTER TABLE app_user
      ADD CONSTRAINT app_user_role_check
      CHECK (role IN ('ADMIN', 'USER'));
  END IF;
END
$$;

COMMIT;
