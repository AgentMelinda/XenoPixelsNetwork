<?php
declare(strict_types=1);

use XenoPixels\Shop\ConfiguredVbIdentityProvider;
use XenoPixels\Shop\Http;
use XenoPixels\Shop\HttpError;
use XenoPixels\Shop\ServerAuthenticator;
use XenoPixels\Shop\LinkService;
use XenoPixels\Shop\DeliveryService;
use XenoPixels\Shop\PaymentProvider;
use XenoPixels\Shop\OrderService;
use XenoPixels\Shop\AdminService;
use XenoPixels\Shop\GamePurchaseService;

$appRoot = is_dir(__DIR__ . '/src') ? __DIR__ : dirname(__DIR__);

require $appRoot . '/src/Http.php';
require $appRoot . '/src/VbIdentityProvider.php';
require $appRoot . '/src/ServerAuthenticator.php';
require $appRoot . '/src/LinkService.php';
require $appRoot . '/src/DeliveryService.php';
require $appRoot . '/src/PaymentProvider.php';
require $appRoot . '/src/OrderService.php';
require $appRoot . '/src/AdminService.php';
require $appRoot . '/src/GamePurchaseService.php';
$configPath = getenv('XENOPIXELS_SHOP_CONFIG') ?: $appRoot . '/config/config.php';
if (!is_file($configPath)) Http::respond(503, ['error' => ['code' => 'not_configured', 'message' => 'Shop is not configured.']]);
$config = require $configPath;
$requestId = bin2hex(random_bytes(8));
// Shop pages contain per-user session and CSRF state. Keep browsers and
// intermediate caches from reusing a token rendered for a different session.
header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
try {
    if (version_compare(PHP_VERSION, '8.2.35', '<')) throw new HttpError(503, 'php_runtime_unsupported', 'PHP 8.2.35 or newer is required.');
    $db = new PDO($config['dsn'], $config['db_user'], $config['db_password'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_EMULATE_PREPARES => false,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
    $identity = new ConfiguredVbIdentityProvider($db, $config);
    if ($uri === '/auth/consume' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        ConfiguredVbIdentityProvider::consumeBridge($db, $config, (string)($_POST['assertion'] ?? ''));
        header('Location: /', true, 303);
        exit;
    }
    if ($uri === '/logout' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        $csrf = $_POST['csrf'] ?? '';
        if (!is_string($csrf) || !$identity->validateCsrf($csrf)) throw new HttpError(403, 'csrf_failed', 'Security token is invalid.');
        $identity->logout();
        header('Location: /', true, 303);
        exit;
    }
    if ($uri === '/shop' || $uri === '/shop/') $uri = '/';
    if (str_starts_with($uri, '/shop/')) $uri = substr($uri, 5);
    if (str_starts_with($uri, '/api/v1/server/')) {
        $rawBody = file_get_contents('php://input', false, null, 0, (int)($config['request_max_bytes'] ?? 65536) + 1);
        if ($rawBody === false || strlen($rawBody) > (int)($config['request_max_bytes'] ?? 65536)) throw new HttpError(413, 'body_too_large', 'Request body exceeds the configured limit.');
        $serverId = (new ServerAuthenticator($db, $config['server_keys'] ?? []))->authenticate($rawBody);
        if ($uri === '/api/v1/server/catalog' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
            $rows = $db->query("SELECT slug,title,description,image_url,web_price_minor,web_currency,mmoecon_price,zeni_cash_price FROM xp_shop_products WHERE enabled=1 ORDER BY id")->fetchAll();
            Http::respond(200, ['request_id' => $requestId, 'products' => $rows]);
        }
        $body = [];
        if ($rawBody !== '') {
            try { $body = json_decode($rawBody, true, 12, JSON_THROW_ON_ERROR); }
            catch (JsonException) { throw new HttpError(400, 'invalid_json', 'Request body must be valid JSON.'); }
            if (!is_array($body) || array_is_list($body)) throw new HttpError(400, 'invalid_json_shape', 'Request body must be a JSON object.');
        }
        if ($uri === '/api/v1/server/link/redeem' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
            $userId = (new LinkService($db, (int)($config['link_code_ttl_seconds'] ?? 600)))->redeem((string)($body['code'] ?? ''), (string)($body['uuid'] ?? ''));
            Http::respond(200, ['request_id' => $requestId, 'linked' => true, 'forum_user_id' => $userId]);
        }
        if ($uri === '/api/v1/server/account/orders' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
            $uuid = strtolower(str_replace('-', '', (string)($body['uuid'] ?? '')));
            if (!preg_match('/^[a-f0-9]{32}$/', $uuid)) throw new HttpError(400, 'invalid_uuid', 'Minecraft UUID is invalid.');
            $account = $db->prepare('SELECT forum_user_id FROM xp_shop_accounts WHERE minecraft_uuid=UNHEX(?)');
            $account->execute([$uuid]);
            $forumUserId = (int)($account->fetchColumn() ?: 0);
            $orders = [];
            if ($forumUserId > 0) {
                $list = $db->prepare('SELECT product_snapshot,payment_method,amount_minor,currency,status,created_at FROM xp_shop_orders WHERE forum_user_id=? ORDER BY id DESC LIMIT 20');
                $list->execute([$forumUserId]);
                foreach ($list->fetchAll() as $order) {
                    $snapshot = json_decode($order['product_snapshot'], true, 12, JSON_THROW_ON_ERROR);
                    $orders[] = ['title' => (string)($snapshot['title'] ?? 'Shop order'), 'payment_method' => $order['payment_method'],
                        'amount' => (int)$order['amount_minor'], 'currency' => $order['currency'], 'status' => $order['status'],
                        'created_at' => $order['created_at']];
                }
            }
            Http::respond(200, ['request_id' => $requestId, 'linked' => $forumUserId > 0, 'orders' => $orders]);
        }
        if ($uri === '/api/v1/server/game-purchases/prepare' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
            $purchase = (new GamePurchaseService($db))->prepare($serverId, (string)($body['purchase_key'] ?? ''), (string)($body['uuid'] ?? ''), (string)($body['slug'] ?? ''), (string)($body['currency'] ?? ''));
            Http::respond(200, ['request_id' => $requestId, 'purchase' => $purchase]);
        }
        if ($uri === '/api/v1/server/game-purchases/finish' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
            $state = (new GamePurchaseService($db))->finish($serverId, (string)($body['purchase_key'] ?? ''), (string)($body['result'] ?? ''));
            Http::respond(200, ['request_id' => $requestId, 'status' => $state]);
        }
        if ($uri === '/api/v1/server/deliveries/claim' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
            $delivery = (new DeliveryService($db))->claim((string)($body['uuid'] ?? ''));
            Http::respond(200, ['request_id' => $requestId, 'delivery' => $delivery]);
        }
        if ($uri === '/api/v1/server/deliveries/ack' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
            $success = ($body['success'] ?? null) === true;
            (new DeliveryService($db))->acknowledge((int)($body['delivery_id'] ?? 0), (string)($body['claim_token'] ?? ''), $success, (string)($body['error_code'] ?? ''));
            Http::respond(200, ['request_id' => $requestId, 'acknowledged' => true]);
        }
        throw new HttpError(404, 'not_found', 'Endpoint not found.');
    }
    if (in_array($uri, ['/api/v1/webhooks/stripe', '/api/v1/webhooks/paypal'], true) && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        $raw = file_get_contents('php://input', false, null, 0, 262145);
        if ($raw === false || strlen($raw) > 262144) throw new HttpError(413, 'body_too_large', 'Webhook payload exceeds the configured limit.');
        $payments = new PaymentProvider($config);
        if ($uri === '/api/v1/webhooks/stripe') {
            $event = $payments->verifyStripeWebhook($raw, (string)($_SERVER['HTTP_STRIPE_SIGNATURE'] ?? ''));
            (new OrderService($db, $payments))->completeWebhook('stripe', $event);
        } else {
            $headers = [];
            foreach (['paypal-auth-algo','paypal-cert-url','paypal-transmission-id','paypal-transmission-sig','paypal-transmission-time'] as $name) {
                $key = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
                $headers[$name] = (string)($_SERVER[$key] ?? '');
            }
            $event = $payments->verifyPayPalWebhook($raw, $headers);
            (new OrderService($db, $payments))->completeWebhook('paypal', $event);
        }
        Http::respond(200, ['received' => true, 'request_id' => $requestId]);
    }
    $userId = $identity->currentUserId();
    $csrfToken = $identity->csrfToken();
    $isAdmin = $userId !== null && $identity->isAdministrator($userId);
    $accountLinked = false;
    if ($userId !== null) {
        $linkCheck = $db->prepare('SELECT 1 FROM xp_shop_accounts WHERE forum_user_id=?');
        $linkCheck->execute([$userId]);
        $accountLinked = (bool)$linkCheck->fetchColumn();
    }
    if ($uri === '/api/v1/health' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        Http::respond(200, ['ok' => true, 'request_id' => $requestId, 'php' => PHP_VERSION]);
    }
    if ($uri === '/api/v1/payment/paypal/capture' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        if ($userId === null) throw new HttpError(401, 'login_required', 'Sign in to the forum to finish checkout.');
        $externalId = (string)($_GET['token'] ?? '');
        if (!preg_match('/^[A-Z0-9]{1,64}$/', $externalId)) throw new HttpError(400, 'invalid_paypal_order', 'PayPal order identifier is invalid.');
        (new OrderService($db, new PaymentProvider($config)))->capturePayPalOrder($userId, $externalId);
        header('Location: /orders?checkout=processing', true, 303);
        exit;
    }
    if (str_starts_with($uri, '/api/v1/')
        && !in_array($uri, ['/api/v1/web/session', '/api/v1/web/link-code', '/api/v1/web/checkout', '/api/v1/web/admin/products'], true)) {
        throw new HttpError(404, 'not_found', 'Endpoint not found.');
    }
    if ($uri === '/' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        $products = $db->query("SELECT slug,title,description,image_url,web_price_minor,web_currency FROM xp_shop_products WHERE enabled=1 ORDER BY id")->fetchAll();
        header('Content-Type: text/html; charset=utf-8');
        header("Content-Security-Policy: default-src 'self'; img-src 'self' https:; style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'");
        if ($csrfToken === null) $csrfToken = '';
        require $appRoot . '/views/catalog.php';
        exit;
    }
    if ($uri === '/link' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        if ($userId === null) throw new HttpError(401, 'login_required', 'Sign in to the forum to link your game account.');
        if ($csrfToken === null) throw new HttpError(503, 'vb_csrf_unavailable', 'The vBulletin CSRF adapter is not configured.');
        header('Content-Type: text/html; charset=utf-8');
        header("Content-Security-Policy: default-src 'self'; style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'");
        require $appRoot . '/views/link.php';
        exit;
    }
    if (preg_match('#^/item/([a-z0-9_-]{1,80})$#', $uri, $match) && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        $stmt = $db->prepare('SELECT slug,title,description,image_url,web_price_minor,web_currency,mmoecon_price,zeni_cash_price FROM xp_shop_products WHERE slug=? AND enabled=1');
        $stmt->execute([$match[1]]);
        $product = $stmt->fetch();
        if (!$product) throw new HttpError(404, 'product_unavailable', 'Product not found.');
        header('Content-Type: text/html; charset=utf-8');
        header("Content-Security-Policy: default-src 'self'; img-src 'self' https:; style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'");
        require $appRoot . '/views/product.php';
        exit;
    }
    if ($uri === '/orders' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        if ($userId === null) throw new HttpError(401, 'login_required', 'Sign in to view your orders.');
        $stmt = $db->prepare('SELECT public_id,product_snapshot,payment_method,amount_minor,currency,status,created_at FROM xp_shop_orders WHERE forum_user_id=? ORDER BY id DESC LIMIT 100');
        $stmt->execute([$userId]);
        $orders = $stmt->fetchAll();
        foreach ($orders as &$order) $order['product_snapshot'] = json_decode($order['product_snapshot'], true, 12, JSON_THROW_ON_ERROR);
        unset($order);
        header('Content-Type: text/html; charset=utf-8');
        header("Content-Security-Policy: default-src 'self'; style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'");
        require $appRoot . '/views/orders.php';
        exit;
    }
    if ($uri === '/admin' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        if ($userId === null || !$identity->isAdministrator($userId)) throw new HttpError(403, 'admin_required', 'Shop administrator access is required.');
        $csrf = $_POST['csrf'] ?? '';
        if (!is_string($csrf) || !$identity->validateCsrf($csrf)) throw new HttpError(403, 'csrf_failed', 'Security token is invalid. Reload the admin page and try again.');
        $input = $_POST;
        $input['enabled'] = isset($_POST['enabled']);
        $productId = (new AdminService($db))->saveProduct($userId, $input);
        header('Location: /admin?saved=' . $productId, true, 303);
        exit;
    }
    if ($uri === '/admin' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        if ($userId === null || !$isAdmin) throw new HttpError(403, 'admin_required', 'Shop administrator access is required.');
        if ($csrfToken === null) throw new HttpError(503, 'vb_csrf_unavailable', 'The vBulletin CSRF adapter is not configured.');
        $products = $db->query('SELECT id,slug,title,enabled,web_price_minor,web_currency,mmoecon_price,zeni_cash_price,reward_json FROM xp_shop_products ORDER BY id DESC')->fetchAll();
        $savedProductId = filter_var($_GET['saved'] ?? null, FILTER_VALIDATE_INT);
        header('Content-Type: text/html; charset=utf-8');
        header("Content-Security-Policy: default-src 'self'; style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'");
        require $appRoot . '/views/admin.php';
        exit;
    }
    if ($uri === '/api/v1/web/session' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET') {
        Http::respond(200, ['request_id' => $requestId, 'authenticated' => $userId !== null]);
    }
    if ($uri === '/api/v1/web/link-code' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        if ($userId === null) throw new HttpError(401, 'login_required', 'Sign in to the forum before linking a game account.');
        $csrf = $_SERVER['HTTP_X_CSRF_TOKEN'] ?? '';
        if (!is_string($csrf) || !$identity->validateCsrf($csrf)) throw new HttpError(403, 'csrf_failed', 'Security token is invalid.');
        $link = (new LinkService($db, (int)($config['link_code_ttl_seconds'] ?? 600)))->createCode($userId);
        Http::respond(201, ['request_id' => $requestId] + $link);
    }
    if ($uri === '/api/v1/web/checkout' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        if ($userId === null) throw new HttpError(401, 'login_required', 'Sign in to the forum before checkout.');
        $csrf = $_SERVER['HTTP_X_CSRF_TOKEN'] ?? '';
        if (!is_string($csrf) || !$identity->validateCsrf($csrf)) throw new HttpError(403, 'csrf_failed', 'Security token is invalid.');
        $body = Http::jsonBody((int)($config['request_max_bytes'] ?? 65536));
        $result = (new OrderService($db, new PaymentProvider($config)))->checkout($userId, (string)($body['slug'] ?? ''), (string)($body['provider'] ?? ''));
        Http::respond(201, ['request_id' => $requestId] + $result);
    }
    if ($uri === '/api/v1/web/admin/products' && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
        if ($userId === null) throw new HttpError(403, 'admin_required', 'Shop administrator access is required.');
        if (!$identity->isAdministrator($userId)) throw new HttpError(403, 'admin_required', 'Shop administrator access is required.');
        $csrf = $_SERVER['HTTP_X_CSRF_TOKEN'] ?? '';
        if (!is_string($csrf) || !$identity->validateCsrf($csrf)) throw new HttpError(403, 'csrf_failed', 'Security token is invalid.');
        $body = Http::jsonBody((int)($config['request_max_bytes'] ?? 65536));
        $productId = (new AdminService($db))->saveProduct($userId, $body);
        Http::respond(200, ['request_id' => $requestId, 'product_id' => $productId]);
    }
    throw new HttpError(404, 'not_found', 'Endpoint not found.');
} catch (HttpError $error) {
    Http::respond($error->status, ['error' => ['code' => $error->errorCode, 'message' => $error->getMessage(), 'request_id' => $requestId]]);
} catch (PDOException $error) {
    $sqlState = (string)($error->errorInfo[0] ?? $error->getCode());
    $driverCode = (int)($error->errorInfo[1] ?? 0);
    error_log('[xenopixels-shop] request=' . $requestId . ' database_error=' . $sqlState . '/' . $driverCode . ' message=' . preg_replace('/[\r\n]+/', ' ', $error->getMessage()));
    if ($sqlState === '42S02' || $driverCode === 1146) {
        Http::respond(503, ['error' => ['code' => 'database_schema_missing', 'message' => 'Shop database tables are missing. Import both sql/001_schema.sql and sql/002_bridge_and_game_purchases.sql into the configured shop database.', 'request_id' => $requestId]]);
    }
    if ($sqlState === '28000' || in_array($driverCode, [1044, 1045], true)) {
        Http::respond(503, ['error' => ['code' => 'database_credentials_invalid', 'message' => 'Shop database credentials or database permissions are invalid. Check config/config.php and the aaPanel database grants.', 'request_id' => $requestId]]);
    }
    if (str_starts_with($sqlState, '08') || in_array($driverCode, [2002, 2003, 2006, 2013, 1049], true)) {
        Http::respond(503, ['error' => ['code' => 'database_unavailable', 'message' => 'The configured shop database could not be reached. Check that the database exists and the DSN host and name are correct.', 'request_id' => $requestId]]);
    }
    Http::respond(503, ['error' => ['code' => 'database_error', 'message' => 'The shop database rejected a request. Check the aaPanel PHP error log using this request ID.', 'request_id' => $requestId]]);
} catch (Throwable $error) {
    error_log('[xenopixels-shop] request=' . $requestId . ' error=' . get_class($error) . ' message=' . preg_replace('/[\r\n]+/', ' ', $error->getMessage()));
    Http::respond(500, ['error' => ['code' => 'internal_error', 'message' => 'Request could not be completed.', 'request_id' => $requestId]]);
}
