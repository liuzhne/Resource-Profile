"""I-1：hybrid_retrieval 纯函数单测（tokenizer / BM25 / RRF / fuse_hits）。

无 IO / 无 Milvus，运行：python -m unittest tests.test_hybrid_retrieval
"""
import unittest

from app.services.hybrid_retrieval import BM25, fuse_hits, rrf_fuse, tokenize


class TestTokenize(unittest.TestCase):
    def test_mixed_cn_en(self):
        toks = tokenize("SCL-90 高数II")
        self.assertIn("scl", toks)
        self.assertIn("90", toks)
        self.assertIn("高", toks)
        self.assertIn("数", toks)
        self.assertIn("高数", toks)  # CJK bigram

    def test_empty(self):
        self.assertEqual(tokenize(""), [])
        self.assertEqual(tokenize(None), [])


class TestBM25(unittest.TestCase):
    def test_ranks_matching_doc_first(self):
        corpus = [
            tokenize("学生出勤率正常 情绪稳定"),
            tokenize("高数 II 连续挂科 学业预警"),
            tokenize("家庭经济困难 申请助学金"),
        ]
        bm = BM25(corpus)
        order = bm.rank(tokenize("高数 II 挂科"))
        self.assertEqual(order[0], 1)

    def test_empty_corpus_score_zero(self):
        bm = BM25([])
        self.assertEqual(bm.score(["x"], 0), 0.0)
        self.assertEqual(bm.rank(["x"]), [])


class TestRRF(unittest.TestCase):
    def test_consensus_top_wins(self):
        # 文档 2 在两个列表都靠前 → 融合后第一
        fused = rrf_fuse([[0, 2, 1], [2, 1, 0]])
        self.assertEqual(fused[0], 2)

    def test_union_of_ids(self):
        fused = rrf_fuse([[0, 1], [2]])
        self.assertCountEqual(fused, [0, 1, 2])


class TestFuseHits(unittest.TestCase):
    def test_empty_and_single_passthrough(self):
        self.assertEqual(fuse_hits("q", []), [])
        one = [{"content": "x"}]
        self.assertEqual(fuse_hits("q", one), one)

    def test_proper_noun_lifted(self):
        # dense 序把含精确专有名词的片段排在最后；BM25 + RRF 应把它顶上来
        hits = [
            {"chunk_id": "a", "content": "学生情绪稳定，社交活跃，无明显异常"},
            {"chunk_id": "b", "content": "学业整体平稳，需持续关注"},
            {"chunk_id": "c", "content": "高数 II 连续两次挂科，学业预警"},
        ]
        fused = fuse_hits("高数 II 挂科", hits)
        ids = [h["chunk_id"] for h in fused]
        # 含精确词的 c 应不再垫底（dense 索引 2 → 融合后名次提前）
        self.assertLess(ids.index("c"), 2)
        # 每条带 hybrid_rank
        self.assertEqual(fused[0]["hybrid_rank"], 0)


if __name__ == "__main__":
    unittest.main()
