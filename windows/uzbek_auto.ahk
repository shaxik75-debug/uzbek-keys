#NoEnv
#Persistent
#SingleInstance Force

; Uzbek Keys for Windows (AutoHotkey v1.1). Built into uzbek_auto.exe by .github/workflows/windows.yml,
; which also sets AppVersion below and publishes the update. Keep this file ASCII-only.

; ==================== SETTINGS ====================
AppVersion := "1.0"
VersionURL := "https://raw.githubusercontent.com/shaxik75-debug/uzbek-keys/main/version.txt"
ExeURL := "https://github.com/shaxik75-debug/uzbek-keys/releases/latest/download/uzbek_auto.exe"
; ==================================================

; Letters (fire immediately):
;   o'  -> o-umlaut     O'  -> capital
;   g'  -> g-breve      G'  -> capital
;   sh  -> s-cedilla    Sh, SH -> capital
;   ch  -> c-cedilla    Ch, CH -> capital
;   Backspace right after a replacement brings back what was typed (as on the phone keyboards).
; Phrases (fire after Space, Enter or punctuation, whole word only):
;   aa  -> Assalomu Alaykum
;   vv  -> Va Alaykum Assalom
; ScrollLock or Ctrl+Alt+U: switch everything off / on.
; Nothing is applied in terminals and code editors (list below).

GroupAdd, NoUzbek, ahk_exe WindowsTerminal.exe
GroupAdd, NoUzbek, ahk_exe cmd.exe
GroupAdd, NoUzbek, ahk_exe powershell.exe
GroupAdd, NoUzbek, ahk_exe pwsh.exe
GroupAdd, NoUzbek, ahk_exe conhost.exe
GroupAdd, NoUzbek, ahk_exe Code.exe
GroupAdd, NoUzbek, ahk_exe devenv.exe
GroupAdd, NoUzbek, ahk_exe pycharm64.exe
GroupAdd, NoUzbek, ahk_exe idea64.exe

; ---- Tray menu ----
StartupLink := A_Startup "\Uzbek Keys.lnk"
TitleItem := "Uzbek Keys " AppVersion
Menu, Tray, NoStandard
Menu, Tray, Tip, %TitleItem%
Menu, Tray, Add, %TitleItem%, TrayNoop
Menu, Tray, Disable, %TitleItem%
Menu, Tray, Add
Menu, Tray, Add, Check for updates, TrayCheck
Menu, Tray, Add, Start with Windows, TrayStartup
Menu, Tray, Add
Menu, Tray, Add, Exit, TrayExit
if FileExist(StartupLink)
    Menu, Tray, Check, Start with Windows

; First update check 5 seconds after start, then every 6 hours.
SetTimer, AutoCheck, -5000

ScrollLock::
^!u::
    Suspend, Toggle
    ToolTip, % A_IsSuspended ? "Uzbek auto-replace: OFF" : "Uzbek auto-replace: ON"
    SetTimer, HideTip, -1500
return

HideTip:
    ToolTip
return

AutoCheck:
    SetTimer, AutoCheck, 21600000
    CheckUpdate(false)
return

TrayNoop:
return

TrayCheck:
    CheckUpdate(true)
return

TrayStartup:
    if FileExist(StartupLink) {
        FileDelete, %StartupLink%
        Menu, Tray, Uncheck, Start with Windows
    } else {
        FileCreateShortcut, %A_ScriptFullPath%, %StartupLink%, %A_ScriptDir%
        Menu, Tray, Check, Start with Windows
    }
return

TrayExit:
    ExitApp
return

; ---- Update logic ----
CheckUpdate(manual) {
    global AppVersion, VersionURL, ExeURL
    if (!A_IsCompiled) {
        if (manual)
            MsgBox, 48, Uzbek Keys, Updates work only in the compiled .exe version.
        return
    }

    tmp := A_Temp "\uzbek_ver.txt"
    FileDelete, %tmp%
    DllCall("wininet\DeleteUrlCacheEntry", "Str", VersionURL)
    UrlDownloadToFile, %VersionURL%, %tmp%
    if (ErrorLevel) {
        if (manual)
            MsgBox, 48, Uzbek Keys, Could not reach the update server. Check the internet connection.
        return
    }
    FileRead, remote, %tmp%
    FileDelete, %tmp%
    remote := Trim(remote, " `t`r`n")
    if !RegExMatch(remote, "^\d+(\.\d+)*$") {
        if (manual)
            MsgBox, 48, Uzbek Keys, The update server returned an unexpected answer.
        return
    }
    if (VerCmp(remote, AppVersion) <= 0) {
        if (manual)
            MsgBox, 64, Uzbek Keys, You have the newest version (%AppVersion%).
        return
    }
    if (manual) {
        MsgBox, 36, Uzbek Keys, New version %remote% is available (you have %AppVersion%).`n`nInstall it now?
        IfMsgBox No
            return
    }

    ToolTip, Updating Uzbek Keys to %remote%...
    newExe := A_ScriptFullPath ".new"
    FileDelete, %newExe%
    DllCall("wininet\DeleteUrlCacheEntry", "Str", ExeURL)
    UrlDownloadToFile, %ExeURL%, %newExe%
    FileGetSize, sz, %newExe%
    FileRead, hdr, *m2 %newExe%
    if (ErrorLevel || sz < 200000 || hdr != "MZ") {
        FileDelete, %newExe%
        ToolTip
        if (manual)
            MsgBox, 48, Uzbek Keys, The update could not be downloaded.
        return
    }

    q := """"
    bat := A_Temp "\uzbek_update.bat"
    FileDelete, %bat%
    script := "@echo off`r`n"
        . "set n=0`r`n"
        . ":retry`r`n"
        . "ping 127.0.0.1 -n 2 >nul`r`n"
        . "set /a n+=1`r`n"
        . "move /y " q newExe q " " q A_ScriptFullPath q " >nul 2>&1`r`n"
        . "if errorlevel 1 if %n% LSS 10 goto retry`r`n"
        . "start " q q " " q A_ScriptFullPath q "`r`n"
        . "del " q "%~f0" q "`r`n"
    FileAppend, %script%, %bat%
    Run, %bat%,, Hide
    ExitApp
}

VerCmp(a, b) {
    pa := StrSplit(a, ".")
    pb := StrSplit(b, ".")
    n := pa.MaxIndex() > pb.MaxIndex() ? pa.MaxIndex() : pb.MaxIndex()
    Loop, %n% {
        x := pa[A_Index] ? pa[A_Index] + 0 : 0
        y := pb[A_Index] ? pb[A_Index] + 0 : 0
        if (x > y)
            return 1
        if (x < y)
            return -1
    }
    return 0
}

; ---- Replacement with undo: Backspace right after it types back the original letters ----
Rep(ch, orig) {
    SendInput %ch%
    ArmUndo(orig)
}

ArmUndo(orig) {
    global UndoHook, UndoText
    if IsObject(UndoHook)
        UndoHook.Stop()
    UndoText := orig
    UndoHook := InputHook("L1 V T10", "{Backspace}")
    UndoHook.MinSendLevel := 1
    UndoHook.KeyOpt("{Backspace}", "+V")
    UndoHook.OnEnd := Func("UndoEnd")
    UndoHook.Start()
}

UndoEnd(ih) {
    global UndoText
    if (ih.EndReason = "EndKey" && ih.EndKey = "Backspace" && !A_IsSuspended) {
        ; The Backspace itself went through and removed the new letter.
        SendInput {Text}%UndoText%
        Hotstring("Reset")
    }
}

#IfWinNotActive ahk_group NoUzbek

; --- Phrases: whole word only, lowercase only ---
:*0?0C:aa::Assalomu Alaykum
:*0?0C:vv::Va Alaykum Assalom

; --- Letters: fire inside words, instantly ---
:*?CX:o'::Rep("{U+00F6}", "o'")
:*?CX:O'::Rep("{U+00D6}", "O'")
:*?CX:g'::Rep("{U+011F}", "g'")
:*?CX:G'::Rep("{U+011E}", "G'")

:*?CX:sh::Rep("{U+015F}", "sh")
:*?CX:Sh::Rep("{U+015E}", "Sh")
:*?CX:SH::Rep("{U+015E}", "SH")

:*?CX:ch::Rep("{U+00E7}", "ch")
:*?CX:Ch::Rep("{U+00C7}", "Ch")
:*?CX:CH::Rep("{U+00C7}", "CH")

#IfWinNotActive
