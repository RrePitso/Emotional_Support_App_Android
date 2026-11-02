import unittest

from lightweight_nlp import EMBEDDING_MODEL, create_embedding, detect_emotions


class LightweightNlpTests(unittest.TestCase):
    def test_detects_emotion_words_and_returns_normalized_scores(self):
        emotions = detect_emotions("I feel nervous and worried about tomorrow.")

        self.assertEqual("nervousness", emotions[0]["label"])
        self.assertEqual(1.0, sum(emotion["score"] for emotion in emotions))

    def test_returns_neutral_for_text_without_lexicon_matches(self):
        self.assertEqual(
            [{"label": "neutral", "score": 1.0}],
            detect_emotions("The notebook is on the table."),
        )

    def test_embedding_is_deterministic_and_normalized(self):
        text = "I felt anxious before the meeting."
        embedding = create_embedding(text)

        self.assertEqual(256, len(embedding))
        self.assertEqual(embedding, create_embedding(text))
        self.assertAlmostEqual(1.0, sum(value * value for value in embedding) ** 0.5)
        self.assertEqual("hashing-embedding-v1", EMBEDDING_MODEL)

    def test_empty_text_returns_zero_vector(self):
        self.assertEqual([0.0] * 256, create_embedding(""))


if __name__ == "__main__":
    unittest.main()
