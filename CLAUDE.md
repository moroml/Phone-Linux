# Phone-Linux

Galaxy 専用の Linux 端末アプリ。termux-app のフォーク(`termux/`、subtree)に、proot なしの Claude Code、物理キーの日本語入力(Mozc 辞書の文節変換)、スマホ操作 API/MCP、Remote Control を足したもの。概要は README、判断の経緯は docs/plan.md、つまずきは docs/architecture.md。

## よく使うコマンド(Windows)
- ビルドと単体テスト: `Set-Location termux; $env:JAVA_HOME = (Split-Path (Split-Path (Get-Command java).Source)); .\gradlew.bat assembleDebug :app:testDebugUnitTest --console=plain`
- 実機へ上書き: `C:\project\work\tools\android-sdk\platform-tools\adb.exe install -r termux\app\build\outputs\apk\debug\termux-app_apt-android-7-debug_arm64-v8a.apk`
- 端末の中を見る: `adb shell run-as com.termux ...`(環境変数 PREFIX/HOME/PATH/LD_PRELOAD を自分で設定する)
- CI: push で `.github/workflows/ci.yml` がビルドと単体テストを回す。

## 守ること
- 内部 ID は `com.termux`、署名は公開テスト鍵、versionCode は 2000 以上(上書きで中身を残すため)。targetSdk 28 のまま。
- 追加コードは `termux/app/src/main/java/com/termux/app/phonelinux/`、同梱スクリプトは `assets/phone-linux/`(先頭に `# phone-linux:` の印。印の無い同名ファイルは利用者のものとして上書きしない)。Termux 本体への変更は最小のフックだけ。
- アセットを `.gz` で終わる名前にしない(ビルドが展開して名前を変える)。辞書は `tools/build_mozc_dict.py` で作り直す。
- 物理キーは IME(Simeji)に渡さず端末へ直接送る。半角/全角はマルチコントロールから `KEYCODE_GRAVE` の押しっぱなしで届くので、押した合図だけで切り替える(離した合図は PC に戻るときにも来る)。
- adb から送るキーは仮想デバイス扱いで日本語入力の処理を通らない。入力の確認は利用者に打ってもらい、必要ならキー記録(shared_prefs の `terminal_view_key_logging_enabled`)を一時的に有効にする。
- ユーザー補助の有効化・通知の許可・ログインは利用者が行う。認証情報はリポジトリにも転送にも入れない。
