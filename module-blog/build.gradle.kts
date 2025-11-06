dependencies {
    // 依赖内部模块（已包含 MyBatis Plus、Swagger 等）
    implementation(project(":common"))
    implementation(project(":module-file"))
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // 拼音工具（slug生成）
    implementation("com.github.stuxuhai:jpinyin:1.1.8")
    
    // BCrypt 密码加密
    implementation("org.springframework.security:spring-security-crypto")
}

