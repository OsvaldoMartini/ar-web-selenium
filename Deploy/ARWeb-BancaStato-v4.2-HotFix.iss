; ============================================================================
;  ARWeb BancaStato v4.2 - Update Installer
;
;  Final structure (user selects the ROOT, e.g. C:\ARWeb):
;     <root>\ARWeb\plugins\        <- plugin zips + manifest
;     <root>\ARWeb-Scanner\        <- JAR files
; ============================================================================

#define MyAppName      "ARWeb"
#define MyAppVersion   "4.2"
#define MyAppPublisher "Allinweb AG"
#define MyAppURL       "https://www.allinweb.ch/"
#define MyAppCopyright "Copyright (C) 2026 Allinweb AG"

; Source paths
#define SrcPlugins  "D:\Projects\ARWeb-Martini\ARWeb\plugins-BancaStato"
#define SrcScanner  "D:\Projects\ARWeb-Martini\ARWeb-Scanner"
#define SrcDeploy   "D:\Projects\AllinWeb\ar-web-selenium\Deploy"

[Setup]
AppId={{A3F7B2D1-9C4E-4A8B-B6D2-1E5F3A7C9D42}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion} Update
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
AppCopyright={#MyAppCopyright}
VersionInfoVersion={#MyAppVersion}.0.0
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=ARWeb BancaStato v{#MyAppVersion} Update
VersionInfoCopyright={#MyAppCopyright}
VersionInfoProductName={#MyAppName}
VersionInfoProductVersion={#MyAppVersion}

; The user picks the ROOT folder (e.g. C:\ARWeb)
; NOT the ARWeb sub-folder
DefaultDirName=C:\ARWeb
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
PrivilegesRequiredOverridesAllowed=dialog
CreateUninstallRegKey=no
UsePreviousAppDir=no
DirExistsWarning=no
AppendDefaultDirName=no

; ── Output ───────────────────────────────────────────────────────────────────
OutputBaseFilename=ARWeb-BancaStato-v4.2-HotFix
OutputDir={#SrcDeploy}
SolidCompression=yes
Compression=lzma2/ultra64

; ── Branding ─────────────────────────────────────────────────────────────────
SetupIconFile={#SrcDeploy}\arweb.ico
WizardStyle=modern
WizardSizePercent=110,110
WindowVisible=no
DisableWelcomePage=no
DisableDirPage=no
DisableProgramGroupPage=yes
DisableReadyPage=no
DisableFinishedPage=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel1=Welcome to the {#MyAppName} v{#MyAppVersion} Update
WelcomeLabel2=This will install the latest plugins and application files for ARWeb BancaStato v{#MyAppVersion}.%n%n  - 7 encrypted plugin packages%n  - AR Web Scanner v4.2%n  - AR Web Engine v4.2%n%nPlease close ARWeb before continuing.
SelectDirBrowseLabel=Select the ROOT installation folder (e.g. C:\ARWeb).%nDo NOT select the ARWeb sub-folder.
SelectDirLabel3=The installer will create:%n%n   <dir>\ARWeb\plugins%n   <dir>\ARWeb-Scanner
ReadyLabel1=Ready to Update
ReadyLabel2a=Click Install to deploy the update files to the selected folder.
FinishedHeadingLabel=Update Complete
FinishedLabel={#MyAppName} v{#MyAppVersion} has been successfully updated.%n%nYou can now launch ARWeb.

[Types]
Name: "full";   Description: "Full update (Plugins + JAR files)"
Name: "custom"; Description: "Custom"; Flags: iscustom

[Components]
Name: "plugins";    Description: "Encrypted plugin packages (7 files + manifest)"; Types: full custom; Flags: fixed
Name: "scanner";    Description: "AR_Web_Scanner-4.2.jar";                         Types: full custom
Name: "engine";     Description: "AR_Web_Engine-4.2.jar";                          Types: full custom

[Files]
; ── Plugins  ->  {app}\ARWeb\plugins ─────────────────────────────────────────
; confirmoverwrite asks the user before replacing existing files
Source: "{#SrcPlugins}\manifest.json";       DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\pageScanner.zip";     DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\hoverPick.zip";       DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\searchList.zip";      DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\searchListAsync.zip"; DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\actionExecutor.zip";  DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\pluginTest.zip";      DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite

; ── JAR files  ->  {app}\ARWeb-Scanner ───────────────────────────────────────
Source: "{#SrcScanner}\AR_Web_Engine-4.2.jar";  DestDir: "{app}\ARWeb-Scanner"; Components: engine;  Flags: ignoreversion confirmoverwrite
Source: "{#SrcScanner}\AR_Web_Scanner-4.2.jar"; DestDir: "{app}\ARWeb-Scanner"; Components: scanner; Flags: ignoreversion confirmoverwrite

[Icons]
; No start menu shortcuts for an update

[Code]

// ── Strip duplicated path segments before install ───────────────────────────
//  C:\ARWeb-Martini\ARWeb\ARWeb        ->  C:\ARWeb-Martini\ARWeb
//  C:\ARWeb-Martini\ARWeb-ARWeb-Scanner -> C:\ARWeb-Martini\ARWeb-Scanner
//  C:\ARWeb\ARWeb\ARWeb                ->  C:\ARWeb

function StripTrailingBackslash(const S: String): String;
begin
  Result := S;
  if (Length(Result) > 3) and (Result[Length(Result)] = '\') then
    Delete(Result, Length(Result), 1);
end;

function EndsWithText(const S, Suffix: String): Boolean;
var
  SLower, SuffixLower: String;
begin
  SLower := Lowercase(S);
  SuffixLower := Lowercase(Suffix);
  Result := (Length(SLower) >= Length(SuffixLower)) and
    (Copy(SLower, Length(SLower) - Length(SuffixLower) + 1, Length(SuffixLower)) = SuffixLower);
end;

procedure SanitizeAppDir;
var
  Dir, Clean: String;
  Changed: Boolean;
begin
  Dir := StripTrailingBackslash(WizardDirValue);
  Changed := True;

  // Keep stripping duplicated trailing segments
  while Changed do
  begin
    Changed := False;
    Clean := Dir;

    // C:\X\ARWeb\ARWeb  ->  C:\X\ARWeb
    if EndsWithText(Clean, '\ARWeb\ARWeb') then
    begin
      Delete(Clean, Length(Clean) - Length('\ARWeb') + 1, Length('\ARWeb'));
      Changed := True;
    end;

    // C:\X\ARWeb-ARWeb-Scanner  ->  C:\X\ARWeb-Scanner
    if EndsWithText(Clean, '\ARWeb\ARWeb-Scanner') then
    begin
      Clean := Copy(Clean, 1, Length(Clean) - Length('\ARWeb\ARWeb-Scanner')) + '\ARWeb-Scanner';
      Changed := True;
    end;

    // C:\X\ARWeb-Scanner\ARWeb-Scanner  ->  C:\X\ARWeb-Scanner
    if EndsWithText(Clean, '\ARWeb-Scanner\ARWeb-Scanner') then
    begin
      Delete(Clean, Length(Clean) - Length('\ARWeb-Scanner') + 1, Length('\ARWeb-Scanner'));
      Changed := True;
    end;

    Dir := Clean;
  end;

  // Apply the cleaned path back
  if Dir <> StripTrailingBackslash(WizardDirValue) then
  begin
    WizardForm.DirEdit.Text := Dir;
    Log('SanitizeAppDir — corrected path to: ' + Dir);
  end;
end;

// ── Custom "What's included" info on the Ready page ─────────────────────────

function UpdateReadyMemo(Space, NewLine, MemoUserInfoInfo, MemoDirInfo,
  MemoTypeInfo, MemoComponentsInfo, MemoGroupInfo, MemoTasksInfo: String): String;
begin
  Result :=
    'Root folder:' + NewLine +
    Space + ExpandConstant('{app}') + NewLine + NewLine +

    'Destination paths:' + NewLine +
    Space + 'Plugins  ->  ' + ExpandConstant('{app}') + '\ARWeb\plugins' + NewLine +
    Space + 'JARs     ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner' + NewLine + NewLine;

  if MemoComponentsInfo <> '' then
    Result := Result + 'Selected components:' + NewLine + MemoComponentsInfo + NewLine + NewLine;

  Result := Result +
    'Existing files:' + NewLine +
    Space + 'You will be asked before overwriting each file.' + NewLine;
end;

// ── Validate the chosen folder is the ROOT, not a sub-folder ────────────────

function NextButtonClick(CurPageID: Integer): Boolean;
var
  AppDir, LastPart, NL, Msg: String;
begin
  Result := True;
  NL := Chr(13) + Chr(10);

  if CurPageID = wpSelectDir then
  begin
    // Clean duplicated segments first
    SanitizeAppDir;

    AppDir := ExpandConstant('{app}');
    LastPart := ExtractFileName(RemoveBackslashUnlessRoot(AppDir));

    // Warn if user accidentally selected the ARWeb sub-folder
    if (CompareText(LastPart, 'ARWeb') = 0) or
       (CompareText(LastPart, 'plugins') = 0) or
       (CompareText(LastPart, 'ARWeb-Scanner') = 0) then
    begin
      Msg := 'It looks like you selected a sub-folder instead of the root.' + NL + NL +
        'You selected:  ' + AppDir + NL + NL +
        'This will create:' + NL +
        '   ' + AppDir + '\ARWeb\plugins' + NL +
        '   ' + AppDir + '\ARWeb-Scanner' + NL + NL +
        'If your existing layout is:' + NL +
        '   C:\ARWeb-Martini\ARWeb\plugins' + NL +
        '   C:\ARWeb-Martini\ARWeb-Scanner' + NL +
        'then select C:\ARWeb-Martini instead.' + NL + NL +
        'Continue with the current selection?';
      Result := (MsgBox(Msg, mbConfirmation, MB_YESNO) = IDYES);
    end

    // Warn if neither sub-folder exists (fresh location)
    else if not DirExists(AppDir + '\ARWeb') and not DirExists(AppDir + '\ARWeb-Scanner') then
    begin
      Msg := 'No existing ARWeb installation found in this folder.' + NL + NL +
        'The update will create:' + NL +
        '   ' + AppDir + '\ARWeb\plugins' + NL +
        '   ' + AppDir + '\ARWeb-Scanner' + NL + NL +
        'Continue?';
      Result := (MsgBox(Msg, mbConfirmation, MB_YESNO) = IDYES);
    end;
  end;
end;

// ── Final safety net: sanitize again right before file copy ─────────────────

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  SanitizeAppDir;
  Result := '';
end;
