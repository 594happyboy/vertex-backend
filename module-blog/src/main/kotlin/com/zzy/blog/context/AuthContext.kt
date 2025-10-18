package com.zzy.blog.context

/**
 * 鉴权上下文
 * @author ZZY
 * @date 2025-10-18
 */
data class AuthUser(
    val role: String,           // USER 或 VISITOR
    val currentUserId: Long?,   // 当前登录用户ID（USER角色）
    val targetUserId: Long?     // 目标用户ID（VISITOR角色）
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
     * 获取当前用户ID（仅USER角色）
     */
    fun getCurrentUserId(): Long {
        val authUser = getAuthUser() ?: throw IllegalStateException("未登录")
        return authUser.currentUserId ?: throw IllegalStateException("无效的用户令牌")
    }
    
    /**
     * 检查是否为游客
     */
    fun isVisitor(): Boolean {
        return getAuthUser()?.role == "VISITOR"
    }
    
    /**
     * 检查是否为已登录用户
     */
    fun isUser(): Boolean {
        return getAuthUser()?.role == "USER"
    }
}

