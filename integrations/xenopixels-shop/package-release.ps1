$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dist = Join-Path $root 'dist'
$stageRoot = Join-Path $env:TEMP ('xpn-shop-package-' + [guid]::NewGuid().ToString('N'))
$shopStage = Join-Path $stageRoot 'shop-root'
$vbStage = Join-Path $stageRoot 'vbulletin-plugin'
New-Item -ItemType Directory -Force -Path $shopStage, $vbStage, $dist | Out-Null
try {
    foreach ($name in @('src','views','sql')) {
        Copy-Item -LiteralPath (Join-Path $root "web/$name") -Destination $shopStage -Recurse
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $shopStage 'assets'), (Join-Path $shopStage 'config') | Out-Null
    Copy-Item -Path (Join-Path $root 'web/public/assets/*') -Destination (Join-Path $shopStage 'assets') -Recurse
    Copy-Item -LiteralPath (Join-Path $root 'web/public/index.php') -Destination (Join-Path $shopStage 'index.php')
    Copy-Item -LiteralPath (Join-Path $root 'web/config/config.example.php') -Destination (Join-Path $shopStage 'config/config.example.php')
    Copy-Item -LiteralPath (Join-Path $root 'web/.htaccess') -Destination (Join-Path $shopStage '.htaccess')
    Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination (Join-Path $shopStage 'INSTALL.md')

    New-Item -ItemType Directory -Force -Path (Join-Path $vbStage 'core/packages/xenopixels_shop'), (Join-Path $vbStage 'xml') | Out-Null
    Copy-Item -Path (Join-Path $root 'vbulletin/core/packages/xenopixels_shop/*') -Destination (Join-Path $vbStage 'core/packages/xenopixels_shop')
    Copy-Item -LiteralPath (Join-Path $root 'vbulletin/xml/product_xenopixels_shop.xml') -Destination (Join-Path $vbStage 'xml/product_xenopixels_shop.xml')
    Copy-Item -LiteralPath (Join-Path $root 'vbulletin/shop-bridge.php') -Destination (Join-Path $vbStage 'shop-bridge.php')
    Set-Content -LiteralPath (Join-Path $vbStage 'INSTALL.txt') -Value @(
        'Upload shop-bridge.php to the vBulletin document root (next to index.php). Merge core/packages/xenopixels_shop into the same root.',
        'Import xml/product_xenopixels_shop.xml in the vBulletin Admin Control Panel. If the product is already imported, overwrite core/packages/xenopixels_shop/hooks.php; do not reimport unless its Product Hooks entry is missing.',
        'In ACP Navigation Manager, set the Shop item Target URL to https://xenopixelsnetwork.com/shop-bridge.php. This endpoint reads the signed-in vBulletin session and POSTs a one-time assertion to the shop.',
        'The plugin can also append a Shop tab to supported forum header output. Verify xenopixels_shop_Hooks and hookFrontendBeforeOutput in ACP Product Hooks, then clear vBulletin caches, reload PHP-FPM/OPcache, and hard-refresh if it is absent.',
        'Set the same random XENOPIXELS_SHOP_BRIDGE_SECRET in both PHP runtimes. Forum option: PHP-FPM environment or define("XENOPIXELS_SHOP_BRIDGE_SECRET", "...") in the vBulletin document-root config.php created by makeconfig.php. Shop option: bridge_shared_secret in config/config.php or PHP-FPM environment.',
        'Set XENOPIXELS_SHOP_ADMIN_USER_IDS in forum PHP-FPM, or define("XENOPIXELS_SHOP_ADMIN_USER_IDS", "123") in forum document-root config.php using numeric vBulletin user IDs. Click the Shop nav link again after changing this value.',
        'Do not upload do_not_upload/development/hooks.txt.'
    ) -Encoding utf8

    $shopZip = Join-Path $dist 'xenopixels-shop-root.zip'
    $vbZip = Join-Path $dist 'xenopixels-vbulletin-plugin.zip'
    $addonJar = Join-Path $dist 'xenopixels-shop-addon-0.1.1.jar'
    Remove-Item -LiteralPath $shopZip, $vbZip -Force -ErrorAction SilentlyContinue
    Compress-Archive -Path (Join-Path $shopStage '*') -DestinationPath $shopZip -CompressionLevel Optimal
    Compress-Archive -Path (Join-Path $vbStage '*') -DestinationPath $vbZip -CompressionLevel Optimal
    Copy-Item -LiteralPath (Join-Path $root 'addon/build/libs/xenopixels-shop-addon-0.1.1.jar') -Destination $addonJar -Force

    if (-not (Test-Path -LiteralPath (Join-Path $shopStage 'index.php'))) { throw 'Shop root package does not contain index.php at its root.' }
    if (Get-ChildItem -LiteralPath $shopStage -Recurse -Filter config.php) { throw 'A live config.php must never be included in a deployment archive.' }
    Get-FileHash -Algorithm SHA256 $shopZip, $vbZip, $addonJar | Format-Table -AutoSize
    Get-Item -LiteralPath $shopZip, $vbZip, $addonJar | Select-Object Name,Length
}
finally {
    Remove-Item -LiteralPath $stageRoot -Recurse -Force -ErrorAction SilentlyContinue
}
