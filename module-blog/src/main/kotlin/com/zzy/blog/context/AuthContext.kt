package com.zzy.blog.context

/**
 * 鉴权上下文
 * @author ZZY
 * @date 2025-10-18
 */
data class AuthUser(
    val userId: Long            // 当前登录用户ID
)

/**
 * 鉴权上下文持有者
 */
object AuthContextHolder {
    private val context = ThreadLocal<AuthUser>()
    
    /**
     * 设置当前鉴权用户
     */
    fun setAuthUser(authUser: AuthUser) {
        context.set(authUser)
    }
    
    /**
     * 获取当前鉴权用户
     */
    fun getAuthUser(): AuthUser? {
        return context.get()
    }
    
    /**
     * 清除鉴权上下文
     */
    fun clear() {
        context.remove()
    }
    
    /**
     * 获取当前用户ID
     */
    fun getCurrentUserId(): Long {
        val authUser = getAuthUser() ?: throw IllegalStateException("未登录")
        return authUser.userId
    }
}

