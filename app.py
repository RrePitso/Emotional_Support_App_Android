from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import pipeline
from ai_providers import ProviderUnavailable, generate_with_fallback

# Emotion classifier pipeline (GoEmotions)
emotion_classifier = pipeline(
    "text-classification",
    model="monologg/bert-base-cased-goemotions-original",
    return_all_scores=True
)

# Emotion to intent mapping
emotion_to_intent = {
    'sadness': 'comfort', 'grief': 'comfort', 'anger': 'validate', 'annoyance': 'validate',
    'disappointment': 'reassure', 'embarrassment': 'reassure', 'remorse': 'forgive',
    'disgust': 'validate', 'fear': 'soothe', 'nervousness': 'soothe', 'confusion': 'clarify',
    'realization': 'acknowledge', 'caring': 'affirm', 'admiration': 'affirm',
    'approval': 'affirm', 'love': 'connect', 'joy': 'celebrate', 'amusement': 'celebrate',
    'excitement': 'celebrate', 'pride': 'celebrate', 'gratitude': 'acknowledge',
    'optimism': 'encourage', 'surprise': 'acknowledge', 'curiosity': 'encourage',
    'desire': 'encourage', 'disapproval': 'validate'
}

app = FastAPI()

class ChatRequest(BaseModel):
    user_input: str

class ChatResponse(BaseModel):
    response: str
    emotion: str
    intent: str

def detect_emotion(text):
    scores = emotion_classifier(text)[0]
    top_emotion = max(scores, key=lambda x: x['score'])['label']
    return top_emotion.lower()

def generate_response(user_input, emotion, intent):
    prompt = (
        "You are a kind, empathetic assistant.\n\n"
        f"User says: \"{user_input}\"\n"
        f"Detected emotion: {emotion}\n"
        f"Desired intent: {intent}\n"
        "Please respond empathetically."
    )
    return generate_with_fallback(prompt)

@app.get("/")
def read_root():
    return {"message": "Welcome to the Emotion-Support Chatbot API (Yi-6B + GoEmotions)"}

@app.post("/chat/", response_model=ChatResponse)
def chat(request: ChatRequest):
    user_input = request.user_input

    # Emotion detection
    emotion = detect_emotion(user_input)
    intent = emotion_to_intent.get(emotion, "acknowledge")

    try:
        response = generate_response(user_input, emotion, intent)
    except ProviderUnavailable as error:
        raise HTTPException(status_code=503, detail=str(error)) from error

    return {"response": response, "emotion": emotion, "intent": intent}
