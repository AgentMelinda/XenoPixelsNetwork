<header class="top">
  <a class="brand" href="/">XenoPixels <span>SHOP</span></a>
  <nav aria-label="Shop navigation">
    <a href="/">Catalog</a>
    <?php if ($userId !== null): ?>
      <a href="/link">Link game account</a>
      <a href="/orders">My orders</a>
      <?php if (!empty($isAdmin)): ?><a href="/admin">Shop admin</a><?php endif; ?>
      <form class="logout-form" action="/logout" method="post"><input type="hidden" name="csrf" value="<?= htmlspecialchars((string)($csrfToken ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?>"><button type="submit">Sign out</button></form>
    <?php else: ?>
      <a href="https://xenopixelsnetwork.com/" rel="noopener">Sign in on the forum</a>
    <?php endif; ?>
  </nav>
</header>
