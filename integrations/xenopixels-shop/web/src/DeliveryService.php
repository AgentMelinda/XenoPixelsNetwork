<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

use PDO;

final class DeliveryService
{
    public function __construct(private readonly PDO $db) {}

    public function claim(string $uuid): ?array
    {
        $hex = self::uuidHex($uuid);
        $this->db->beginTransaction();
        try {
            $select = $this->db->prepare("SELECT id,order_public_id,reward_json FROM xp_shop_deliveries WHERE minecraft_uuid=UNHEX(?) AND status IN ('queued','retry') ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED");
            $select->execute([$hex]);
            $row = $select->fetch();
            if (!$row) { $this->db->commit(); return null; }
            $reward = json_decode($row['reward_json'], true, 12, JSON_THROW_ON_ERROR);
            self::assertReward($reward);
            $token = bin2hex(random_bytes(16));
            $update = $this->db->prepare("UPDATE xp_shop_deliveries SET status='claimed',claim_token=?,claimed_at=UTC_TIMESTAMP(),attempts=attempts+1 WHERE id=?");
            $update->execute([$token, (int)$row['id']]);
            $this->db->commit();
            return ['delivery_id' => (int)$row['id'], 'order_id' => $row['order_public_id'], 'claim_token' => $token, 'reward' => $reward];
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) $this->db->rollBack();
            throw $error;
        }
    }

    public function acknowledge(int $deliveryId, string $token, bool $success, string $errorCode = ''): void
    {
        if ($deliveryId < 1 || !preg_match('/^[a-f0-9]{32}$/', $token)) throw new HttpError(400, 'invalid_delivery_ack', 'Delivery acknowledgement is invalid.');
        $status = $success ? 'delivered' : 'retry';
        $stmt = $this->db->prepare("UPDATE xp_shop_deliveries SET status=?,delivered_at=IF(?='delivered',UTC_TIMESTAMP(),NULL),last_error_code=? WHERE id=? AND claim_token=? AND status='claimed'");
        $stmt->execute([$status, $status, $success ? null : substr(preg_replace('/[^a-z0-9_-]/i', '', $errorCode) ?: 'grant_failed', 0, 64), $deliveryId, $token]);
        if ($stmt->rowCount() !== 1) {
            $check = $this->db->prepare("SELECT status FROM xp_shop_deliveries WHERE id=? AND claim_token=?");
            $check->execute([$deliveryId, $token]);
            if ($success && $check->fetchColumn() === 'delivered') return;
            throw new HttpError(409, 'delivery_state_conflict', 'Delivery claim is missing, stale, or already acknowledged.');
        }
        if ($success) {
            $order = $this->db->prepare("UPDATE xp_shop_orders SET status='delivered' WHERE public_id=(SELECT order_public_id FROM xp_shop_deliveries WHERE id=?) AND status IN ('paid','delivery_pending')");
            $order->execute([$deliveryId]);
        }
    }

    public static function assertReward(mixed $reward): void
    {
        if (!is_array($reward) || !array_is_list($reward) || count($reward) < 1 || count($reward) > 32) {
            throw new HttpError(422, 'invalid_reward', 'Stored reward definition is invalid.');
        }
        foreach ($reward as $entry) {
            if (!is_array($entry) || !in_array($entry['type'] ?? null, ['item', 'kit'], true)) {
                throw new HttpError(422, 'invalid_reward', 'Reward contains an unsupported operation.');
            }
            if ($entry['type'] === 'item') {
                if (!is_string($entry['item'] ?? null) || !preg_match('/^[a-z0-9_.-]+:[a-z0-9_./-]+$/', $entry['item'])
                    || !is_int($entry['count'] ?? null) || $entry['count'] < 1 || $entry['count'] > 64) {
                    throw new HttpError(422, 'invalid_reward', 'Item reward is outside allowed bounds.');
                }
            } elseif (!is_string($entry['kit'] ?? null) || !preg_match('/^[a-z0-9_-]{1,48}$/', $entry['kit'])) {
                throw new HttpError(422, 'invalid_reward', 'Kit reward identifier is invalid.');
            }
        }
    }

    private static function uuidHex(string $uuid): string
    {
        $hex = str_replace('-', '', strtolower($uuid));
        if (!preg_match('/^[a-f0-9]{32}$/', $hex)) throw new HttpError(400, 'invalid_uuid', 'Minecraft UUID is invalid.');
        return $hex;
    }
}
