<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

use PDO;

final class GamePurchaseService
{
    public function __construct(private readonly PDO $db) {}

    public function prepare(string $serverId, string $purchaseKey, string $uuid, string $slug, string $currency): array
    {
        $hex = self::uuidHex($uuid);
        if (!preg_match('/^[a-f0-9]{32}$/', $purchaseKey)
            || !preg_match('/^[a-z0-9_-]{1,80}$/', $slug)
            || !in_array($currency, ['mmoecon', 'zeni_cash'], true)) {
            throw new HttpError(400, 'invalid_game_purchase', 'Game purchase request is invalid.');
        }
        $this->db->beginTransaction();
        try {
            $existing = $this->db->prepare('SELECT minecraft_uuid,currency_id,amount,status FROM xp_shop_game_purchases WHERE server_id=? AND purchase_key=? FOR UPDATE');
            $existing->execute([$serverId, $purchaseKey]);
            $row = $existing->fetch();
            if ($row) {
                if (strtolower(bin2hex($row['minecraft_uuid'])) !== $hex || $row['currency_id'] !== $currency) {
                    throw new HttpError(409, 'purchase_key_conflict', 'Purchase key is already bound to a different request.');
                }
                $this->db->commit();
                return ['purchase_key' => $purchaseKey, 'amount' => (int)$row['amount'], 'currency' => $currency, 'status' => $row['status']];
            }

            $account = $this->db->prepare('SELECT forum_user_id FROM xp_shop_accounts WHERE minecraft_uuid=UNHEX(?)');
            $account->execute([$hex]);
            $userId = (int)($account->fetchColumn() ?: 0);
            if ($userId < 1) throw new HttpError(403, 'account_not_linked', 'Link your game account to the forum before buying.');

            $priceColumn = $currency === 'mmoecon' ? 'mmoecon_price' : 'zeni_cash_price';
            $query = $this->db->prepare("SELECT id,slug,title,description,reward_json,{$priceColumn} AS amount FROM xp_shop_products WHERE slug=? AND enabled=1");
            $query->execute([$slug]);
            $product = $query->fetch();
            $amount = (int)($product['amount'] ?? 0);
            if (!$product || $amount < 1 || $amount > 1000000000) throw new HttpError(409, 'currency_purchase_unavailable', 'This product is not available for that in-game currency.');
            $reward = json_decode($product['reward_json'], true, 12, JSON_THROW_ON_ERROR);
            DeliveryService::assertReward($reward);
            $snapshot = json_encode(['slug'=>$product['slug'],'title'=>$product['title'],'description'=>$product['description'],'reward'=>$reward], JSON_THROW_ON_ERROR);

            $insert = $this->db->prepare("INSERT INTO xp_shop_game_purchases(server_id,purchase_key,forum_user_id,minecraft_uuid,product_id,product_snapshot,currency_id,amount,status) VALUES(?,?,?,UNHEX(?),?,?,?,?, 'prepared')");
            $insert->execute([$serverId,$purchaseKey,$userId,$hex,$product['id'],$snapshot,$currency,$amount]);
            $ledger = $this->db->prepare("INSERT INTO xp_shop_currency_ledger(server_id,purchase_key,minecraft_uuid,currency_id,amount,status) VALUES(?,?,UNHEX(?),?,?, 'prepared')");
            $ledger->execute([$serverId,$purchaseKey,$hex,$currency,$amount]);
            $this->db->commit();
            return ['purchase_key'=>$purchaseKey,'amount'=>$amount,'currency'=>$currency,'status'=>'prepared'];
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) $this->db->rollBack();
            throw $error;
        }
    }

    public function finish(string $serverId, string $purchaseKey, string $result): string
    {
        if (!preg_match('/^[a-f0-9]{32}$/', $purchaseKey) || !in_array($result, ['debited','declined','uncertain'], true)) {
            throw new HttpError(400, 'invalid_game_purchase_result', 'Game purchase result is invalid.');
        }
        $this->db->beginTransaction();
        try {
            $select = $this->db->prepare('SELECT * FROM xp_shop_game_purchases WHERE server_id=? AND purchase_key=? FOR UPDATE');
            $select->execute([$serverId,$purchaseKey]);
            $purchase = $select->fetch();
            if (!$purchase) throw new HttpError(404, 'game_purchase_missing', 'Game purchase was not found.');
            if ($purchase['status'] === 'settled') { $this->db->commit(); return 'settled'; }
            if ($purchase['status'] === 'cancelled') { $this->db->commit(); return 'cancelled'; }
            if ($purchase['status'] === 'uncertain') { $this->db->commit(); return 'uncertain'; }
            if ($purchase['status'] !== 'prepared') throw new HttpError(409, 'game_purchase_state', 'Game purchase cannot be completed in its current state.');

            if ($result === 'uncertain') {
                $this->setStatus($serverId, $purchaseKey, 'uncertain');
                $this->db->commit();
                return 'uncertain';
            }
            if ($result === 'declined') {
                $this->setStatus($serverId, $purchaseKey, 'cancelled');
                $this->db->commit();
                return 'cancelled';
            }

            $publicId = $purchaseKey;
            $currencyCode = $purchase['currency_id'] === 'mmoecon' ? 'MMO' : 'ZEN';
            $order = $this->db->prepare("INSERT INTO xp_shop_orders(public_id,forum_user_id,minecraft_uuid,product_id,product_snapshot,payment_method,game_purchase_key,amount_minor,currency,status) VALUES(?,?,UNHEX(?),?,?,?, ?,?,?, 'delivery_pending')");
            $order->execute([$publicId,(int)$purchase['forum_user_id'],bin2hex($purchase['minecraft_uuid']),(int)$purchase['product_id'],$purchase['product_snapshot'],$purchase['currency_id'],$purchaseKey,(int)$purchase['amount'],$currencyCode]);
            $snapshot = json_decode($purchase['product_snapshot'], true, 12, JSON_THROW_ON_ERROR);
            $delivery = $this->db->prepare("INSERT INTO xp_shop_deliveries(order_public_id,minecraft_uuid,reward_json,status) VALUES(?,UNHEX(?),?,'queued')");
            $delivery->execute([$publicId,bin2hex($purchase['minecraft_uuid']),json_encode($snapshot['reward'], JSON_THROW_ON_ERROR)]);
            $this->setStatus($serverId, $purchaseKey, 'settled');
            $this->db->commit();
            return 'settled';
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) $this->db->rollBack();
            throw $error;
        }
    }

    private function setStatus(string $serverId, string $purchaseKey, string $status): void
    {
        $update = $this->db->prepare('UPDATE xp_shop_game_purchases SET status=? WHERE server_id=? AND purchase_key=?');
        $update->execute([$status,$serverId,$purchaseKey]);
        $ledger = $this->db->prepare('UPDATE xp_shop_currency_ledger SET status=? WHERE server_id=? AND purchase_key=?');
        $ledger->execute([$status,$serverId,$purchaseKey]);
    }

    private static function uuidHex(string $uuid): string
    {
        $hex = str_replace('-', '', strtolower($uuid));
        if (!preg_match('/^[a-f0-9]{32}$/', $hex)) throw new HttpError(400, 'invalid_uuid', 'Minecraft UUID is invalid.');
        return $hex;
    }
}
