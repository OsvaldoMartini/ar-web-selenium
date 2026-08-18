@echo off

REM Set base directories (relative)
SET "BASE_DIR=%~dp0"  REM This gets the current script's directory
SET "JAVA_HOME=%BASE_DIR%java"
SET "ARWEB_CONFIG=D:\Projects\ARWeb-Martini\Config-4.7\ARWeb.config"

REM Add Java to PATH
SET "PATH=%JAVA_HOME%\bin;%PATH%"

REM Change to working directory
cd /d "%BASE_DIR%"

REM Run backend/React container app
java ^
-Djava.library.path=./javaJCE ^
-jar AR_Web_Scanner-4.7.jar -c "%ARWEB_CONFIG%"


REM pause
