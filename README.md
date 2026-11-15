# Phone-Linux

Galaxy 専用の Linux アプリ。Termux を土台に作り込み、アプリを入れるだけで Claude Code が proot なしで速く動き、PC のキーボードで日本語が打て、Claude がスマホを操作できる。
計画は [docs/plan.md](docs/plan.md)、仕組みとつまずいた所は [docs/architecture.md](docs/architecture.md)。

## できること

| 機能 | 使い方 |
|---|---|
| Claude Code | 初回起動で自動セットアップ。以後 `claude` で起動、`claude-update` で更新 |
| 日本語入力(物理キーボード・マルチコントロール) | 半角/全角で日本語/英語を切り替え(カーソルが緑なら日本語)。配列は JIS/US を選べる |
| PC と同じショートカット | Ctrl+Shift+C/V(コピー/貼り付け)、Ctrl+Shift+T(新しいセッション)、Ctrl+Tab(切り替え)、Ctrl+=/-(文字サイズ)、Ctrl+Shift+I(画像)。`~/.phone-linux/keys.json` で変更可 |
| 画像を指示に入れる | Ctrl+Shift+I、画面の長押しメニュー、他のアプリからの共有。`~/inbox/` に保存され、場所が入力欄に入る |
| 見た目 | 背景(画像・単色・暗さ)、配色、日本語対応フォント(UDEV Gothic)。下のキー列は初期状態で非表示 |
| スマホ操作 | ユーザー補助を有効にし、設定で「Claude Code 用の鍵を作成」→ `phone-mcp-register`。Claude が画面を撮る・読む・タップ・入力・アプリを開く |
| Remote Control | ドロワーの「Remote Control」かアイコン長押しのショートカット。スリープ防止つき |
| 自動更新 | 毎日、充電中の定額回線で `pkg` と Claude Code を更新。失敗時だけ通知 |

設定はドロワーの歯車(「Phone-Linux の設定」)にまとまっている。

## ビルドと入れ方(Windows)

1. JDK 17、Android SDK(platforms;android-36、build-tools 36、ndk 29.0.14206865)を用意し、`termux/local.properties` に `sdk.dir` を書く。
2. ビルド:
```powershell
Set-Location termux
.\gradlew.bat assembleDebug :app:testDebugUnitTest
```
3. 入れる(GitHub 版 Termux と同じ公開テスト鍵なので、入っている Termux に上書きでき、中身は残る):
```powershell
adb install -r termux\app\build\outputs\apk\debug\termux-app_apt-android-7-debug_arm64-v8a.apk
```

## リポジトリの中身

| 場所 | 役割 |
|---|---|
| `termux/` | termux-app のフォーク(subtree)。追加分は `app/src/main/java/com/termux/app/phonelinux/` と `app/src/main/assets/phone-linux/` |
| `pc/sync_to_phone.py` | PC の `~/.claude` ハーネスをスマホの Termux へ片方向で送る(`--dry-run` で件数だけ確認) |
| `phone/` | proot の Ubuntu を使う旧方式(予備) |
| `docs/` | 計画と仕組み |

## 注意
- 内部 ID は `com.termux` のままなので、公式 Termux とは同時に入れられない(入れ替え)。
- スマホ操作の API は、アプリ専用領域の Unix ソケットだけで受け付ける。PC から直接は呼べない(PC からは Remote Control 経由で Claude に頼む)。
- スマホの Claude は `/sdcard` を読み書きでき、スマホ操作も強い権限を持つ。大事な操作では確認ありの鍵を使う。
- 認証情報(`.credentials.json`、API の鍵)はリポジトリにも転送にも含めない。
- GPLv3(termux-app 由来)。人に配るならソースも公開する。
