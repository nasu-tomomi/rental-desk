-- 既存DB用。予約・利用者データを保持して追加テーブルを作成する。
CREATE TABLE IF NOT EXISTS `reservation_user_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reservation_user_id` bigint NOT NULL,
  `price_master_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `reservation_user_id` (`reservation_user_id`),
  KEY `reservation_user_items_price_idx` (`price_master_id`),
  CONSTRAINT `reservation_user_items_user_fk` FOREIGN KEY (`reservation_user_id`) REFERENCES `reservation_users` (`id`),
  CONSTRAINT `reservation_user_items_price_fk` FOREIGN KEY (`price_master_id`) REFERENCES `price_master` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
