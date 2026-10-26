<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

use PDO;

final class ServerAuthenticator
{
    public function __construct(private readonly PDO $db, private readonly array $serverKeys) {}

    public function authenticate(string $rawBody): string
    {
        $serverId = self::header('X-Xeno-Server');
        $timestamp = self::header('X-Xeno-Timestamp');
        $nonce = self::header('X-Xeno-Nonce');
        $signature = self::header('X-Xeno-Signature');
        if (!preg_match('/^[a-zA-Z0-9_-]{1,64}$/', $serverId)
            || !preg_match('/^[0-9]{10}$/', $timestamp)
            || !preg_match('/^[a-f0-9]{32}$/', $nonce)
            || !preg_match('/^[a-f0-9]{64}$/', $signature)) {
            throw new HttpError(401, 'invalid_server_auth', 'Server authentication headers are invalid.');
        }
        $key = $this->serverKeys[$serverId] ?? null;
        if (!is_string($key) || strlen($key) < 32 || abs(time() - (int)$timestamp) > 60) {
            throw new HttpError(401, 'invalid_server_auth', 'Server authentication failed.');
        }
        $method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
        $path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
        $message = $timestamp . "\n" . $nonce . "\n" . $method . "\n" . $path . "\n" . hash('sha256', $rawBody);
        if (!hash_equals(hash_hmac('sha256', $message, $key), $signature)) {
            throw new HttpError(401, 'invalid_server_auth', 'Server authentication failed.');
        }
        $stmt = $this->db->prepare('INSERT INTO xp_shop_server_nonces(server_id, nonce, expires_at) VALUES(?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 2 MINUTE))');
        try {
            $stmt->execute([$serverId, $nonce]);
        } catch (\PDOException $error) {
            if (($error->errorInfo[0] ?? '') === '23000') {
                throw new HttpError(409, 'replayed_request', 'Request nonce has already been used.');
            }
            throw $error;
        }
        return $serverId;
    }

    private static function header(string $name): string
    {
        $value = $_SERVER['HTTP_' . strtoupper(str_replace('-', '_', $name))] ?? '';
        return is_string($value) ? trim($value) : '';
    }
}
