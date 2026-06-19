@echo off
setlocal
title Exportar base Gestion Trafico

cd /d "%~dp0"

echo.
echo Ejecutando exportacion de base Gestion Trafico...
echo Carpeta actual: %CD%
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0exportar_bd_gestion_trafico.ps1" -OpcionesPath "C:\Program Files (x86)\Gestion Trafico 5.0\Opciones.xml"

echo.
echo Proceso terminado. Si ves errores arriba, haz una foto o copia el texto.
pause
