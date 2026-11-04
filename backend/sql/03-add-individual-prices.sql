-- 既存DB用。既存OR行を保持し、未登録の個別品目のみ追加する。再実行可能。
SET NAMES utf8mb4;

INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'グローブ', 'おとな', 1000, 1500, 500
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'グローブ' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'グローブ', 'こども', 500, 800, 300
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'グローブ' AND division = 'こども');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ゴーグル', 'おとな', 1000, 1500, 500
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ゴーグル' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ゴーグル', 'こども', 500, 800, 300
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ゴーグル' AND division = 'こども');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ヘルメット', 'おとな', 1000, 1500, 500
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ヘルメット' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ヘルメット', 'こども', 500, 800, 300
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ヘルメット' AND division = 'こども');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'スキーストック', 'おとな', 500, 800, 300
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'スキーストック' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'スキーストック', 'こども', 300, 500, 200
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'スキーストック' AND division = 'こども');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', '長靴', 'おとな', 500, 800, 300
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = '長靴' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', '長靴', 'こども', 300, 500, 200
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = '長靴' AND division = 'こども');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ニットキャップ', 'おとな', 500, 800, 300
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ニットキャップ' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ニットキャップ', 'こども', 300, 500, 200
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ニットキャップ' AND division = 'こども');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ウエアジャケット', 'おとな', 2000, 3500, 1500
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ウエアジャケット' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ウエアジャケット', 'こども', 1500, 2500, 1000
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ウエアジャケット' AND division = 'こども');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ウエアパンツ', 'おとな', 2000, 3500, 1500
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ウエアパンツ' AND division = 'おとな');
INSERT INTO price_master (category, item, division, price_1day, price_2day, price_after_3rd)
SELECT 'スタンダード', 'ウエアパンツ', 'こども', 1500, 2500, 1000
WHERE NOT EXISTS (SELECT 1 FROM price_master WHERE category = 'スタンダード' AND item = 'ウエアパンツ' AND division = 'こども');

SELECT COUNT(*) AS total_rows FROM price_master;
SELECT category, item, division, price_1day, price_2day, price_after_3rd
FROM price_master WHERE item IN ('グローブ', 'ゴーグル', 'ヘルメット', 'スキーストック', '長靴', 'ニットキャップ', 'ウエアジャケット', 'ウエアパンツ') ORDER BY id;
