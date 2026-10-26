<?php
declare(strict_types=1);

// Drop this file in the vBulletin document root. Boot the normal vB frontend
// so the existing forum login cookie is resolved through vBulletin's session.
if (!defined('VB_REQUEST_START_TIME')) {
	define('VB_REQUEST_START_TIME', microtime(true));
}
if (!defined('VB_ENTRY')) {
	define('VB_ENTRY', 1);
}

require_once __DIR__ . '/includes/vb5/autoloader.php';
vB5_Autoloader::register(__DIR__);
vB5_Frontend_Application::init(__DIR__);

$userId = 0;
try
{
	$userId = (int) vB::getCurrentSession()->get('userid');
}
catch (Throwable $error)
{
	$userId = 0;
}

header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('Referrer-Policy: no-referrer');

if ($userId < 1)
{
	http_response_code(401);
	echo '<!doctype html><html lang="en"><meta charset="utf-8"><title>Shop sign-in</title><p>Please sign in to the forum, then click Shop again.</p></html>';
	exit;
}

$secret = defined('XENOPIXELS_SHOP_BRIDGE_SECRET') ? XENOPIXELS_SHOP_BRIDGE_SECRET : getenv('XENOPIXELS_SHOP_BRIDGE_SECRET');
if (!is_string($secret) || strlen($secret) < 32)
{
	http_response_code(503);
	echo '<!doctype html><html lang="en"><meta charset="utf-8"><title>Shop sign-in unavailable</title><p>The shop login bridge is not configured. Contact the site administrator.</p></html>';
	exit;
}

$adminIdsValue = getenv('XENOPIXELS_SHOP_ADMIN_USER_IDS');
if (!is_string($adminIdsValue) || trim($adminIdsValue) === '')
{
	$adminIdsValue = defined('XENOPIXELS_SHOP_ADMIN_USER_IDS') ? XENOPIXELS_SHOP_ADMIN_USER_IDS : '';
}
$adminIds = array_filter(array_map('trim', explode(',', (string)$adminIdsValue)), 'ctype_digit');
$claims = [
	'uid' => $userId,
	'iat' => time(),
	'nonce' => bin2hex(random_bytes(16)),
	'admin' => in_array((string)$userId, $adminIds, true),
];
$encoded = rtrim(strtr(base64_encode(json_encode($claims, JSON_THROW_ON_ERROR)), '+/', '-_'), '=');
$signature = rtrim(strtr(base64_encode(hash_hmac('sha256', $encoded, $secret, true)), '+/', '-_'), '=');
$assertion = $encoded . '.' . $signature;
$nonce = rtrim(strtr(base64_encode(random_bytes(18)), '+/', '-_'), '=');
$destination = 'https://shop.xenopixelsnetwork.com/auth/consume';

header("Content-Security-Policy: default-src 'none'; script-src 'nonce-" . $nonce . "'; form-action https://shop.xenopixelsnetwork.com; base-uri 'none'; frame-ancestors 'none'");
?>
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="referrer" content="no-referrer"><title>Opening XenoPixels Shop…</title></head>
<body>
<form id="shop-bridge" method="post" action="<?= htmlspecialchars($destination, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?>">
	<input type="hidden" name="assertion" value="<?= htmlspecialchars($assertion, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?>">
	<noscript><button type="submit">Continue to XenoPixels Shop</button></noscript>
</form>
<script nonce="<?= htmlspecialchars($nonce, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?>">document.getElementById('shop-bridge').submit();</script>
</body>
</html>
