#!/usr/bin/env python3
"""最小桩 LLM（OpenAI 兼容）—— 替代 llama.cpp，用于无 GPU 环境真跑 AgentLoop HTTP 链路。

监听 :8091，对 POST /v1/chat/completions 返回一个 ReAct `final_answer` 轮次（双 JSON schema），
使 AgentLoop 一轮即收口、无需 MCP 工具，从而验证：
  Spring 启动 → 控制器 → ChatClient/拦截器链 → HTTP → 解析 final_answer → 落库 → COMPLETED。

不是真实推理，只为打通"活的 HTTP 端到端"代码路径（活模型质量验证见 E2E_RUNBOOK）。
用法：python3 scripts/mock_llm_server.py [port]
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FINAL_INNER = json.dumps({
    # 用 low：doExecuteAgentLoop 低/无风险短路直接 COMPLETED（risk/plan 仍落库），
    # 避开合规审核阶段对 Python ai-inference 的 Feign 调用，使最小真跑无需起 Python。
    "risk_analysis": {
        "risk_level": "low", "risk_score": 35, "primary_risk_type": "学业滑坡",
        "root_cause_analysis": "单科成绩波动，出勤略有下滑，暂未形成趋势",
        "key_indicators": ["GPA:2.6", "单科预警:高数 II"],
        "recommended_intervention_types": ["学业辅导"],
        "urgency_reason": "潜在风险，纳入常规关注",
    },
    "intervention_plan": {
        "report_title": "学业帮扶方案", "summary": "一对一补考辅导 + 学业规划",
        "immediate_actions": [{"action": "安排补考辅导", "owner": "学业导师", "deadline": "2周内"}],
        "long_term_plan": [], "talk_outline": "先共情再帮扶", "resources": [], "references": [],
    },
}, ensure_ascii=False)

REACT_TURN = json.dumps({"thought": "数据已足够，直接给出结论", "final_answer": FINAL_INNER}, ensure_ascii=False)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.endswith("/models"):
            self._send({"object": "list", "data": [{"id": "mock-qwen", "object": "model"}]})
        else:
            self._send({"status": "ok"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        _ = self.rfile.read(length)  # 读掉请求体（含 messages / cache_prompt）
        sys.stderr.write("[mock-llm] /v1/chat/completions hit -> 返回 final_answer\n")
        self._send({
            "id": "mock-cmpl", "object": "chat.completion", "created": 0, "model": "mock-qwen",
            "choices": [{"index": 0, "message": {"role": "assistant", "content": REACT_TURN},
                         "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 50, "completion_tokens": 120, "total_tokens": 170},
            "timings": {"prompt_n": 50, "predicted_n": 120, "cached_n": 0, "prompt_ms": 12.0},
        })


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8091
    print(f"[mock-llm] listening on :{port}  (POST /v1/chat/completions -> ReAct final_answer)")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
