from openai import embeddings
from sentence_transformers import SentenceTransformer
import logging

logger = logging.getLogger(__name__)

class EmbeddingService:
    """
    BAAI/bge-large-zh-v1.5 本地 Embedding 服务
    维度 1024，单例模式避免重复加载模型
    """
    _instance = None
    _model = None
    _model_name = "默认模型名"

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        if self._model is not None:
            return
        logger.info("正在加载 Embedding 模型")
        self._model = SentenceTransformer(self._model_name)
        logger.info("模型加载完成,维度为 %d",self._model.get_sentence_embedding_dimension())

    def embed(self,texts: list[str]) -> list[list[float]]:
        """
        批量向量化，返回归一化后的 1024 维向量
        """
        if isinstance(texts, str):
            texts = [texts]
            embeddings = self._model.encode(texts,normalize_embeddings=True,batch_size=32,show_progress_bar=False)
        return embeddings.tolist()