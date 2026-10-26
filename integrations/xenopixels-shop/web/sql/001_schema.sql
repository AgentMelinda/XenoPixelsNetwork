CREATE TABLE IF NOT EXISTS xp_shop_products (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  slug VARCHAR(80) NOT NULL,
  title VARCHAR(120) NOT NULL,
  description TEXT NOT NULL,
  image_url VARCHAR(500) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  web_price_minor BIGINT UNSIGNED NULL,
  web_currency CHAR(3) NULL,
  mmoecon_price BIGINT UNSIGNED NULL,
  zeni_cash_price BIGINT UNSIGNED NULL,
  reward_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_xp_shop_product_slug (slug),
  CHECK (enabled IN (0,1)), CHECK (web_price_minor IS NULL OR web_price_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_links (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  forum_user_id BIGINT UNSIGNED NOT NULL,
  code_hash BINARY(32) NOT NULL,
  expires_at DATETIME NOT NULL,
  redeemed_uuid BINARY(16) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_xp_shop_link_code (code_hash),
  KEY ix_xp_shop_link_user_expiry (forum_user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_accounts (
  forum_user_id BIGINT UNSIGNED NOT NULL,
  minecraft_uuid BINARY(16) NOT NULL,
  linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (forum_user_id), UNIQUE KEY uq_xp_shop_minecraft_uuid (minecraft_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_orders (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id CHAR(32) NOT NULL,
  forum_user_id BIGINT UNSIGNED NOT NULL,
  minecraft_uuid BINARY(16) NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  product_snapshot JSON NOT NULL,
  payment_method VARCHAR(16) NOT NULL,
  provider_order_id VARCHAR(160) NULL,
  game_purchase_key CHAR(32) NULL,
  amount_minor BIGINT UNSIGNED NOT NULL,
  currency CHAR(3) NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_xp_shop_order_public (public_id),
  UNIQUE KEY uq_xp_shop_game_purchase_key (game_purchase_key),
  KEY ix_xp_shop_order_user (forum_user_id, created_at),
  CONSTRAINT fk_xp_shop_order_product FOREIGN KEY (product_id) REFERENCES xp_shop_products(id),
  CHECK (status IN ('pending','paid','delivery_pending','delivered','failed','refunded'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_payment_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider VARCHAR(16) NOT NULL,
  provider_event_id VARCHAR(160) NOT NULL,
  order_public_id CHAR(32) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  accepted TINYINT(1) NOT NULL DEFAULT 0,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_xp_shop_payment_event (provider, provider_event_id),
  KEY ix_xp_shop_payment_order (order_public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_deliveries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_public_id CHAR(32) NOT NULL,
  minecraft_uuid BINARY(16) NOT NULL,
  reward_json JSON NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'queued',
  claim_token CHAR(32) NULL,
  attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  claimed_at DATETIME NULL,
  delivered_at DATETIME NULL,
  last_error_code VARCHAR(64) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_xp_shop_delivery_order (order_public_id),
  KEY ix_xp_shop_delivery_queue (status, created_at),
  CHECK (status IN ('queued','claimed','delivered','retry','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_server_nonces (
  server_id VARCHAR(64) NOT NULL,
  nonce CHAR(32) NOT NULL,
  expires_at DATETIME NOT NULL,
  PRIMARY KEY (server_id, nonce), KEY ix_xp_shop_nonce_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_type VARCHAR(16) NOT NULL,
  actor_id VARCHAR(96) NOT NULL,
  action VARCHAR(64) NOT NULL,
  subject_id VARCHAR(96) NULL,
  metadata JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY ix_xp_shop_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
