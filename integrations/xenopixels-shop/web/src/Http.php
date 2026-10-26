<?php
declare(strict_types=1);

namespace XenoPixels\Shop;

final class Http
{
    public static function jsonBody(int $maxBytes): array
    {
        $length = (int)($_SERVER['CONTENT_LENGTH'] ?? 0);
        if ($length < 0 || $length > $maxBytes) {
            throw new HttpError(413, 'body_too_large', 'Request body exceeds the configured limit.');
        }
        $body = file_get_contents('php://input', false, null, 0, $maxBytes + 1);
        if ($body === false || strlen($body) > $maxBytes) {
            throw new HttpError(413, 'body_too_large', 'Request body exceeds the configured limit.');
        }
        if ($body === '') return [];
        try {
            $decoded = json_decode($body, true, 16, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new HttpError(400, 'invalid_json', 'Request body must be valid JSON.');
        }
        if (!is_array($decoded) || array_is_list($decoded)) {
            throw new HttpError(400, 'invalid_json_shape', 'Request body must be a JSON object.');
        }
        return $decoded;
    }

    public static function respond(int $status, array $payload): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        header('X-Content-Type-Options: nosniff');
        echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
        exit;
    }
}

final class HttpError extends \RuntimeException
{
    public function __construct(public readonly int $status, public readonly string $errorCode, string $message)
    {
        parent::__construct($message);
    }
}
