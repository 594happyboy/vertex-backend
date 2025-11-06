package com.zzy.common.context

/**
 * 鉴权用户信息
 * @author ZZY
 * @date 2025-11-06
 */
data class AuthUser(
    val userId: Long            // 当前登录用户ID
)

/**
 * 鉴权上下文持有者
 * 基于 ThreadLocal 实现线程隔离的用户上下文管理
 * 
 * @author ZZY
 * @date 2025-11-06
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
     * @throws IllegalStateException 如果用户未登录
     */
    fun getCurrentUserId(): Long {
        val authUser = getAuthUser() ?: throw IllegalStateException("未登录")
        return authUser.userId
    }
}

