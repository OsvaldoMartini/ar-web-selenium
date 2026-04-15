; ============================================================================
;  ARWeb Avaloq v4.2 - Update Installer
;
;  Final structure (user selects the ROOT, e.g. C:\ARWebAvaloq):
;     <root>\ARWeb\plugins\        <- plugin zips + manifest
;     <root>\ARWeb-Scanner\        <- JAR files
; ============================================================================

#define MyAppName      "ARWeb"
#define MyAppVersion   "4.2"
#define MyAppPublisher "Allinweb AG"
#define MyAppURL       "https://www.allinweb.ch/"
#define MyAppCopyright "Copyright (C) 2026 Allinweb AG"

; Source paths (read from the working tree — the installer is built off this)
#define SrcRoot     "C:\ARWebAvaloq"
#define SrcARWeb    "C:\ARWebAvaloq\ARWeb"
#define SrcPlugins  "C:\ARWebAvaloq\ARWeb\plugins"
#define SrcScanner  "C:\ARWebAvaloq\ARWeb-Scanner"
#define SrcConfig   "C:\ARWebAvaloq\Config-4.2"
#define SrcDeploy   "C:\Martini\abr-web-selenium\Deploy"

[Setup]
AppId={{B4E8C3D2-AD5F-4B9C-C7E3-2F6G4B8D0E53}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion} Avaloq Install
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
AppCopyright={#MyAppCopyright}
VersionInfoVersion={#MyAppVersion}.0.0
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=ARWeb Avaloq v{#MyAppVersion} Install
VersionInfoCopyright={#MyAppCopyright}
VersionInfoProductName={#MyAppName}
VersionInfoProductVersion={#MyAppVersion}

#define MyAppDefaultDir "C:\ARWebAvaloq"

; The user picks the ROOT folder (e.g. C:\ARWebAvaloq)
; NOT the ARWeb sub-folder
DefaultDirName={#MyAppDefaultDir}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
PrivilegesRequiredOverridesAllowed=dialog
CreateUninstallRegKey=yes
UninstallDisplayName={#MyAppName} v{#MyAppVersion} Avaloq
UsePreviousAppDir=no
DirExistsWarning=no
AppendDefaultDirName=no

; ── Output ───────────────────────────────────────────────────────────────────
OutputBaseFilename=ARWeb-Avaloq-v4-2
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
WelcomeLabel1=Welcome to the {#MyAppName} v{#MyAppVersion} Avaloq Install
WelcomeLabel2=This will install the full ARWeb Avaloq v{#MyAppVersion} environment.%n%n  - 7 encrypted plugin packages%n  - AR Web Scanner + Engine v4.2%n  - ARWeb.config, Edge WebDriver%n  - JavaFX, JavaJCE, Lang%n  - Excel template (Apo Bank)%n%nPlease close ARWeb before continuing.
SelectDirBrowseLabel=Select the ROOT installation folder (e.g. C:\ARWebAvaloq).%nDo NOT select the ARWeb sub-folder.
SelectDirLabel3=The installer will create:%n%n   <dir>\ARWeb\plugins%n   <dir>\ARWeb-Scanner
ReadyLabel1=Ready to Install
ReadyLabel2a=Click Install to deploy the files to the selected folder.
FinishedHeadingLabel=Installation Complete
FinishedLabel={#MyAppName} v{#MyAppVersion} Avaloq has been successfully installed.%n%nYou can now launch ARWeb.

[Types]
Name: "full";   Description: "Full install (Plugins + JAR files)"
Name: "custom"; Description: "Custom"; Flags: iscustom

[Components]
Name: "plugins";    Description: "Encrypted plugin packages (7 files + manifest)"; Types: full custom; Flags: fixed
Name: "scanner";    Description: "AR_Web_Scanner-4.2.jar";                         Types: full custom
Name: "engine";     Description: "AR_Web_Engine-4.2.jar";                          Types: full custom
Name: "config";     Description: "ARWeb.config";                                   Types: full custom
Name: "excel";      Description: "Apo Bank.xlsx (Excel template)";                 Types: full custom
Name: "database";   Description: "database.db";                                    Types: full custom
Name: "edgedriver"; Description: "Edge WebDriver (146.0.3856.62)";                 Types: full custom
Name: "javafx";     Description: "JavaFX runtime";                                 Types: full custom
Name: "javajce";    Description: "Java JCE extensions";                            Types: full custom
Name: "javart";     Description: "Java runtime (java/ folder)";                    Types: full custom
Name: "lang";       Description: "Language files";                                 Types: full custom
Name: "appconfig";  Description: "configuration.properties";                       Types: full custom
Name: "launcher";   Description: "exec_launcher-4.2.bat";                         Types: full custom

[Files]
; ── Plugins  ->  {app}\ARWeb\plugins ─────────────────────────────────────────
; confirmoverwrite asks the user before replacing existing files
Source: "{#SrcPlugins}\manifest.json";       DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\hoverPick.zip";       DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\searchListAsync.zip"; DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite
Source: "{#SrcPlugins}\actionExecutor.zip";  DestDir: "{app}\ARWeb\plugins"; Components: plugins; Flags: ignoreversion confirmoverwrite

; ── JAR files  ->  {app}\ARWeb-Scanner ───────────────────────────────────────
Source: "{#SrcScanner}\AR_Web_Engine-4.2.jar";  DestDir: "{app}\ARWeb-Scanner"; Components: engine;  Flags: ignoreversion confirmoverwrite
Source: "{#SrcScanner}\AR_Web_Scanner-4.2.jar"; DestDir: "{app}\ARWeb-Scanner"; Components: scanner; Flags: ignoreversion confirmoverwrite

; ── Config  ->  {app}\Config-4.2 ─────────────────────────────────────────────
Source: "{#SrcConfig}\ARWeb.config"; DestDir: "{app}\Config-4.2"; Components: config; Flags: ignoreversion confirmoverwrite

; ── Excel  ->  {app}\ARWeb\Excel ─────────────────────────────────────────────
Source: "{#SrcARWeb}\Excel\Apo Bank.xlsx"; DestDir: "{app}\ARWeb\Excel"; Components: excel; Flags: ignoreversion confirmoverwrite

; ── Database  ->  {app}\ARWeb ────────────────────────────────────────────────
Source: "{#SrcARWeb}\database.db"; DestDir: "{app}\ARWeb"; Components: database; Flags: ignoreversion confirmoverwrite

; ── Lang  ->  {app}\lang ─────────────────────────────────────────────────────
Source: "{#SrcScanner}\lang\labels.en.properties"; DestDir: "{app}\ARWeb-Scanner\lang"; Components: lang; Flags: ignoreversion confirmoverwrite

; ── JavaJCE  ->  {app}\ARWeb-Scanner\javaJCE (recursive) ────────────────────
Source: "{#SrcScanner}\javaJCE\*"; DestDir: "{app}\ARWeb-Scanner\javaJCE"; Components: javajce; Flags: ignoreversion confirmoverwrite recursesubdirs createallsubdirs

; ── JavaFX  ->  {app}\ARWeb-Scanner\javaFX (recursive) ──────────────────────
Source: "{#SrcScanner}\javaFX\*"; DestDir: "{app}\ARWeb-Scanner\javaFX"; Components: javafx; Flags: ignoreversion confirmoverwrite recursesubdirs createallsubdirs

; ── Java runtime  ->  {app}\ARWeb-Scanner\java (recursive) ──────────────────
Source: "{#SrcScanner}\java\*"; DestDir: "{app}\ARWeb-Scanner\java"; Components: javart; Flags: ignoreversion confirmoverwrite recursesubdirs createallsubdirs

; ── Edge WebDriver  ->  {app}\ARWeb-Scanner\edgedriver-versions ──────────────
Source: "{#SrcScanner}\edgedriver-versions\*"; DestDir: "{app}\ARWeb-Scanner\edgedriver-versions"; Components: edgedriver; Flags: ignoreversion confirmoverwrite recursesubdirs createallsubdirs

; ── configuration.properties  ->  {app}\ARWeb-Scanner\config ────────────────
Source: "{#SrcScanner}\config\configuration.properties"; DestDir: "{app}\ARWeb-Scanner\config"; Components: appconfig; Flags: ignoreversion confirmoverwrite

; ── Launcher bat  ->  {app}\ARWeb-Scanner ────────────────────────────────────
Source: "{#SrcScanner}\exec_launcher-4.2.bat"; DestDir: "{app}\ARWeb-Scanner"; Components: launcher; Flags: ignoreversion confirmoverwrite

; ── Create empty directories ──────────────────────────────────────────────────
[Dirs]
Name: "{app}\ARWeb\Reports"
Name: "{app}\ARWeb\Export"
Name: "{app}\ARWeb\Logs"
Name: "{app}\ARWeb\Excel"
Name: "{app}\ARWeb\plugins"
Name: "{app}\ARWeb-Scanner\TakedShot"
Name: "{app}\Config-4.2"
Name: "{app}\ARWeb-Scanner\lang"

[Icons]
; No start menu shortcuts for an install

[Code]

// ── Strip duplicated path segments before install ───────────────────────────
//  C:\ARWebAvaloq\ARWeb\ARWeb          ->  C:\ARWebAvaloq\ARWeb
//  C:\ARWebAvaloq\ARWeb-ARWeb-Scanner  ->  C:\ARWebAvaloq\ARWeb-Scanner
//  C:\ARWebAvaloq\ARWeb\ARWeb          ->  C:\ARWebAvaloq

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

    // C:\X\ARWeb\ARWeb-Scanner  ->  C:\X\ARWeb-Scanner
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
    Space + 'Plugins      ->  ' + ExpandConstant('{app}') + '\ARWeb\plugins' + NewLine +
    Space + 'JARs         ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner' + NewLine +
    Space + 'Config       ->  ' + ExpandConstant('{app}') + '\Config-4.2' + NewLine +
    Space + 'Excel        ->  ' + ExpandConstant('{app}') + '\ARWeb\Excel' + NewLine +
    Space + 'Database     ->  ' + ExpandConstant('{app}') + '\ARWeb\database.db' + NewLine +
    Space + 'JavaFX       ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner\javaFX' + NewLine +
    Space + 'JavaJCE      ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner\javaJCE' + NewLine +
    Space + 'Java RT      ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner\java' + NewLine +
    Space + 'Lang         ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner\lang' + NewLine +
    Space + 'Edge Driver  ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner\edgedriver-versions' + NewLine +
    Space + 'App Config   ->  ' + ExpandConstant('{app}') + '\ARWeb-Scanner\config' + NewLine + NewLine;

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
        '   C:\ARWebAvaloq\ARWeb\plugins' + NL +
        '   C:\ARWebAvaloq\ARWeb-Scanner' + NL +
        'then select C:\ARWebAvaloq instead.' + NL + NL +
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

// ── String replace helper (Inno Setup Pascal has no built-in replace) ───────

function MyStringReplace(const S, OldStr, NewStr: String): String;
var
  Pos: Integer;
begin
  Result := S;
  Pos := 1;
  while Pos <= Length(Result) do
  begin
    Pos := Pos;
    if Copy(Result, Pos, Length(OldStr)) = OldStr then
    begin
      Delete(Result, Pos, Length(OldStr));
      Insert(NewStr, Result, Pos);
      Pos := Pos + Length(NewStr);
    end
    else
      Pos := Pos + 1;
  end;
end;

// ── Update paths inside a config/bat file after install ─────────────────────
//
//  originFilePath : full path to the installed file
//  toBeReplaced   : the old path fragment to find (e.g. 'C\:\\ARWebAvaloq\\')
//  AppNewPath     : the new root path ({app})
//  newWords       : path separator to use ('\\' for .config, '\' for .bat)
//  alsoColon      : true = escape colons (Java .properties format)

procedure UpdateConfigFile(originFilePath, toBeReplaced, AppNewPath, newWords: String; alsoColon: Boolean);
var
  Lines: TArrayOfString;
  i: Integer;
  NL: String;
begin
  NL := Chr(13) + Chr(10);
  AppNewPath := MyStringReplace(AppNewPath, '\', '|');
  AppNewPath := MyStringReplace(AppNewPath, '|', newWords);
  if alsoColon then
  begin
    AppNewPath := MyStringReplace(AppNewPath, ':', '|');
    AppNewPath := MyStringReplace(AppNewPath, '|', '\:') + newWords;
  end
  else
  begin
    AppNewPath := AppNewPath + newWords;
  end;

  if LoadStringsFromFile(originFilePath, Lines) then
  begin
    for i := 0 to GetArrayLength(Lines) - 1 do
    begin
      Log('Before: ' + Lines[i]);
      Lines[i] := MyStringReplace(Lines[i], toBeReplaced, AppNewPath);
      Log('After:  ' + Lines[i]);
    end;
    SaveStringsToFile(originFilePath, Lines, False);
    if alsoColon then
    begin
      MsgBox('File updated:' + NL +
        '"ARWeb.config"' + NL + NL +
        'Paths updated to:' + NL +
        ExpandConstant('{app}'),
        mbInformation, MB_OK);
    end;
  end
  else
  begin
    MsgBox('Failed to load:' + NL + originFilePath, mbError, MB_OK);
  end;
end;

// ── Simple line replace in a text file ───────────────────────────────────────

procedure ReplaceLineInFile(filePath, oldText, newText: String);
var
  Lines: TArrayOfString;
  i: Integer;
begin
  if LoadStringsFromFile(filePath, Lines) then
  begin
    for i := 0 to GetArrayLength(Lines) - 1 do
      Lines[i] := MyStringReplace(Lines[i], oldText, newText);
    SaveStringsToFile(filePath, Lines, False);
    Log('ReplaceLineInFile — replaced "' + oldText + '" with "' + newText + '" in ' + filePath);
  end;
end;

// ── Post-install: rewrite paths in config and launcher ──────────────────────

procedure CurStepChanged(CurStep: TSetupStep);
var
  AppNewPath, ExpectedPath: String;
begin
  if CurStep = ssPostInstall then
  begin
    AppNewPath := ExpandConstant('{app}');
    ExpectedPath := ExpandConstant('{#MyAppDefaultDir}');

    // ARWeb.config: replace escaped Java .properties paths
    // C\:\\ARWebAvaloq\\  ->  <root>\\
    if not SameText(AppNewPath, ExpectedPath) then
    begin
      UpdateConfigFile(
        AppNewPath + '\Config-4.2\ARWeb.config',
        'C\:\\ARWebAvaloq\\',
        AppNewPath, '\\', True);

      // exec_launcher-4.2.bat: replace plain paths
      // C:\ARWebAvaloq\  ->  <root>\
      UpdateConfigFile(
        AppNewPath + '\ARWeb-Scanner\exec_launcher-4.2.bat',
        'C:\ARWebAvaloq\',
        AppNewPath, '\', False);
    end;

    // ARWeb.config: change data_base from Access to TEXT
    ReplaceLineInFile(
      AppNewPath + '\Config-4.2\ARWeb.config',
      'data_base=Access',
      'data_base=TEXT');

    // Rename unins000.exe to ARWeb-Uninstall.exe
    if FileExists(AppNewPath + '\unins000.exe') then
    begin
      RenameFile(AppNewPath + '\unins000.exe', AppNewPath + '\ARWeb-Uninstall.exe');
      Log('Renamed unins000.exe -> ARWeb-Uninstall.exe');
    end;
    if FileExists(AppNewPath + '\unins000.dat') then
    begin
      RenameFile(AppNewPath + '\unins000.dat', AppNewPath + '\ARWeb-Uninstall.dat');
      Log('Renamed unins000.dat -> ARWeb-Uninstall.dat');
    end;
  end;
end;
