@echo off
rem O Maven corre dentro do Dockerfile (build stage), nao e preciso JDK no host.
docker build -t saigt-api:local .
if errorlevel 1 exit /b 1
echo Image saigt-api:local built.
