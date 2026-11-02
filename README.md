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

Emotion analysis and journal embeddings run locally using lightweight,
deterministic Python code; the backend does not download or load transformer
models. Emotion detection uses a small word/phrase lexicon, and journal retrieval
uses 256-dimensional hashed word and adjacent-word features. These are intended
for a small portfolio demo, not production-quality language understanding.
The chatbot tries Gemini first and Cloudflare Workers AI if Gemini is unavailable.
Set `GEMINI_API_KEY` in `.env`. For Cloudflare fallback, also set
`CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN`. Create the token in Cloudflare
with the minimum account permission needed to run Workers AI models.

`.env` is ignored by Git; `.env.example` contains placeholders only. At least one
chat provider must be configured. Provider free-tier availability and quotas can
change; check the provider's current terms and usage limits.

## Deploy the backend to Render

The root `render.yaml` is a Render Blueprint for a free Python web service.

1. Push this repository to GitHub.
2. In Render, choose **New → Blueprint**, connect the repository, and deploy the
   `render.yaml` Blueprint.
3. Enter `GEMINI_API_KEY` when prompted. To enable the optional Cloudflare
   fallback, add `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN` under the
   service's **Environment** settings. Keep these values in Render, not in the
   repository.
4. After the deploy succeeds, open the service URL ending in `.onrender.com`.
   The root page should return a JSON welcome message.
5. Build the APK with that HTTPS URL, including the final slash:

   ```bash
   bash ./gradlew assembleDebug -PbackendBaseUrl=https://YOUR-SERVICE.onrender.com/
   ```

Render's free service has limited memory and spins down when idle. This backend
does not load large local ML models, so its emotion analysis and embeddings avoid
the previous transformer-related memory spike. The first request after idle may
still take time while the service wakes. Chat replies still require a configured
Gemini or Cloudflare provider and are subject to that provider's availability and
quotas. The API is public, so keep provider credentials secret.

## Build and test the Android app

Install Android Studio and Android SDK Platform 35 (including Android SDK Build
Tools) using SDK Manager. Open this project in Android Studio and let Gradle sync;
Android Studio configures the local SDK path without committing it. Use JDK 21
for the Gradle build.

Start the backend on an address reachable from the phone. For a phone and
development computer on the same Wi-Fi, use the computer's LAN IP and allow port
8000 through its firewall. For an Android emulator, use `http://10.0.2.2:8000/`.
`10.0.2.2` is only the Android emulator's alias for the computer running the
emulator; it will not reach your computer from a physical device or an online APK
tester. For those, use a backend URL reachable by that device/test service. For a
remote Codespace or Render deployment, use its forwarded HTTPS port URL. Build
a debug APK with the corresponding backend URL:

```bash
bash ./gradlew assembleDebug -PbackendBaseUrl=http://192.168.1.10:8000/
```

Replace the example address with the backend address your phone can reach. The
APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install that APK
on the phone with Android Studio/ADB or transfer it to the device. Debug builds
permit cleartext HTTP for local testing; release builds keep the existing
network-security restrictions. Use HTTPS for a deployed backend and do not ship
a release build using plain HTTP.

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
they have been analyzed. Embeddings use the `hashing-embedding-v1` identifier;
entries with vectors from the previous transformer model are ignored for retrieval
until re-analyzed.

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
