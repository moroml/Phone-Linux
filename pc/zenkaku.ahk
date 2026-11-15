; 半角/全角キーを、PC では今までどおり IME 切替にし、同時にスマホ(マルチコントロール)向けに
; 正しい「押す→離す」の LANG5(sc076) を送る。スマホ側では KEY_ZENKAKUHANKAKU として届く想定。
; 起源: 2026-11-15 ユーザー指示。Windows の半角/全角は離した合図を出さず、マルチコントロール経由で
;   スマホに「`」の押しっぱなしとして届き、連打と切替不能が起きた(getevent で確認)。
; やめるとき: タスクトレイの AutoHotkey を終了し、スタートアップのショートカットを消す。
#Requires AutoHotkey v2.0
#SingleInstance Force
DetectHiddenWindows true

sc029:: {
    ToggleIme()
    SendEvent "{sc076 down}"
    Sleep 30
    SendEvent "{sc076 up}"
}

ToggleIme() {
    try {
        ime := DllCall("imm32\ImmGetDefaultIMEWnd", "Ptr", WinGetID("A"), "Ptr")
        open := SendMessage(0x283, 0x5, 0, , "ahk_id " ime)   ; WM_IME_CONTROL / IMC_GETOPENSTATUS
        SendMessage(0x283, 0x6, !open, , "ahk_id " ime)        ; IMC_SETOPENSTATUS
    }
}
