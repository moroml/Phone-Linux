"""Runs the phone-mcp asset script against a mock phone API. Run: python -I test_phone_mcp.py"""
import base64
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "main", "assets", "phone-linux", "bin", "phone-mcp")
PNG = b"\x89PNG\r\n\x1a\nfake"


class Phone(BaseHTTPRequestHandler):
	seen = []
	fail = None  # (status, error message) to answer every request with

	def log_message(self, *a):
		pass

	def _send(self, status, ctype, body):
		self.send_response(status)
		self.send_header("Content-Type", ctype)
		self.send_header("Content-Length", str(len(body)))
		self.end_headers()
		self.wfile.write(body)

	def _handle(self):
		n = int(self.headers.get("Content-Length") or 0)
		body = json.loads(self.rfile.read(n)) if n else None
		Phone.seen.append((self.command, self.path, self.headers.get("Authorization"), body))
		if Phone.fail:
			return self._send(Phone.fail[0], "application/json", json.dumps({"error": Phone.fail[1]}).encode())
		if self.path == "/v1/screenshot":
			return self._send(200, "image/png", PNG)
		if self.path == "/v1/ui":
			return self._send(200, "application/json; charset=utf-8", b'{"package":"x","root":{}}')
		self._send(200, "application/json", b'{"ok":true}')

	do_GET = do_POST = _handle


class PhoneMcpTest(unittest.TestCase):
	@classmethod
	def setUpClass(cls):
		cls.server = HTTPServer(("127.0.0.1", 0), Phone)
		threading.Thread(target=cls.server.serve_forever, daemon=True).start()
		cls.home = tempfile.mkdtemp()
		os.makedirs(os.path.join(cls.home, ".phone-linux"))

	@classmethod
	def tearDownClass(cls):
		cls.server.shutdown()

	def setUp(self):
		Phone.seen.clear()
		Phone.fail = None
		self.write_key("plk_testkey")

	def write_key(self, key):
		path = os.path.join(self.home, ".phone-linux", "phone-api-key")
		if key is None:
			if os.path.exists(path):
				os.remove(path)
		else:
			with open(path, "w") as f:
				f.write(key + "\n")

	def run_mcp(self, messages, url=None, extra_env=None):
		env = {k: v for k, v in os.environ.items() if not k.startswith("PHONE_API")}
		env.update(HOME=self.home, USERPROFILE=self.home, PHONE_API_URL=url or "http://127.0.0.1:%d" % self.server.server_port)
		env.update(extra_env or {})
		p = subprocess.run([sys.executable, "-I", SCRIPT], input="\n".join(json.dumps(m) for m in messages) + "\n",
			capture_output=True, text=True, env=env, timeout=60)
		self.assertEqual(p.returncode, 0, p.stderr)
		return [json.loads(l) for l in p.stdout.splitlines()]

	def call(self, name, args=None, **kw):
		out = self.run_mcp([{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": name, "arguments": args or {}}}], **kw)
		self.assertEqual(len(out), 1)
		return out[0]

	def test_header_and_marker(self):
		with open(SCRIPT, "rb") as f:
			head = f.read(600)
		self.assertTrue(head.startswith(b"#!/data/data/com.termux/files/usr/bin/python3\n# phone-linux:"))
		self.assertNotIn(b"\r", open(SCRIPT, "rb").read())

	def test_initialize_echoes_client_version_and_ignores_notifications(self):
		out = self.run_mcp([
			{"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-03-26", "capabilities": {}}},
			{"jsonrpc": "2.0", "method": "notifications/initialized"},
			{"jsonrpc": "2.0", "id": 2, "method": "initialize", "params": {}},
			{"jsonrpc": "2.0", "id": 3, "method": "ping"},
			{"jsonrpc": "2.0", "id": 4, "method": "bogus"},
		])
		self.assertEqual([o["id"] for o in out], [1, 2, 3, 4])
		self.assertEqual(out[0]["result"]["protocolVersion"], "2025-03-26")
		self.assertEqual(out[0]["result"]["capabilities"], {"tools": {}})
		self.assertEqual(out[1]["result"]["protocolVersion"], "2025-06-18")
		self.assertEqual(out[2]["result"], {})
		self.assertEqual(out[3]["error"]["code"], -32601)

	def test_tools_list(self):
		out = self.run_mcp([{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}])
		tools = {t["name"]: t for t in out[0]["result"]["tools"]}
		self.assertEqual(set(tools), {"phone_screenshot", "phone_ui_tree", "phone_tap", "phone_swipe", "phone_type", "phone_key", "phone_open"})
		self.assertEqual(tools["phone_tap"]["inputSchema"]["required"], ["x", "y"])
		self.assertEqual(tools["phone_key"]["inputSchema"]["properties"]["name"]["enum"], ["back", "home", "recents", "notifications"])

	def test_screenshot_returns_image_content(self):
		r = self.call("phone_screenshot")["result"]
		self.assertFalse(r["isError"])
		self.assertEqual(r["content"], [{"type": "image", "data": base64.b64encode(PNG).decode(), "mimeType": "image/png"}])
		self.assertEqual(Phone.seen[0], ("GET", "/v1/screenshot", "Bearer plk_testkey", None))

	def test_ui_tree_returns_text(self):
		r = self.call("phone_ui_tree")["result"]
		self.assertEqual(json.loads(r["content"][0]["text"])["package"], "x")

	def test_actions_post_filtered_arguments(self):
		self.call("phone_tap", {"x": 5, "y": 6, "evil": 1})
		self.call("phone_swipe", {"x1": 1, "y1": 2, "x2": 3, "y2": 4})
		self.call("phone_type", {"text": "héllo", "append": True})
		self.call("phone_key", {"name": "back"})
		self.call("phone_open", {"url": "https://example.com"})
		self.assertEqual([(m, p, b) for m, p, _, b in Phone.seen], [
			("POST", "/v1/tap", {"x": 5, "y": 6}),
			("POST", "/v1/swipe", {"x1": 1, "y1": 2, "x2": 3, "y2": 4}),
			("POST", "/v1/type", {"text": "héllo", "append": True}),
			("POST", "/v1/key", {"name": "back"}),
			("POST", "/v1/open", {"url": "https://example.com"}),
		])

	def test_missing_key_is_a_clear_tool_error(self):
		self.write_key(None)
		r = self.call("phone_tap", {"x": 1, "y": 2})["result"]
		self.assertTrue(r["isError"])
		self.assertIn("鍵", r["content"][0]["text"])
		self.assertEqual(Phone.seen, [])

	def test_service_off_is_a_clear_tool_error(self):
		r = self.call("phone_screenshot", url="http://127.0.0.1:1")["result"]
		self.assertTrue(r["isError"])
		self.assertIn("ユーザー補助", r["content"][0]["text"])

	def test_http_errors_become_tool_errors(self):
		Phone.fail = (401, "missing or unknown key")
		r = self.call("phone_ui_tree")["result"]
		self.assertTrue(r["isError"])
		self.assertIn("401", r["content"][0]["text"])
		Phone.fail = (403, "denied by user")
		r = self.call("phone_tap", {"x": 1, "y": 2})["result"]
		self.assertTrue(r["isError"])
		self.assertIn("denied by user", r["content"][0]["text"])

	def test_unknown_tool_and_bad_json(self):
		self.assertEqual(self.call("nope")["error"]["code"], -32602)
		p = subprocess.run([sys.executable, "-I", SCRIPT], input="{bad\n", capture_output=True, text=True, timeout=30)
		self.assertEqual(json.loads(p.stdout)["error"]["code"], -32700)

	def test_port_file_is_used(self):
		with open(os.path.join(self.home, ".phone-linux", "phone-api-port"), "w") as f:
			f.write("1\n")
		try:
			env = {"PHONE_API_URL": ""}
			r = self.call("phone_screenshot", url="", extra_env=env)["result"]
			self.assertTrue(r["isError"])
			self.assertIn("127.0.0.1:1", r["content"][0]["text"])
		finally:
			os.remove(os.path.join(self.home, ".phone-linux", "phone-api-port"))


if __name__ == "__main__":
	unittest.main()
