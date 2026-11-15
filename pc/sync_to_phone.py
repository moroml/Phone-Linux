"""PC の Claude Code ハーネス(~/.claude と dotfiles の pack-router)をスマホの Termux(ネイティブ)へ片方向で送る。

使い方: python -I sync_to_phone.py [--dry-run]
- PC が正。スマホ側の共有部分(skills・agents・hooks など)は毎回置き換える。kb はスマホで足した分を残す。
- Windows 専用のもの(.cmd/.ps1、orca・kev-gate・statusLine、codex/ollama 依存のエージェント)は送らない。
- settings.json はスマホ用に組み立て、スマホ側の theme などは残す。
起源: 2026-11-15 ユーザー指示「スマホの claude にもハーネスを」。dotfiles の ai-sync は双方向で、
  スマホ用に書き換えた settings.json が PC に戻ると PC の hooks を壊すため、片方向の別経路にした。
  同日、proot の Ubuntu をやめ Termux ネイティブで動かす方針に変えたため、送り先を Termux のホームに付け替えた。
"""
import io, json, os, re, subprocess, sys, tarfile, time
from pathlib import Path

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
ADB = os.environ.get("ADB", "adb")  # PATH に無ければ環境変数 ADB に adb.exe の場所を入れる
HOME = Path.home()
CL = HOME / ".claude"
DOT = HOME / "dotfiles"
TERMUX = "/data/data/com.termux/files"
PHONE_HOME = f"{TERMUX}/home"
PHONE_PY = f"{TERMUX}/usr/bin/python"

# (PC 側の元, スマホ側の置き場所 ホーム起点)。置き換え対象のフォルダ
TREES = [
    (CL / "skills", ".claude/skills"),
    (CL / "agents", ".claude/agents"),
    (CL / "rules", ".claude/rules"),
    (CL / "commands", ".claude/commands"),
    (CL / "team", ".claude/team"),
    (CL / "hooks", ".claude/hooks"),
    (CL / "scripts", ".claude/scripts"),
    (CL / "local-plugins", ".claude/local-plugins"),
    (DOT / "pack-router", "dotfiles/pack-router"),
    (DOT / "packs", "dotfiles/packs"),
]
MERGE_TREES = [(CL / "kb", ".claude/kb")]  # 消さずに上書き(スマホで足した学びを残す)
FILES = [(CL / "CLAUDE.md", ".claude/CLAUDE.md"),
         (CL / "pack-router" / "index.json", ".claude/pack-router/index.json")]

SKIP_DIRS = {".git", "__pycache__", "node_modules", ".trash", "synced", "logs", "state",
             "results", "feedback", "cache", ".cache"}
SKIP_EXT = {".pyc", ".cmd", ".ps1", ".psm1", ".exe", ".log", ".bak"}
SKIP_NAMES = {"codex-reviewer.md", "local-reviewer.md", "kev-gate-hook.py", "browser-batch-approve.py",
              ".env", ".credentials.json", "update.log"}
TEXT_EXT = {".md", ".py", ".json", ".txt", ".toml", ".yaml", ".yml", ".sh", ".js", ".ts", ".html", ".css", ".csv"}


def home_re(name: str) -> re.Pattern:
    """Windows のホーム(C:\\Users\\name、C:/Users/name、/c/Users/name、JSON の \\\\ 区切り)とその後ろのパスを捕まえる。"""
    return re.compile(r"(?:/?/c/|[Cc]:(?:\\\\|\\|/))Users(?:\\\\|\\|/)" + re.escape(name)
                      + r"((?:(?:\\\\|\\|/)[\w.~-]+)*)", re.I)


def rewrite_home(text: str, name: str = HOME.name) -> str:
    """Windows ホーム配下のパスを Termux のホームに付け替え、区切りを / にそろえる。"""
    return home_re(name).sub(lambda m: PHONE_HOME + re.sub(r"\\\\|\\", "/", m.group(1)), text)


PHONE_RULE = """# スマホ(Android)で動いているときの前提

- ここは Galaxy の Termux(Ubuntu などの仮想環境ではなく、ネイティブの Android 上)。Windows・PowerShell・`C:\\` のパスは無い。CLAUDE.md の Windows 前提の記述は、この環境に読み替える。
- 作業場所は起動した Termux の現在のフォルダ(`~/projects` など)。`C:\\project` の代わりにここを使う。ホームは `/data/data/com.termux/files/home`(`~`)。
- スマホの内部ストレージは `~/storage`(`~/storage/shared` が `/sdcard` 相当)。写真などを消す・上書きする前は、ユーザーに確認する。
- 使えないもの: codex-reviewer・local-reviewer(codex と ollama が無い)、kev-gate、ブラウザ操作、managed settings と githooks の強制。レビューは supervisor など Claude のエージェントで代える。
- kb は `python -I ~/.claude/scripts/kb.py`。
- `~/.claude` の skills・agents・hooks・rules・CLAUDE.md・settings の共有部分は PC から片方向で上書きされる。ここで直しても次の同期で消えるので、恒久的な変更は PC 側で行う。kb への add は残る。
"""


def keep(p: Path) -> bool:
    if any(part in SKIP_DIRS for part in p.parts):
        return False
    return p.suffix.lower() not in SKIP_EXT and p.name not in SKIP_NAMES


def data_of(p: Path) -> bytes:
    b = p.read_bytes()
    if p.suffix.lower() in TEXT_EXT:
        try:
            b = rewrite_home(b.decode("utf-8")).encode("utf-8")
        except UnicodeDecodeError:
            pass
    return b


def phone_settings() -> dict:
    pc = json.loads((CL / "settings.json").read_text(encoding="utf-8"))
    py = f"{PHONE_PY} -I {PHONE_HOME}/.claude"
    hook = lambda cmd, t=10: {"type": "command", "command": f"{py}/{cmd}", "timeout": t}
    env = {k: v for k, v in pc.get("env", {}).items()
           if k not in {"CLAUDE_CODE_USE_POWERSHELL_TOOL", "PANEL_CODEX_CMD"}}
    perms = pc.get("permissions", {})
    bash = lambda rules: [r for r in rules if r.startswith("Bash(")]
    return {
        "language": pc.get("language"), "model": pc.get("model"), "effortLevel": pc.get("effortLevel"),
        "autoMemoryEnabled": pc.get("autoMemoryEnabled"), "autoCompactWindow": pc.get("autoCompactWindow"),
        "skillOverrides": pc.get("skillOverrides", {}), "skipWorkflowUsageWarning": True, "env": env,
        "enabledPlugins": {k: v for k, v in pc.get("enabledPlugins", {}).items() if v},
        "extraKnownMarketplaces": {"local-curated": {"source": {"source": "directory",
                                                                 "path": f"{PHONE_HOME}/.claude/local-plugins"}}},
        "permissions": {
            "allow": ["Glob", "Grep", "Read", "Edit(./**)"] + bash(perms.get("allow", [])),
            "deny": bash(perms.get("deny", [])), "ask": bash(perms.get("ask", [])),
            "defaultMode": "auto"},
        "hooks": {
            "SessionStart": [{"hooks": [hook("hooks/pack-router-hook.py sessionstart"),
                                        hook("hooks/stray-agents.py", 5), hook("scripts/session_metrics.py start", 5)]}],
            "UserPromptSubmit": [{"hooks": [hook("hooks/pack-router-hook.py userprompt")]}],
            "Stop": [{"hooks": [hook("hooks/japanese-reply-gate.py")]}],
            "SessionEnd": [{"hooks": [hook("scripts/session_metrics.py end", 5)]}],
        },
    }


def build() -> tuple[bytes, int]:
    buf, n = io.BytesIO(), 0
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        def add(arc: str, data: bytes):
            nonlocal n
            ti = tarfile.TarInfo(arc); ti.size = len(data); ti.mtime = int(time.time()); ti.mode = 0o644
            tar.addfile(ti, io.BytesIO(data)); n += 1
        for src, dst in TREES + MERGE_TREES:
            if src.is_dir():
                for p in src.rglob("*"):
                    rel = p.relative_to(src)
                    if p.is_file() and keep(rel):
                        add(f"{dst}/{rel.as_posix()}", data_of(p))
        for src, dst in FILES:
            if src.is_file():
                add(dst, data_of(src))
        add(".claude/rules/android-phone.md", PHONE_RULE.encode("utf-8"))
        add(".phone-sync/settings.json", json.dumps(phone_settings(), ensure_ascii=False, indent=2).encode("utf-8"))
    return buf.getvalue(), n


APPLY = r"""set -e
cd "$HOME"
python -c 'import yaml' 2>/dev/null || pip install pyyaml
for d in {replace}; do [ -d "$d" ] && find "$d" -mindepth 1 -maxdepth 1 ! -name synced -exec rm -rf {{}} +; done
tar xzf - -C "$HOME"
python - <<'PY'
import json, os
home = os.environ['HOME']
p, new = home + '/.claude/settings.json', json.load(open(home + '/.phone-sync/settings.json'))
cur = json.load(open(p)) if os.path.exists(p) else {{}}
cur.update(new); json.dump(cur, open(p, 'w'), ensure_ascii=False, indent=2)
PY
echo applied
"""


def run(cmd: str, data: bytes | None = None) -> str:
    p = subprocess.run([ADB, "shell", cmd], input=data, capture_output=True)
    out = (p.stdout + p.stderr).decode("utf-8", "replace")
    if p.returncode:
        raise SystemExit(f"失敗: {out.strip()}")
    return out


def in_termux(script: str) -> str:
    # run-as で Termux として入り、標準入力をそのまま渡す
    env = f"env -i HOME={PHONE_HOME} PREFIX={TERMUX}/usr PATH={TERMUX}/usr/bin TMPDIR={TERMUX}/usr/tmp LANG=C.UTF-8"
    return f"run-as com.termux {env} {TERMUX}/usr/bin/bash -c {sh_quote(script)}"


def sh_quote(s: str) -> str:
    return "'" + s.replace("'", "'\\''") + "'"


def main():
    data, n = build()
    print(f"送るファイル: {n} 件 / {len(data) / 1e6:.1f} MB")
    if "--dry-run" in sys.argv:
        return
    remote = "/data/local/tmp/claude-harness.tgz"
    tmp = Path(os.environ.get("TEMP", ".")) / "claude-harness.tgz"
    tmp.write_bytes(data)
    try:
        subprocess.run([ADB, "push", str(tmp), remote], check=True, capture_output=True)
    finally:
        tmp.unlink()
    replace = " ".join(f'"$HOME/{d}"' for _, d in TREES)
    print(run(f"cat {remote} | " + in_termux(APPLY.format(replace=replace))).strip())
    run(f"rm -f {remote}")


if __name__ == "__main__":
    main()
