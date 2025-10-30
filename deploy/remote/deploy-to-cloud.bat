@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================
REM Vertex Backend - 一键部署到云服务器脚本
REM ============================================

echo ========================================
echo   Vertex Backend 云服务器部署脚本
echo ========================================
echo.

REM 云服务器配置
set SERVER_IP=47.109.191.242
set SERVER_USER=root
set SERVER_PATH=/opt/vertex-backend

echo [1/5] 开始构建 JAR 文件...
echo ----------------------------------------
call gradlew.bat :app-bootstrap:bootJar --no-daemon
if errorlevel 1 (
    echo ❌ 构建失败！
    pause
    exit /b 1
)
echo ✅ 构建成功！
echo.

REM 检查 JAR 文件是否存在
if not exist "app-bootstrap\build\libs\vertex-backend.jar" (
    echo ❌ 找不到 JAR 文件！
    pause
    exit /b 1
)

echo [2/5] 检查文件是否存在...
echo ----------------------------------------
set "FILES_OK=1"
if not exist "..\schema.sql" (
    echo ❌ 找不到 schema.sql
    set "FILES_OK=0"
)
if not exist "docker-compose.yml" (
    echo ❌ 找不到 docker-compose.yml
    set "FILES_OK=0"
)
if not exist "Dockerfile" (
    echo ❌ 找不到 Dockerfile
    set "FILES_OK=0"
)

if "!FILES_OK!"=="0" (
    echo.
    echo ❌ 缺少必要文件！
    pause
    exit /b 1
)
echo ✅ 所有文件检查通过！
echo.

echo [3/5] 打包部署文件...
echo ----------------------------------------
set TEMP_DIR=temp_deploy_%RANDOM%
set TEMP_ARCHIVE=deploy_%RANDOM%.tar

echo 创建临时目录...
mkdir %TEMP_DIR% 2>nul
copy /Y app-bootstrap\build\libs\vertex-backend.jar %TEMP_DIR%\ >nul
copy /Y ..\schema.sql %TEMP_DIR%\ >nul
copy /Y docker-compose.yml %TEMP_DIR%\ >nul
copy /Y Dockerfile %TEMP_DIR%\ >nul

echo 检查 tar 命令是否可用...
where tar >nul 2>&1
if errorlevel 1 (
    echo ⚠️  未找到 tar 命令，将使用传统方式上传（需要多次输入密码）
    goto traditional_upload
)

echo 打包文件...
tar -czf %TEMP_ARCHIVE% -C %TEMP_DIR% .
if errorlevel 1 (
    echo ⚠️  打包失败，将使用传统方式上传
    goto traditional_upload
)
echo ✅ 打包完成！
echo.

echo [4/5] 上传并部署到云服务器...
echo ----------------------------------------
echo 🔑 只需输入一次密码即可完成所有操作...
echo.

echo 正在上传打包文件...
scp -o StrictHostKeyChecking=no %TEMP_ARCHIVE% %SERVER_USER%@%SERVER_IP%:/tmp/
if errorlevel 1 (
    echo ❌ 上传失败！
    del %TEMP_ARCHIVE% 2>nul
    rd /s /q %TEMP_DIR% 2>nul
    goto upload_error
)

echo 正在服务器上解压并部署...
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "mkdir -p %SERVER_PATH% && cd %SERVER_PATH% && tar -xzf /tmp/%TEMP_ARCHIVE% && rm -f /tmp/%TEMP_ARCHIVE% && echo '✅ 压缩包已解压到: %SERVER_PATH%' && echo '' && echo '📁 部署文件列表：' && ls -lh vertex-backend.jar schema.sql docker-compose.yml Dockerfile 2>/dev/null | awk '{if(NR>1) print \"   \" $9 \" (\" $5 \")\"}'"
if errorlevel 1 (
    echo ❌ 解压失败！
    del %TEMP_ARCHIVE% 2>nul
    rd /s /q %TEMP_DIR% 2>nul
    goto upload_error
)

echo.
echo 清理本地临时文件...
del %TEMP_ARCHIVE% 2>nul
rd /s /q %TEMP_DIR% 2>nul

echo ✅ 所有文件已成功部署到服务器！
echo.
goto deploy_finish

:traditional_upload
echo.
echo 使用传统方式上传文件（需要多次输入密码）...
echo.

echo 创建服务器目录...
ssh -o StrictHostKeyChecking=no %SERVER_USER%@%SERVER_IP% "mkdir -p %SERVER_PATH%"
if errorlevel 1 (
    rd /s /q %TEMP_DIR% 2>nul
    goto upload_error
)

echo 上传 vertex-backend.jar...
scp -o StrictHostKeyChecking=no %TEMP_DIR%\vertex-backend.jar %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q %TEMP_DIR% 2>nul
    goto upload_error
)

echo 上传 schema.sql...
scp -o StrictHostKeyChecking=no %TEMP_DIR%\schema.sql %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q %TEMP_DIR% 2>nul
    goto upload_error
)

echo 上传 docker-compose.yml...
scp -o StrictHostKeyChecking=no %TEMP_DIR%\docker-compose.yml %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q %TEMP_DIR% 2>nul
    goto upload_error
)

echo 上传 Dockerfile...
scp -o StrictHostKeyChecking=no %TEMP_DIR%\Dockerfile %SERVER_USER%@%SERVER_IP%:%SERVER_PATH%/
if errorlevel 1 (
    rd /s /q %TEMP_DIR% 2>nul
    goto upload_error
)

rd /s /q %TEMP_DIR% 2>nul
echo ✅ 所有文件上传成功！
echo.

:deploy_finish

echo [5/5] 显示部署后续步骤...
echo ----------------------------------------
echo.
echo 📋 文件已上传到云服务器，接下来需要手动执行以下命令：
echo.
echo 1. 连接到云服务器：
echo    ssh root@%SERVER_IP%
echo.
echo 2. 进入部署目录：
echo    cd %SERVER_PATH%
echo.
echo 3. 启动服务（首次部署）：
echo    docker-compose up -d
echo.
echo 4. 查看日志：
echo    docker-compose logs -f vertex-backend
echo.
echo ========================================
echo   部署完成！✅
echo ========================================
echo.
pause
exit /b 0

:upload_error
echo.
echo ❌ 文件上传失败！
echo.
echo 💡 故障排查：
echo 1. 检查网络连接是否正常
echo 2. 确认服务器 IP 地址正确：%SERVER_IP%
echo 3. 确认密码输入正确
echo 4. 检查是否安装了 OpenSSH 客户端
echo    - Windows 10+ 自带，在 "设置 → 应用 → 可选功能" 中启用
echo    - 或者安装 Git for Windows
echo.
pause
exit /b 1

