import json
import os
import time
import logging
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from dotenv import load_dotenv

logger = logging.getLogger("uvicorn.error")
load_dotenv()

MAX_ATTEMPTS = 2
REQUEST_TIMEOUT_SECONDS = 20
RETRY_BASE_SECONDS = 0.5


class ProviderUnavailable(RuntimeError):
    pass


def _post_json(provider, url, headers, payload):
    request = Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    last_error = "network or timeout error"

    for attempt in range(MAX_ATTEMPTS):
        try:
            with urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                try:
                    return json.loads(response.read().decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    raise ProviderUnavailable(f"{provider} returned invalid JSON.") from None
        except HTTPError as error:
            status = error.code
            if status not in (408, 429) and not 500 <= status <= 599:
                raise ProviderUnavailable(f"{provider} returned HTTP {status}.") from None
            last_error = f"HTTP {status}"
        except (URLError, TimeoutError, OSError):
            last_error = "network or timeout error"

        if attempt + 1 < MAX_ATTEMPTS:
            time.sleep(RETRY_BASE_SECONDS * (2 ** attempt))

    raise ProviderUnavailable(
        f"{provider} failed after {MAX_ATTEMPTS} attempts ({last_error})."
    )


def _generate_gemini(prompt, api_key):
    model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    response = _post_json(
        "Gemini",
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        {"x-goog-api-key": api_key},
        {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": 0.7, "maxOutputTokens": 256},
        },
    )
    try:
        text = "".join(
            part["text"]
            for part in response["candidates"][0]["content"]["parts"]
            if "text" in part
        ).strip()
    except (KeyError, IndexError, TypeError):
        text = ""
    if not text:
        raise ProviderUnavailable("Gemini returned an empty response.")
    return text


def _generate_cloudflare(prompt, account_id, api_token):
    model = os.getenv(
        "CLOUDFLARE_AI_MODEL", "@cf/meta/llama-3.1-8b-instruct-fp8"
    )
    response = _post_json(
        "Cloudflare Workers AI",
        f"https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}",
        {"Authorization": f"Bearer {api_token}"},
        {
            "messages": [{"role": "user", "content": prompt}],
            "max_tokens": 256,
            "temperature": 0.7,
        },
    )
    try:
        text = response["result"]["response"].strip()
    except (AttributeError, KeyError, TypeError):
        text = ""
    if not text:
        raise ProviderUnavailable("Cloudflare Workers AI returned an empty response.")
    return text


def generate_with_fallback(prompt):
    failures = []
    gemini_key = os.getenv("GEMINI_API_KEY")
    if gemini_key:
        start = time.time()
        try:
            result = _generate_gemini(prompt, gemini_key)
            logger.info("Provider=Gemini ok in %.1fs", time.time() - start)
            return result
        except ProviderUnavailable as error:
            logger.warning("Gemini failed after %.1fs: %s", time.time() - start, error)
            failures.append(str(error))
    else:
        logger.warning("Gemini not configured")
        failures.append("Gemini is not configured (GEMINI_API_KEY is unset).")

    account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID")
    api_token = os.getenv("CLOUDFLARE_API_TOKEN")
    if account_id and api_token:
        start = time.time()
        try:
            result = _generate_cloudflare(prompt, account_id, api_token)
            logger.info("Provider=Cloudflare ok in %.1fs", time.time() - start)
            return result
        except ProviderUnavailable as error:
            logger.warning("Cloudflare failed after %.1fs: %s", time.time() - start, error)
            failures.append(str(error))
    else:
        logger.warning("Cloudflare not configured")
        failures.append(
            "Cloudflare Workers AI is not configured "
            "(CLOUDFLARE_ACCOUNT_ID or CLOUDFLARE_API_TOKEN is unset)."
        )

    raise ProviderUnavailable("AI response unavailable. " + " ".join(failures))