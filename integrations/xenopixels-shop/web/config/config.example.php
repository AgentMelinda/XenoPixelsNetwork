<?php
declare(strict_types=1);

return [
    'dsn' => 'mysql:host=localhost;dbname=CHANGE_ME;charset=utf8mb4',
    'db_user' => 'CHANGE_ME',
    'db_password' => 'CHANGE_ME',
    'base_url' => 'https://shop.xenopixelsnetwork.com',
    'bridge_shared_secret' => getenv('XENOPIXELS_SHOP_BRIDGE_SECRET') ?: '',
    'stripe_secret_key' => getenv('XENOPIXELS_STRIPE_SECRET_KEY') ?: '',
    'stripe_webhook_secret' => getenv('XENOPIXELS_STRIPE_WEBHOOK_SECRET') ?: '',
    'paypal_client_id' => getenv('XENOPIXELS_PAYPAL_CLIENT_ID') ?: '',
    'paypal_client_secret' => getenv('XENOPIXELS_PAYPAL_CLIENT_SECRET') ?: '',
    'paypal_webhook_id' => getenv('XENOPIXELS_PAYPAL_WEBHOOK_ID') ?: '',
    'server_keys' => [],
    'link_code_ttl_seconds' => 600,
    'request_max_bytes' => 65536,
];
