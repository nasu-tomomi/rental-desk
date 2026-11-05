-- 既存DBへ1回だけ実行。申込時のnoteはそのまま保持する。
ALTER TABLE reservations ADD COLUMN staff_memo text COLLATE utf8mb4_unicode_ci;
