from math import exp, sqrt
from typing import Any, Mapping, Sequence


DAY_MILLISECONDS = 24 * 60 * 60 * 1000
RECENCY_DECAY_DAYS = 30


def _cosine_similarity(left: Sequence[float], right: Sequence[float]) -> float:
    if not left or len(left) != len(right):
        return 0.0

    dot_product = sum(a * b for a, b in zip(left, right))
    left_norm = sqrt(sum(value * value for value in left))
    right_norm = sqrt(sum(value * value for value in right))
    if not left_norm or not right_norm:
        return 0.0
    return max(-1.0, min(1.0, dot_product / (left_norm * right_norm)))


def _emotion_labels(values: Sequence[Any]) -> set[str]:
    labels = set()
    for value in values:
        if isinstance(value, Mapping):
            label = value.get("label")
        else:
            label = getattr(value, "label", None)
        if label:
            labels.add(str(label).lower())
    return labels


def score_entries(
    query_embedding: Sequence[float],
    query_emotions: Sequence[str],
    entries: Sequence[Mapping[str, Any]],
    now_ms: int,
    limit: int = 3,
) -> list[Mapping[str, Any]]:
    query_labels = {label.lower() for label in query_emotions}
    scored = []

    for entry in entries:
        embedding = entry.get("embedding")
        if not embedding:
            continue

        similarity = (_cosine_similarity(query_embedding, embedding) + 1.0) / 2.0
        age_days = max(0.0, (now_ms - int(entry.get("timestamp", 0))) / DAY_MILLISECONDS)
        recency = exp(-age_days / RECENCY_DECAY_DAYS)
        entry_labels = _emotion_labels(
            entry.get("emotionLabels") or entry.get("emotion_labels") or []
        )
        union = query_labels | entry_labels
        emotion_match = len(query_labels & entry_labels) / len(union) if union else 0.0

        score = 0.60 * similarity + 0.25 * recency + 0.15 * emotion_match
        scored.append((score, int(entry.get("timestamp", 0)), entry))

    scored.sort(key=lambda result: (result[0], result[1]), reverse=True)
    return [entry for _, _, entry in scored[:limit]]
