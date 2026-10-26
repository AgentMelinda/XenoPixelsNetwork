<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

use PDO;

final class AdminService
{
    public function __construct(private readonly PDO $db) {}

    public function saveProduct(int $adminId, array $input): int
    {
        if ($adminId < 1) throw new HttpError(403, 'admin_required', 'Shop administrator access is required.');
        $id = filter_var($input['id'] ?? null, FILTER_VALIDATE_INT);
        $slug = trim((string)($input['slug'] ?? ''));
        $title = trim((string)($input['title'] ?? ''));
        $description = trim((string)($input['description'] ?? ''));
        $image = trim((string)($input['image_url'] ?? ''));
        if (($id !== false && $id !== null && $id < 1) || !preg_match('/^[a-z0-9_-]{1,80}$/', $slug)
            || $title === '' || strlen($title) > 120 || strlen($description) > 8000 || strlen($image) > 500
            || ($image !== '' && (!filter_var($image, FILTER_VALIDATE_URL) || !str_starts_with($image, 'https://')))) {
            throw new HttpError(400, 'invalid_product', 'Product fields are invalid or exceed limits.');
        }
        $webAmount = self::optionalAmount($input['web_price_minor'] ?? null);
        $webCurrency = strtoupper(trim((string)($input['web_currency'] ?? '')));
        if ($webAmount !== null && !preg_match('/^[A-Z]{3}$/', $webCurrency)) throw new HttpError(400, 'invalid_web_currency', 'Web price requires a three-letter currency.');
        if ($webAmount === null) $webCurrency = null;
        $mmo = self::optionalAmount($input['mmoecon_price'] ?? null);
        $zeni = self::optionalAmount($input['zeni_cash_price'] ?? null);
        if ($webAmount === null && $mmo === null && $zeni === null) throw new HttpError(400, 'missing_price', 'Set at least one purchase price.');
        $rewardRaw = (string)($input['reward_json'] ?? '');
        try { $reward = json_decode($rewardRaw, true, 12, JSON_THROW_ON_ERROR); }
        catch (\JsonException) { throw new HttpError(400, 'invalid_reward_json', 'Reward definition must be valid JSON.'); }
        DeliveryService::assertReward($reward);
        $enabled = ($input['enabled'] ?? false) === true;
        if ($id) {
            $stmt = $this->db->prepare('UPDATE xp_shop_products SET slug=?,title=?,description=?,image_url=?,enabled=?,web_price_minor=?,web_currency=?,mmoecon_price=?,zeni_cash_price=?,reward_json=? WHERE id=?');
            $stmt->execute([$slug,$title,$description,$image === '' ? null : $image,(int)$enabled,$webAmount,$webCurrency,$mmo,$zeni,json_encode($reward, JSON_THROW_ON_ERROR),$id]);
            if ($stmt->rowCount() !== 1) throw new HttpError(404, 'product_not_found', 'Product does not exist or no fields changed.');
            $productId = (int)$id;
        } else {
            $stmt = $this->db->prepare('INSERT INTO xp_shop_products(slug,title,description,image_url,enabled,web_price_minor,web_currency,mmoecon_price,zeni_cash_price,reward_json) VALUES(?,?,?,?,?,?,?,?,?,?)');
            try { $stmt->execute([$slug,$title,$description,$image === '' ? null : $image,(int)$enabled,$webAmount,$webCurrency,$mmo,$zeni,json_encode($reward, JSON_THROW_ON_ERROR)]); }
            catch (\PDOException $error) {
                if (($error->errorInfo[0] ?? '') === '23000') throw new HttpError(409, 'product_slug_exists', 'A product already uses this slug.');
                throw $error;
            }
            $productId = (int)$this->db->lastInsertId();
        }
        $audit = $this->db->prepare("INSERT INTO xp_shop_audit(actor_type,actor_id,action,subject_id,metadata) VALUES('forum',?,'product.save',?,?)");
        $audit->execute([(string)$adminId,(string)$productId,json_encode(['slug'=>$slug,'enabled'=>$enabled], JSON_THROW_ON_ERROR)]);
        return $productId;
    }

    private static function optionalAmount(mixed $value): ?int
    {
        if ($value === null || $value === '') return null;
        $amount = filter_var($value, FILTER_VALIDATE_INT);
        if ($amount === false || $amount < 1 || $amount > 1000000000) throw new HttpError(400, 'invalid_price', 'Prices must be positive integers within the configured maximum.');
        return $amount;
    }
}
