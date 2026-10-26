<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

use PDO;

final class OrderService
{
    public function __construct(private readonly PDO $db, private readonly PaymentProvider $payments) {}

    public function checkout(int $userId, string $slug, string $provider): array
    {
        if ($userId < 1 || !preg_match('/^[a-z0-9_-]{1,80}$/', $slug)) throw new HttpError(400, 'invalid_checkout', 'Checkout request is invalid.');
        if (!in_array($provider, ['stripe', 'paypal'], true)) throw new HttpError(400, 'invalid_payment_method', 'Choose Stripe or PayPal.');
        $find = $this->db->prepare('SELECT p.*,a.minecraft_uuid FROM xp_shop_products p JOIN xp_shop_accounts a ON a.forum_user_id=? WHERE p.slug=? AND p.enabled=1');
        $find->execute([$userId, $slug]);
        $product = $find->fetch();
        if (!$product) throw new HttpError(404, 'product_unavailable', 'Product is unavailable or your game account is not linked.');
        $amount = (int)($product['web_price_minor'] ?? 0);
        $currency = strtoupper((string)($product['web_currency'] ?? ''));
        if ($amount < 1 || $amount > 100000000 || !preg_match('/^[A-Z]{3}$/', $currency)) throw new HttpError(409, 'web_checkout_unavailable', 'This product has no valid web price.');
        $publicId = bin2hex(random_bytes(16));
        $snapshot = [
            'slug' => $product['slug'], 'title' => $product['title'],
            'description' => $product['description'], 'reward' => json_decode($product['reward_json'], true, 12, JSON_THROW_ON_ERROR),
        ];
        DeliveryService::assertReward($snapshot['reward']);
        $insert = $this->db->prepare("INSERT INTO xp_shop_orders(public_id,forum_user_id,minecraft_uuid,product_id,product_snapshot,payment_method,amount_minor,currency,status) SELECT ?,?,?,id,?,?,?,?,'pending' FROM xp_shop_products WHERE id=? AND enabled=1");
        $insert->execute([$publicId, $userId, $product['minecraft_uuid'], json_encode($snapshot, JSON_THROW_ON_ERROR), $provider, $amount, $currency, $product['id']]);
        if ($insert->rowCount() !== 1) throw new HttpError(409, 'product_unavailable', 'Product changed during checkout.');
        try {
            $checkout = $this->payments->createCheckout($provider, ['public_id' => $publicId, 'amount_minor' => $amount, 'currency' => $currency], $product);
            if (!preg_match('/^[A-Za-z0-9_-]{1,160}$/', (string)($checkout['provider_order_id'] ?? ''))
                || !is_string($checkout['url'] ?? null)) throw new HttpError(502, 'checkout_invalid', 'Payment provider response is invalid.');
            $saveProvider = $this->db->prepare('UPDATE xp_shop_orders SET provider_order_id=? WHERE public_id=? AND status=?');
            $saveProvider->execute([$checkout['provider_order_id'], $publicId, 'pending']);
        } catch (\Throwable $error) {
            $failed = $this->db->prepare("UPDATE xp_shop_orders SET status='failed' WHERE public_id=? AND status='pending'");
            $failed->execute([$publicId]);
            throw $error;
        }
        return ['order_id' => $publicId, 'checkout_url' => $checkout['url']];
    }

    public function capturePayPalOrder(int $userId, string $providerOrderId): void
    {
        $find = $this->db->prepare("SELECT public_id FROM xp_shop_orders WHERE forum_user_id=? AND payment_method='paypal' AND provider_order_id=? AND status='pending'");
        $find->execute([$userId, $providerOrderId]);
        if (!$find->fetchColumn()) throw new HttpError(404, 'paypal_order_missing', 'PayPal order was not found for this account.');
        $this->payments->capturePayPalOrder($providerOrderId);
    }

    public function completeWebhook(string $provider, array $event): void
    {
        $eventId = (string)($event['id'] ?? '');
        $eventType = (string)($event['type'] ?? '');
        $object = $provider === 'stripe' ? ($event['data']['object'] ?? null) : ($event['resource'] ?? null);
        if ($eventId === '' || strlen($eventId) > 160 || !is_array($object)) throw new HttpError(400, 'invalid_provider_event', 'Payment provider event is incomplete.');
        if (($provider === 'stripe' && ($eventType !== 'checkout.session.completed' || ($object['payment_status'] ?? '') !== 'paid'))
            || ($provider === 'paypal' && $eventType !== 'PAYMENT.CAPTURE.COMPLETED')) return;
        $orderId = $provider === 'stripe'
            ? (string)($object['metadata']['order_id'] ?? $object['client_reference_id'] ?? '')
            : (string)($object['custom_id'] ?? '');
        if ($provider === 'paypal' && $orderId === '') {
            // PayPal capture events may omit purchase_units[].custom_id. Resolve the
            // Orders v2 id from the signed capture resource, then verify ownership below.
            $paypalOrderId = (string)($object['supplementary_data']['related_ids']['order_id'] ?? '');
            if (preg_match('/^[A-Z0-9]{1,64}$/', $paypalOrderId)) {
                $lookup = $this->db->prepare("SELECT public_id FROM xp_shop_orders WHERE payment_method='paypal' AND provider_order_id=?");
                $lookup->execute([$paypalOrderId]);
                $orderId = (string)($lookup->fetchColumn() ?: '');
            }
        }
        if (!preg_match('/^[a-f0-9]{32}$/', $orderId)) return;
        $amount = $provider === 'stripe' ? (int)($object['amount_total'] ?? 0) : self::minorUnits($object['amount']['value'] ?? null);
        $currency = strtoupper((string)($provider === 'stripe' ? ($object['currency'] ?? '') : ($object['amount']['currency_code'] ?? '')));
        $this->db->beginTransaction();
        try {
            $eventInsert = $this->db->prepare('INSERT INTO xp_shop_payment_events(provider,provider_event_id,order_public_id,event_type,accepted) VALUES(?,?,?,?,0)');
            try { $eventInsert->execute([$provider, $eventId, $orderId, $eventType]); }
            catch (\PDOException $error) {
                if (($error->errorInfo[0] ?? '') === '23000') { $this->db->rollBack(); return; }
                throw $error;
            }
            $select = $this->db->prepare('SELECT * FROM xp_shop_orders WHERE public_id=? FOR UPDATE');
            $select->execute([$orderId]);
            $order = $select->fetch();
            if (!$order || $order['status'] !== 'pending' || $order['payment_method'] !== $provider
                || (int)$order['amount_minor'] !== $amount || strtoupper($order['currency']) !== $currency) {
                $this->db->commit();
                return;
            }
            $snapshot = json_decode($order['product_snapshot'], true, 12, JSON_THROW_ON_ERROR);
            DeliveryService::assertReward($snapshot['reward'] ?? null);
            $update = $this->db->prepare("UPDATE xp_shop_orders SET status='delivery_pending' WHERE id=? AND status='pending'");
            $update->execute([(int)$order['id']]);
            $delivery = $this->db->prepare("INSERT INTO xp_shop_deliveries(order_public_id,minecraft_uuid,reward_json,status) VALUES(?,?,?,'queued')");
            $delivery->execute([$orderId, $order['minecraft_uuid'], json_encode($snapshot['reward'], JSON_THROW_ON_ERROR)]);
            $accept = $this->db->prepare('UPDATE xp_shop_payment_events SET accepted=1 WHERE provider=? AND provider_event_id=?');
            $accept->execute([$provider, $eventId]);
            $this->db->commit();
        } catch (\Throwable $error) {
            if ($this->db->inTransaction()) $this->db->rollBack();
            throw $error;
        }
    }

    private static function minorUnits(mixed $value): int
    {
        if (!is_string($value) && !is_numeric($value)) return -1;
        if (!preg_match('/^(0|[1-9][0-9]{0,7})(\.[0-9]{1,2})?$/', (string)$value)) return -1;
        [$whole, $fraction] = array_pad(explode('.', (string)$value, 2), 2, '');
        return ((int)$whole * 100) + (int)str_pad($fraction, 2, '0');
    }
}
