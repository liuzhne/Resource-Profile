from contextlib import asynccontextmanager

from fastapi import FastAPI
from starlette.middleware.cors import CORSMiddleware

from app.api import agent, diagnostics, health, llm, rag, rag_upsert
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

    # 跨域（A4）：收敛为显式白名单 + 关 credentials（消除 *+credentials 非法组合）。
    cors_origins = [o.strip() for o in settings.CORS_ALLOW_ORIGINS.split(",") if o.strip()]
    app.add_middleware(
        CORSMiddleware,
        allow_origins=cors_origins,
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 注册路由
    app.include_router(health.router)
    app.include_router(llm.router)
    app.include_router(agent.router)
    app.include_router(rag.router)
    app.include_router(diagnostics.router)
    app.include_router(rag_upsert.router)

    return app

app = create_app()