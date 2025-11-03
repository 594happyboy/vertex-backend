package com.zzy.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger/Knife4j API文档配置
 * 
 * ## 访问地址
 * - Knife4j增强文档：http://localhost:8080/doc.html （推荐）
 * - 原生Swagger UI：http://localhost:8080/swagger-ui.html
 * 
 * ## 功能特性
 * - 自动生成API文档
 * - 在线调试接口
 * - JWT令牌认证支持
 * - 请求/响应示例
 * 
 * ## 使用说明
 * 1. 启动应用后访问上述地址
 * 2. 点击"授权"按钮，输入JWT令牌（格式：Bearer <token>）
 * 3. 授权后即可调试需要认证的接口
 * 
 * @author ZZY
 * @date 2025-10-09
 */
@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Vertex Backend API 文档")
                    .description("""
                        # Vertex Backend 多功能后端系统
                        
                        ## 功能模块
                        - 📝 **博客管理**：分组、文档、发布、权限控制
                        - 📁 **文件管理**：上传、下载、文件夹、秒传、回收站
                        - 🔐 **用户认证**：JWT令牌、用户/游客角色
                        
                        ## 技术栈
                        - Spring Boot 3.2.12
                        - Kotlin 1.9.25
                        - MyBatis-Plus 3.5.5
                        - Redis缓存
                        - MinIO对象存储
                        
                        ## 认证说明
                        大部分接口需要JWT令牌认证，请先调用登录接口获取令牌。
                    """.trimIndent())
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("ZZY")
                            .email("your-email@example.com")
                            .url("https://github.com/yourusername/vertex-backend")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .components(
                Components()
                    .addSecuritySchemes("Bearer认证", 
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("请输入JWT令牌（无需添加Bearer前缀）")
                    )
            )
            .addSecurityItem(
                SecurityRequirement().addList("Bearer认证")
            )
    }
}

