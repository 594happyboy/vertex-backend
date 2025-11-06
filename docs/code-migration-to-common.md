# 代码迁移到 Common 模块 - 总结报告

**迁移日期**: 2025-11-06  
**目标**: 将 blog 模块中的通用代码迁移到 common 模块，提升代码复用性

---

## 📊 迁移概览

### ✅ 已迁移的组件

| 组件类别 | 原位置 (blog) | 新位置 (common) | 说明 |
|---------|--------------|----------------|------|
| **认证异常类** | `blog.exception.AuthException` | `common.exception.CustomException` | 8个认证相关异常类 |
| **JWT工具** | `blog.util.JwtUtil` | `common.util.JwtUtil` | Token 生成、解析、验证 |
| **认证上下文** | `blog.context.AuthContext` | `common.context.AuthContext` | 线程级用户上下文 |
| **Token配置** | `blog.config.TokenConfig` | `common.config.TokenConfig` | Token 刷新配置 |
| **认证常量** | `blog.constants.AuthConstants` | `common.constants.AuthConstants` | 认证相关常量 |

---

## 📝 详细迁移清单

### 1. 认证异常类

迁移到 `common/src/main/kotlin/com/zzy/common/exception/CustomException.kt`

**包含的异常类：**
- ✅ `AuthException` - 基础认证异常类
- ✅ `UnauthorizedException` - 未授权异常 (401)
- ✅ `ForbiddenException` - 禁止访问异常 (403)
- ✅ `InvalidTokenException` - 令牌无效异常 (401)
- ✅ `TokenExpiredException` - 令牌过期异常 (401)
- ✅ `UserNotFoundException` - 用户不存在异常 (404)
- ✅ `PasswordIncorrectException` - 密码错误异常 (401)
- ✅ `ResourceNotFoundException` - 资源不存在异常 (404)

### 2. JWT 工具类

**新位置**: `common/src/main/kotlin/com/zzy/common/util/JwtUtil.kt`

**功能：**
- 生成 AccessToken
- 解析 Token
- 验证 Token 有效性
- 提取用户信息（userId, username, role）

### 3. 认证上下文

**新位置**: `common/src/main/kotlin/com/zzy/common/context/AuthContext.kt`

**包含：**
- `AuthUser` - 用户信息数据类
- `AuthContextHolder` - ThreadLocal 上下文管理器

**功能：**
- 线程级用户上下文设置
- 获取当前登录用户
- 自动清理上下文

### 4. Token 配置

**新位置**: `common/src/main/kotlin/com/zzy/common/config/TokenConfig.kt`

**配置项：**
- `lockTimeout` - 分布式锁超时
- `tokenCacheTtl` - Token 缓存时长
- `refreshTokenTtl` - RefreshToken 有效期
- `gracePeriod` - Token 轮换宽限期

### 5. 认证常量

**新位置**: `common/src/main/kotlin/com/zzy/common/constants/AuthConstants.kt`

**常量：**
- `REFRESH_TOKEN_COOKIE_NAME` - Cookie 名称
- `NEW_ACCESS_TOKEN_HEADER` - 响应头名称
- `COOKIE_MAX_AGE` - Cookie 有效期
- `UNKNOWN` - 未知值标识

---

## 🔄 依赖调整

### Common 模块新增依赖

```kotlin
// JWT 依赖
api("io.jsonwebtoken:jjwt-api:0.11.5")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
```

### Blog 模块移除依赖

从 `module-blog/build.gradle.kts` 中移除了 JWT 依赖（现在从 common 继承）

---

## 📂 受影响的文件

### Blog 模块 - 更新的文件 (13个)

#### 控制器层 (3个)
- ✅ `controller/AuthController.kt`
- ✅ `controller/DocumentController.kt`
- ✅ `controller/UserController.kt`

#### 服务层 (9个)
- ✅ `service/AuthService.kt`
- ✅ `service/TokenRefreshService.kt`
- ✅ `service/RefreshTokenService.kt`
- ✅ `service/DirectoryTreeService.kt`
- ✅ `service/DocumentService.kt`
- ✅ `service/BatchUploadService.kt`
- ✅ `service/UserService.kt`
- ✅ `service/GroupService.kt`
- ✅ `service/SortService.kt`

#### 拦截器 (1个)
- ✅ `interceptor/AuthInterceptor.kt`

### Blog 模块 - 删除的文件 (5个)

- 🗑️ `exception/AuthException.kt`
- 🗑️ `util/JwtUtil.kt`
- 🗑️ `context/AuthContext.kt`
- 🗑️ `config/TokenConfig.kt`
- 🗑️ `constants/AuthConstants.kt`

### Common 模块 - 新增的文件 (5个)

- ✅ `exception/CustomException.kt` (新增认证异常)
- ✅ `util/JwtUtil.kt`
- ✅ `context/AuthContext.kt`
- ✅ `config/TokenConfig.kt`
- ✅ `constants/AuthConstants.kt`

---

## 🔧 Import 语句变化示例

### 之前 (Blog 模块)
```kotlin
import com.zzy.blog.exception.UnauthorizedException
import com.zzy.blog.util.JwtUtil
import com.zzy.blog.context.AuthContextHolder
import com.zzy.blog.config.TokenConfig
import com.zzy.blog.constants.AuthConstants
```

### 之后 (Common 模块)
```kotlin
import com.zzy.common.exception.UnauthorizedException
import com.zzy.common.util.JwtUtil
import com.zzy.common.context.AuthContextHolder
import com.zzy.common.config.TokenConfig
import com.zzy.common.constants.AuthConstants
```

---

## ✅ 编译验证

### 编译结果
```
BUILD SUCCESSFUL in 1m 9s
18 actionable tasks: 12 executed, 6 up-to-date
```

### 验证的模块
- ✅ common - 编译成功
- ✅ module-file - 编译成功
- ✅ module-blog - 编译成功
- ✅ app-bootstrap - 编译成功

---

## 🎯 迁移效果

### 代码复用性提升
- 认证相关的通用代码现在可以被所有模块使用
- 减少了代码重复
- 统一了异常处理规范

### 模块职责更清晰
- **Common**: 提供基础的认证、JWT、异常处理能力
- **Blog**: 专注于博客业务逻辑
- **Module-File**: 可以直接使用 common 的认证能力

### 未来扩展性
如果添加新的业务模块（如 module-forum、module-chat），可以直接使用 common 模块的：
- 认证和授权功能
- JWT Token 处理
- 统一的异常体系
- 用户上下文管理

---

## 📚 最佳实践建议

### 1. 识别通用代码的标准
- ✅ 多个模块都可能使用的功能
- ✅ 与具体业务逻辑无关的基础设施代码
- ✅ 可以独立测试和维护的组件

### 2. 迁移到 Common 的候选
- 认证和授权相关
- 通用工具类
- 基础异常类
- 共享的配置类
- 通用常量

### 3. 保留在业务模块的内容
- 业务特定的 DTO
- 业务实体类
- 特定业务逻辑的服务
- 业务相关的 Mapper

---

## 🔮 后续建议

### 可能的进一步优化
1. **考虑创建 common-auth 子模块**
   - 如果认证相关代码继续增多，可以单独抽离
   
2. **检查 module-file 模块**
   - 看是否也有可以提取到 common 的代码
   
3. **统一配置管理**
   - 考虑将更多配置类迁移到 common

---

**迁移状态**: ✅ 已完成  
**编译状态**: ✅ 通过  
**测试状态**: ⚠️ 建议运行完整测试套件

