# 仕組みと、作りながら分かったこと

自分で組み立て直すときに、どこが何のためにあるかを判断できるように残す。2026-11-15、Galaxy(SM-S938Q・Android 17)と Windows 11 で確かめた内容。

## 全体の構造

```
PC (Windows)                                   スマホ (Android)
┌────────────────────────────┐                 ┌──────────────────────────────────┐
│ Claude Code アプリ ─────────┼── claude.ai ────┼─ Claude Code (Remote Control)     │
│ ~/.claude (ハーネス)        │                 │   └ Ubuntu (proot) の中で動く      │
│   └ pc/sync_to_phone.py ────┼── USB・adb ────>│      └ Termux の中で動く           │
│ キーボード ─────────────────┼── マルチコントロール ─> Gboard (かな変換)           │
└────────────────────────────┘                 └──────────────────────────────────┘
```

| 層 | 役割 | ここにあるもの |
|---|---|---|
| Termux | Android 上の端末。ここからすべてを起動する | `phone/termux.properties` |
| Ubuntu (proot) | 管理者権限なしで動く Linux。Claude Code の実行場所 | `phone/setup.sh` |
| `claude` 入口 | Termux から Ubuntu の Claude Code を、今のフォルダで起動する | `phone/claude` |
| ハーネス転送 | PC の `~/.claude` をスマホ用に直して送る(片方向) | `pc/sync_to_phone.py` |
| Remote Control | PC とスマホの Claude を、互いの端末から操作する | 各端末で有効化するだけ |
| キー入力 | PC のキーボードでスマホに日本語を打つ | 今は Gboard 任せ。自作アプリで扱う予定 |

## なぜこの形なのか

- **Ubuntu を挟む理由**: npm の Claude Code は各 OS 用の本体を別に落とす作りで、`linux-arm64-android` 用は配布されていない(2.1.325 時点)。glibc の Ubuntu なら `linux-arm64` 版が動く。
- **Termux は GitHub 版**: Play ストア版は更新が止まっている。GitHub の `github-debug` 版はデバッグ可能なので、PC から `adb shell run-as com.termux` で中に入れる(自動化に便利)。
- **ハーネスは片方向**: dotfiles の同期は双方向で、スマホ用に書き換えた settings.json が PC に戻ると PC のフックを壊す。だから PC を正にして上書きで送る。kb だけは消さずに重ねる。
- **送らないもの**: Windows 専用(`.cmd`・`.ps1`)、codex・ollama を使うレビュー役、kev-gate、Orca、statusLine。pack-router は PyYAML が要る。

## つまずいた所(同じ失敗をしないために)

| 症状 | 原因 | 対処 |
|---|---|---|
| Termux で Ctrl+C やスペースが効かない | Simeji が変換途中の文字としてキーを抱え込む | Samsung キーボードか Gboard を使う |
| Termux で日本語に切り替わらない | `enforce-char-based-input = true` で入力欄が英字専用になる | `false` にする |
| 入力中に振動する | Termux のベル(空行の Backspace など) | `bell-character = ignore` |
| 🌐で画面がガクッとなる | Samsung キーボードの「ツールバー」オフで帯が出たり消えたりする | ツールバーをオン |
| 物理キーボードで日本語が打てない | Samsung キーボードは物理キー入力をかなにしない | Gboard(日本語 QWERTY)を使う |
| Ctrl+Space で Samsung に飛ぶ | 言語ショートカットは全キーボードを巡回する。Samsung は無効にできない | Gboard 内の切替(半角/全角の信号)を使う |
| 半角/全角で「゛」が止まらない | Windows の半角/全角は離した合図を出さない。マルチコントロール経由で `KEY_GRAVE` の押しっぱなしとして届き、次に押したとき離す→押すが続けて届く | 自作アプリ側で、この `KEY_GRAVE` を自動連打させず日本語/英語の切替として扱う |
| `adb input text` が化ける | 日本語 IME が変換してしまう | 英語モードで送るか、`run-as` でファイル経由にする |

## 自作アプリで Galaxy の設定を触らずに済ませるために

上のつまずきの多くは「Galaxy 本体のキーボード設定」に原因がある。アプリの中で次を持てば、本体の設定を変えずに済む。

- 端末画面が物理キーを直接受け取り、ローマ字→かなの変換と日本語/英語の切替をアプリ内で行う(本体の言語ショートカットや Samsung キーボードに頼らない)。
- 上の `KEY_GRAVE` の押しっぱなしを切替キーとして扱う。
- キー列・ベル・入力欄の種類などの Termux 設定を初期値として組み込む。

## 未確認のまま残っていること

- 転送で消した `~/.claude/skills/synced` を、Claude アプリが作り直すか。
