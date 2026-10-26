<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

final class PaymentProvider
{
    public function __construct(private readonly array $config) {}

    public function createCheckout(string $provider, array $order, array $product): array
    {
        $base = rtrim((string)($this->config['base_url'] ?? ''), '/');
        if (!str_starts_with($base, 'https://')) throw new HttpError(503, 'https_required', 'Checkout requires an HTTPS base URL.');
        if ($provider === 'stripe') return $this->stripe($base, $order, $product);
        if ($provider === 'paypal') return $this->paypal($base, $order, $product);
        throw new HttpError(400, 'invalid_payment_method', 'Choose Stripe or PayPal.');
    }

    public function verifyStripeWebhook(string $raw, string $signatureHeader): array
    {
        $secret = (string)($this->config['stripe_webhook_secret'] ?? '');
        if (strlen($secret) < 16 || strlen($raw) > 262144) throw new HttpError(503, 'stripe_not_configured', 'Stripe webhook is not configured.');
        $parts = [];
        foreach (explode(',', $signatureHeader) as $part) {
            [$key, $value] = array_pad(explode('=', trim($part), 2), 2, '');
            if ($key !== '' && $value !== '') $parts[$key][] = $value;
        }
        $timestamp = (int)($parts['t'][0] ?? 0);
        if ($timestamp < 1 || abs(time() - $timestamp) > 300) throw new HttpError(400, 'stripe_signature_expired', 'Stripe webhook signature is expired.');
        $expected = hash_hmac('sha256', $timestamp . '.' . $raw, $secret);
        $valid = false;
        foreach ($parts['v1'] ?? [] as $candidate) $valid = $valid || hash_equals($expected, $candidate);
        if (!$valid) throw new HttpError(401, 'stripe_signature_invalid', 'Stripe webhook signature is invalid.');
        return self::decodeEvent($raw);
    }

    public function verifyPayPalWebhook(string $raw, array $headers): array
    {
        $webhookId = (string)($this->config['paypal_webhook_id'] ?? '');
        if ($webhookId === '' || strlen($raw) > 262144) throw new HttpError(503, 'paypal_not_configured', 'PayPal webhook is not configured.');
        $event = self::decodeEvent($raw);
        $token = $this->paypalToken();
        $payload = [
            'auth_algo' => $headers['paypal-auth-algo'] ?? '',
            'cert_url' => $headers['paypal-cert-url'] ?? '',
            'transmission_id' => $headers['paypal-transmission-id'] ?? '',
            'transmission_sig' => $headers['paypal-transmission-sig'] ?? '',
            'transmission_time' => $headers['paypal-transmission-time'] ?? '',
            'webhook_id' => $webhookId,
            'webhook_event' => $event,
        ];
        $verified = $this->requestJson('https://api-m.paypal.com/v1/notifications/verify-webhook-signature', $payload, ['Authorization: Bearer ' . $token]);
        if (($verified['verification_status'] ?? '') !== 'SUCCESS') throw new HttpError(401, 'paypal_signature_invalid', 'PayPal webhook signature is invalid.');
        return $event;
    }

    public function capturePayPalOrder(string $orderId): array
    {
        if (!preg_match('/^[A-Z0-9]{1,64}$/', $orderId)) throw new HttpError(400, 'invalid_paypal_order', 'PayPal order identifier is invalid.');
        return $this->requestJson('https://api-m.paypal.com/v2/checkout/orders/' . rawurlencode($orderId) . '/capture', (object)[], [
            'Authorization: Bearer ' . $this->paypalToken(), 'PayPal-Request-Id: xenopixels-capture-' . $orderId,
        ]);
    }

    private function stripe(string $base, array $order, array $product): array
    {
        $secret = (string)($this->config['stripe_secret_key'] ?? '');
        if ($secret === '' || !extension_loaded('curl')) throw new HttpError(503, 'stripe_not_configured', 'Stripe checkout is not configured.');
        $form = [
            'mode' => 'payment', 'success_url' => $base . '/orders?checkout=success', 'cancel_url' => $base . '/orders?checkout=cancelled',
            'client_reference_id' => $order['public_id'], 'metadata[order_id]' => $order['public_id'],
            'line_items[0][quantity]' => '1', 'line_items[0][price_data][currency]' => strtolower($order['currency']),
            'line_items[0][price_data][unit_amount]' => (string)$order['amount_minor'],
            'line_items[0][price_data][product_data][name]' => (string)$product['title'],
        ];
        $response = $this->requestForm('https://api.stripe.com/v1/checkout/sessions', $form, ['Authorization: Bearer ' . $secret, 'Idempotency-Key: xenopixels-' . $order['public_id']]);
        if (!is_string($response['url'] ?? null) || !str_starts_with($response['url'], 'https://checkout.stripe.com/')
            || !is_string($response['id'] ?? null)) throw new HttpError(502, 'stripe_checkout_failed', 'Stripe did not return a hosted checkout session.');
        return ['url' => $response['url'], 'provider_order_id' => $response['id']];
    }

    private function paypal(string $base, array $order, array $product): array
    {
        $token = $this->paypalToken();
        $major = number_format(((int)$order['amount_minor']) / 100, 2, '.', '');
        $body = ['intent' => 'CAPTURE', 'purchase_units' => [[
            'custom_id' => $order['public_id'], 'description' => mb_substr((string)$product['title'], 0, 120),
            'amount' => ['currency_code' => strtoupper($order['currency']), 'value' => $major],
        ]], 'application_context' => ['return_url' => $base . '/api/v1/payment/paypal/capture', 'cancel_url' => $base . '/orders?checkout=cancelled']];
        $response = $this->requestJson('https://api-m.paypal.com/v2/checkout/orders', $body, ['Authorization: Bearer ' . $token, 'PayPal-Request-Id: xenopixels-' . $order['public_id']]);
        foreach ($response['links'] ?? [] as $link) {
            if (($link['rel'] ?? '') === 'approve' && is_string($link['href'] ?? null) && str_starts_with($link['href'], 'https://www.paypal.com/') && is_string($response['id'] ?? null)) {
                return ['url' => $link['href'], 'provider_order_id' => $response['id']];
            }
        }
        throw new HttpError(502, 'paypal_checkout_failed', 'PayPal did not return a hosted approval URL.');
    }

    private function paypalToken(): string
    {
        $client = (string)($this->config['paypal_client_id'] ?? '');
        $secret = (string)($this->config['paypal_client_secret'] ?? '');
        if ($client === '' || $secret === '' || !extension_loaded('curl')) throw new HttpError(503, 'paypal_not_configured', 'PayPal checkout is not configured.');
        $response = $this->requestForm('https://api-m.paypal.com/v1/oauth2/token', ['grant_type' => 'client_credentials'], ['Authorization: Basic ' . base64_encode($client . ':' . $secret)]);
        if (!is_string($response['access_token'] ?? null)) throw new HttpError(502, 'paypal_auth_failed', 'PayPal authentication failed.');
        return $response['access_token'];
    }

    private function requestForm(string $url, array $form, array $headers): array
    {
        return $this->request($url, http_build_query($form), array_merge($headers, ['Content-Type: application/x-www-form-urlencoded']));
    }

    private function requestJson(string $url, array|object $body, array $headers): array
    {
        return $this->request($url, json_encode($body, JSON_THROW_ON_ERROR), array_merge($headers, ['Content-Type: application/json']));
    }

    private function request(string $url, string $body, array $headers): array
    {
        $handle = curl_init($url);
        if ($handle === false) throw new HttpError(503, 'payment_transport_unavailable', 'Payment transport is unavailable.');
        curl_setopt_array($handle, [CURLOPT_POST => true, CURLOPT_POSTFIELDS => $body, CURLOPT_HTTPHEADER => $headers, CURLOPT_RETURNTRANSFER => true, CURLOPT_CONNECTTIMEOUT => 5, CURLOPT_TIMEOUT => 15, CURLOPT_PROTOCOLS => CURLPROTO_HTTPS, CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTPS]);
        $response = curl_exec($handle);
        $status = (int)curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        curl_close($handle);
        if (!is_string($response) || $status < 200 || $status >= 300) throw new HttpError(502, 'payment_provider_unavailable', 'Payment provider request failed.');
        return self::decodeEvent($response);
    }

    private static function decodeEvent(string $raw): array
    {
        try { $value = json_decode($raw, true, 20, JSON_THROW_ON_ERROR); }
        catch (\JsonException) { throw new HttpError(400, 'invalid_provider_event', 'Payment provider payload is invalid.'); }
        if (!is_array($value) || array_is_list($value)) throw new HttpError(400, 'invalid_provider_event', 'Payment provider payload is invalid.');
        return $value;
    }
}
