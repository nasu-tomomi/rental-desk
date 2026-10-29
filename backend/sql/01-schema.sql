-- 作成済みDBから取得した3テーブルの定義。
SET NAMES utf8mb4;

CREATE TABLE `price_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `item` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `division` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price_1day` int NOT NULL,
  `price_2day` int NOT NULL,
  `price_after_3rd` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reservations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `applicant_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `applicant_kana` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mobile_phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pickup_store` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rental_date` date NOT NULL,
  `pickup_time` time DEFAULT NULL,
  `return_datetime` datetime DEFAULT NULL,
  `ski_resort` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `transport` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `accommodation` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_method` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_amount` int DEFAULT '0',
  `note` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reservation_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reservation_id` bigint NOT NULL,
  `user_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `division` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rental_days` int DEFAULT NULL,
  `experience` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `height` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shoe_size` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price_master_id` bigint DEFAULT NULL,
  `small_items` text COLLATE utf8mb4_unicode_ci,
  `lent_size_memo` text COLLATE utf8mb4_unicode_ci,
  `amount` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `reservation_id` (`reservation_id`),
  KEY `price_master_id` (`price_master_id`),
  CONSTRAINT `reservation_users_ibfk_1` FOREIGN KEY (`reservation_id`) REFERENCES `reservations` (`id`),
  CONSTRAINT `reservation_users_ibfk_2` FOREIGN KEY (`price_master_id`) REFERENCES `price_master` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
