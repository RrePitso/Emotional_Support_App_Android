import unittest

from journal_memory import DAY_MILLISECONDS, score_entries


class JournalMemoryEvaluationTests(unittest.TestCase):
    def setUp(self):
        self.now_ms = 1_800_000_000_000

    def test_semantically_similar_entry_ranks_first(self):
        entries = [
            {
                "text": "I felt calm after taking a walk.",
                "timestamp": self.now_ms - 3 * DAY_MILLISECONDS,
                "emotionLabels": [{"label": "relief"}],
                "embedding": [1.0, 0.0],
            },
            {
                "text": "I cooked dinner.",
                "timestamp": self.now_ms,
                "emotionLabels": [{"label": "joy"}],
                "embedding": [0.0, 1.0],
            },
        ]

        ranked = score_entries([1.0, 0.0], ["relief"], entries, self.now_ms)

        self.assertEqual("I felt calm after taking a walk.", ranked[0]["text"])

    def test_recency_breaks_ties_for_equally_similar_entries(self):
        entries = [
            {
                "text": "Older reflection",
                "timestamp": self.now_ms - 60 * DAY_MILLISECONDS,
                "emotionLabels": [],
                "embedding": [1.0, 0.0],
            },
            {
                "text": "Recent reflection",
                "timestamp": self.now_ms,
                "emotionLabels": [],
                "embedding": [1.0, 0.0],
            },
        ]

        ranked = score_entries([1.0, 0.0], [], entries, self.now_ms)

        self.assertEqual("Recent reflection", ranked[0]["text"])

    def test_emotion_overlap_ranks_when_similarity_and_date_match(self):
        entries = [
            {
                "text": "A note about anxiety",
                "timestamp": self.now_ms,
                "emotionLabels": [{"label": "anxiety"}],
                "embedding": [1.0, 0.0],
            },
            {
                "text": "A note about joy",
                "timestamp": self.now_ms,
                "emotionLabels": [{"label": "joy"}],
                "embedding": [1.0, 0.0],
            },
        ]

        ranked = score_entries([1.0, 0.0], ["anxiety"], entries, self.now_ms)

        self.assertEqual("A note about anxiety", ranked[0]["text"])

    def test_entries_without_embeddings_are_not_retrieved(self):
        entries = [{"text": "Legacy note", "timestamp": self.now_ms, "embedding": None}]

        self.assertEqual([], score_entries([1.0, 0.0], [], entries, self.now_ms))


if __name__ == "__main__":
    unittest.main()
