# XenoPixels Shop

This integration contains a PHP 8.2.35+ shop for `https://shop.xenopixelsnetwork.com`, a vBulletin 5.7.5 login bridge, and a separate NeoForge 1.21.1 addon. The website has Stripe and PayPal checkout, forum-backed sessions/admin access, Minecraft account linking, item/allow-listed kit rewards, and an in-game catalog with MMO Econ and Zeni cash purchase buttons.

## Upload the shop to aaPanel

The shop hostname is its own virtual host. In aaPanel, open the **shop.xenopixelsnetwork.com** site and confirm its document root is `/www/wwwroot/shop.xenopixelsnetwork.com`. Upload and extract `xenopixels-shop-root.zip` directly into that directory. The archive has `index.php` at its root, with `src/`, `views/`, `assets/`, `config/`, and `sql/` beside it. Do not upload the archive's `web/` parent directory.

1. Back up the current shop vhost directory.
2. Upload/extract the ZIP at the vhost root so `/www/wwwroot/shop.xenopixelsnetwork.com/index.php` exists.
3. Upload `dist/config.php` to `/www/wwwroot/shop.xenopixelsnetwork.com/config/config.php`. It is prepared with the database values supplied for this setup; edit the bridge secret and other provider/server settings as described below. If using a different database, use `config/config.example.php` instead. Keep this config file private. It is intentionally excluded from the shop ZIP.
4. Import `sql/001_schema.sql` and `sql/002_bridge_and_game_purchases.sql` into the shop database.
5. Confirm the vhost serves PHP 8.2.35 or newer and has PDO MySQL, cURL, and JSON enabled. Open `https://shop.xenopixelsnetwork.com/api/v1/health`; expect JSON with `ok: true`. This endpoint reports the PHP version serving the site.
6. The supplied `.htaccess` denies web access to `config`, `src`, and `sql`. Keep Apache overrides enabled. If aaPanel uses Nginx, add equivalent location denies for those directories before opening the shop.

`config/config.example.php` defaults to the shop subdomain. Configure `base_url` as `https://shop.xenopixelsnetwork.com`, not the forum host. All URL routes are rooted at `/`.

## Install the vBulletin login bridge

Extract `xenopixels-vbulletin-plugin.zip` into the forum's existing web root. It contains `shop-bridge.php` for the document root and the `core/packages/xenopixels_shop/` plugin files. Import `xml/product_xenopixels_shop.xml` in the vBulletin Admin Control Panel. In ACP Navigation Manager, set the Shop item's Target URL to `https://xenopixelsnetwork.com/shop-bridge.php` (not directly to the shop host). This endpoint reads the current vBulletin session and automatically POSTs a short-lived signed assertion to the shop. The assertion is never included in a URL. The plugin can also append a styled Shop tab to compatible header output.

If the product is already imported, replace the old file at `core/packages/xenopixels_shop/hooks.php` with the file from the new ZIP; importing the XML again is unnecessary. Confirm the product is active and the vBulletin ACP **Product Hooks** list shows `xenopixels_shop_Hooks` with `hookFrontendBeforeOutput`. If using the ACP-created tab, point it to `/shop-bridge.php` as above. Clear vBulletin's template/cache data and restart the forum PHP-FPM pool after replacing files. If the class/method is absent from Product Hooks, reimport the XML and confirm the plugin files are in the forum's actual document-root `core/packages/xenopixels_shop/` directory.

Configure the same random 32-byte-or-longer `XENOPIXELS_SHOP_BRIDGE_SECRET` in both PHP runtimes. For the forum, either set the PHP-FPM environment variable or add `define('XENOPIXELS_SHOP_BRIDGE_SECRET', 'YOUR_SHARED_RANDOM_SECRET');` to the **forum document-root `config.php`** created by vBulletin's `makeconfig.php` (do not replace the existing config; add the define). In shop `config/config.php`, set `'bridge_shared_secret' => 'YOUR_SHARED_RANDOM_SECRET'` (or provide the matching shop PHP-FPM environment variable). The value must match exactly. Generate it locally with `openssl rand -hex 32`; do not paste it into chat or commit it.

Set `XENOPIXELS_SHOP_ADMIN_USER_IDS` in the forum PHP-FPM environment to a comma-separated allowlist of vBulletin user IDs who administer shop products. Alternatively, add `define('XENOPIXELS_SHOP_ADMIN_USER_IDS', '123');` to the forum document-root `config.php`, replacing `123` with your numeric vBulletin user ID. Do not use usernames or client-supplied role fields. After changing the allowlist, click the Shop nav link again to mint a new shop session with the updated admin flag. `do_not_upload/development/hooks.txt` is documentation only and is not an upload file.

The shop uses the forum's deep navy panels, cyan outlines, and cyan/green neon accents for text, navigation, buttons, and product tiles. The bridge requires HTTPS. Its shop session is host-only for `shop.xenopixelsnetwork.com`, HttpOnly, Secure, SameSite=Lax, expires after 12 hours, and is revocable through Sign out. Admin and web actions validate a session-bound CSRF token.

## Configure the Minecraft server and addon

Install `xenopixels-shop-addon-0.1.1.jar` on both the client and server with XenoPixels Network and XenoAPI present. Start the server once to create `config/xenopixels-shop.properties`; set its base URL, server ID, and per-server HMAC secret. Add the matching server ID/secret to the PHP `server_keys` map. Keep those values private. Use `/xshop` to browse, `/xshop link CODE` to link the account, and `/xshop claim` to retry pending web rewards. In the catalog, choose an item and pay with MMO Econ or Zeni cash; the server takes the price from the signed shop API response and debits via XenoPixels `CommerceApi` on the server thread.

Kit rewards are allow-listed in `config/xenopixels-shop-kits.json`. The addon creates a small example file on first use; edit it to define permitted kits. Product rewards accept item IDs/counts or kit names. Arbitrary commands are not supported.

## Payments and database

Add shop products at `/admin` after assigning your forum account ID in the forum environment. Configure Stripe and PayPal credentials and webhook IDs/secrets in the shop environment/config. Point Stripe to `/api/v1/webhooks/stripe` and PayPal to `/api/v1/webhooks/paypal`; configure the same webhook URLs in provider dashboards. A successful browser redirect is not treated as payment: only verified provider webhooks enqueue web rewards. Keep checkout disabled until webhook delivery has been verified in provider sandbox accounts.

Game-currency purchase records are idempotent at the shop database boundary. XenoAPI's currency debit is atomic but does not accept an idempotency key. If the game process stops after debit and before the shop records settlement, the player may need staff reconciliation; the addon warns them not to retry in that state. Do not promise exactly-once debit/reward across a server crash without a transactional or idempotent currency provider.

## vBulletin security patch

For the supplied vBulletin 5.7.5 Patch Level 3 archive, the template math evaluator in `upload/includes/vb5/template/runtime.php` used `eval`. A local patched copy replaces that evaluator with a bounded arithmetic parser. The original file is saved beside it as `runtime.php.xpn-original`. Review the patch, take a backup of the installed forum file, and replace only `includes/vb5/template/runtime.php` with the patched file from the supplied archive. Then run the forum's normal upgrade/cache refresh procedure and exercise affected templates. The vendor's official security update remains preferred; this local patch has not been deployed or PHP-linted in this environment.

The shop checks its own PHP minimum. It does not make an older PHP build safe for other sites or vBulletin itself. Verify the web-serving PHP-FPM version, not only CLI PHP. PHP 8.2 is near end of upstream security support; plan migration to a supported PHP branch.

## Validation status and limits

Java addon compilation can be performed from `addon/`. The PHP CLI is not installed in this authoring environment, and no live vBulletin, MySQL, Stripe/PayPal, or Minecraft server was available. Therefore successful code compilation does not verify the production bridge, migrations, payments, PHP-FPM configuration, or in-game behavior. Use a staging database and provider sandbox before enabling paid products.
