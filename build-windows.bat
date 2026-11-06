@echo off
rem ---------------------------------------------------------------------------
rem  Fog Is Coming - Windows build helper.
rem
rem  Same as "gradlew.bat build", but works around JDK/Gradle bugs on Windows
rem  accounts whose user name contains non-Latin characters (Cyrillic etc.):
rem    * "java.io.IOException: Unable to establish loopback connection"
rem    * "Could not find or load main class ...GradleWorkerMain"
rem  Both are caused by non-ASCII paths of the temp folder and of the Gradle
rem  home (%USERPROFILE%\.gradle). This script moves both to C:\fogtmp.
rem
rem  IMPORTANT: the project folder itself must also have an ASCII-only path,
rem  e.g. C:\mods\FogIsComing (not inside C:\Users\<cyrillic name>\...).
rem ---------------------------------------------------------------------------
setlocal
set "FOG_TMP=%SystemDrive%\fogtmp"
if not exist "%FOG_TMP%" mkdir "%FOG_TMP%"
set "TEMP=%FOG_TMP%"
set "TMP=%FOG_TMP%"
set "GRADLE_USER_HOME=%FOG_TMP%\gradle-home"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%FOG_TMP% -Djava.io.tmpdir=%FOG_TMP%"
call "%~dp0gradlew.bat" build %*
set "RC=%ERRORLEVEL%"
if "%RC%"=="0" (
  echo.
  echo Build OK. The mod jar is in build\libs\
)
endlocal & exit /b %RC%
