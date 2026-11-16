@echo off
rem Uso: build.bat [production|development]  (default: production)
rem O ng build corre dentro do Dockerfile, nao e preciso Node no host.
set CONFIG=%1
if "%CONFIG%"=="" set CONFIG=production
docker build --build-arg BUILD_CONFIG=%CONFIG% -t saigt-ui:local .
if errorlevel 1 exit /b 1
echo Image saigt-ui:local built (%CONFIG%).
