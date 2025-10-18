# Redis 使用指南

## 📝 概述

本项目提供了完善的Redis配置，完美支持Kotlin数据类的序列化和反序列化。

## 🔧 配置说明

### 已提供的Bean

#### 1. ObjectMapper（通用）
```kotlin
@Autowired
private lateinit var objectMapper: ObjectMapper
```

**特性**：
- ✅ 完整支持Kotlin数据类
- ✅ 支持Java 8时间类型（LocalDateTime等）
- ✅ 忽略未知属性
- ✅ 多态类型支持
- ✅ 日期格式化（非时间戳）

**用途**：整个应用的统一JSON处理器

---

#### 2. RedisTemplate<String, Any>（复杂对象）
```kotlin
@Autowired
private lateinit var redisTemplate: RedisTemplate<String, Any>
```

**特性**：
- Key: String序列化
- Value: JSON序列化（带类型信息）
- 支持复杂对象和集合

**适用场景**：简单场景，对象结构不复杂时

---

#### 3. StringRedisTemplate（推荐⭐）
```kotlin
@Autowired
private lateinit var stringRedisTemplate: StringRedisTemplate

@Autowired
private lateinit var objectMapper: ObjectMapper
```

**特性**：
- Key: String
- Value: String（存储JSON字符串）
- 手动序列化/反序列化
- **最可靠，推荐使用**

**适用场景**：复杂对象、Kotlin数据类、需要完全控制序列化过程

---

## 💡 使用示例

### 方式1：使用 StringRedisTemplate（推荐）

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.TimeUnit

@Service
class MyService(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    
    // 保存单个对象
    fun saveUser(userId: Long, user: User) {
        val key = "user:$userId"
        val json = objectMapper.writeValueAsString(user)
        stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES)
    }
    
    // 获取单个对象
    fun getUser(userId: Long): User? {
        val key = "user:$userId"
        val json = stringRedisTemplate.opsForValue().get(key) ?: return null
        return objectMapper.readValue<User>(json)
    }
    
    // 保存列表
    fun saveUserList(users: List<User>) {
        val key = "users:all"
        val json = objectMapper.writeValueAsString(users)
        stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES)
    }
    
    // 获取列表
    fun getUserList(): List<User>? {
        val key = "users:all"
        val json = stringRedisTemplate.opsForValue().get(key) ?: return null
        return objectMapper.readValue<List<User>>(json)
    }
    
    // 保存Map
    fun saveUserMap(userMap: Map<Long, User>) {
        val key = "users:map"
        val json = objectMapper.writeValueAsString(userMap)
        stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES)
    }
    
    // 获取Map
    fun getUserMap(): Map<Long, User>? {
        val key = "users:map"
        val json = stringRedisTemplate.opsForValue().get(key) ?: return null
        return objectMapper.readValue<Map<Long, User>>(json)
    }
    
    // 删除缓存
    fun clearCache(userId: Long) {
        stringRedisTemplate.delete("user:$userId")
    }
    
    // 批量删除
    fun clearAllUsers() {
        val pattern = "user:*"
        val keys = stringRedisTemplate.keys(pattern)
        if (keys.isNotEmpty()) {
            stringRedisTemplate.delete(keys)
        }
    }
}
```

**数据类示例**：
```kotlin
data class User(
    val id: Long,
    val username: String,
    val email: String,
    val createdAt: LocalDateTime,
    val profile: UserProfile?
)

data class UserProfile(
    val avatar: String,
    val bio: String
)
```

---

### 方式2：使用 RedisTemplate（简单场景）

```kotlin
import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.TimeUnit

@Service
class SimpleService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    
    // 保存简单对象
    fun saveCounter(key: String, value: Int) {
        redisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS)
    }
    
    // 获取简单对象
    fun getCounter(key: String): Int? {
        return redisTemplate.opsForValue().get(key) as? Int
    }
    
    // Hash操作
    fun saveToHash(key: String, field: String, value: String) {
        redisTemplate.opsForHash<String, String>().put(key, field, value)
    }
    
    fun getFromHash(key: String, field: String): String? {
        return redisTemplate.opsForHash<String, String>().get(key, field)
    }
}
```

---

## 🎯 最佳实践

### 1. 命名规范

```kotlin
// 使用冒号分隔的层级结构
"user:123"                    // 单个用户
"user:123:profile"            // 用户资料
"article:list:published"      // 文章列表
"cache:directory_tree:1:USER" // 目录树缓存
```

### 2. 过期时间设置

```kotlin
// 热点数据：较短时间
stringRedisTemplate.opsForValue().set(key, json, 5, TimeUnit.MINUTES)

// 一般数据：中等时间
stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES)

// 配置数据：较长时间
stringRedisTemplate.opsForValue().set(key, json, 1, TimeUnit.HOURS)

// 永久数据（需手动删除）
stringRedisTemplate.opsForValue().set(key, json)
```

### 3. 异常处理

```kotlin
fun getFromCache(key: String): MyData? {
    return try {
        val json = stringRedisTemplate.opsForValue().get(key)
        if (json != null) {
            objectMapper.readValue<MyData>(json)
        } else {
            null
        }
    } catch (e: Exception) {
        logger.warn("从缓存获取数据失败: {}", e.message)
        null  // 返回null，业务层从数据库加载
    }
}

fun saveToCache(key: String, data: MyData) {
    try {
        val json = objectMapper.writeValueAsString(data)
        stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES)
    } catch (e: Exception) {
        logger.error("保存到缓存失败: {}", e.message, e)
        // 缓存失败不影响业务，只记录日志
    }
}
```

### 4. 缓存更新策略

```kotlin
@Service
class ArticleService(
    private val articleMapper: ArticleMapper,
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    
    // 查询时使用缓存
    fun getArticle(id: Long): Article? {
        val key = "article:$id"
        
        // 1. 先查缓存
        val cached = getFromCache(key)
        if (cached != null) {
            return cached
        }
        
        // 2. 查数据库
        val article = articleMapper.selectById(id) ?: return null
        
        // 3. 写入缓存
        saveToCache(key, article)
        
        return article
    }
    
    // 更新时清除缓存
    @Transactional
    fun updateArticle(id: Long, request: UpdateRequest): Article {
        val article = articleMapper.selectById(id)
        // ... 更新逻辑
        articleMapper.updateById(article)
        
        // 清除缓存
        clearCache(id)
        
        return article
    }
    
    // 删除时清除缓存
    @Transactional
    fun deleteArticle(id: Long) {
        articleMapper.deleteById(id)
        clearCache(id)
    }
    
    private fun getFromCache(key: String): Article? {
        return try {
            val json = stringRedisTemplate.opsForValue().get(key)
            json?.let { objectMapper.readValue<Article>(it) }
        } catch (e: Exception) {
            logger.warn("缓存读取失败", e)
            null
        }
    }
    
    private fun saveToCache(key: String, article: Article) {
        try {
            val json = objectMapper.writeValueAsString(article)
            stringRedisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES)
        } catch (e: Exception) {
            logger.error("缓存写入失败", e)
        }
    }
    
    private fun clearCache(id: Long) {
        try {
            stringRedisTemplate.delete("article:$id")
        } catch (e: Exception) {
            logger.error("缓存清除失败", e)
        }
    }
}
```

---

## 🚫 常见错误及解决方案

### 错误1：反序列化失败

**错误信息**：
```
JsonMappingException: object is not an instance of declaring class
```

**原因**：使用 `RedisTemplate<String, Any>` 直接存储复杂Kotlin对象

**解决方案**：改用 `StringRedisTemplate` + 手动序列化

```kotlin
// ❌ 错误做法
redisTemplate.opsForValue().set(key, complexObject)
val obj = redisTemplate.opsForValue().get(key) as MyClass

// ✅ 正确做法
val json = objectMapper.writeValueAsString(complexObject)
stringRedisTemplate.opsForValue().set(key, json)
val obj = objectMapper.readValue<MyClass>(stringRedisTemplate.opsForValue().get(key)!!)
```

---

### 错误2：类型转换失败

**错误信息**：
```
ClassCastException: LinkedHashMap cannot be cast to MyClass
```

**原因**：泛型擦除，Redis反序列化为Map而不是目标类型

**解决方案**：使用 `StringRedisTemplate` + TypeReference

```kotlin
// ❌ 错误做法
val list = redisTemplate.opsForValue().get(key) as List<MyClass>

// ✅ 正确做法
val json = stringRedisTemplate.opsForValue().get(key)
val list = objectMapper.readValue<List<MyClass>>(json!!)
```

---

### 错误3：日期格式问题

**问题**：LocalDateTime序列化为数组或时间戳

**解决方案**：使用项目提供的 `ObjectMapper` Bean（已配置）

```kotlin
// ✅ 正确做法（自动注入）
@Autowired
private lateinit var objectMapper: ObjectMapper

// 自动支持 LocalDateTime、LocalDate 等
```

---

## 📊 性能优化建议

1. **合理设置过期时间**：避免内存溢出
2. **使用管道（Pipeline）**：批量操作时提升性能
3. **避免大Key**：单个Key不要超过1MB
4. **监控缓存命中率**：定期检查缓存效果
5. **设置合理的最大内存**：防止OOM

---

## 🔍 调试技巧

### 查看Redis中的数据

```bash
# 连接Redis
redis-cli

# 查看所有key
keys *

# 查看具体的值
get "user:123"

# 查看key的类型
type "user:123"

# 查看key的过期时间（秒）
ttl "user:123"

# 删除key
del "user:123"
```

---

## 📚 参考资源

- [Spring Data Redis 官方文档](https://spring.io/projects/spring-data-redis)
- [Jackson Kotlin Module](https://github.com/FasterXML/jackson-module-kotlin)
- [Redis 命令参考](https://redis.io/commands)

---

**最后更新**: 2025-10-18  
**维护者**: ZZY

