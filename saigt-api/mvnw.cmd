@echo off
setlocal
set BASEDIR=%~dp0
set WRAPPER_JAR=%BASEDIR%.mvn\wrapper\maven-wrapper.jar
set WRAPPER_PROPERTIES=%BASEDIR%.mvn\wrapper\maven-wrapper.properties

if not exist "%WRAPPER_JAR%" (
  for /f "tokens=2 delims==" %%A in ('findstr /b "wrapperUrl=" "%WRAPPER_PROPERTIES%"') do set WRAPPER_URL=%%A
  echo Downloading Maven Wrapper from %WRAPPER_URL% ...
  powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
)

java -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
