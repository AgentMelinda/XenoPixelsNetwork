<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Link game account | XenoPixels Shop</title><link rel="stylesheet" href="/assets/shop.css?v=2"></head>
<body><header class="top"><a class="brand" href="/">XenoPixels <span>SHOP</span></a><nav><a href="/">Catalog</a><a href="/orders">My orders</a></nav></header>
<main><section class="hero"><p class="eyebrow">ACCOUNT CONNECTION</p><h1>Link your game account.</h1><p>Generate a one-time code, then enter it in game with <strong>/xshop link CODE</strong>. The code expires in ten minutes and can only be used once.</p><button class="button" id="create-link" data-csrf="<?= htmlspecialchars($csrfToken, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?>">Generate one-time code</button><output id="link-result" aria-live="polite"></output></section></main>
<script src="/assets/shop.js?v=0.1.1" defer></script></body></html>
