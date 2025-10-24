# Redis 缓存使用指南

> 本指南面向后端新手，详细介绍如何在本项目中使用 Redis 缓存

## 目录

1. [核心概念](#核心概念)
2. [快速开始](#快速开始)
3. [使用示例](#使用示例)
4. [最佳实践](#最佳实践)
5. [常见问题](#常见问题)
6. [进阶话题](#进阶话题)

---

## 核心概念

### 什么是缓存？

缓存是将数据存储在高速存储介质（内存）中，避免重复查询数据库或重复计算，提高系统性能。

```
没有缓存的流程：
用户请求 -> 查询数据库 -> 返回结果 (100ms)

有缓存的流程：
用户请求 -> 查询缓存(命中) -> 返回结果 (5ms) ⚡
用户请求 -> 查询缓存(未命中) -> 查询数据库 -> 写入缓存 -> 返回结果 (100ms)
```

### 什么是序列化和反序列化？

- **序列化**：将对象转换为可存储/传输的格式（如JSON、字节流）
- **反序列化**：将存储格式还原为对象

```kotlin
// 对象
val user = User(id=1, name="张三")

// 序列化为JSON
val json = """{"id":1,"name":"张三"}"""

// 序列化为JSON + 类型信息
val jsonWithType = """{"@class":"com.zzy.User","id":1,"name":"张三"}"""
```

### 为什么需要类型信息？

Redis 存储的是字节流，反序列化时需要知道对象的确切类型：

```kotlin
// 问题场景：存储List
val list: List<User> = listOf(user1, user2)
redisTemplate.set("users", list)

// 反序列化时，如果没有类型信息：
val result = redisTemplate.get("users") 
// ❌ result 只知道是 List，不知道元素是 User

// 有类型信息：
// ✅ result 知道是 List<User>，可以正确反序列化
```

---

## 快速开始

### 1. 注入 RedisUtil

```kotlin
@Service
class YourService(
    private val redisUtil: RedisUtil  // Spring 会自动注入
) {
    // ...
}
```

### 2. 基本操作

```kotlin
// 存储数据（5分钟过期）
redisUtil.set("user:1", user, 5, TimeUnit.MINUTES)

// 读取数据
val user = redisUtil.get("user:1", User::class.java)

// 删除数据
redisUtil.delete("user:1")
```

### 3. 完整示例

```kotlin
@Service
class UserService(
    private val userMapper: UserMapper,
    private val redisUtil: RedisUtil
) {
    fun getUserById(id: Long): User {
        val cacheKey = "user:info:$id"
        
        // 1. 尝试从缓存获取
        val cached = redisUtil.get(cacheKey, User::class.java)
        if (cached != null) {
            logger.info("缓存命中: {}", cacheKey)
            return cached
        }
        
        // 2. 缓存未命中，查询数据库
        val user = userMapper.selectById(id)
            ?: throw NotFoundException("用户不存在")
        
        // 3. 写入缓存（5分钟过期）
        redisUtil.set(cacheKey, user, 5, TimeUnit.MINUTES)
        
        return user
    }
    
    fun updateUser(user: User) {
        // 1. 更新数据库
        userMapper.updateById(user)
        
        // 2. 删除缓存（下次查询时会重新加载）
        redisUtil.delete("user:info:${user.id}")
    }
}
```

---

## 使用示例

### 示例 1：缓存单个对象

```kotlin
@Service
class FileService(
    private val fileMapper: FileMapper,
    private val redisUtil: RedisUtil
) {
    companion object {
        private const val CACHE_KEY_PREFIX = "file:info:"
        private const val CACHE_EXPIRE_MINUTES = 10L
    }
    
    fun getFileInfo(id: Long): FileResponse {
        val cacheKey = "$CACHE_KEY_PREFIX$id"
        
        // 尝试从缓存获取
        val cached = redisUtil.get(cacheKey, FileResponse::class.java)
        if (cached != null) return cached
        
        // 从数据库查询
        val file = fileMapper.selectById(id)
            ?: throw FileNotFoundException("文件不存在")
        
        val response = FileResponse.fromEntity(file)
        
        // 写入缓存
        redisUtil.set(cacheKey, response, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
        
        return response
    }
}
```

### 示例 2：缓存列表

```kotlin
@Service
class FileService(
    private val fileMapper: FileMapper,
    private val redisUtil: RedisUtil
) {
    fun getFileList(page: Int, size: Int): FileListResponse {
        val cacheKey = "file:list:$page:$size"
        
        // 尝试从缓存获取
        val cached = redisUtil.get(cacheKey, FileListResponse::class.java)
        if (cached != null) return cached
        
        // 从数据库查询
        val pageObj = Page<FileMetadata>(page.toLong(), size.toLong())
        val pageResult = fileMapper.selectPage(pageObj, null)
        
        val response = FileListResponse(
            total = pageResult.total,
            page = page,
            size = size,
            files = pageResult.records.map { FileResponse.fromEntity(it) }
        )
        
        // 写入缓存（3分钟过期，列表变化频繁）
        redisUtil.set(cacheKey, response, 3, TimeUnit.MINUTES)
        
        return response
    }
}
```

### 示例 3：缓存失效（更新时删除缓存）

```kotlin
@Service
class FileService(
    private val fileMapper: FileMapper,
    private val redisUtil: RedisUtil
) {
    fun uploadFile(file: MultipartFile): FileResponse {
        // 1. 保存文件和元数据
        val fileMetadata = saveFile(file)
        
        // 2. 删除列表缓存（因为列表内容已改变）
        clearFileListCache()
        
        return FileResponse.fromEntity(fileMetadata)
    }
    
    fun deleteFile(id: Long) {
        // 1. 删除文件
        fileMapper.deleteById(id)
        
        // 2. 删除文件信息缓存
        redisUtil.delete("file:info:$id")
        
        // 3. 删除列表缓存
        clearFileListCache()
    }
    
    private fun clearFileListCache() {
        // 删除所有文件列表缓存
        redisUtil.deleteByPattern("file:list:*")
    }
}
```

### 示例 4：缓存计数器

```kotlin
@Service
class FileService(
    private val fileMapper: FileMapper,
    private val redisUtil: RedisUtil
) {
    fun getDownloadCount(fileId: Long): Long {
        val cacheKey = "file:download:count:$fileId"
        
        // 尝试从缓存获取
        val cached = redisUtil.get(cacheKey, Long::class.java)
        if (cached != null) return cached
        
        // 从数据库查询
        val file = fileMapper.selectById(fileId)
        val count = file?.downloadCount ?: 0L
        
        // 写入缓存（1小时过期）
        redisUtil.set(cacheKey, count, 1, TimeUnit.HOURS)
        
        return count
    }
    
    fun incrementDownloadCount(fileId: Long) {
        // 1. 更新数据库
        fileMapper.increaseDownloadCount(fileId)
        
        // 2. 删除缓存（让下次查询重新加载最新值）
        redisUtil.delete("file:download:count:$fileId")
    }
}
```

---

## 最佳实践

### 1. 缓存键命名规范

使用冒号分隔的层级结构，便于管理和批量删除：

```kotlin
// ✅ 推荐
"user:info:1001"           // 用户信息
"user:list:page1:size20"   // 用户列表
"file:download:count:123"  // 文件下载次数
"blog:doc:456"             // 博客文档

// ❌ 不推荐
"user1001"                 // 不明确
"userInfoPage1Size20"      // 难以批量删除
"downloadCount123"         // 不知道是什么模块
```

### 2. 合理设置过期时间

```kotlin
// 根据数据特性设置不同的过期时间

// 配置信息：较长（30分钟-1小时）
redisUtil.set("config:system", config, 30, TimeUnit.MINUTES)

// 查询结果：中等（5-10分钟）
redisUtil.set("user:info:$id", user, 5, TimeUnit.MINUTES)

// 列表数据：较短（1-5分钟，变化频繁）
redisUtil.set("file:list:$page", files, 3, TimeUnit.MINUTES)

// 临时数据：很短（30秒-1分钟）
redisUtil.set("temp:token:$token", data, 30, TimeUnit.SECONDS)

// ❌ 避免无过期时间（容易导致内存泄漏）
redisUtil.set("data", value) // 不推荐
```

### 3. 缓存更新策略

推荐使用 **Cache Aside** 模式（旁路缓存）：

```kotlin
// 读取：先查缓存，未命中再查数据库
fun read(id: Long): Data {
    val cached = redisUtil.get("data:$id", Data::class.java)
    if (cached != null) return cached
    
    val data = database.query(id)
    redisUtil.set("data:$id", data, 5, TimeUnit.MINUTES)
    return data
}

// 更新：先更新数据库，再删除缓存
fun update(data: Data) {
    database.update(data)
    redisUtil.delete("data:${data.id}")  // 删除缓存，下次查询时重新加载
}
```

**为什么删除而不是更新缓存？**
1. 简单可靠，避免缓存和数据库不一致
2. 避免并发更新问题
3. 如果缓存很少被读取，更新缓存浪费资源

### 4. 防止缓存穿透

缓存穿透：查询不存在的数据，缓存和数据库都没有，导致每次都查询数据库。

```kotlin
// ❌ 问题代码：不存在的数据不会被缓存
fun getUser(id: Long): User? {
    val cached = redisUtil.get("user:$id", User::class.java)
    if (cached != null) return cached
    
    val user = userMapper.selectById(id)
    if (user != null) {
        redisUtil.set("user:$id", user, 5, TimeUnit.MINUTES)
    }
    return user
}

// ✅ 解决方案：缓存空结果（短过期时间）
fun getUser(id: Long): User? {
    val cacheKey = "user:$id"
    val cached = redisUtil.get(cacheKey, String::class.java)
    
    // 缓存命中
    if (cached != null) {
        return if (cached == "NULL") null else 
            objectMapper.readValue(cached, User::class.java)
    }
    
    // 查询数据库
    val user = userMapper.selectById(id)
    
    // 缓存结果（包括空结果）
    if (user != null) {
        val json = objectMapper.writeValueAsString(user)
        redisUtil.set(cacheKey, json, 5, TimeUnit.MINUTES)
    } else {
        // 缓存空结果，短过期时间（避免恶意攻击）
        redisUtil.set(cacheKey, "NULL", 1, TimeUnit.MINUTES)
    }
    
    return user
}
```

### 5. 防止缓存雪崩

缓存雪崩：大量缓存同时过期，导致数据库压力激增。

```kotlin
// ❌ 问题代码：所有缓存同时过期
for (id in 1..1000) {
    redisUtil.set("user:$id", users[id], 5, TimeUnit.MINUTES)
}

// ✅ 解决方案：添加随机过期时间
for (id in 1..1000) {
    // 5分钟 + 随机0-60秒
    val expireTime = 300 + Random.nextInt(60)
    redisUtil.set("user:$id", users[id], expireTime.toLong(), TimeUnit.SECONDS)
}

// ✅ 或者使用工具方法
fun setWithRandomExpire(key: String, value: Any, baseMinutes: Long) {
    val randomSeconds = Random.nextInt(60)
    val totalSeconds = baseMinutes * 60 + randomSeconds
    redisUtil.set(key, value, totalSeconds, TimeUnit.SECONDS)
}
```

### 6. 避免缓存大对象

```kotlin
// ❌ 不推荐：缓存大对象（>1MB）
val bigData = queryHugeResult() // 10MB的数据
redisUtil.set("big:data", bigData, 10, TimeUnit.MINUTES)

// ✅ 推荐：分页缓存
fun getPagedData(page: Int, size: Int): List<Data> {
    val cacheKey = "data:page:$page:$size"
    val cached = redisUtil.get(cacheKey, DataListResponse::class.java)
    if (cached != null) return cached.data
    
    val data = queryPagedResult(page, size)
    redisUtil.set(cacheKey, data, 5, TimeUnit.MINUTES)
    return data
}
```

### 7. 定期清理缓存

```kotlin
@Service
class CacheCleanupService(
    private val redisUtil: RedisUtil
) {
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
    fun cleanupExpiredCache() {
        logger.info("开始清理过期缓存")
        
        // 清理临时缓存
        redisUtil.deleteByPattern("temp:*")
        
        // 清理过期的会话
        redisUtil.deleteByPattern("session:expired:*")
        
        logger.info("缓存清理完成")
    }
}
```

---

## 常见问题

### Q1: 缓存何时使用？

**适合缓存的场景：**
- ✅ 读多写少的数据（用户信息、配置）
- ✅ 计算复杂的结果（统计数据、报表）
- ✅ 查询慢的数据（多表关联、大数据量）
- ✅ 热点数据（首页、排行榜）

**不适合缓存的场景：**
- ❌ 写多读少的数据（日志、消息）
- ❌ 强一致性要求的数据（订单、支付）
- ❌ 数据量很小且查询很快的数据
- ❌ 实时性要求极高的数据

### Q2: 缓存和数据库不一致怎么办？

本项目使用 **Cache Aside** 模式，可能出现短暂不一致：

```
时间线：
T1: 线程A查询数据库，得到user.age=20
T2: 线程B更新数据库，user.age=21
T3: 线程B删除缓存
T4: 线程A写入缓存，user.age=20（旧数据）

结果：缓存中是旧数据（age=20）
```

**解决方案：**
1. 设置合理的过期时间（5-10分钟），自动修复
2. 双删策略：更新后延迟再删一次缓存
3. 关键业务使用分布式锁保证一致性

### Q3: 为什么缓存命中率低？

可能原因：
1. 过期时间太短
2. 缓存键设计不合理（每次都不同）
3. 数据变化频繁
4. 业务场景不适合缓存

### Q4: Redis内存不足怎么办？

**排查步骤：**
```bash
# 查看Redis内存使用
docker exec vertex-redis redis-cli INFO memory

# 查看key数量
docker exec vertex-redis redis-cli DBSIZE

# 查看大key
docker exec vertex-redis redis-cli --bigkeys
```

**解决方案：**
1. 调整过期时间，加快淘汰
2. 删除不需要的缓存
3. 检查是否有内存泄漏（无过期时间的key）
4. 增加Redis内存限制

### Q5: 如何调试缓存问题？

```kotlin
// 1. 启用DEBUG日志
// application.properties
// logging.level.com.zzy.common.util.RedisUtil=DEBUG

// 2. 手动查看Redis数据
fun debugCache(key: String) {
    val value = redisUtil.get(key)
    logger.info("Cache debug: key={}, value={}", key, value)
    logger.info("Cache expire: {}秒", redisUtil.getExpire(key))
}

// 3. 使用Redis客户端工具
// - Redis Desktop Manager
// - RedisInsight
// - 命令行：docker exec -it vertex-redis redis-cli
```

---

## 进阶话题

### 1. 序列化机制详解

本项目使用 **GenericJackson2JsonRedisSerializer** 序列化：

```json
// 存储格式示例
{
  "@class": "com.zzy.file.dto.FileListResponse",
  "total": 2,
  "page": 1,
  "size": 20,
  "files": [
    "java.util.ArrayList",
    [
      {
        "@class": "com.zzy.file.dto.FileResponse",
        "id": 1,
        "fileName": "test.txt"
      }
    ]
  ]
}
```

**优点：**
- 自动处理类型信息
- 支持复杂对象（List、Map、多态）
- 无需手动序列化

**缺点：**
- 存储空间增加（类型信息）
- 可能存在安全风险（反序列化漏洞）

### 2. 替代方案：手动序列化

如果不需要类型信息，可以使用 `StringRedisTemplate` + 手动序列化：

```kotlin
@Service
class ManualCacheService(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper  // 不带类型信息
) {
    fun set(key: String, value: Any, timeout: Long, unit: TimeUnit) {
        // 手动序列化为JSON
        val json = objectMapper.writeValueAsString(value)
        stringRedisTemplate.opsForValue().set(key, json, timeout, unit)
    }
    
    fun <T> get(key: String, clazz: Class<T>): T? {
        val json = stringRedisTemplate.opsForValue().get(key) ?: return null
        // 手动反序列化
        return objectMapper.readValue(json, clazz)
    }
}
```

**优点：**
- JSON干净无类型信息
- 存储空间更小
- 更安全（不会自动反序列化任意类）

**缺点：**
- 需要手动序列化/反序列化
- 不支持复杂泛型（如 `List<User>` 需要 TypeReference）

### 3. 分布式锁（简单实现）

防止缓存击穿（热点数据并发查询）：

```kotlin
fun getWithLock(id: Long): Data {
    val cacheKey = "data:$id"
    val lockKey = "lock:data:$id"
    
    // 1. 尝试从缓存获取
    val cached = redisUtil.get(cacheKey, Data::class.java)
    if (cached != null) return cached
    
    // 2. 获取分布式锁
    val lockValue = UUID.randomUUID().toString()
    val locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS)
    
    return if (locked == true) {
        try {
            // 3. 再次检查缓存（可能其他线程已加载）
            val cachedAgain = redisUtil.get(cacheKey, Data::class.java)
            if (cachedAgain != null) return cachedAgain
            
            // 4. 查询数据库
            val data = database.query(id)
            
            // 5. 写入缓存
            redisUtil.set(cacheKey, data, 5, TimeUnit.MINUTES)
            
            data
        } finally {
            // 6. 释放锁
            val currentValue = redisTemplate.opsForValue().get(lockKey)
            if (currentValue == lockValue) {
                redisTemplate.delete(lockKey)
            }
        }
    } else {
        // 7. 没获取到锁，等待后重试
        Thread.sleep(50)
        getWithLock(id)  // 递归重试（生产环境应限制重试次数）
    }
}
```

**注意：** 这只是简单实现，生产环境建议使用 Redisson 等成熟库。

---

## 总结

### 使用流程

1. 注入 `RedisUtil`
2. 读取时先查缓存，未命中再查数据库，并写入缓存
3. 更新时先更新数据库，再删除缓存
4. 设置合理的过期时间
5. 使用规范的缓存键命名

### 注意事项

- ⚠️ 缓存不是万能的，需要权衡一致性和性能
- ⚠️ 合理设置过期时间，避免内存泄漏
- ⚠️ 注意缓存穿透、雪崩、击穿问题
- ⚠️ 定期监控缓存命中率和内存使用
- ⚠️ 重要数据做好降级方案（缓存失败不影响业务）

### 学习资源

- [Redis官方文档](https://redis.io/documentation)
- [Spring Data Redis文档](https://spring.io/projects/spring-data-redis)
- [缓存最佳实践](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)

---

有问题欢迎查看代码注释或询问团队成员！🎉

