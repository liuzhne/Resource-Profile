"""H-5：memory_store 纯函数 + memory_adapter 校验/降级单测。

不依赖 Redis：纯函数直接验；adapter 的校验分支在 Redis 不可用时也能跑（get_redis 返回 None → 降级空结果）。
运行：python -m unittest tests.test_memory_store
"""
import asyncio
import unittest

from app.services import memory_store
from app.mcp import memory_adapter


class TestMemoryStorePure(unittest.TestCase):
    def test_keys(self):
        self.assertEqual(memory_store.working_key("7"), "edu:mem:working:7")
        self.assertEqual(memory_store.episode_key("7"), "edu:mem:episode:7")
        self.assertEqual(memory_store.semantic_key("7"), "edu:mem:semantic:7")

    def test_build_episode_defaults(self):
        ep = memory_store.build_episode("  挂科预警  ", None)
        self.assertEqual(ep["text"], "挂科预警")
        self.assertEqual(ep["metadata"], {})
        self.assertIsInstance(ep["ts"], float)

    def test_build_episode_explicit_ts_and_meta(self):
        ep = memory_store.build_episode("x", {"task_id": 9}, ts=123.0)
        self.assertEqual(ep["ts"], 123.0)
        self.assertEqual(ep["metadata"], {"task_id": 9})

    def test_filter_episodes_substring(self):
        eps = [
            {"text": "高数挂科", "metadata": {}},
            {"text": "出勤正常", "metadata": {"type": "attendance"}},
        ]
        self.assertEqual(len(memory_store.filter_episodes(eps, "挂科")), 1)
        self.assertEqual(len(memory_store.filter_episodes(eps, "attendance")), 1)  # 命中 metadata
        self.assertEqual(len(memory_store.filter_episodes(eps, "")), 2)            # 空 query 原样
        self.assertEqual(len(memory_store.filter_episodes(eps, "不存在")), 0)


class TestMemoryAdapterValidation(unittest.TestCase):
    def test_recall_empty_sid(self):
        r = asyncio.run(memory_adapter.recall_student_history("  "))
        self.assertFalse(r["ok"])

    def test_save_empty_sid_or_episode(self):
        self.assertFalse(asyncio.run(memory_adapter.save_episode("", "x"))["ok"])
        self.assertFalse(asyncio.run(memory_adapter.save_episode("7", "   "))["ok"])

    def test_summarize_empty_sid(self):
        self.assertFalse(asyncio.run(memory_adapter.summarize_long_term(""))["ok"])

    def test_clamp_top_k(self):
        self.assertEqual(memory_adapter._clamp_top_k(0), 1)
        self.assertEqual(memory_adapter._clamp_top_k(999), 50)
        self.assertEqual(memory_adapter._clamp_top_k("bad"), 10)
        self.assertEqual(memory_adapter._clamp_top_k(8), 8)

    def test_recall_with_redis_down_degrades(self):
        # Redis 不可用 → list_episodes 返回 []，整体 ok=True 空结果，不抛异常
        r = asyncio.run(memory_adapter.recall_student_history("7", top_k=5))
        self.assertTrue(r["ok"])
        self.assertEqual(r["episodes"], [])
        self.assertIsNone(r["semantic"])

    def test_summarize_no_episodes(self):
        r = asyncio.run(memory_adapter.summarize_long_term("7"))
        self.assertTrue(r["ok"])
        self.assertIsNone(r["summary"])

    def test_rule_based_summary(self):
        eps = [{"text": "a"}, {"text": "b"}, {"text": ""}]
        s = memory_adapter._rule_based_summary(eps)
        self.assertTrue(s["degraded"])
        self.assertEqual(s["facts"], ["a", "b"])


if __name__ == "__main__":
    unittest.main()
