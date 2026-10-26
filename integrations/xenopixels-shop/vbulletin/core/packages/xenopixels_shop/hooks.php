<?php

class xenopixels_shop_Hooks
{
	public static function hookFrontendBeforeOutput($params)
	{
		if (empty($params['pageHtml']) OR !is_string($params['pageHtml']))
		{
			return;
		}

		$shopUrl = self::shopUrl();
		if ($shopUrl === '')
		{
			return;
		}

		$userId = 0;
		try
		{
			$userId = (int) vB::getCurrentSession()->get('userid');
		}
		catch (Throwable $e)
		{
			$userId = 0;
		}

		if ($userId > 0)
		{
			$assertion = self::assertion($userId);
			if ($assertion === '')
			{
				$button = '<a class="h-left xpn-shop-nav-link" title="The XenoPixels shop login bridge is not configured." href="' . htmlspecialchars($shopUrl, ENT_QUOTES, 'UTF-8') . '">Shop</a>';
			}
			else
			{
				$button = '<form class="xpn-shop-nav-form" method="post" action="' . htmlspecialchars($shopUrl . '/auth/consume', ENT_QUOTES, 'UTF-8') . '">' .
					'<input type="hidden" name="assertion" value="' . htmlspecialchars($assertion, ENT_QUOTES, 'UTF-8') . '">' .
					'<button class="h-left xpn-shop-nav-link" type="submit">Shop</button></form>';
			}
		}
		else
		{
			$button = '<a class="h-left xpn-shop-nav-link" href="' . htmlspecialchars($shopUrl, ENT_QUOTES, 'UTF-8') . '">Shop</a>';
		}

		$navItem = '<li class="section-item js-shrink-event-child xpn-shop-nav-item" data-xpn-shop-nav="1">' . $button . '</li>';
		$style = '<style>.xpn-shop-nav-form{margin:0}.xpn-shop-nav-link{display:flex;align-items:center;min-height:42px;padding:0 18px;color:#ced5e6!important;background:linear-gradient(135deg,rgba(13,25,41,.82),rgba(16,36,58,.76))!important;border:1px solid rgba(109,222,255,.18)!important;border-radius:12px!important;box-shadow:inset 0 1px rgba(255,255,255,.045),0 4px 12px rgba(0,0,0,.12)!important;font:700 14px Arial,Helvetica,sans-serif;text-decoration:none!important;text-shadow:0 1px 2px rgba(0,0,0,.54);cursor:pointer}.xpn-shop-nav-link::before{content:"";width:15px;height:15px;margin-right:8px;background:currentColor;-webkit-mask:url("/tvseries/icons/globe.svg") center/contain no-repeat;mask:url("/tvseries/icons/globe.svg") center/contain no-repeat}.xpn-shop-nav-link:hover,.xpn-shop-nav-link:focus{color:#fff!important;border-color:rgba(93,228,255,.62)!important;box-shadow:0 0 14px rgba(93,228,255,.22)!important}.xpn-shop-nav-item{list-style:none}</style>';
		$html = $params['pageHtml'];
		if (strpos($html, 'data-xpn-shop-nav="1"') !== false)
		{
			return;
		}

		$pattern = '~(<ul\b(?=[^>]*\bclass=["\'][^"\']*\bchannel-tabbar-list\b)[^>]*>)(.*?)(</ul\s*>)~is';
		$updated = preg_replace_callback($pattern, static function ($matches) use ($navItem)
		{
			return $matches[1] . $matches[2] . $navItem . $matches[3];
		}, $html, 1, $count);
		if (!is_string($updated) OR $count !== 1)
		{
			return;
		}

		$headPosition = stripos($updated, '</head>');
		if ($headPosition !== false)
		{
			$updated = substr_replace($updated, $style, $headPosition, 0);
		}
		$params['pageHtml'] = $updated;
	}

	private static function shopUrl()
	{
		$url = getenv('XENOPIXELS_SHOP_URL');
		if (!is_string($url) OR $url === '')
		{
			$url = 'https://shop.xenopixelsnetwork.com';
		}
		$parts = parse_url($url);
		if (!is_array($parts) OR ($parts['scheme'] ?? '') !== 'https' OR strtolower($parts['host'] ?? '') !== 'shop.xenopixelsnetwork.com')
		{
			return '';
		}
		return rtrim($url, '/');
	}

	private static function assertion($userId)
	{
		$key = getenv('XENOPIXELS_SHOP_BRIDGE_SECRET');
		if (!is_string($key) OR $key === '')
		{
			$key = defined('XENOPIXELS_SHOP_BRIDGE_SECRET') ? XENOPIXELS_SHOP_BRIDGE_SECRET : '';
		}
		if (!is_string($key) OR strlen($key) < 32)
		{
			return '';
		}
		$adminIdsValue = getenv('XENOPIXELS_SHOP_ADMIN_USER_IDS');
		if (!is_string($adminIdsValue) OR trim($adminIdsValue) === '')
		{
			$adminIdsValue = defined('XENOPIXELS_SHOP_ADMIN_USER_IDS') ? XENOPIXELS_SHOP_ADMIN_USER_IDS : '';
		}
		$adminIds = array_filter(array_map('trim', explode(',', (string)$adminIdsValue)), 'ctype_digit');
		$claims = array(
			'uid' => (int)$userId,
			'iat' => time(),
			'nonce' => bin2hex(random_bytes(16)),
			'admin' => in_array((string)$userId, $adminIds, true),
		);
		$encoded = rtrim(strtr(base64_encode(json_encode($claims, JSON_THROW_ON_ERROR)), '+/', '-_'), '=');
		$signature = rtrim(strtr(base64_encode(hash_hmac('sha256', $encoded, $key, true)), '+/', '-_'), '=');
		return $encoded . '.' . $signature;
	}
}
