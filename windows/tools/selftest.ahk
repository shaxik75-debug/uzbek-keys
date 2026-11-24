#NoEnv
; Built with the same base file as uzbek_auto.exe; the workflow runs it to prove the embedded script works.
; Checks: InputHook (Backspace undo) exists, and UTF-8 text (Cyrillic, ş) is read correctly.
ih := InputHook("L1 T1")
text := "işax Как"
expected := "i" Chr(0x015F) "ax " Chr(0x041A) Chr(0x0430) Chr(0x043A)
utf8 := (text == expected) ? 1 : 0
FileDelete, %A_ScriptDir%\selftest.txt
FileAppend, % "ok AHK " A_AhkVersion " compiled=" A_IsCompiled " inputhook=" IsObject(ih) " utf8=" utf8, %A_ScriptDir%\selftest.txt
ExitApp
