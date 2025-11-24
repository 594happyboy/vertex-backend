@echo off
setlocal EnableExtensions EnableDelayedExpansion

for /f "tokens=2 delims=: " %%a in ('chcp') do set "ORIGINAL_CP=%%a"
set "ORIGINAL_CP=%ORIGINAL_CP: =%"
if not "%ORIGINAL_CP%"=="65001" (
    chcp 65001 >nul
    set "RESTORE_CP=%ORIGINAL_CP%"
)

REM ============================================
REM Vertex Backend - 部署脚本
REM ============================================

cd /d "%~dp0\..\.."

set SERVER_IP=142.171.169.111
set SERVER_USER=root
set SERVER_PATH=/opt/docker/vertex-backend

REM SSH 配置（默认查找 %USERPROFILE%\.ssh 下的常用密钥）
set "SSH_KEY_DIR=%USERPROFILE%\.ssh"
set "SSH_KEY_FILE="

if exist "%SSH_KEY_DIR%\id_ed25519" set "SSH_KEY_FILE=%SSH_KEY_DIR%\id_ed25519"
if not defined SSH_KEY_FILE if exist "%SSH_KEY_DIR%\id_rsa" set "SSH_KEY_FILE=%SSH_KEY_DIR%\id_rsa"
if not defined SSH_KEY_FILE if exist "%SSH_KEY_DIR%\id_ecdsa" set "SSH_KEY_FILE=%SSH_KEY_DIR%\id_ecdsa"
if not defined SSH_KEY_FILE if exist "%SSH_KEY_DIR%\id_dsa" set "SSH_KEY_FILE=%SSH_KEY_DIR%\id_dsa"

if defined SSH_KEY_FILE (
    echo [信息] 使用 SSH 密钥：%SSH_KEY_FILE%
    set "SSH_OPTIONS=-o StrictHostKeyChecking=no -i \"%SSH_KEY_FILE%\" -o IdentitiesOnly=yes -o PreferredAuthentications=publickey -o PasswordAuthentication=no"
) else (
    echo [警告] 在 %SSH_KEY_DIR% 下未找到 SSH 私钥，将尝试密码认证
    set "SSH_OPTIONS=-o StrictHostKeyChecking=no"
)

goto main_menu_level1

:build_and_upload
echo.
echo ========================================
echo 构建并上传
echo ========================================
echo.
echo 当前工作目录：%CD%
echo.

echo [1/4] 正在构建 JAR 文件...
echo ----------------------------------------
call gradlew.bat :app-bootstrap:bootJar --no-daemon
if errorlevel 1 (
    echo [错误] 构建失败
    echo.
    pause
    goto main_menu_level1
)
echo [完成] 构建成功
echo.

if not exist "app-bootstrap\build\libs\vertex-backend.jar" (
    echo [错误] 未找到生成的 JAR：app-bootstrap\build\libs\vertex-backend.jar
    echo.
    pause
    goto main_menu_level1
)

echo [2/4] 正在检查必需文件...
echo ----------------------------------------
set "FILES_OK=1"
for %%F in (deploy\schema.sql deploy\remote\docker-compose.yml deploy\remote\Dockerfile) do (
    if not exist "%%~F" (
        echo [错误] 未找到文件：%%~F
        set "FILES_OK=0"
    )
)

if "!FILES_OK!"=="0" (
    echo.
    echo [错误] 缺少所需文件，操作终止
    echo.
    pause
    goto main_menu_level1
)
echo [完成] 文件校验通过
echo.

echo [3/4] 正在准备部署文件...
echo ----------------------------------------
set "TEMP_DIR=temp_deploy_%RANDOM%"
set "TEMP_ARCHIVE=deploy_%RANDOM%.tar.gz"

echo 正在创建临时目录...
mkdir "%TEMP_DIR%" 2>nul
if not exist "%TEMP_DIR%" (
    echo [错误] 创建临时目录失败
    echo.
    pause
    goto main_menu_level1
)

echo 正在复制 JAR 文件...
copy /Y "app-bootstrap\build\libs\vertex-backend.jar" "%TEMP_DIR%\" >nul
if errorlevel 1 goto copy_error

echo 正在复制其他文件...
for %%F in (deploy\schema.sql deploy\remote\docker-compose.yml deploy\remote\Dockerfile) do (
    copy /Y "%%~F" "%TEMP_DIR%\" >nul
    if errorlevel 1 goto copy_error
)
echo [完成] 文件复制完成
echo.

echo 正在检查 tar 命令...
where tar >nul 2>&1
if errorlevel 1 (
    echo [警告] 未检测到 tar，将使用逐文件上传方式
    goto traditional_upload
)

echo 正在打包文件...
tar -czf "%TEMP_ARCHIVE%" -C "%TEMP_DIR%" .
if errorlevel 1 (
    echo [警告] 打包失败，将使用逐文件上传方式
    del "%TEMP_ARCHIVE%" 2>nul
    goto traditional_upload
)
echo [完成] 打包完成
echo.

echo [4/4] 正在上传至服务器...
echo ----------------------------------------
echo 本地压缩包：%TEMP_ARCHIVE%
scp %SSH_OPTIONS% "%TEMP_ARCHIVE%" %SERVER_USER%@%SERVER_IP%:/tmp/
if errorlevel 1 (
    echo [错误] 上传压缩包失败
    del "%TEMP_ARCHIVE%" 2>nul
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)
echo [完成] 上传成功
echo.

echo 正在服务器端解压并替换文件...
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "echo '[步骤 1/6] 校验上传的压缩包...' && ls -lh /tmp/%TEMP_ARCHIVE% && echo '[步骤 2/6] 创建目标目录...' && mkdir -p %SERVER_PATH% && echo '[步骤 3/6] 列出解压前目录内容...' && ls -l %SERVER_PATH%/ 2>/dev/null || echo '  目录为空或尚未创建' && echo '[步骤 4/6] 清理旧文件...' && cd %SERVER_PATH% && rm -f vertex-backend.jar schema.sql docker-compose.yml Dockerfile && echo '[步骤 5/6] 解压新的部署包...' && tar -xzvf /tmp/%TEMP_ARCHIVE% && echo '[步骤 6/6] 验证解压结果...' && ls -lh %SERVER_PATH%/ && echo '[清理] 删除服务器临时压缩包...' && rm -f /tmp/%TEMP_ARCHIVE% && echo '[成功] 所有操作已完成'"
if errorlevel 1 (
    echo [错误] 服务器解压或替换失败
    del "%TEMP_ARCHIVE%" 2>nul
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)
echo [完成] 服务器处理成功
echo.

echo 正在清理本地临时文件...
del "%TEMP_ARCHIVE%" 2>nul
rd /s /q "%TEMP_DIR%" 2>nul
goto upload_success

:traditional_upload
echo.
echo 使用逐文件上传模式（在缺少 tar 或打包失败时启用）
echo.

echo 正在创建服务器目录...
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "mkdir -p %SERVER_PATH%"
if errorlevel 1 (
    rd /s /q "%TEMP_DIR%" 2>nul
    goto upload_error
)

for %%F in (vertex-backend.jar schema.sql docker-compose.yml Dockerfile) do (
    call :scp_upload_helper "%TEMP_DIR%\%%~F" "%SERVER_PATH%/"
    if errorlevel 1 (
        rd /s /q "%TEMP_DIR%" 2>nul
        goto upload_error
    )
)

rd /s /q "%TEMP_DIR%" 2>nul
goto upload_success

:scp_upload_helper
set "LOCAL_FILE=%~1"
set "REMOTE_DIR=%~2"
if "%REMOTE_DIR%"=="" set "REMOTE_DIR=%SERVER_PATH%/"
set "DISPLAY_NAME=%~nx1"

if not exist "%LOCAL_FILE%" (
    echo [错误] 未找到待上传文件：%LOCAL_FILE%
    exit /b 1
)

echo 正在上传 %DISPLAY_NAME%...
scp %SSH_OPTIONS% "%LOCAL_FILE%" %SERVER_USER%@%SERVER_IP%:"%REMOTE_DIR%%DISPLAY_NAME%"
if errorlevel 1 (
    echo [错误] 上传失败：%DISPLAY_NAME%
    exit /b 1
)
echo [完成] %DISPLAY_NAME% 上传完成
exit /b 0

:upload_success
echo.
echo ========================================
echo [完成] 文件上传与同步成功
echo ========================================
echo.
pause
goto main_menu_level1

:main_menu
goto main_menu_level1

:main_menu_level1
cls
echo.
echo ========================================
echo Vertex Backend 服务器管理
echo ========================================
echo.
echo 服务器：%SERVER_USER%@%SERVER_IP%
echo 部署目录：%SERVER_PATH%
echo.
echo ========================================
echo 主菜单
echo ========================================
echo.
echo 1. 部署与更新          - 构建、上传并重建服务
echo 2. 监控                - 查看日志与状态
echo 3. 控制                - 重启或停止服务
echo 4. 手动指令            - 查看常用命令
echo 5. 退出
echo.
set /p CHOICE="请输入选项 (1-5)： "

if "%CHOICE%"=="" goto main_menu_level1
if "%CHOICE%"=="1" goto menu_deployment
if "%CHOICE%"=="2" goto menu_monitoring
if "%CHOICE%"=="3" goto menu_control
if "%CHOICE%"=="4" goto option_manual
if "%CHOICE%"=="5" goto end_script

cls
echo.
echo [错误] 无效选项
echo.
timeout /t 2 >nul
goto main_menu_level1

:menu_deployment
cls
echo.
echo ========================================
echo 部署与更新
echo ========================================
echo.
echo 服务器：%SERVER_USER%@%SERVER_IP%
echo 部署目录：%SERVER_PATH%
echo.
echo 1. 构建并上传 JAR               - 本地构建并上传至服务器
echo 2. 仅更新后端                   - 重建并启动后端（最常用）
echo 3. 重建所有服务                 - 保留数据的完整重建
echo 4. 重建所有服务（删除全部数据） - 完全重置并清空数据
echo 0. 返回主菜单
echo.
set /p CHOICE="请输入选项 (0-4)： "

if "%CHOICE%"=="" goto menu_deployment
if "%CHOICE%"=="1" goto build_and_upload
if "%CHOICE%"=="2" goto option_update_backend
if "%CHOICE%"=="3" goto option_rebuild_all
if "%CHOICE%"=="4" goto option_rebuild_all_delete_volumes
if "%CHOICE%"=="0" goto main_menu_level1

cls
echo.
echo [错误] 无效选项
echo.
timeout /t 2 >nul
goto menu_deployment

:menu_monitoring
cls
echo.
echo ========================================
echo 监控
echo ========================================
echo.
echo 服务器：%SERVER_USER%@%SERVER_IP%
echo 部署目录：%SERVER_PATH%
echo.
echo 1. 查看日志            - 实时查看应用日志
echo 2. 检查状态            - 查看服务状态与资源
echo 0. 返回主菜单
echo.
set /p CHOICE="请输入选项 (0-2)： "

if "%CHOICE%"=="" goto menu_monitoring
if "%CHOICE%"=="1" goto option_logs
if "%CHOICE%"=="2" goto option_status
if "%CHOICE%"=="0" goto main_menu_level1

cls
echo.
echo [错误] 无效选项
echo.
timeout /t 2 >nul
goto menu_monitoring

:menu_control
cls
echo.
echo ========================================
echo 控制
echo ========================================
echo.
echo 服务器：%SERVER_USER%@%SERVER_IP%
echo 部署目录：%SERVER_PATH%
echo.
echo 1. 仅重启后端          - 快速重启后端（不重建）
echo 2. 重启全部服务        - 不重建镜像的整体重启
echo 3. 停止全部服务        - 停止所有容器
echo 0. 返回主菜单
echo.
set /p CHOICE="请输入选项 (0-3)： "

if "%CHOICE%"=="" goto menu_control
if "%CHOICE%"=="1" goto option_restart_backend
if "%CHOICE%"=="2" goto option_restart_all
if "%CHOICE%"=="3" goto option_stop
if "%CHOICE%"=="0" goto main_menu_level1

cls
echo.
echo [错误] 无效选项
echo.
timeout /t 2 >nul
goto menu_control

:option_update_backend
cls
echo.
echo ========================================
echo 仅更新后端
echo ========================================
echo.
echo [范围] 仅影响后端服务
echo [说明] 使用新的 JAR 构建镜像
echo [说明] 会应用后端环境变量变更
echo [说明] 完成后自动启动后端容器
echo [提示] 不会影响 MySQL / Redis / MinIO
echo [场景] 上传新 JAR 或修改后端配置后
echo.
echo 说明：如已配置 SSH 密钥，通常无需输入密码
echo.
echo 正在更新 vertex-backend 容器...
echo.
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose up -d --build --force-recreate --no-deps vertex-backend && echo && docker compose ps"
if errorlevel 1 (
    echo.
    echo [错误] 重建或启动容器失败
    echo.
    echo 排查建议：
    echo 1. 确认服务器上存在最新 JAR
    echo 2. 检查 Dockerfile 是否可用
    echo 3. 确认 Docker 服务已启动
)
goto after_operation

:option_rebuild_all
cls
echo.
echo ========================================
echo 重建所有服务
echo ========================================
echo.
echo [范围] MySQL + Redis + MinIO + 后端
echo [警告] 将重建并重启全部服务
echo [说明] 完成后自动启动所有服务
echo [影响] 数据库连接会短暂中断
echo [影响] Redis 缓存会被清空
echo [影响] MinIO 将重启
echo [场景] docker-compose.yml 或依赖改动
echo.
set /p CONFIRM="确认重建所有服务？(y/n)： "
if /i not "%CONFIRM%"=="y" (
    echo.
    echo 操作已取消
    goto after_operation
)
echo.
echo 说明：如已配置 SSH 密钥，通常无需输入密码
echo.
echo 正在重建所有服务...
echo.
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker stop vertex-backend vertex-mysql vertex-redis vertex-minio 2>/dev/null || true && docker rm -f vertex-backend vertex-mysql vertex-redis vertex-minio 2>/dev/null || true && docker compose down 2>/dev/null || true && docker compose up -d --build --force-recreate && echo && docker compose ps"
if errorlevel 1 (
    echo.
    echo [错误] 重建或启动失败
    echo.
    echo 排查建议：
    echo 1. 确认服务器上文件齐全
    echo 2. 检查 docker-compose.yml 是否有效
    echo 3. 确认 Docker 服务已启动
    echo 4. 亦可手动执行：
    echo    ssh %SERVER_USER%@%SERVER_IP%
    echo    cd %SERVER_PATH% ^&^& docker compose down ^&^& docker compose up -d --build
)
goto after_operation

:option_rebuild_all_delete_volumes
cls
echo.
echo ========================================
echo 重建所有服务（删除全部数据）
echo ========================================
echo.
echo [范围] MySQL + Redis + MinIO + 后端
echo [危险] 将删除所有卷及数据
echo [提醒] 这是不可逆的完全重置
echo [说明] 重建完成后自动启动所有服务
echo [影响] MySQL / Redis / MinIO 数据将被永久删除
echo [场景] 需要全新环境或清空数据
echo.
echo !!! 警告 !!!
echo 将永久删除：
echo   - 所有 MySQL 数据
echo   - 所有 Redis 数据
echo   - 所有 MinIO 文件
echo   - 所有 Docker 卷
echo.
echo 此操作不可撤销。
echo.
set /p CONFIRM="请输入 DELETE（大写）以确认： "
if not "%CONFIRM%"=="DELETE" (
    echo.
    echo [已取消] 输入不匹配，操作终止
    goto after_operation
)
echo.
set /p CONFIRM2="请再次确认 (y/n)： "
if /i not "%CONFIRM2%"=="y" (
    echo.
    echo 操作已取消
    goto after_operation
)
echo.
echo 说明：如已配置 SSH 密钥，通常无需输入密码
echo.
echo 正在删除所有卷并重建...
echo.
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose down -v && echo && echo 'All volumes deleted.' && echo 'Rebuilding all services...' && echo && docker compose up -d --build --force-recreate && echo && docker compose ps"
if errorlevel 1 (
    echo.
    echo [错误] 重建失败
    echo.
    echo 排查建议：
    echo 1. 确认服务器上文件齐全
    echo 2. 检查 docker-compose.yml 是否有效
    echo 3. 确认 Docker 服务已启动
    echo 4. 亦可手动执行：
    echo    ssh %SERVER_USER%@%SERVER_IP%
    echo    cd %SERVER_PATH% ^&^& docker compose down -v ^&^& docker compose up -d --build
)
goto after_operation

:option_restart_backend
cls
echo.
echo ========================================
echo 仅重启后端
echo ========================================
echo.
echo [范围] 仅影响后端服务
echo [说明] 仅重启容器，不会使用新 JAR
echo [说明] 不会应用配置变更
echo [提示] 不影响 MySQL / Redis / MinIO
echo [场景] 后端卡死但无需重建
echo.
echo 正在重启后端...
echo.
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose restart vertex-backend && echo && docker compose ps vertex-backend"
if errorlevel 1 (
    echo.
    echo [错误] 重启失败
    echo 请检查容器是否存在并可访问
)
goto after_operation

:option_restart_all
cls
echo.
echo ========================================
echo 重启全部服务
echo ========================================
echo.
echo [范围] MySQL + Redis + MinIO + 后端
echo [说明] 仅重启，不会重建镜像
echo [说明] 不会应用配置或 JAR 变更
echo [影响] 所有服务短暂中断
echo [场景] 所有服务卡死或需要快速重启
echo.
set /p CONFIRM="确认重启全部服务？(y/n)： "
if /i not "%CONFIRM%"=="y" (
    echo.
    echo 操作已取消
    goto after_operation
)
echo.
echo 正在重启所有服务...
echo.
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose restart && echo && docker compose ps"
if errorlevel 1 (
    echo.
    echo [错误] 重启失败
    echo 请参考上方错误信息
)
goto after_operation

:option_logs
cls
echo.
echo ========================================
echo 查看日志
echo ========================================
echo.
echo [信息] 正在输出实时日志，按 Ctrl+C 退出
echo.
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose logs -f vertex-backend"
goto after_operation

:option_status
cls
echo.
echo ========================================
echo 检查状态
echo ========================================
echo.
echo [信息] 查看服务状态与资源占用
echo.
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose ps && echo && echo 'Resource usage:' && docker stats --no-stream vertex-backend 2>/dev/null || echo '  Container not running'"
goto after_operation

:option_stop
cls
echo.
echo ========================================
echo 停止全部服务
echo ========================================
echo.
echo [警告] 将停止后端、MySQL、Redis、MinIO
echo [说明] 数据卷会被保留
echo.
set /p CONFIRM="确认停止？(y/n)： "
if /i not "%CONFIRM%"=="y" (
    echo.
    echo 操作已取消
    goto after_operation
)
echo.
echo 正在停止所有服务...
ssh %SSH_OPTIONS% %SERVER_USER%@%SERVER_IP% "cd %SERVER_PATH% && docker compose down"
if errorlevel 1 (
    echo.
    echo [错误] 停止失败
    echo 请参考上方错误信息
)
goto after_operation

:option_manual
cls
echo.
echo ========================================
echo 手动指令指南
echo ========================================
echo.
echo 连接服务器：
echo   ssh %SERVER_USER%@%SERVER_IP%
echo.
echo 切换到部署目录：
echo   cd %SERVER_PATH%
echo.
echo 常用命令：
echo   仅更新后端：      docker compose up -d --build --force-recreate --no-deps vertex-backend
echo   重建所有服务：    docker compose down ^&^& docker compose up -d --build --force-recreate
echo   完全重置（删数据）：docker compose down -v ^&^& docker compose up -d --build --force-recreate
echo   仅重启后端：      docker compose restart vertex-backend
echo   重启全部服务：    docker compose restart
echo   查看日志：        docker compose logs -f vertex-backend
echo   检查状态：        docker compose ps
echo   停止全部服务：    docker compose down
echo.
echo 常用流程：
echo   1. 上传新 JAR：   主菜单 ^> 1 ^> 1
echo   2. 更新后端：     主菜单 ^> 1 ^> 2
echo   3. 查看日志：     主菜单 ^> 2 ^> 1
echo.
echo 重建全量流程：
echo   1. 上传新 JAR：   主菜单 ^> 1 ^> 1
echo   2. 重建全部：     主菜单 ^> 1 ^> 3（保留数据）
echo   3. 检查状态：     主菜单 ^> 2 ^> 2
echo.
echo 完全重置流程：
echo   1. 上传新 JAR：   主菜单 ^> 1 ^> 1
echo   2. 全量重置：     主菜单 ^> 1 ^> 4（清空数据）
echo   3. 检查状态：     主菜单 ^> 2 ^> 2
echo.
echo 故障排查：
echo   后端卡死：       主菜单 ^> 3 ^> 1（仅重启后端）
echo   所有服务卡死：   主菜单 ^> 3 ^> 2（重启全部）
echo   配置变更：       主菜单 ^> 1 ^> 2（后端）或 ^> 1 ^> 3（全部）
echo.
goto after_operation

:after_operation
echo.
echo ----------------------------------------
set /p CONTINUE="是否继续执行其他操作？(y/n)： "
if /i "%CONTINUE%"=="y" (
    goto main_menu_level1
)
goto end_script

:end_script
cls
echo.
echo ========================================
echo 感谢使用 Vertex Backend 部署工具！
echo ========================================
echo.
if defined RESTORE_CP chcp %RESTORE_CP% >nul
pause
exit /b 0

:copy_error
echo [错误] 复制文件失败
rd /s /q "%TEMP_DIR%" 2>nul
echo.
pause
goto main_menu_level1

:upload_error
cls
echo.
echo ========================================
echo [错误] 上传失败
echo ========================================
echo.
echo 排查建议：
echo 1. 检查本地网络连接
echo 2. 核对服务器 IP：%SERVER_IP%
echo 3. 确认 SSH 密钥或密码配置正确
echo 4. 确认已安装并启用 OpenSSH 客户端（或安装 Git for Windows）
echo.
del "%TEMP_ARCHIVE%" 2>nul
rd /s /q "%TEMP_DIR%" 2>nul
echo 按任意键返回...
pause >nul
goto main_menu_level1
