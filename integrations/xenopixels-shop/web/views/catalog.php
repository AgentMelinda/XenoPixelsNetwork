<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <title>XenoPixels Shop</title><link rel="stylesheet" href="/assets/shop.css?v=2">
</head>
<body>
  <?php require __DIR__ . '/topnav.php'; ?>
  <main><section class="hero"><p class="eyebrow">XENOPIXELS NETWORK</p><h1>Power up your next adventure.</h1><p>Pay online with Stripe or PayPal, or use MMO Econ and Zeni in game. Rewards are delivered through the server.</p><?php if ($userId === null): ?><p><a href="https://xenopixelsnetwork.com/" rel="noopener">Sign in on the forum</a>, then use the Shop button there to connect your account.</p><?php elseif (!$accountLinked): ?><p>Your forum account is connected. <a href="/link">Link your game account</a> before checkout.</p><?php endif; ?></section>
    <section class="grid" aria-label="Shop catalog">
      <?php foreach ($products as $product): ?><article class="card">
        <?php if (!empty($product['image_url'])): ?><img src="<?= htmlspecialchars($product['image_url'], ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?>" alt="" loading="lazy"><?php endif; ?>
        <div class="card-body"><h2><?= htmlspecialchars($product['title'], ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?></h2><p><?= nl2br(htmlspecialchars($product['description'], ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')) ?></p><div class="card-foot"><span><?= $product['web_price_minor'] === null ? 'In-game purchase' : htmlspecialchars($product['web_currency'], ENT_QUOTES, 'UTF-8') . ' ' . number_format(((int)$product['web_price_minor']) / 100, 2) ?></span><a class="button" href="/item/<?= rawurlencode($product['slug']) ?>">Details</a></div></div>
      </article><?php endforeach; ?>
      <?php if (!$products): ?><p class="empty">The catalog is being prepared. Please check back soon.</p><?php endif; ?>
    </section>
  </main><footer>Secure checkout is handled by Stripe or PayPal. XenoPixels never stores card details.</footer>
</body></html>
