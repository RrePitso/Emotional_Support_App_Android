import os
import unittest
from io import BytesIO
from unittest.mock import patch
from urllib.error import HTTPError

import ai_providers


class ProviderFallbackTests(unittest.TestCase):
    def test_retries_transient_http_error_once(self):
        unavailable = HTTPError("https://example.test", 503, "Unavailable", {}, None)
        response = BytesIO(b'{"ok": true}')
        with patch.object(ai_providers, "urlopen", side_effect=[unavailable, response]) as request:
            with patch.object(ai_providers.time, "sleep"):
                result = ai_providers._post_json(
                    "Test provider", "https://example.test", {}, {"prompt": "hello"}
                )

        self.assertEqual(result, {"ok": True})
        self.assertEqual(request.call_count, 2)

    def test_uses_cloudflare_after_gemini_fails(self):
        environment = {
            "GEMINI_API_KEY": "test-gemini-key",
            "CLOUDFLARE_ACCOUNT_ID": "test-account",
            "CLOUDFLARE_API_TOKEN": "test-cloudflare-token",
        }
        with patch.dict(os.environ, environment, clear=True):
            with patch.object(
                ai_providers,
                "_generate_gemini",
                side_effect=ai_providers.ProviderUnavailable("Gemini returned HTTP 503."),
            ):
                with patch.object(
                    ai_providers, "_generate_cloudflare", return_value="Fallback response"
                ) as fallback:
                    result = ai_providers.generate_with_fallback("Hello")

        self.assertEqual(result, "Fallback response")
        fallback.assert_called_once_with("Hello", "test-account", "test-cloudflare-token")

    def test_cloudflare_request_uses_configured_api_token(self):
        response = BytesIO(b'{"result": {"response": "Hello"}}')
        with patch.object(ai_providers, "urlopen", return_value=response) as request:
            ai_providers._generate_cloudflare(
                "Hello", "test-account", "test-cloudflare-token"
            )

        sent_request = request.call_args.args[0]
        self.assertEqual(
            "Bearer test-cloudflare-token",
            sent_request.get_header("Authorization"),
        )

    def test_reports_missing_provider_configuration(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ai_providers.ProviderUnavailable) as error:
                ai_providers.generate_with_fallback("Hello")

        self.assertIn("GEMINI_API_KEY is unset", str(error.exception))
        self.assertIn("CLOUDFLARE_API_TOKEN is unset", str(error.exception))


if __name__ == "__main__":
    unittest.main()