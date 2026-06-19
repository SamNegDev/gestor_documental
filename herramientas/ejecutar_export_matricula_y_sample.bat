@echo off
setlocal EnableExtensions
title Exportar matricula y muestra Gestion Trafico

cd /d "%~dp0"

echo.
echo Exportacion acotada de Gestion Trafico
echo Carpeta actual: %CD%
echo.

if not exist "%~dp0exportar_bd_gestion_trafico.ps1" (
    echo No encuentro exportar_bd_gestion_trafico.ps1 en esta carpeta.
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

set /p "SAMPLE_ROWS=Filas de muestra por tabla [2]: "
if "%SAMPLE_ROWS%"=="" set "SAMPLE_ROWS=2"

set /p "MATRICULA_MAX_ROWS=Maximo de filas por tabla para la matricula [100]: "
if "%MATRICULA_MAX_ROWS%"=="" set "MATRICULA_MAX_ROWS=100"

echo.
echo Ejecutando exportacion...
echo Matricula: %MATRICULA%
echo SampleRows: %SAMPLE_ROWS%
echo MatriculaMaxRows: %MATRICULA_MAX_ROWS%
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0exportar_bd_gestion_trafico.ps1" -OpcionesPath "%OPCIONES%" -Matricula "%MATRICULA%" -SampleRows %SAMPLE_ROWS% -MatriculaMaxRows %MATRICULA_MAX_ROWS%

echo.
echo Proceso terminado.
echo Copia aqui la carpeta export_gestion_trafico_YYYYMMDD_HHMMSS o el ZIP generado en el Escritorio.
echo Si ves errores arriba, copia el texto.
pause
