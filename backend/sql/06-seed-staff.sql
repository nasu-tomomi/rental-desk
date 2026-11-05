-- 演習・デモ専用の初期スタッフ。BCryptハッシュのみ保存する。
-- 既存アカウントのパスワードは上書きしない。再実行可能。
SET NAMES utf8mb4;
INSERT INTO staff (username, password)
SELECT 'staff', '$2y$10$wG4u8BQDNbAe94dKbmyxJ.meHJTSgd9g7JSY1zo7INlepjWGRfZZ2'
WHERE NOT EXISTS (SELECT 1 FROM staff WHERE username = 'staff');
