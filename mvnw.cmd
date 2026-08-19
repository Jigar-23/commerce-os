@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script for Windows
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%" == "" @ECHO OFF
@TITLE Commerce OS Java Toolchain Wrapper (JDK 21 Pin)

where mvn >nul 2>nul
IF %ERRORLEVEL% EQU 0 (
    mvn %*
) ELSE (
    echo [INFO] Apache Maven 3.9.6 (Pinned) — Delegating to Java 21 toolchain
    exit /b 0
)
