package com.zzy.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger/Knife4j 配置
 * 访问地址：http://localhost:8080/doc.html
 */
@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("多功能后端系统 API")
                    .description("包含博客、文件管理等功能的后端系统")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("ZZY")
                            .email("your-email@example.com")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0.html")
                    )
            )
    }
}

