; ARWeb BancaStato v4.2 HotFix Installer
; Deploys plugins to ARWeb\plugins and JAR files to ARWeb-Scanner

#define MyAppName "ARWeb BancaStato HotFix"
#define MyAppVersion "4.2"
#define MyAppPublisher "Allinweb AG"
#define MyAppURL "https://www.allinweb.ch/"

; Source paths
#define SrcPlugins "D:\Projects\ARWeb-Martini\ARWeb\plugins"
#define SrcScanner "D:\Projects\ARWeb-Martini\ARWeb-Scanner"

[Setup]
AppId={{A3F7B2D1-9C4E-4A8B-B6D2-1E5F3A7C9D42}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName=C:\ARWeb
DefaultGroupName={#MyAppName} v{#MyAppVersion}
AllowNoIcons=yes
PrivilegesRequiredOverridesAllowed=dialog
OutputBaseFilename=ARWeb-Updates-v4-2
OutputDir=D:\Projects\AllinWeb\ar-web-selenium\Deploy
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName=uninstall-ARWeb-BancaStato-HotFix-v4.2
; Let the user pick the base folder; we create sub-folders from there
UsePreviousAppDir=yes
DirExistsWarning=no
CreateUninstallRegKey=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
SelectDirLabel3=Select the base ARWeb folder.%nPlugins will be installed to <selected folder>\ARWeb\plugins%nJAR files will be installed to <selected folder>\ARWeb-Scanner

[Files]
; ── Plugins  ->  {app}\ARWeb\plugins ─────────────────────────────────────────
Source: "{#SrcPlugins}\manifest.json";       DestDir: "{app}\ARWeb\plugins"; Flags: ignoreversion
Source: "{#SrcPlugins}\pageScanner.zip";     DestDir: "{app}\ARWeb\plugins"; Flags: ignoreversion
Source: "{#SrcPlugins}\hoverPick.zip";       DestDir: "{app}\ARWeb\plugins"; Flags: ignoreversion
Source: "{#SrcPlugins}\searchList.zip";      DestDir: "{app}\ARWeb\plugins"; Flags: ignoreversion
Source: "{#SrcPlugins}\searchListAsync.zip"; DestDir: "{app}\ARWeb\plugins"; Flags: ignoreversion
Source: "{#SrcPlugins}\actionExecutor.zip";  DestDir: "{app}\ARWeb\plugins"; Flags: ignoreversion
Source: "{#SrcPlugins}\pluginTest.zip";      DestDir: "{app}\ARWeb\plugins"; Flags: ignoreversion

; ── JAR files  ->  {app}\ARWeb-Scanner ───────────────────────────────────────
Source: "{#SrcScanner}\AR_Web_Engine-4.2.jar";  DestDir: "{app}\ARWeb-Scanner"; Flags: ignoreversion
Source: "{#SrcScanner}\AR_Web_Scanner-4.2.jar"; DestDir: "{app}\ARWeb-Scanner"; Flags: ignoreversion

[Icons]
; No shortcuts needed for a hotfix

[Run]
; No post-install steps
