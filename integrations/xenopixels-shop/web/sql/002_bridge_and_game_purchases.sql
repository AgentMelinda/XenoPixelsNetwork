CREATE TABLE IF NOT EXISTS xp_shop_bridge_nonces (
  nonce_hash BINARY(32) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (nonce_hash), KEY ix_xp_shop_bridge_nonce_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_sessions (
  token_hash BINARY(32) NOT NULL,
  forum_user_id BIGINT UNSIGNED NOT NULL,
  is_admin TINYINT(1) NOT NULL DEFAULT 0,
  expires_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token_hash), KEY ix_xp_shop_session_expiry (expires_at),
  CHECK (is_admin IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_game_purchases (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  server_id VARCHAR(64) NOT NULL,
  purchase_key CHAR(32) NOT NULL,
  forum_user_id BIGINT UNSIGNED NOT NULL,
  minecraft_uuid BINARY(16) NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  product_snapshot JSON NOT NULL,
  currency_id VARCHAR(48) NOT NULL,
  amount BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'prepared',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_xp_shop_game_purchase (server_id,purchase_key),
  KEY ix_xp_shop_game_purchase_user (forum_user_id,created_at),
  CONSTRAINT fk_xp_shop_game_purchase_product FOREIGN KEY (product_id) REFERENCES xp_shop_products(id),
  CHECK (status IN ('prepared','settled','cancelled','uncertain'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_shop_currency_ledger (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  server_id VARCHAR(64) NOT NULL,
  purchase_key CHAR(32) NOT NULL,
  minecraft_uuid BINARY(16) NOT NULL,
  currency_id VARCHAR(48) NOT NULL,
  amount BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_xp_shop_currency_purchase (server_id,purchase_key),
  CHECK (status IN ('prepared','debit_confirmed','settled','cancelled','uncertain'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
