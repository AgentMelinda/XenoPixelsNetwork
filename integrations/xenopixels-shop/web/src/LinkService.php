<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

use PDO;

final class LinkService
{
    public function __construct(private readonly PDO $db, private readonly int $ttlSeconds) {}

    public function createCode(int $forumUserId): array
    {
        if ($forumUserId < 1 || $this->ttlSeconds < 60 || $this->ttlSeconds > 1800) {
            throw new HttpError(503, 'link_unavailable', 'Account linking is not configured.');
        }
        $code = strtoupper(bin2hex(random_bytes(8)));
        $hash = hash('sha256', $code, true);
        $stmt = $this->db->prepare('INSERT INTO xp_shop_links(forum_user_id, code_hash, expires_at) VALUES(?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND))');
        $stmt->execute([$forumUserId, $hash, $this->ttlSeconds]);
        return ['code' => $code, 'expires_in' => $this->ttlSeconds];
    }

    public function redeem(string $code, string $uuid): int
    {
        if (!preg_match('/^[A-F0-9]{16}$/', $code) || !preg_match('/^[a-f0-9-]{36}$/i', $uuid)) {
            throw new HttpError(400, 'invalid_link_request', 'Link code or Minecraft UUID is invalid.');
        }
        $uuidHex = str_replace('-', '', strtolower($uuid));
        if (!preg_match('/^[a-f0-9]{32}$/', $uuidHex)) throw new HttpError(400, 'invalid_link_request', 'Minecraft UUID is invalid.');
        $this->db->beginTransaction();
        try {
            $find = $this->db->prepare('SELECT id, forum_user_id FROM xp_shop_links WHERE code_hash = ? AND expires_at > UTC_TIMESTAMP() AND redeemed_uuid IS NULL FOR UPDATE');
            $find->execute([hash('sha256', $code, true)]);
            $link = $find->fetch();
            if (!$link) throw new HttpError(404, 'link_code_expired', 'Link code is invalid, expired, or already used.');
            $insert = $this->db->prepare('INSERT INTO xp_shop_accounts(forum_user_id, minecraft_uuid) VALUES(?, UNHEX(?))');
            $insert->execute([(int)$link['forum_user_id'], $uuidHex]);
            $consume = $this->db->prepare('UPDATE xp_shop_links SET redeemed_uuid=UNHEX(?) WHERE id=? AND redeemed_uuid IS NULL');
            $consume->execute([$uuidHex, (int)$link['id']]);
            if ($consume->rowCount() !== 1) throw new HttpError(409, 'link_code_used', 'Link code was already used.');
            $this->db->commit();
            return (int)$link['forum_user_id'];
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) $this->db->rollBack();
            if ($error instanceof HttpError) throw $error;
            if ($error instanceof \PDOException && ($error->errorInfo[0] ?? '') === '23000') {
                throw new HttpError(409, 'account_already_linked', 'This forum account or Minecraft account is linked elsewhere.');
            }
            throw $error;
        }
    }
}
