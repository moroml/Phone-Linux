# phone-claude-setup

Android スマホ(Termux)で Claude Code を動かし、PC と行き来して使うための土台。
完成品の自動インストーラではなく、自分で一から組み立てるための部品と手順を置いている。
仕組みとつまずいた所は [docs/architecture.md](docs/architecture.md)。

## 中身

| ファイル | 動く場所 | 役割 |
|---|---|---|
| `phone/setup.sh` | スマホ(Termux) | Ubuntu(proot)と Claude Code を入れ、`claude` で起動できるようにする |
| `phone/claude` | スマホ(Termux) | Ubuntu の Claude Code を今のフォルダで起動する入口 |
| `phone/termux.properties` | スマホ(Termux) | キー列・日本語入力・ベルの設定 |
| `pc/sync_to_phone.py` | PC | `~/.claude` のハーネスをスマホ用に直して USB で送る(片方向) |
| `pc/zenkaku.ahk` | PC | 半角/全角キーを、PC では今まで通り・スマホには正しい信号で送る |

## 組み立て手順

### 1. スマホに Termux を入れる
1. スマホで 開発者向けオプション → USB デバッグ をオン(「ADB 認証の自動取り消しを無効化」もオン推奨)。
2. [termux-app の Releases](https://github.com/termux/termux-app/releases) から `github-debug_arm64-v8a.apk` と `sha256sums` を落とし、ハッシュを照合する。
3. `adb install <apk>` で入れて、一度開いて初期化を待つ。

### 2. Claude Code を入れる
1. このフォルダの `phone/` をスマホへ送る: `adb push phone /sdcard/Download/phone-setup`
2. Termux で: `termux-setup-storage` → `bash ~/storage/downloads/phone-setup/setup.sh`
3. `cd ~/projects && claude` でログイン(表示された URL をブラウザで開き、コードを貼る)。

### 3. PC のハーネスを送る(任意)
```bash
python -I pc/sync_to_phone.py --dry-run
python -I pc/sync_to_phone.py
```
`adb` が PATH に無いときは、環境変数 `ADB` に adb.exe の場所を入れる。

### 4. 両方向から操作する(任意)
- スマホ: Termux で `claude remote-control --name Galaxy`(先に `termux-wake-lock`)。
- PC: Claude アプリでセッションの Remote Control をオン。
- 操作は claude.ai/code か Claude アプリの Code タブから。

### 5. PC のキーボードで日本語を打つ(任意)
1. スマホに Gboard を入れ、日本語を QWERTY にして標準キーボードにする。
2. PC に AutoHotkey v2 を入れ、`pc/zenkaku.ahk` を起動する(常用するならスタートアップに置く)。
3. マルチコントロールでスマホを操作し、半角/全角で切り替わるか試す。

## 注意
- スマホの `~/.claude` の共有部分は、手順3を実行するたび PC の内容で上書きされる。直すなら PC 側で。
- スマホの Claude は `/sdcard` を読み書きできる。写真などを扱うときは確認ありのモードで。
- 認証情報(`.credentials.json` など)はこのリポジトリにも転送にも含めない。
