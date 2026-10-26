<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

use PDO;

interface VbIdentityProvider
{
    public function currentUserId(): ?int;
    public function validateCsrf(string $token): bool;
    public function csrfToken(): ?string;
    public function isAdministrator(int $userId): bool;
    public function logout(): void;
}

/** Shop-side session established only by a signed, single-use vBulletin assertion. */
final class ConfiguredVbIdentityProvider implements VbIdentityProvider
{
    private const COOKIE = '__Host-xp_shop';
    private ?array $session = null;
    private ?string $rawSessionToken = null;

    public function __construct(private readonly PDO $db, private readonly array $config)
    {
        $raw = $_COOKIE[self::COOKIE] ?? '';
        if (!is_string($raw) || !preg_match('/^[a-f0-9]{64}$/', $raw)) return;
        $stmt = $this->db->prepare('SELECT forum_user_id,is_admin FROM xp_shop_sessions WHERE token_hash=UNHEX(?) AND expires_at>UTC_TIMESTAMP()');
        $stmt->execute([hash('sha256', $raw)]);
        $session = $stmt->fetch();
        if ($session) {
            $this->session = $session;
            $this->rawSessionToken = $raw;
        }
    }

    public function currentUserId(): ?int
    {
        $id = (int)($this->session['forum_user_id'] ?? 0);
        return $id > 0 ? $id : null;
    }

    public function validateCsrf(string $token): bool
    {
        $expected = $this->csrfToken();
        return $expected !== null && $token !== '' && hash_equals($expected, $token);
    }

    public function csrfToken(): ?string
    {
        if ($this->rawSessionToken === null) return null;
        $key = (string)($this->config['bridge_shared_secret'] ?? '');
        if (strlen($key) < 32) return null;
        return hash_hmac('sha256', 'csrf', $this->rawSessionToken . $key);
    }

    public function isAdministrator(int $userId): bool
    {
        return $userId > 0 && $this->currentUserId() === $userId && (int)($this->session['is_admin'] ?? 0) === 1;
    }

    public function logout(): void
    {
        if ($this->rawSessionToken !== null) {
            $stmt = $this->db->prepare('DELETE FROM xp_shop_sessions WHERE token_hash=UNHEX(?)');
            $stmt->execute([hash('sha256', $this->rawSessionToken)]);
        }
        setcookie(self::COOKIE, '', ['expires' => 1, 'path' => '/', 'secure' => true, 'httponly' => true, 'samesite' => 'Lax']);
        $this->session = null;
        $this->rawSessionToken = null;
    }

    public static function consumeBridge(PDO $db, array $config, string $assertion): int
    {
        $key = (string)($config['bridge_shared_secret'] ?? '');
        if (strlen($key) < 32 || strlen($assertion) > 2048 || substr_count($assertion, '.') !== 1) {
            throw new HttpError(503, 'bridge_unavailable', 'Forum login bridge is not configured.');
        }
        [$encoded, $signature] = explode('.', $assertion, 2);
        $payload = self::decodeBase64Url($encoded);
        $providedSignature = self::decodeBase64Url($signature);
        $expectedSignature = hash_hmac('sha256', $encoded, $key, true);
        if ($payload === null || $providedSignature === null || !hash_equals($expectedSignature, $providedSignature)) {
            throw new HttpError(401, 'bridge_invalid', 'Forum login assertion is invalid.');
        }
        try { $claims = json_decode($payload, true, 8, JSON_THROW_ON_ERROR); }
        catch (\JsonException) { throw new HttpError(401, 'bridge_invalid', 'Forum login assertion is invalid.'); }
        $userId = filter_var($claims['uid'] ?? null, FILTER_VALIDATE_INT);
        $issuedAt = filter_var($claims['iat'] ?? null, FILTER_VALIDATE_INT);
        $nonce = (string)($claims['nonce'] ?? '');
        $isAdmin = ($claims['admin'] ?? null) === true;
        if ($userId === false || $userId < 1 || $issuedAt === false || abs(time() - $issuedAt) > 120
            || !preg_match('/^[a-f0-9]{32}$/', $nonce)) {
            throw new HttpError(401, 'bridge_expired', 'Forum login assertion is expired or malformed.');
        }

        $db->beginTransaction();
        try {
            $insertNonce = $db->prepare('INSERT INTO xp_shop_bridge_nonces(nonce_hash,expires_at) VALUES(UNHEX(?),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 5 MINUTE))');
            try { $insertNonce->execute([hash('sha256', $nonce)]); }
            catch (\PDOException $error) {
                if (($error->errorInfo[0] ?? '') === '23000') throw new HttpError(409, 'bridge_replayed', 'Forum login assertion has already been used.');
                throw $error;
            }
            $rawToken = bin2hex(random_bytes(32));
            $expires = 43200;
            $insertSession = $db->prepare('INSERT INTO xp_shop_sessions(token_hash,forum_user_id,is_admin,expires_at) VALUES(UNHEX(?),?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? SECOND))');
            $insertSession->execute([hash('sha256', $rawToken), (int)$userId, (int)$isAdmin, $expires]);
            $db->commit();
        } catch (\Throwable $error) {
            if ($db->inTransaction()) $db->rollBack();
            throw $error;
        }

        setcookie(self::COOKIE, $rawToken, ['expires' => time() + $expires, 'path' => '/', 'secure' => true, 'httponly' => true, 'samesite' => 'Lax']);
        return (int)$userId;
    }

    private static function decodeBase64Url(string $value): ?string
    {
        if ($value === '' || !preg_match('/^[A-Za-z0-9_-]+$/', $value)) return null;
        $decoded = base64_decode(strtr($value, '-_', '+/') . str_repeat('=', (4 - strlen($value) % 4) % 4), true);
        return $decoded === false ? null : $decoded;
    }
}
