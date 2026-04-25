from pyexpat.errors import messages

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from app.services.llm_client import get_llm_client

router = APIRouter(prefix="/api/v1/llm",tags=["llm"])

class ChatRequest(BaseModel):
    message:str
    system_prompt:str = "你是一个教育分析师"

class ChatResponse(BaseModel):
    content: str
    model: str


@router.post("/chat", response_model=ChatResponse)
async def chat_test(req: ChatRequest):
    try:
        llm = get_llm_client()
        messages = [
            {"role":"system", "content":req.system_prompt},
            {"role":"user", "content":req.message},
        ]
        response = await llm.ainvoke(messages)
        return ChatResponse(content=response.content,model="qwen2.5-32b-instruct-q4_k_m")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM 调用失败：{str(e)}")