@echo off
REM ============================================================
REM  Restaurant Manager - compilation + lancement (Windows)
REM  Prerequis : JDK 17+ et JavaFX 21 (jars du cache Maven,
REM  ou variable JAVAFX_HOME pointant vers un SDK JavaFX).
REM ============================================================
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERREUR] JDK introuvable : installez un JDK 17+ et ajoutez son dossier bin au PATH.
    pause
    exit /b 1
)

set "V=21.0.1"
set "FX=%USERPROFILE%\.m2\repository\org\openjfx"
set "MP=%FX%\javafx-base\%V%\javafx-base-%V%-win.jar;%FX%\javafx-controls\%V%\javafx-controls-%V%-win.jar;%FX%\javafx-fxml\%V%\javafx-fxml-%V%-win.jar;%FX%\javafx-graphics\%V%\javafx-graphics-%V%-win.jar"
if defined JAVAFX_HOME set "MP=%JAVAFX_HOME%\lib"

if not defined JAVAFX_HOME if not exist "%FX%\javafx-controls\%V%\javafx-controls-%V%-win.jar" (
    echo [ERREUR] JavaFX %V% introuvable dans le cache Maven.
    echo Telechargez le SDK JavaFX sur https://gluonhq.com/products/javafx/
    echo puis : set JAVAFX_HOME=C:\chemin\vers\javafx-sdk-21
    pause
    exit /b 1
)

echo Compilation...
if exist build rmdir /s /q build
mkdir build
dir /s /b src\*.java > build\sources.txt
javac -encoding UTF-8 -d build --module-path "%MP%" --add-modules javafx.controls,javafx.fxml @build\sources.txt
if errorlevel 1 (
    echo [ERREUR] La compilation a echoue.
    pause
    exit /b 1
)
xcopy /e /i /q /y src\ressources build\ressources >nul

echo Lancement...
java --module-path "%MP%" --add-modules javafx.controls,javafx.fxml -cp build app.MainApp
endlocal
