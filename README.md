# Emotional Support App

An Android journal and emotional-support chat app backed by a FastAPI service.
Journal entries stay in Android `SharedPreferences`; the configured backend
analyzes each new entry and returns emotion labels and a normalized text embedding.
When chatting, the app sends the latest 100 entries with embeddings and a seven-day
mood summary so the backend can retrieve relevant context.

## Backend setup

Python 3.10 or newer is recommended.

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Edit .env locally and add your Gemini key. Do not paste it into chat or commit it.
uvicorn app:app --host 0.0.0.0 --port 8000
```

The first request downloads the GoEmotions classifier and the
`sentence-transformers/all-MiniLM-L6-v2` embedding model from Hugging Face.
The chatbot tries Gemini first and Cloudflare Workers AI if Gemini is unavailable.
Set `GEMINI_API_KEY` in `.env`. For Cloudflare fallback, also set
`CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN`. Create the token in Cloudflare
with the minimum account permission needed to run Workers AI models.

`.env` is ignored by Git; `.env.example` contains placeholders only. At least one
provider must be configured. For a different embedding model, set
`EMBEDDING_MODEL` in `.env` before starting the service; stored vectors from a
different model should be regenerated before using them together.

## Build and test the Android app

Install Android Studio and Android SDK Platform 35 (including Android SDK Build
Tools) using SDK Manager. Open this project in Android Studio and let Gradle sync;
Android Studio configures the local SDK path without committing it. Use JDK 21
for the Gradle build.

Start the backend on an address reachable from the phone. For a phone and
development computer on the same Wi-Fi, use the computer's LAN IP and allow port
8000 through its firewall. For an Android emulator, use `http://10.0.2.2:8000/`.
For a remote Codespace, use its forwarded HTTPS port URL. Build a debug APK with
the corresponding backend URL:

```bash
bash ./gradlew assembleDebug -PbackendBaseUrl=http://192.168.1.10:8000/
```

Replace the example address with the backend address your phone can reach. The
APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install that APK
on the phone with Android Studio/ADB or transfer it to the device. Debug builds
permit cleartext HTTP for local testing; release builds keep the existing
network-security restrictions. Do not ship a release build using plain HTTP.

Provider keys stay on the backend and are never included in the APK. The app
contains only the backend URL.

## Journal memory flow

1. Saving an entry writes it to local storage immediately.
2. The app posts the text to `POST /journal/analyze`; if analysis succeeds, it
   stores the returned emotion scores and embedding alongside the entry. A backend
   failure does not discard the journal text and is shown in the journal screen.
3. Chat requests include the latest 100 entries that have embeddings. The backend
   scores them using cosine similarity (60%), recency (25%, 30-day decay), and
   emotion overlap (15%), then includes up to three relevant excerpts in the
   response prompt.
4. The prompt also contains a seven-day detected-emotion trend and supportive
   response and safety guidance. Retrieved journal text is treated as untrusted
   context, not as instructions.

Journal text is sent to the configured backend for save-time analysis. During chat,
the app sends embedded entries and mood trends to that backend; the selected
excerpts and current message are then sent to whichever configured AI provider
answers the request. Avoid entering information you do not want processed by
those services.

Older journal entries without metadata remain readable and are not retrieved until
they have been analyzed.

## Tests

```bash
python -m unittest test_ai_providers test_journal_memory
bash ./gradlew testDebugUnitTest
```

The retrieval evaluation cases check semantic similarity, recency, emotion overlap,
and exclusion of entries without embeddings.

## Interview talking points

- **Provider resilience:** Gemini and Cloudflare Workers AI use a shared JSON
  request helper with bounded retries, exponential backoff, and explicit
  unavailable-provider errors.
- **Privacy and persistence:** journal text is stored locally first; backend
  analysis enriches each entry without making a backend outage lose the entry.
- **Retrieval:** normalized embeddings, recency decay, and emotion overlap combine
  into a transparent score before a small number of past entries enter the prompt.
- **Safety-aware prompting:** mood history is contextual rather than diagnostic;
  the assistant is instructed to avoid diagnoses and to direct imminent-risk
  situations toward immediate human or emergency support.
- **Evaluation:** a small deterministic test set verifies ranking behavior without
  requiring model downloads or live provider credentials.
