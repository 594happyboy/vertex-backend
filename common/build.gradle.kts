dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-data-redis")
    
    // MyBatis Plus - 使用 api 暴露给依赖模块
    api("com.baomidou:mybatis-plus-boot-starter:3.5.5")
    
    // MinIO - 使用 api 暴露给依赖模块
    api("io.minio:minio:8.5.7")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // Jackson Kotlin 模块 - 使用 api 暴露给依赖模块
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Hutool - 使用 api 暴露给依赖模块
    api("cn.hutool:hutool-all:5.8.24")
    
    // Knife4j (Swagger增强版) - 使用 api 暴露给依赖模块
    api("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:4.4.0")
    
    // JWT - 使用 api 暴露给依赖模块
    api("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
}

