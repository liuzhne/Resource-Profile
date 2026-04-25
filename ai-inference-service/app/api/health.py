from fastapi import APIRouter,status
from pydantic import BaseModel

router = APIRouter(prefix="/health",tags=["health"])

class HealthResponse(BaseModel):
    status: str
    version: str

@router.get("",response_model=HealthResponse,
            status_code=status.HTTP_200_OK)
async def health_check():
    return HealthResponse(status="OK",version="1.0")