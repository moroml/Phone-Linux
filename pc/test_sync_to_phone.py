"""sync_to_phone の純粋ロジックのテスト。実行: pc/ で python -m unittest test_sync_to_phone"""
import sys, unittest
sys.path.insert(0, ".")
import sync_to_phone as s

H = "/data/data/com.termux/files/home"


class RewriteHome(unittest.TestCase):
    def test_forms(self):
        for src in [r"C:\Users\bob\.claude\hooks\x.py", "C:/Users/bob/.claude/hooks/x.py",
                    "/c/Users/bob/.claude/hooks/x.py", r"C:\\Users\\bob\\.claude\\hooks\\x.py"]:
            self.assertEqual(s.rewrite_home(src, "bob"), f"{H}/.claude/hooks/x.py", src)

    def test_other_user_untouched(self):
        self.assertEqual(s.rewrite_home(r"C:\Users\alice\x", "bob"), r"C:\Users\alice\x")

    def test_inside_sentence(self):
        self.assertEqual(s.rewrite_home('run "C:\\Users\\bob\\a\\b.py" now', "bob"), f'run "{H}/a/b.py" now')


class Settings(unittest.TestCase):
    def test_hooks_use_termux_python(self):
        cmds = [h["command"] for g in s.phone_settings()["hooks"].values() for e in g for h in e["hooks"]]
        self.assertTrue(cmds and all(c.startswith(f"{s.PHONE_PY} -I {H}/.claude/") for c in cmds))

    def test_no_ubuntu_leftovers(self):
        self.assertNotIn("/root", str(s.phone_settings()) + s.PHONE_RULE)
        self.assertNotIn("proot", s.APPLY)


if __name__ == "__main__":
    unittest.main()
