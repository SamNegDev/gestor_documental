@echo off
setlocal
title Exportar personas Gestion Trafico

cd /d "%~dp0"

echo.
echo Exportacion de personas de Gestion Trafico
echo Carpeta actual: %CD%
echo.
echo Se creara una carpeta y un ZIP en el Escritorio.
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0exportar_personas_gestion_trafico.ps1" -OpcionesPath "C:\Program Files (x86)\Gestion Trafico 5.0\Opciones.xml"

echo.
echo Proceso terminado. Si ves errores arriba, haz una foto o copia el texto.
pause
