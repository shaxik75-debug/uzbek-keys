#NoEnv
; Built with the same base file as uzbek_auto.exe; the workflow runs it to prove the embedded script works.
; It also checks that InputHook (used for Backspace undo) is available in this AutoHotkey version.
ih := InputHook("L1 T1")
FileDelete, %A_ScriptDir%\selftest.txt
FileAppend, % "ok AHK " A_AhkVersion " compiled=" A_IsCompiled " inputhook=" IsObject(ih), %A_ScriptDir%\selftest.txt
ExitApp
