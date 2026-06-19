@echo off
setlocal EnableExtensions
title Exportar relacion por matricula Gestion Trafico

cd /d "%~dp0"

echo.
echo Exportacion relacionada por matricula
echo Carpeta actual: %CD%
echo.

if not exist "%~dp0exportar_relacion_matricula_gestion_trafico.ps1" (
    echo No encuentro exportar_relacion_matricula_gestion_trafico.ps1 en esta carpeta.
    echo Copia este BAT junto al archivo PS1 y vuelve a ejecutarlo.
    echo.
    pause
    exit /b 1
)

set "OPCIONES=C:\Program Files (x86)\Gestion Trafico 5.0\Opciones.xml"
set /p "MATRICULA=Introduce la matricula a buscar, sin espacios ni guiones: "

if "%MATRICULA%"=="" (
    echo.
    echo No se ha indicado matricula. Cancelado.
    pause
    exit /b 1
)

set /p "MAX_ROWS=Maximo de filas por bloque [500]: "
if "%MAX_ROWS%"=="" set "MAX_ROWS=500"

echo.
echo Ejecutando exportacion relacionada...
echo Matricula: %MATRICULA%
echo MaxRows: %MAX_ROWS%
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0exportar_relacion_matricula_gestion_trafico.ps1" -OpcionesPath "%OPCIONES%" -Matricula "%MATRICULA%" -MaxRows %MAX_ROWS%

echo.
echo Proceso terminado.
echo Copia aqui el ZIP generado en el Escritorio.
echo Si ves errores arriba, copia el texto.
pause
