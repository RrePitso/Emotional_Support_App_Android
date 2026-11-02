import hashlib
import re
from collections import Counter


EMBEDDING_MODEL = "hashing-embedding-v1"

_EMOTION_WORDS = {
    "admiration": {"admire", "amazing", "brilliant", "impressive", "inspired", "respect"},
    "amusement": {"amused", "funny", "hilarious", "joke", "laugh", "laughing"},
    "anger": {"angry", "furious", "hate", "mad", "rage", "resentful"},
    "annoyance": {"annoyed", "bothered", "frustrated", "irritated", "ugh"},
    "approval": {"agree", "approved", "good", "great", "right", "well done"},
    "caring": {"care", "caring", "help", "kind", "support", "thoughtful"},
    "confusion": {"confused", "uncertain", "unclear", "unsure", "what"},
    "curiosity": {"curious", "explore", "how", "learn", "question", "wonder"},
    "desire": {"crave", "hope for", "long for", "need", "want", "wish"},
    "disappointment": {"disappointed", "let down", "regret", "unmet", "unsatisfied"},
    "disapproval": {"bad", "disagree", "disapprove", "wrong", "shouldn't"},
    "disgust": {"disgusted", "gross", "nasty", "repulsed", "sickening"},
    "embarrassment": {"awkward", "embarrassed", "humiliated", "mortified"},
    "excitement": {"can't wait", "eager", "excited", "thrilled", "pumped"},
    "fear": {"afraid", "danger", "fear", "scared", "terrified", "threat"},
    "gratitude": {"appreciate", "grateful", "gratitude", "thankful", "thanks"},
    "grief": {"bereaved", "grieving", "loss", "mourning", "passed away"},
    "joy": {"delighted", "happy", "joy", "joyful", "pleased", "wonderful"},
    "love": {"adore", "love", "loved", "loving", "cherish"},
    "nervousness": {"anxious", "nervous", "on edge", "panic", "worried", "worry"},
    "optimism": {"hopeful", "optimistic", "possible", "positive", "brighter"},
    "pride": {"accomplished", "earned", "proud", "succeeded", "achievement"},
    "realization": {"realized", "realise", "noticed", "understand", "understood"},
    "relief": {"relieved", "safe now", "finally", "phew", "better now"},
    "remorse": {"apologize", "ashamed", "fault", "guilty", "sorry"},
    "sadness": {"down", "heartbroken", "lonely", "sad", "unhappy", "upset"},
    "surprise": {"astonished", "unexpected", "shocked", "surprised", "wow"},
}
_TOKEN_PATTERN = re.compile(r"[a-z0-9']+")
_EMOTION_PHRASES = {
    emotion: tuple(
        (phrase, tuple(_TOKEN_PATTERN.findall(phrase)))
        for phrase in words
        if " " in phrase
    )
    for emotion, words in _EMOTION_WORDS.items()
}
_EMOTION_TOKENS = {
    emotion: words - {phrase for phrase in words if " " in phrase}
    for emotion, words in _EMOTION_WORDS.items()
}


def detect_emotions(text: str) -> list[dict[str, float | str]]:
    tokens = _TOKEN_PATTERN.findall(text.casefold())
    token_counts = Counter(tokens)
    scores = {
        emotion: float(sum(token_counts[word] for word in words))
        for emotion, words in _EMOTION_TOKENS.items()
    }

    for emotion, phrases in _EMOTION_PHRASES.items():
        for phrase, phrase_tokens in phrases:
            width = len(phrase_tokens)
            scores[emotion] += sum(
                tokens[index : index + width] == list(phrase_tokens)
                for index in range(len(tokens) - width + 1)
            )

    matches = [(emotion, score) for emotion, score in scores.items() if score > 0]
    if not matches:
        return [{"label": "neutral", "score": 1.0}]

    total = sum(score for _, score in matches)
    matches.sort(key=lambda result: result[1], reverse=True)
    return [
        {"label": emotion, "score": score / total}
        for emotion, score in matches[:5]
    ]


def create_embedding(text: str, dimensions: int = 256) -> list[float]:
    tokens = _TOKEN_PATTERN.findall(text.casefold())
    features = tokens + [
        f"{left}_{right}" for left, right in zip(tokens, tokens[1:])
    ]
    vector = [0.0] * dimensions
    for feature in features:
        digest = hashlib.blake2b(feature.encode("utf-8"), digest_size=8).digest()
        value = int.from_bytes(digest, "big")
        vector[value % dimensions] += 1.0 if value & (1 << 63) else -1.0

    norm = sum(value * value for value in vector) ** 0.5
    if norm:
        return [value / norm for value in vector]
    return vector
