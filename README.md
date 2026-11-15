# Phone-Linux

Galaxy 専用の Linux アプリを自作するための土台。目標は、アプリを入れるだけで Linux と Claude Code が使え、Galaxy 側の設定を手で触らなくて済むこと。
方針は「Termux を土台に作り込み、Claude Code は proot なしで直接動かす」。計画は [docs/plan.md](docs/plan.md)、仕組みとつまずいた所は [docs/architecture.md](docs/architecture.md)。
下の手順と `phone/` は、Ubuntu(proot) を使う以前の組み立て方で、予備として残している。

## 中身

| ファイル | 動く場所 | 役割 |
|---|---|---|
| `phone/setup.sh` | スマホ(Termux) | Ubuntu(proot)と Claude Code を入れ、`claude` で起動できるようにする |
| `phone/claude` | スマホ(Termux) | Ubuntu の Claude Code を今のフォルダで起動する入口 |
| `phone/termux.properties` | スマホ(Termux) | キー列・日本語入力・ベルの設定。自作アプリの初期設定の元 |
| `pc/sync_to_phone.py` | PC | `~/.claude` のハーネスをスマホ用に直して USB で送る(片方向) |

## 現行の組み立て方(Termux を使う)

1. Termux の [Releases](https://github.com/termux/termux-app/releases) から `github-debug_arm64-v8a.apk` を入れる(`sha256sums` で照合)。
2. `phone/` をスマホへ送り、Termux で `bash setup.sh` を実行する。
3. `cd ~/projects && claude` でログインする。
4. 任意: `python -I pc/sync_to_phone.py` で PC のハーネスを送る(`adb` が PATH に無いときは環境変数 `ADB`)。
5. 任意: Termux で `claude remote-control --name Galaxy` を動かすと、PC から操作できる。

## 注意
- スマホの `~/.claude` の共有部分は、手順4を実行するたび PC の内容で上書きされる。直すなら PC 側で。
- スマホの Claude は `/sdcard` を読み書きできる。写真などを扱うときは確認ありのモードで。
- 認証情報(`.credentials.json` など)はこのリポジトリにも転送にも含めない。
