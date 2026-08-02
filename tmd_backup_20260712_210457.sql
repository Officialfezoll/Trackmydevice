/*M!999999\- enable the sandbox mode */ 
-- MariaDB dump 10.19  Distrib 10.11.14-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: localhost    Database: tmd
-- ------------------------------------------------------
-- Server version	10.11.14-MariaDB-0ubuntu0.24.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_predict_configs`
--

DROP TABLE IF EXISTS `ai_predict_configs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_predict_configs` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `cf_account_id` varchar(255) NOT NULL,
  `cf_api_token` varchar(255) NOT NULL,
  `model_id` varchar(255) NOT NULL DEFAULT '@cf/meta/llama-3-8b-instruct',
  `system_prompt` text DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_predict_configs`
--

LOCK TABLES `ai_predict_configs` WRITE;
/*!40000 ALTER TABLE `ai_predict_configs` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_predict_configs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `alert_rules`
--

DROP TABLE IF EXISTS `alert_rules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_rules` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `type` varchar(50) NOT NULL,
  `threshold` double DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `channels` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '["app"]' CHECK (json_valid(`channels`)),
  `sound_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `priority` int(11) NOT NULL DEFAULT 1,
  `email_template` varchar(255) DEFAULT NULL,
  `sms_template` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `alert_rules`
--

LOCK TABLES `alert_rules` WRITE;
/*!40000 ALTER TABLE `alert_rules` DISABLE KEYS */;
INSERT INTO `alert_rules` VALUES
(1,'Battery Low Alert','battery_low',10,1,'[\"app\"]',1,1,NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(2,'SIM Change Alert','sim_change',NULL,1,'[\"app\"]',1,1,NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(3,'Device Offline Alert','offline',NULL,1,'[\"app\"]',1,1,NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53');
/*!40000 ALTER TABLE `alert_rules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cache`
--

DROP TABLE IF EXISTS `cache`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cache` (
  `key` varchar(255) NOT NULL,
  `value` mediumtext NOT NULL,
  `expiration` bigint(20) NOT NULL,
  PRIMARY KEY (`key`),
  KEY `cache_expiration_index` (`expiration`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cache`
--

LOCK TABLES `cache` WRITE;
/*!40000 ALTER TABLE `cache` DISABLE KEYS */;
/*!40000 ALTER TABLE `cache` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cache_locks`
--

DROP TABLE IF EXISTS `cache_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cache_locks` (
  `key` varchar(255) NOT NULL,
  `owner` varchar(255) NOT NULL,
  `expiration` bigint(20) NOT NULL,
  PRIMARY KEY (`key`),
  KEY `cache_locks_expiration_index` (`expiration`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cache_locks`
--

LOCK TABLES `cache_locks` WRITE;
/*!40000 ALTER TABLE `cache_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `cache_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device_alerts`
--

DROP TABLE IF EXISTS `device_alerts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_alerts` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) unsigned NOT NULL,
  `user_id` bigint(20) unsigned DEFAULT NULL,
  `type` varchar(50) NOT NULL,
  `message` varchar(255) NOT NULL,
  `meta` text DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `priority` int(11) NOT NULL DEFAULT 1,
  `sound_played` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `device_alerts_user_id_is_read_index` (`user_id`,`is_read`),
  KEY `device_alerts_device_id_created_at_index` (`device_id`,`created_at`),
  CONSTRAINT `device_alerts_device_id_foreign` FOREIGN KEY (`device_id`) REFERENCES `devices` (`id`) ON DELETE CASCADE,
  CONSTRAINT `device_alerts_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device_alerts`
--

LOCK TABLES `device_alerts` WRITE;
/*!40000 ALTER TABLE `device_alerts` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_alerts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device_commands`
--

DROP TABLE IF EXISTS `device_commands`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_commands` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) unsigned NOT NULL,
  `user_id` bigint(20) unsigned DEFAULT NULL,
  `command` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'queued',
  `sent_by` varchar(255) DEFAULT NULL,
  `executed_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `device_commands_user_id_foreign` (`user_id`),
  KEY `device_commands_device_id_status_index` (`device_id`,`status`),
  KEY `device_commands_device_id_created_at_index` (`device_id`,`created_at`),
  CONSTRAINT `device_commands_device_id_foreign` FOREIGN KEY (`device_id`) REFERENCES `devices` (`id`) ON DELETE CASCADE,
  CONSTRAINT `device_commands_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device_commands`
--

LOCK TABLES `device_commands` WRITE;
/*!40000 ALTER TABLE `device_commands` DISABLE KEYS */;
INSERT INTO `device_commands` VALUES
(1,5,1,'alarm','Trigger alarm sound','queued','Fadhili Clever',NULL,'2026-07-12 12:21:44','2026-07-12 12:21:44'),
(2,7,1,'alarm','Trigger alarm sound','executed','Fadhili Clever','2026-07-12 12:36:17','2026-07-12 12:35:25','2026-07-12 12:36:17'),
(3,7,NULL,'locate','Request immediate location','executed',NULL,'2026-07-12 12:36:17','2026-07-12 12:35:49','2026-07-12 12:36:17'),
(4,7,1,'alarm_off','Stop alarm','executed','Fadhili Clever','2026-07-12 12:37:17','2026-07-12 12:36:42','2026-07-12 12:37:17'),
(5,7,1,'lock','Lock the device','executed','Fadhili Clever','2026-07-12 12:38:17','2026-07-12 12:38:00','2026-07-12 12:38:17'),
(6,7,1,'alarm_off','Stop alarm','sent','Fadhili Clever',NULL,'2026-07-12 12:53:18','2026-07-12 12:53:18'),
(7,7,1,'lock','Lock the device','sent','Fadhili Clever',NULL,'2026-07-12 12:53:28','2026-07-12 12:53:28'),
(8,7,1,'unlock','Unlock the device','sent','Fadhili Clever',NULL,'2026-07-12 12:53:36','2026-07-12 12:53:36'),
(9,7,1,'locate','Request immediate location','sent','Fadhili Clever',NULL,'2026-07-12 12:53:46','2026-07-12 12:53:46'),
(10,7,1,'alarm','Trigger alarm sound','sent','Fadhili Clever',NULL,'2026-07-12 12:53:52','2026-07-12 12:53:52'),
(11,7,1,'alarm_off','Stop alarm','sent','Fadhili Clever',NULL,'2026-07-12 12:53:59','2026-07-12 12:53:59'),
(12,7,NULL,'alarm','Trigger alarm sound','executed',NULL,'2026-07-12 12:57:11','2026-07-12 12:57:09','2026-07-12 12:57:11'),
(13,7,1,'alarm_off','Stop alarm','sent','Fadhili Clever',NULL,'2026-07-12 12:57:53','2026-07-12 12:57:53'),
(14,7,1,'lock','Lock the device','sent','Fadhili Clever',NULL,'2026-07-12 13:03:04','2026-07-12 13:03:04'),
(15,7,NULL,'lock','Lock device remotely','executed',NULL,'2026-07-12 13:09:44','2026-07-12 13:09:31','2026-07-12 13:09:44'),
(16,7,1,'lock','Lock the device','sent','Fadhili Clever',NULL,'2026-07-12 13:14:02','2026-07-12 13:14:02'),
(17,7,NULL,'silent','Activate silent mode','executed',NULL,'2026-07-12 13:14:44','2026-07-12 13:14:36','2026-07-12 13:14:44'),
(18,7,NULL,'lock','Lock device remotely','executed',NULL,'2026-07-12 13:18:28','2026-07-12 13:18:23','2026-07-12 13:18:28'),
(19,7,1,'unlock','Unlock the device','sent','Fadhili Clever',NULL,'2026-07-12 13:19:09','2026-07-12 13:19:09'),
(20,7,1,'lock','Lock device screen','sent','Fadhili Clever',NULL,'2026-07-12 13:32:37','2026-07-12 13:32:37'),
(21,7,1,'lock','Lock device screen','sent','Fadhili Clever',NULL,'2026-07-12 13:32:45','2026-07-12 13:32:45'),
(22,7,1,'alarm','Trigger alarm sound','sent','Fadhili Clever',NULL,'2026-07-12 13:33:16','2026-07-12 13:33:16'),
(23,7,1,'silent','Enable silent mode','sent','Fadhili Clever',NULL,'2026-07-12 13:33:23','2026-07-12 13:33:23'),
(24,7,1,'gps_on','Enable GPS','sent','Fadhili Clever',NULL,'2026-07-12 13:34:04','2026-07-12 13:34:04'),
(25,7,NULL,'lock','Lock device with PIN screen','executed',NULL,'2026-07-12 13:34:48','2026-07-12 13:34:46','2026-07-12 13:34:48'),
(26,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 13:37:06','2026-07-12 13:37:06'),
(27,7,1,'restart','Restart tracking service','sent','Fadhili Clever',NULL,'2026-07-12 13:37:27','2026-07-12 13:37:27'),
(28,7,1,'normal','Restore normal sound','sent','Fadhili Clever',NULL,'2026-07-12 13:39:35','2026-07-12 13:39:35'),
(29,7,1,'alarm','Trigger alarm sound','sent','Fadhili Clever',NULL,'2026-07-12 13:39:41','2026-07-12 13:39:41'),
(30,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 13:39:55','2026-07-12 13:39:55'),
(31,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 13:44:00','2026-07-12 13:44:00'),
(32,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 13:45:35','2026-07-12 13:45:35'),
(33,7,NULL,'lock','Lock device with PIN screen','executed',NULL,'2026-07-12 13:50:58','2026-07-12 13:45:56','2026-07-12 13:50:58'),
(34,7,NULL,'alarm','Test command: alarm','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(35,7,NULL,'alarm_off','Test command: alarm_off','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(36,7,NULL,'lock','Test command: lock','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(37,7,NULL,'unlock','Test command: unlock','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(38,7,NULL,'locate','Test command: locate','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(39,7,NULL,'silent','Test command: silent','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(40,7,NULL,'normal','Test command: normal','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(41,7,NULL,'restart','Test command: restart','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(42,7,NULL,'stealth_on','Test command: stealth_on','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(43,7,NULL,'stealth_off','Test command: stealth_off','executed',NULL,'2026-07-12 13:53:34','2026-07-12 13:53:21','2026-07-12 13:53:34'),
(44,7,1,'lock','Lock device screen','sent','Fadhili Clever',NULL,'2026-07-12 13:59:38','2026-07-12 13:59:38'),
(45,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 14:00:11','2026-07-12 14:00:11'),
(46,7,1,'lock','Lock device screen','sent','Fadhili Clever',NULL,'2026-07-12 14:03:13','2026-07-12 14:03:13'),
(47,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 14:03:31','2026-07-12 14:03:31'),
(48,7,1,'restart','Restart tracking service','sent','Fadhili Clever',NULL,'2026-07-12 14:03:36','2026-07-12 14:03:36'),
(49,7,1,'silent','Enable silent mode','sent','Fadhili Clever',NULL,'2026-07-12 14:03:43','2026-07-12 14:03:43'),
(50,7,1,'normal','Restore normal sound','sent','Fadhili Clever',NULL,'2026-07-12 14:03:49','2026-07-12 14:03:49'),
(51,7,1,'alarm','Trigger alarm sound','sent','Fadhili Clever',NULL,'2026-07-12 14:03:57','2026-07-12 14:03:57'),
(52,7,1,'alarm','Trigger alarm sound','sent','Fadhili Clever',NULL,'2026-07-12 14:03:59','2026-07-12 14:03:59'),
(53,7,1,'alarm_off','Stop alarm','sent','Fadhili Clever',NULL,'2026-07-12 14:04:04','2026-07-12 14:04:04'),
(54,7,NULL,'lock','Lock device fullscreen','executed',NULL,'2026-07-12 14:07:38','2026-07-12 14:07:31','2026-07-12 14:07:38'),
(55,7,1,'alarm','🔔 Alarm triggered on device','executed','Fadhili Clever','2026-07-12 14:19:57','2026-07-12 14:19:50','2026-07-12 14:19:57'),
(56,7,1,'alarm_off','🔕 Alarm stopped on device','executed','Fadhili Clever','2026-07-12 14:20:05','2026-07-12 14:20:03','2026-07-12 14:20:05'),
(57,7,1,'set_pin','Set unlock PIN: 1234','executed','Fadhili Clever','2026-07-12 14:37:06','2026-07-12 14:37:00','2026-07-12 14:37:06'),
(58,7,1,'lock','Lock device screen','sent','Fadhili Clever',NULL,'2026-07-12 14:37:02','2026-07-12 14:37:02'),
(59,7,1,'set_pin','Set unlock PIN: 1111','executed','Fadhili Clever','2026-07-12 14:37:21','2026-07-12 14:37:18','2026-07-12 14:37:21'),
(60,7,1,'lock','Lock device screen','sent','Fadhili Clever',NULL,'2026-07-12 14:37:20','2026-07-12 14:37:20'),
(61,7,1,'set_pin','Set unlock PIN: 1111','executed','Fadhili Clever','2026-07-12 14:37:37','2026-07-12 14:37:34','2026-07-12 14:37:37'),
(62,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 14:37:36','2026-07-12 14:37:36'),
(63,7,1,'locate','Request immediate location','sent','Fadhili Clever',NULL,'2026-07-12 14:37:43','2026-07-12 14:37:43'),
(64,7,1,'alarm','Trigger alarm sound','sent','Fadhili Clever',NULL,'2026-07-12 14:37:49','2026-07-12 14:37:49'),
(65,7,1,'alarm','Trigger alarm sound','sent','Fadhili Clever',NULL,'2026-07-12 14:38:02','2026-07-12 14:38:02'),
(66,7,1,'alarm_off','Stop alarm','sent','Fadhili Clever',NULL,'2026-07-12 14:38:08','2026-07-12 14:38:08'),
(67,7,NULL,'lock','Lock device','executed',NULL,'2026-07-12 14:49:07','2026-07-12 14:48:55','2026-07-12 14:49:07'),
(68,7,NULL,'lock','Lock device','executed',NULL,'2026-07-12 14:59:17','2026-07-12 14:59:07','2026-07-12 14:59:17'),
(69,7,1,'set_pin','Set unlock PIN: 1111','executed','Fadhili Clever','2026-07-12 15:03:11','2026-07-12 15:03:08','2026-07-12 15:03:11'),
(70,7,1,'unlock','Unlock device','sent','Fadhili Clever',NULL,'2026-07-12 15:03:10','2026-07-12 15:03:10'),
(71,7,1,'alarm','🔔 Alarm triggered on device','executed','Fadhili Clever','2026-07-12 15:03:41','2026-07-12 15:03:38','2026-07-12 15:03:41'),
(72,7,1,'alarm_off','🔕 Alarm stopped on device','executed','Fadhili Clever','2026-07-12 15:03:56','2026-07-12 15:03:51','2026-07-12 15:03:56');
/*!40000 ALTER TABLE `device_commands` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device_geofence`
--

DROP TABLE IF EXISTS `device_geofence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_geofence` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) unsigned NOT NULL,
  `geofence_id` bigint(20) unsigned NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `device_geofence_device_id_foreign` (`device_id`),
  KEY `device_geofence_geofence_id_foreign` (`geofence_id`),
  CONSTRAINT `device_geofence_device_id_foreign` FOREIGN KEY (`device_id`) REFERENCES `devices` (`id`) ON DELETE CASCADE,
  CONSTRAINT `device_geofence_geofence_id_foreign` FOREIGN KEY (`geofence_id`) REFERENCES `geofences` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device_geofence`
--

LOCK TABLES `device_geofence` WRITE;
/*!40000 ALTER TABLE `device_geofence` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_geofence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device_locations`
--

DROP TABLE IF EXISTS `device_locations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_locations` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `device_id` bigint(20) unsigned NOT NULL,
  `lat` decimal(10,7) NOT NULL,
  `lng` decimal(10,7) NOT NULL,
  `accuracy` double DEFAULT NULL,
  `altitude` double DEFAULT NULL,
  `speed` double DEFAULT NULL,
  `bearing` double DEFAULT NULL,
  `source` varchar(20) NOT NULL DEFAULT 'gps',
  `recorded_at` timestamp NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `device_locations_device_id_recorded_at_index` (`device_id`,`recorded_at`),
  CONSTRAINT `device_locations_device_id_foreign` FOREIGN KEY (`device_id`) REFERENCES `devices` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device_locations`
--

LOCK TABLES `device_locations` WRITE;
/*!40000 ALTER TABLE `device_locations` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_locations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `devices`
--

DROP TABLE IF EXISTS `devices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `devices` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) unsigned DEFAULT NULL,
  `uuid` varchar(255) NOT NULL,
  `short_code` varchar(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `type` varchar(30) NOT NULL DEFAULT 'mobile',
  `imei` varchar(50) DEFAULT NULL,
  `mac_address` varchar(50) DEFAULT NULL,
  `sim_number` varchar(30) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `os_version` varchar(50) DEFAULT NULL,
  `app_version` varchar(20) DEFAULT NULL,
  `api_token` varchar(80) NOT NULL,
  `fcm_token` varchar(255) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `is_registered` tinyint(1) NOT NULL DEFAULT 0,
  `is_online` tinyint(1) NOT NULL DEFAULT 0,
  `is_charging` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(30) NOT NULL DEFAULT 'offline',
  `battery_level` int(11) DEFAULT NULL,
  `last_lat` decimal(10,7) DEFAULT NULL,
  `last_lng` decimal(10,7) DEFAULT NULL,
  `last_seen_at` timestamp NULL DEFAULT NULL,
  `geofence_states` text DEFAULT NULL,
  `notes` text DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `devices_uuid_unique` (`uuid`),
  UNIQUE KEY `devices_short_code_unique` (`short_code`),
  UNIQUE KEY `devices_api_token_unique` (`api_token`),
  KEY `devices_user_id_foreign` (`user_id`),
  CONSTRAINT `devices_user_id_foreign` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `devices`
--

LOCK TABLES `devices` WRITE;
/*!40000 ALTER TABLE `devices` DISABLE KEYS */;
INSERT INTO `devices` VALUES
(1,2,'UUID-DEMO-0001','UHS8TOVE','Toyota Camry 2022','car','35982243367019030','1C:09:0D:51:E4:5E:68:97:96:E3:9E:23:FE:23:3B:11','+17538369493','Toyota Camry 2022',NULL,NULL,'j1t4jIY2bXXLkpR5X5lGPD2g6EeYruglP5nxS3OXOgohNlNCRXpHezVcSsGZ',NULL,1,1,1,0,'online',32,12.3533000,77.2253000,'2026-06-21 08:21:53',NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(2,3,'UUID-DEMO-0002','LNTPKO9I','iPhone 14 Pro','mobile','35348301319301765','A1:57:59:E7:1D:3F:6F:66:FD:EE:63:38:D1:8B:6E:E1','+14978776745','iPhone 14 Pro',NULL,NULL,'UdyQ6aR2MSQrYchWBYWjI2MzjyhBqubHyikloCAnu6CQUti252L3xvAwEtqT',NULL,1,1,0,0,'offline',66,12.3422000,77.2313000,'2026-06-21 09:09:53',NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(3,2,'UUID-DEMO-0003','RTMHNDKR','Samsung Smart TV','tv','35653434863819334','CA:62:83:99:FD:C0:4A:6B:9B:83:82:B3:D3:B2:2A:B1','+16347262878','Samsung Smart TV',NULL,NULL,'bepxnPyi0ciW8YlsT7vhcDzO2hf4vkHE7XYQHEEf5ed2yICYbaOC8Vp65ttX',NULL,1,1,1,0,'online',36,12.3576000,77.2369000,'2026-06-21 07:54:53',NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(4,3,'UUID-DEMO-0004','DEBBBTOO','GPS Tracker #1','other','35696079483106540','AB:D2:36:2A:23:FC:45:D7:28:C7:21:D0:30:A5:B9:C6','+13012205616','GPS Tracker #1',NULL,NULL,'0KdZScYhUovLtZor3xW88sjdZjdCzHouzYJzxEvYEGXvO9nqfDZsGRrQsL9G',NULL,1,1,0,0,'offline',28,12.3526000,77.2420000,'2026-06-21 07:43:53',NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(5,2,'UUID-DEMO-0005','M8VTCEHA','Ford Ranger','car','35293434504274942','2F:7C:86:27:27:24:8A:B9:26:61:CE:23:F9:6D:AE:80','+14761037175','Ford Ranger',NULL,NULL,'pKkpJCz3p83b7AZoPffwdFt7bZftJgWxF6bNqH2Q6RgWJQOlVpPhD5Xo3ieg',NULL,1,1,1,0,'online',68,12.3688000,77.2561000,'2026-06-21 08:19:53',NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(6,3,'UUID-DEMO-0006','DJ3TGL2T','Android Pixel 7','mobile','35811319170601661','5A:B2:8F:68:D4:68:53:ED:85:1A:C0:BF:57:50:18:C2','+19557119297','Android Pixel 7',NULL,NULL,'mKrYwmgJ3kwrHOrW4zkYpyfWpvWSz7lEPW9rcns6S5YscxIGyLaMdWEPujDg',NULL,1,1,0,0,'offline',41,12.3727000,77.2560000,'2026-06-21 08:12:53',NULL,NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(7,1,'b93eb33e3f3d0cf3','2X45MBVW','GOOGLE PIXEL 7','other',NULL,NULL,NULL,'cheetah','13',NULL,'WiKErnkgDOKTpX1d75Doo0ENYM7XyLZQ7ZFbtenKZAdXmA1jPs8CcaUEvDhA','eV4ahDFLRzKZGdzDZx-iCT:APA91bG8xmfuwTY_eL3KJj-Y7Pxna3KmXzjaa1KZB0K2w_uoj-snNFDJnzjGf_xtd5AskOtELvWNPo5rjrCU7QwWDim65mojf1Zt4LxYqodBtv9okDHy0f8',1,1,1,1,'online',85,NULL,NULL,'2026-07-12 15:04:45',NULL,'{\"unlock_pin\":\"1111\",\"pin_updated_at\":\"2026-07-12T18:03:08+00:00\",\"pin_set_by\":\"Fadhili Clever\"}','2026-07-12 11:55:05','2026-07-12 15:04:45'),
(8,NULL,'test123','K9KQE5XS','DEVICE-GH8NGY','other',NULL,NULL,NULL,NULL,NULL,NULL,'FV96q6ouN8bavETQM7gp62VBk6ob0hgOi9b1XRg0g0tdy5ZWv4TNbuVpNNIq',NULL,1,0,0,0,'offline',NULL,NULL,NULL,NULL,NULL,NULL,'2026-07-12 12:06:44','2026-07-12 12:06:44');
/*!40000 ALTER TABLE `devices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `failed_jobs`
--

DROP TABLE IF EXISTS `failed_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `failed_jobs` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `uuid` varchar(255) NOT NULL,
  `connection` text NOT NULL,
  `queue` text NOT NULL,
  `payload` longtext NOT NULL,
  `exception` longtext NOT NULL,
  `failed_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `failed_jobs_uuid_unique` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `failed_jobs`
--

LOCK TABLES `failed_jobs` WRITE;
/*!40000 ALTER TABLE `failed_jobs` DISABLE KEYS */;
/*!40000 ALTER TABLE `failed_jobs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `geofences`
--

DROP TABLE IF EXISTS `geofences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `geofences` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `polygon_points` text NOT NULL,
  `alert_on_enter` tinyint(1) NOT NULL DEFAULT 1,
  `alert_on_exit` tinyint(1) NOT NULL DEFAULT 1,
  `apply_to_all` tinyint(1) NOT NULL DEFAULT 0,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_by` bigint(20) unsigned DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `geofences_created_by_foreign` (`created_by`),
  CONSTRAINT `geofences_created_by_foreign` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `geofences`
--

LOCK TABLES `geofences` WRITE;
/*!40000 ALTER TABLE `geofences` DISABLE KEYS */;
/*!40000 ALTER TABLE `geofences` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `job_batches`
--

DROP TABLE IF EXISTS `job_batches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_batches` (
  `id` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `total_jobs` int(11) NOT NULL,
  `pending_jobs` int(11) NOT NULL,
  `failed_jobs` int(11) NOT NULL,
  `failed_job_ids` longtext NOT NULL,
  `options` mediumtext DEFAULT NULL,
  `cancelled_at` int(11) DEFAULT NULL,
  `created_at` int(11) NOT NULL,
  `finished_at` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `job_batches`
--

LOCK TABLES `job_batches` WRITE;
/*!40000 ALTER TABLE `job_batches` DISABLE KEYS */;
/*!40000 ALTER TABLE `job_batches` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jobs`
--

DROP TABLE IF EXISTS `jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `jobs` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `queue` varchar(255) NOT NULL,
  `payload` longtext NOT NULL,
  `attempts` tinyint(3) unsigned NOT NULL,
  `reserved_at` int(10) unsigned DEFAULT NULL,
  `available_at` int(10) unsigned NOT NULL,
  `created_at` int(10) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `jobs_queue_index` (`queue`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jobs`
--

LOCK TABLES `jobs` WRITE;
/*!40000 ALTER TABLE `jobs` DISABLE KEYS */;
/*!40000 ALTER TABLE `jobs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `migrations`
--

DROP TABLE IF EXISTS `migrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `migrations` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `migration` varchar(255) NOT NULL,
  `batch` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `migrations`
--

LOCK TABLES `migrations` WRITE;
/*!40000 ALTER TABLE `migrations` DISABLE KEYS */;
INSERT INTO `migrations` VALUES
(1,'0001_01_01_000000_create_users_table',1),
(2,'0001_01_01_000001_create_cache_table',1),
(3,'0001_01_01_000002_create_jobs_table',1),
(4,'2024_01_01_100000_update_users_table',1),
(5,'2024_01_01_100001_create_devices_table',1),
(6,'2024_01_01_100002_create_device_locations_table',1),
(7,'2024_01_01_100003_create_device_alerts_table',1),
(8,'2024_01_01_100004_create_geofences_table',1),
(9,'2024_01_01_100005_create_alert_rules_table',1),
(10,'2024_01_01_100006_create_ai_predict_configs_table',1),
(11,'2024_01_02_000000_update_alert_rules_table',1),
(12,'2024_01_02_000001_update_device_alerts_table',1),
(13,'2024_01_02_000002_create_device_commands_table',1);
/*!40000 ALTER TABLE `migrations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_tokens`
--

DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `email` varchar(255) NOT NULL,
  `token` varchar(255) NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_tokens`
--

LOCK TABLES `password_reset_tokens` WRITE;
/*!40000 ALTER TABLE `password_reset_tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `password_reset_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sessions`
--

DROP TABLE IF EXISTS `sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sessions` (
  `id` varchar(255) NOT NULL,
  `user_id` bigint(20) unsigned DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` text DEFAULT NULL,
  `payload` longtext NOT NULL,
  `last_activity` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `sessions_user_id_index` (`user_id`),
  KEY `sessions_last_activity_index` (`last_activity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sessions`
--

LOCK TABLES `sessions` WRITE;
/*!40000 ALTER TABLE `sessions` DISABLE KEYS */;
INSERT INTO `sessions` VALUES
('2g9TEUPYjWIT25YKqGdAhAn8772b6qI6HxUyCzqm',NULL,'127.0.0.1','curl/8.5.0','eyJfdG9rZW4iOiJweW5BU0lERGNMRlFscXpXTU5JVXZ4WVBzaVVMYURiU3hoWldJaFh5IiwiX3ByZXZpb3VzIjp7InVybCI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDAwXC9sb2dpbiIsInJvdXRlIjoibG9naW4ifSwiX2ZsYXNoIjp7Im9sZCI6W10sIm5ldyI6W119fQ==',1783876381),
('cVzhJuScOPrfDrhF1kVC7DZKTnKSxn1y9E6jmUBN',NULL,'127.0.0.1','Mozilla/5.0 (X11; Ubuntu; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','eyJfdG9rZW4iOiJqRjRPb0FwRDlnQldIRUJmdjA3Q0s1SlQ2QkQyVG0zODBraGVaOUQ3IiwiX3ByZXZpb3VzIjp7InVybCI6Imh0dHA6XC9cLzEyNy4wLjAuMTo4MDAwXC9tYXBcL2xpdmUtZGF0YSIsInJvdXRlIjoibWFwLmxpdmVfZGF0YSJ9LCJfZmxhc2giOnsib2xkIjpbXSwibmV3IjpbXX0sInVzZXJfbG9nZ2VkX2luIjp0cnVlLCJ1c2VyX2lkIjoxLCJ1c2VyX25hbWUiOiJGYWRoaWxpIENsZXZlciIsInVzZXJfZW1haWwiOiJmYWRoaWxpQGdtYWlsLmNvbSIsInVzZXJfcm9sZSI6ImFkbWluIn0=',1783879496),
('rGkYlG1v52jfAHXcY82puz0YhOF4pgLQXX9kKIqz',NULL,'127.0.0.1','curl/8.5.0','eyJfdG9rZW4iOiJQY2NUVDVNTlltb0RldEtMYzUzWEhZWFN6WDNLZjZGdVRmUGFPbkE5IiwiX3ByZXZpb3VzIjp7InVybCI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDAwXC9sb2dpbiIsInJvdXRlIjoibG9naW4ifSwiX2ZsYXNoIjp7Im9sZCI6W10sIm5ldyI6W119fQ==',1783876267),
('VnHFYo6P1MW2KSzK1OPa1QDYveGZNzT4ODV5iNDm',NULL,'127.0.0.1','curl/8.5.0','eyJfdG9rZW4iOiI1TTg3Z1B2SkxqMmxpZEpnUTFBVHN0d2VKTzE3THZyeXNhWFdYT3k2IiwiX2ZsYXNoIjp7Im9sZCI6W10sIm5ldyI6W119fQ==',1783876267);
/*!40000 ALTER TABLE `sessions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'customer',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `email_verified_at` timestamp NULL DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `remember_token` varchar(100) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `users_email_unique` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES
(1,'Fadhili Clever','fadhili@gmail.com','+255622531087','admin',1,NULL,'$2y$12$V010aHmJkVUdUeeFvarra.jdovJXIp2hbYBVljS2RTRAC9aowi7S2',NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(2,'Benjamin Fredy','benjamin@gmail.com','+255769484784','customer',1,NULL,'$2y$12$fKWR2DyzlRB.r5OlTOgCn.F27BKxNQbZnzaJ3RijJeeVUMgPrP6uq',NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53'),
(3,'Said Shaban','said@gmail.com','+255752090145','customer',1,NULL,'$2y$12$8h2E3H.RFIQ3IFnTv7Pdheh52hTaMi.6eN9FVGI7wEV8MEgSPblty',NULL,'2026-06-21 09:39:53','2026-06-21 09:39:53');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-12 21:04:57
