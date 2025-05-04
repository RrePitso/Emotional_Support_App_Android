from fastapi import FastAPI
from pydantic import BaseModel
from transformers import pipeline, AutoTokenizer, AutoModelForCausalLM
import torch
from huggingface_hub import login

# Login to HuggingFace (for private model access if needed)
login(token="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")  # Replace with env var for security

# 1. Emotion Classifier Pipeline (GoEmotions)
emotion_classifier = pipeline(
    "text-classification",
    model="monologg/bert-base-cased-goemotions-original",
    return_all_scores=True
)

# 2. Load Yi-6B-Chat Model and Tokenizer (update with your actual model)
chat_model_name = "NousResearch/Yi-6B-Chat"  # Or your fine-tuned version
tokenizer = AutoTokenizer.from_pretrained(chat_model_name)
model = AutoModelForCausalLM.from_pretrained(
    chat_model_name,
    torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
    device_map="auto"
)

# 3. Emotion to Intent Mapping
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

# 4. FastAPI App
app = FastAPI()

# 5. Request and Response Models
class ChatRequest(BaseModel):
    user_input: str

class ChatResponse(BaseModel):
    response: str
    emotion: str
    intent: str

# 6. Helper Functions
def detect_emotion(text):
    scores = emotion_classifier(text)[0]
    top_emotion = max(scores, key=lambda x: x['score'])['label']
    return top_emotion.lower()

def generate_response(user_input, emotion, intent):
    chat_prompt = (
        "<|im_start|>system\n"
        "You are a kind, empathetic assistant.\n"
        "<|im_end|>\n"
        "<|im_start|>user\n"
        f"User says: \"{user_input}\"\n"
        f"Detected emotion: {emotion}\n"
        f"Desired intent: {intent}\n"
        "Please respond empathetically.\n"
        "<|im_end|>\n"
        "<|im_start|>assistant\n"
    )
    inputs = tokenizer(chat_prompt, return_tensors="pt").to(model.device)
    outputs = model.generate(
        inputs["input_ids"],
        max_new_tokens=100,
        temperature=0.7,
        top_p=0.9,
        do_sample=True,
        pad_token_id=tokenizer.eos_token_id,
    )
    generated = tokenizer.decode(outputs[0], skip_special_tokens=True)
    return generated.split("assistant\n", 1)[-1].strip()

# 7. API Endpoints
@app.get("/")
def read_root():
    return {"message": "Welcome to the Emotion-Support Chatbot API (Yi-6B + GoEmotions)"}

@app.post("/chat/", response_model=ChatResponse)
def chat(request: ChatRequest):
    user_input = request.user_input

    # Emotion detection
    emotion = detect_emotion(user_input)
    intent = emotion_to_intent.get(emotion, "acknowledge")

    # Generate chatbot response
    response = generate_response(user_input, emotion, intent)

    return {"response": response, "emotion": emotion, "intent": intent}
