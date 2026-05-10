from app.services.embedding_service import EmbeddingService
from pymilvus import Collection

class RAGService:

    COLLECTION_MAP = {
        "case_db": "edu_cases",
        "psychology_db": "edu_psychology",
        "policy_db": "edu_policies",
        "success_db": "edu_success"
    }

    def __init__(self):
        self.embedding_service = EmbeddingService()

    def retrieve(self, query: str, top_k: int = 5) -> list[dict]:
        """
        多路召回： 从 4 个知识库中检索出最相关的片段
        """
        # 查询向量化
        query_vec = self.embedding_service.embed([query])[0]

        all_results = []

        for source_db, coll_name in self.COLLECTION_MAP.items():
            try:
                collection = Collection(coll_name)
                collection.load()


