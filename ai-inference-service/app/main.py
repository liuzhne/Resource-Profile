from contextlib import asynccontextmanager

from fastapi import FastAPI
from starlette.middleware.cors import CORSMiddleware

from app.api import health, llm
from app.core.config import settings

@asynccontextmanager
async def lifespan(app: FastAPI):
    print(f"   {settings.APP_NAME} v{settings.APP_VERSION} 已启动")
    print(f"   LLM Base URL: {settings.LLM_BASE_URL}")
    print(f"   Milvus Host:  {settings.MILVUS_HOST}:{settings.MILVUS_PORT}")

    yield

    print(f"  {settings.APP_NAME} 正在关闭...")

def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.APP_NAME,
        version=settings.APP_VERSION,
        description="EduCare AI 推理服务 - llama.cpp & milvus",
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
    )

    # 跨域
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 注册路由
    app.include_router(health.router)
    app.include_router(llm.router)

    return app

app = create_app()