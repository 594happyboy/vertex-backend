package com.zzy.common.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.core.annotation.Order

/**
 * 安全过滤器 - 防御常见的网络攻击
 * @author ZZY
 * @date 2025-10-26
 */
@Component
@Order(1)
class SecurityFilter : Filter {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // 恶意模式列表 - JNDI注入、代码执行等
    private val maliciousPatterns = listOf(
        "\${", "jndi:", "ldap:", "rmi:", "dns:",
        "eval(", "base64_decode", "shell_exec", 
        "system(", "exec(", "passthru(", "curl ",
        "wget ", "/bin/bash", "/bin/sh",
        "TomcatBypass", "Command/Base64"
    )
    
    // 可疑User-Agent
    private val suspiciousUserAgents = listOf(
        "masscan", "nmap", "nikto", "sqlmap",
        "acunetix", "netsparker", "metasploit"
    )
    
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        
        val remoteAddr = getClientIP(httpRequest)
        val uri = httpRequest.requestURI
        
        // 检查Accept头
        val acceptHeader = httpRequest.getHeader("Accept")
        if (acceptHeader != null && containsMaliciousPattern(acceptHeader)) {
            logger.warn("🚨 阻止恶意请求 [JNDI注入] - IP: $remoteAddr, URI: $uri, Accept: ${acceptHeader.take(100)}")
            sendForbiddenResponse(httpResponse)
            return
        }
        
        // 检查User-Agent
        val userAgent = httpRequest.getHeader("User-Agent") ?: ""
        if (containsSuspiciousUserAgent(userAgent)) {
            logger.warn("🚨 阻止恶意请求 [可疑User-Agent] - IP: $remoteAddr, URI: $uri, UA: $userAgent")
            sendForbiddenResponse(httpResponse)
            return
        }
        
        // 检查所有请求头
        val headerNames = httpRequest.headerNames
        while (headerNames.hasMoreElements()) {
            val headerName = headerNames.nextElement()
            val headerValue = httpRequest.getHeader(headerName)
            if (headerValue != null && containsMaliciousPattern(headerValue)) {
                logger.warn("🚨 阻止恶意请求 [恶意Header] - IP: $remoteAddr, Header: $headerName, Value: ${headerValue.take(100)}")
                sendForbiddenResponse(httpResponse)
                return
            }
        }
        
        // 检查URI路径
        if (containsMaliciousPattern(uri)) {
            logger.warn("🚨 阻止恶意请求 [恶意URI] - IP: $remoteAddr, URI: $uri")
            sendForbiddenResponse(httpResponse)
            return
        }
        
        chain.doFilter(request, response)
    }
    
    /**
     * 检查是否包含恶意模式
     */
    private fun containsMaliciousPattern(input: String): Boolean {
        return maliciousPatterns.any { pattern ->
            input.contains(pattern, ignoreCase = true)
        }
    }
    
    /**
     * 检查是否为可疑User-Agent
     */
    private fun containsSuspiciousUserAgent(userAgent: String): Boolean {
        return suspiciousUserAgents.any { suspicious ->
            userAgent.contains(suspicious, ignoreCase = true)
        }
    }
    
    /**
     * 获取客户端真实IP
     */
    private fun getClientIP(request: HttpServletRequest): String {
        var ip = request.getHeader("X-Forwarded-For")
        if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.getHeader("X-Real-IP")
        }
        if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.remoteAddr
        }
        // 如果有多个代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim()
        }
        return ip ?: "unknown"
    }
    
    /**
     * 返回403禁止访问响应
     */
    private fun sendForbiddenResponse(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"code":403,"message":"Forbidden","data":null}""")
    }
}

