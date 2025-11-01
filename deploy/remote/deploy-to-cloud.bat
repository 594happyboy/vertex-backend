@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Vertex Backend - Deploy Script
REM ============================================

cd /d "%~dp0\..\..\"

set SERVER_IP=47.109.191.242
set SERVER_USER=root
set SERVER_PATH=/opt/vertex-backend

goto main_menu

:build_and_upload
echo.
echo ========================================
echo Build and Upload
echo ========================================
echo.
echo Current directory: %CD%
echo.

echo [1/4] Building JAR file...
echo ----------------------------------------
call gradlew.bat :app-bootstrap:bootJar --no-daemon
if errorlevel 1 (
    echo [ERROR] Build failed
    echo.
    pause
    goto main_menu
)
echo [DONE] Build success
echo.

if not exist "app-bootstrap\build\libs\vertex-backend.jar" (
    echo [ERROR] JAR file not found
    echo.
    pause
    goto main_menu
)

echo [2/4] Checking files...
echo ----------------------------------------
set "FILES_OK=1"
if not exist "deploy\schema.sql" (
    echo [ERROR] schema.sql not found
    set "FILES_OK=0"
)
if not exist "deploy\remote\docker-compose.yml" (
    echo [ERROR] docker-compose.yml not found
    set "FILES_OK=0"
)
if not exist "deploy\remote\Dockerfile" (
    echo [ERROR] Dockerfile not found
    set "FILES_OK=0"
)

if "!FILES_OK!"=="0" (
    echo.
    echo [ERROR] Missing required files
    echo.
    pause
    goto main_menu
)
echo [DONE] All files checked
echo.

echo [3/4] Packing files...
echo ----------------------------------------
set TEMP_DIR=temp_deploy_%RANDOM%
set TEMP_ARCHIVE=deploy_%RANDOM%.tar.gz

echo Creating temp directory...
mkdir "%TEMP_DIR%" 2>nul
if not exist "%TEMP_DIR%" (
    echo [ERROR] Failed to create temp directory
    echo.
    pause
    goto main_menu
)

echo Copying files to temp directory...
copy /Y "app-bootstrap\build\libs\vertex-backend.jar" "%TEMP_DIR%\" >nul
if errorlevel 1 goto copy_error

copy /Y "deploy\schema.sql" "%TEMP_DIR%\" >nul
if errorlevel 1 goto copy_error

copy /Y "deploy\remote\docker-compose.yml" "%TEMP_DIR%\" >nul
if errorlevel 1 goto copy_error

copy /Y "deploy\remote\Dockerfile" "%TEMP_DIR%\" >nul
if errorlevel 1 goto copy_error

echo [DONE] Files copied
echo.

echo Checking tar command...
where tar >nul 2>&1
if errorlevel 1 (
    echo [WARNING] tar not found, using traditional upload
    goto traditional_upload
)

echo Packing files...
tar -czf "%TEMP_ARCHIVE%" -C "%TEMP_DIR%" .
if errorlevel 1 (
    echo [WARNING] Pack failed, using traditional upload
    del "%TEMP_ARCHIVE%" 2>nul
    goto traditional_upload
)
echo [DONE] Pack complete
echo.

echo [4/4] Uploading to server...
echo ----------------------------------------
echo NOTE: Enter password once to complete all operations
echo.

echo Uploading archive...
echo Local archive: %TEMP_ARCHIVE%
scp -o StrictHostKeyChecking=no "%TEMP_ARCHIVE%" %SERVER_USER%@%SERVER_IP%:/tmp/
if errorlevel 1 (
    echo [ERROR] Upload failed
    del "%TEMP_ARCHIVE%" 2>nul
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)
echo [DONE] Upload complete
echo.

echo Extracting on server...
echo Target path: %SERVER_PATH%
echo Archive name: %TEMP_ARCHIVE%
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "echo '[Step 1/6] Verifying uploaded archive...' && ls -lh /tmp/%TEMP_ARCHIVE% && echo '[Step 2/6] Creating target directory...' && mkdir -p %SERVER_PATH% && echo '[Step 3/6] Listing files before extract...' && ls -l %SERVER_PATH%/ 2>/dev/null || echo '  Directory is empty or new' && echo '[Step 4/6] Removing old files...' && cd %SERVER_PATH% && rm -f vertex-backend.jar schema.sql docker-compose.yml Dockerfile && echo '[Step 5/6] Extracting archive...' && tar -xzvf /tmp/%TEMP_ARCHIVE% && echo '[Step 6/6] Verifying extracted files...' && ls -lh %SERVER_PATH%/ && echo '[Cleanup] Removing temp archive...' && rm -f /tmp/%TEMP_ARCHIVE% && echo '[SUCCESS] All operations completed'"
if errorlevel 1 (
    echo [ERROR] Extract failed
    del "%TEMP_ARCHIVE%" 2>nul
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)
echo [DONE] Extract complete

echo.
echo Cleaning temp files...
del "%TEMP_ARCHIVE%" 2>nul
rd /s /q "%TEMP_DIR%" 2>nul
goto upload_success

:traditional_upload
echo.
echo Using traditional upload (multiple password prompts)
echo.

echo Creating server directory...
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "mkdir -p %SERVER_PATH%"
if errorlevel 1 (
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)

echo Uploading vertex-backend.jar...
scp -o StrictHostKeyChecking=no "%TEMP_DIR%\vertex-backend.jar" %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)

echo Uploading schema.sql...
scp -o StrictHostKeyChecking=no "%TEMP_DIR%\schema.sql" %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)

echo Uploading docker-compose.yml...
scp -o StrictHostKeyChecking=no "%TEMP_DIR%\docker-compose.yml" %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)

echo Uploading Dockerfile...
scp -o StrictHostKeyChecking=no "%TEMP_DIR%\Dockerfile" %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)

rd /s /q "%TEMP_DIR%" 2>nul
goto upload_success

:upload_success
echo.
echo ========================================
echo [DONE] All files uploaded successfully
echo ========================================
echo.
pause
goto main_menu

:main_menu
cls
echo.
echo ========================================
echo Vertex Backend Server Management
echo ========================================
echo.
echo Server: %SERVER_USER%@%SERVER_IP%
echo Deploy path: %SERVER_PATH%
echo.
echo [Deployment and Update]
echo 1. Build and Upload JAR       - Build locally and upload to server
echo 2. Update Backend Only        - Rebuild backend and start [MOST USED]
echo 3. Rebuild All Services       - Rebuild all and start (MySQL/Redis/MinIO/Backend)
echo.
echo [Monitoring]
echo 4. View Logs                  - View real-time application logs
echo 5. Check Status               - Check service status and resources
echo.
echo [Control]
echo 6. Restart Backend Only       - Quick restart backend (no rebuild, for stuck app)
echo 7. Restart All Services       - Restart all services (no rebuild)
echo 8. Stop All Services          - Stop all running services
echo 9. Manual Guide               - Show manual commands
echo 0. Exit
echo.
set /p CHOICE="Enter option (0-9): "

if "%CHOICE%"=="" goto main_menu
if "%CHOICE%"=="1" goto build_and_upload
if "%CHOICE%"=="2" goto option_update_backend
if "%CHOICE%"=="3" goto option_rebuild_all
if "%CHOICE%"=="4" goto option_logs
if "%CHOICE%"=="5" goto option_status
if "%CHOICE%"=="6" goto option_restart_backend
if "%CHOICE%"=="7" goto option_restart_all
if "%CHOICE%"=="8" goto option_stop
if "%CHOICE%"=="9" goto option_manual
if "%CHOICE%"=="0" goto end_script

cls
echo.
echo [ERROR] Invalid option
echo.
timeout /t 2 >nul
goto main_menu

:option_update_backend
cls
echo.
echo ========================================
echo Update Backend Only
echo ========================================
echo.
echo [SCOPE] Backend service ONLY
echo [INFO] This will rebuild backend image using new JAR file
echo [INFO] This will apply backend environment variable changes
echo [INFO] Will automatically START backend after rebuild
echo [NOTE] MySQL, Redis, MinIO will NOT be affected
echo [USE CASE] Use after uploading new JAR (Option 1) or config changes
echo.
echo NOTE: Enter password ONCE for all operations
echo.
echo Updating vertex-backend container...
echo.
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose up -d --build --force-recreate --no-deps vertex-backend && echo && docker compose ps"
if errorlevel 1 (
    echo.
    echo [ERROR] Failed to rebuild and start container
    echo.
    echo Troubleshooting:
    echo 1. Check if JAR file exists on server
    echo 2. Check if Dockerfile is valid
    echo 3. Check Docker service is running
)
goto after_operation

:option_rebuild_all
cls
echo.
echo ========================================
echo Rebuild All Services
echo ========================================
echo.
echo [SCOPE] ALL services (MySQL + Redis + MinIO + Backend)
echo [WARNING] This will rebuild and restart ALL services
echo [INFO] Will automatically START all services after rebuild
echo [IMPACT] Database connections will be interrupted briefly
echo [IMPACT] Redis cache will be reset
echo [IMPACT] MinIO file service will restart
echo [USE CASE] Use when docker-compose.yml is modified or dependencies need reset
echo.
set /p CONFIRM="Confirm rebuild ALL services? (y/n): "
if /i not "%CONFIRM%"=="y" (
    echo.
    echo Operation cancelled
    goto after_operation
)
echo.
echo NOTE: Enter password ONCE for all operations
echo.
echo Rebuilding all services...
echo.
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker stop vertex-backend vertex-mysql vertex-redis vertex-minio 2>/dev/null || true && docker rm -f vertex-backend vertex-mysql vertex-redis vertex-minio 2>/dev/null || true && docker compose down 2>/dev/null || true && docker compose up -d --build --force-recreate && echo && docker compose ps"
if errorlevel 1 (
    echo.
    echo [ERROR] Failed to rebuild and start services
    echo.
    echo Troubleshooting:
    echo 1. Check if all required files exist on server
    echo 2. Check if docker-compose.yml is valid
    echo 3. Check Docker service is running
    echo 4. Try manual rebuild: ssh to server and run:
    echo    cd %SERVER_PATH% ^&^& docker compose down ^&^& docker compose up -d --build
)
goto after_operation

:option_restart_backend
cls
echo.
echo ========================================
echo Restart Backend Only
echo ========================================
echo.
echo [SCOPE] Backend service ONLY
echo [INFO] This will restart the backend container
echo [NOTE] Will NOT rebuild image or use new JAR
echo [NOTE] Will NOT apply environment variable changes
echo [NOTE] MySQL, Redis, MinIO will NOT be affected
echo [USE CASE] Use this only if backend is stuck or frozen
echo.
echo Restarting backend...
echo.
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose restart vertex-backend && echo && docker compose ps vertex-backend"
if errorlevel 1 (
    echo.
    echo [ERROR] Failed to restart container
    echo Check if the container exists and the service is accessible
)
goto after_operation

:option_restart_all
cls
echo.
echo ========================================
echo Restart All Services
echo ========================================
echo.
echo [SCOPE] ALL services (MySQL + Redis + MinIO + Backend)
echo [INFO] This will restart all running services
echo [NOTE] Will NOT rebuild images or use new JAR
echo [NOTE] Will NOT apply configuration changes
echo [IMPACT] Brief interruption to all services
echo [USE CASE] Use when all services are stuck or need quick restart
echo.
set /p CONFIRM="Confirm restart ALL services? (y/n): "
if /i not "%CONFIRM%"=="y" (
    echo.
    echo Operation cancelled
    goto after_operation
)
echo.
echo Restarting all services...
echo.
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose restart && echo && docker compose ps"
if errorlevel 1 (
    echo.
    echo [ERROR] Failed to restart services
    echo Check the error messages above
)
goto after_operation

:option_logs
cls
echo.
echo ========================================
echo View Logs
echo ========================================
echo.
echo [INFO] Showing real-time application logs
echo [INFO] Press Ctrl+C to exit
echo.
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose logs -f vertex-backend"
goto after_operation

:option_status
cls
echo.
echo ========================================
echo Check Status
echo ========================================
echo.
echo [INFO] Checking service status and resource usage
echo.
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose ps && echo && echo 'Resource usage:' && docker stats --no-stream vertex-backend 2>/dev/null || echo '  Container not running'"
goto after_operation

:option_stop
cls
echo.
echo ========================================
echo Stop All Services
echo ========================================
echo.
echo [WARNING] This will stop all services (Backend, MySQL, Redis, MinIO)
echo [NOTE] Data volumes will be preserved
echo.
set /p CONFIRM="Confirm stop? (y/n): "
if /i not "%CONFIRM%"=="y" (
    echo.
    echo Operation cancelled
    goto after_operation
)
echo.
echo Stopping all services...
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose down"
if errorlevel 1 (
    echo.
    echo [ERROR] Failed to stop services
    echo Check the error messages above
)
goto after_operation

:option_manual
cls
echo.
echo ========================================
echo Manual Guide
echo ========================================
echo.
echo Connect to server:
echo   ssh %SERVER_USER%@%SERVER_IP%
echo.
echo Go to deploy directory:
echo   cd %SERVER_PATH%
echo.
echo Common commands:
echo   Update backend only:     docker compose up -d --build --force-recreate --no-deps vertex-backend
echo   Rebuild all services:    docker compose down ^&^& docker compose up -d --build --force-recreate
echo   Restart backend only:    docker compose restart vertex-backend
echo   Restart all services:    docker compose restart
echo   View logs:               docker compose logs -f vertex-backend
echo   Check status:            docker compose ps
echo   Stop all services:       docker compose down
echo.
echo Common workflow:
echo   1. Upload new JAR:       Use Option 1 in this script
echo   2. Update backend:       Use Option 2 in this script
echo   3. View logs:            Use Option 4 in this script
echo.
echo Rebuild all workflow:
echo   1. Upload new JAR:       Use Option 1 in this script
echo   2. Rebuild all:          Use Option 3 in this script (includes dependencies)
echo   3. Check status:         Use Option 5 in this script
echo.
echo Troubleshooting:
echo   Backend stuck:           Use Option 6 (restart backend only)
echo   All services stuck:      Use Option 7 (restart all)
echo   Config changes:          Use Option 2 (backend) or Option 3 (all)
echo.
goto after_operation

:after_operation
echo.
echo ----------------------------------------
set /p CONTINUE="Continue other operations? (y/n): "
if /i "%CONTINUE%"=="y" (
    goto main_menu
)
goto end_script

:end_script
cls
echo.
echo ========================================
echo Thank you for using!
echo ========================================
echo.
pause
exit /b 0

:copy_error
echo [ERROR] Failed to copy files
rd /s /q "%TEMP_DIR%" 2>nul
echo.
pause
goto main_menu

:upload_error
cls
echo.
echo ========================================
echo [ERROR] Upload failed
echo ========================================
echo.
echo Troubleshooting:
echo 1. Check network connection
echo 2. Verify server IP: %SERVER_IP%
echo 3. Verify password
echo 4. Check OpenSSH client installed
echo    Enable in Windows Settings
echo    Or install Git for Windows
echo.
echo Press any key to return...
pause >nul
goto main_menu

