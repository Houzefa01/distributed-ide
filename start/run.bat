@echo off
REM ================================================================================
REM Distributed IDE - Launcher Script (Windows)
REM Detects Java automatically, sets JVM options and launches the IDE
REM ================================================================================

setlocal enabledelayedexpansion

REM ================================================================================
REM CONFIGURATION
REM ================================================================================

set "SCRIPT_DIR=%~dp0"
set "IDE_HOME=%SCRIPT_DIR:~0,-1%"
set "LIB_DIR=%IDE_HOME%\lib"
set "LOG_DIR=%IDE_HOME%\logs"
set "CONFIG_DIR=%IDE_HOME%\config"

REM Create log directory if it doesn't exist
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM Log file with timestamp
for /f "tokens=2-4 delims=/ " %%a in ('date /t') do (set mydate=%%c%%a%%b)
for /f "tokens=1-2 delims=/:" %%a in ('time /t') do (set mytime=%%a%%b)
set "LOG_FILE=%LOG_DIR%\ide-%mydate%-%mytime%.log"

REM Main class
set "MAIN_CLASS=com.distributed_ide.javafx_client.DistributedIDEApp"

REM Java version check
set "MIN_JAVA_VERSION=17"
set "RECOMMENDED_JAVA_VERSION=21"

REM ================================================================================
REM UTILITY FUNCTIONS
REM ================================================================================

:print_info
    setlocal
    echo [INFO] %~1
    endlocal
    exit /b

:print_success
    setlocal
    echo [SUCCESS] %~1
    endlocal
    exit /b

:print_error
    setlocal
    echo [ERROR] %~1 1>&2
    endlocal
    exit /b

:print_warning
    setlocal
    echo [WARNING] %~1
    endlocal
    exit /b

REM ================================================================================
REM FIND JAVA
REM ================================================================================

:find_java
    call :print_info "Searching for Java..."
    
    REM Check JAVA_HOME environment variable
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" (
            set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
            call :print_success "Java found via JAVA_HOME"
            exit /b 0
        )
    )
    
    REM Try to find java in PATH
    for %%X in (java.exe) do (set "JAVA_CMD=%%~$PATH:X")
    if defined JAVA_CMD (
        call :print_success "Java found in PATH"
        exit /b 0
    )
    
    REM Try default installation paths
    if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set "JAVA_CMD=C:\Program Files\Java\jdk-21\bin\java.exe"
        call :print_success "Java found in default path"
        exit /b 0
    )
    
    if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
        set "JAVA_CMD=C:\Program Files\Java\jdk-17\bin\java.exe"
        call :print_success "Java found in default path"
        exit /b 0
    )
    
    REM Java not found
    call :print_error "Java not found!"
    echo.
    echo To resolve this problem:
    echo   1. Install Java 17+ (OpenJDK recommended)
    echo   2. Set JAVA_HOME environment variable:
    echo      set JAVA_HOME=C:\path\to\jdk
    echo   3. Rerun this script
    echo.
    exit /b 1

REM ================================================================================
REM CHECK JAVA VERSION
REM ================================================================================

:check_java_version
    call :print_info "Checking Java version..."
    
    for /f "tokens=*" %%i in ('"%JAVA_CMD%" -version 2^>^&1 ^| find "version"') do (
        set "JAVA_VERSION_STR=%%i"
    )
    
    for /f tokens^=2^ delims^=^" %%i in ("%JAVA_VERSION_STR%") do (
        set "JAVA_VERSION=%%i"
    )
    
    REM Extract major version
    for /f "tokens=1 delims=." %%i in ("%JAVA_VERSION%") do (
        set "JAVA_MAJOR=%%i"
    )
    
    if not defined JAVA_MAJOR (
        for /f "tokens=*" %%i in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr "release"') do (
            for /f "tokens=2" %%j in ("%%i") do (
                set "JAVA_MAJOR=%%j"
            )
        )
    )
    
    call :print_success "Java !JAVA_MAJOR! detected (!JAVA_VERSION!)"
    
    if not defined JAVA_MAJOR (
        call :print_error "Could not determine Java version"
        exit /b 1
    )
    
    if !JAVA_MAJOR! lss %MIN_JAVA_VERSION% (
        call :print_error "Java %MIN_JAVA_VERSION%+ is required (found: !JAVA_MAJOR!)"
        exit /b 1
    )
    
    if !JAVA_MAJOR! lss %RECOMMENDED_JAVA_VERSION% (
        call :print_warning "Java %RECOMMENDED_JAVA_VERSION% recommended for best performance"
    )
    
    exit /b 0

REM ================================================================================
REM CHECK DIRECTORIES
REM ================================================================================

:check_directories
    call :print_info "Checking directories..."
    
    if not exist "%LIB_DIR%" (
        call :print_error "Directory %LIB_DIR% not found"
        echo Make sure you have run: mvn clean package
        exit /b 1
    )
    
    if not exist "%IDE_HOME%\distributed-ide.jar" (
        call :print_error "File %IDE_HOME%\distributed-ide.jar not found"
        echo Make sure you have run: mvn clean package
        exit /b 1
    )
    
    call :print_success "Files found"
    exit /b 0

REM ================================================================================
REM BUILD CLASSPATH
REM ================================================================================

:build_classpath
    call :print_info "Building classpath..."
    
    set "CLASSPATH=%IDE_HOME%\distributed-ide.jar"
    
    for %%f in ("%LIB_DIR%\*.jar") do (
        set "CLASSPATH=!CLASSPATH!;%%f"
    )
    
    call :print_success "Classpath built"
    exit /b 0

REM ================================================================================
REM BUILD JVM OPTIONS
REM ================================================================================

:build_jvm_options
    call :print_info "Configuring JVM options..."
    
    set "JVM_OPTS="
    
    REM Memory
    set "JVM_OPTS=!JVM_OPTS! -Xms512m"
    set "JVM_OPTS=!JVM_OPTS! -Xmx2048m"
    
    REM Garbage collection optimized
    set "JVM_OPTS=!JVM_OPTS! -XX:+UseG1GC"
    set "JVM_OPTS=!JVM_OPTS! -XX:+UseStringDeduplication"
    set "JVM_OPTS=!JVM_OPTS! -XX:MaxGCPauseMillis=200"
    
    REM Encoding UTF-8
    set "JVM_OPTS=!JVM_OPTS! -Dfile.encoding=UTF-8"
    set "JVM_OPTS=!JVM_OPTS! -Dsun.jnu.encoding=UTF-8"
    
    REM Logs
    set "JVM_OPTS=!JVM_OPTS! -Dlogging.file=%LOG_FILE%"
    
    REM No headless mode (we want GUI)
    set "JVM_OPTS=!JVM_OPTS! -Djava.awt.headless=false"
    
    call :print_success "JVM options configured"
    exit /b 0

REM ================================================================================
REM LAUNCH IDE
REM ================================================================================

:launch_ide
    call :print_info "Launching Distributed IDE..."
    echo.
    
    REM Export variables for child processes
    endlocal & setlocal enabledelayedexpansion
    
    REM Debug line (uncomment to see full command):
    REM echo Debug: "%JAVA_CMD%" !JVM_OPTS! -cp "%CLASSPATH%" %MAIN_CLASS%
    
    REM Launch the IDE
    "%JAVA_CMD%" !JVM_OPTS! -cp "%CLASSPATH%" %MAIN_CLASS% %*
    
    set "LAUNCH_STATUS=%ERRORLEVEL%"
    
    if %LAUNCH_STATUS% equ 0 (
        call :print_success "IDE closed cleanly"
    ) else (
        call :print_error "IDE closed with status %LAUNCH_STATUS%"
    )
    
    exit /b %LAUNCH_STATUS%

REM ================================================================================
REM CLEANUP
REM ================================================================================

:cleanup
    if exist "%LOG_FILE%" (
        call :print_success "Logs saved to: %LOG_FILE%"
    )
    exit /b 0

REM ================================================================================
REM MAIN ENTRY POINT
REM ================================================================================

:main
    cls
    echo.
    echo ===========================================
    echo     Distributed IDE Launcher
    echo ===========================================
    echo.
    
    REM Step 1: Find Java
    call :find_java
    if errorlevel 1 exit /b 1
    
    REM Step 2: Check version
    call :check_java_version
    if errorlevel 1 exit /b 1
    
    REM Step 3: Check directories
    call :check_directories
    if errorlevel 1 exit /b 1
    
    REM Step 4: Build classpath
    call :build_classpath
    
    REM Step 5: Build JVM options
    call :build_jvm_options
    
    REM Step 6: Launch IDE
    call :launch_ide %*
    set "RESULT=%ERRORLEVEL%"
    
    REM Step 7: Cleanup
    call :cleanup
    
    exit /b %RESULT%

REM ================================================================================
REM START
REM ================================================================================

call :main %*
endlocal
