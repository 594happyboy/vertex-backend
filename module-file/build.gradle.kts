dependencies {
    // 依赖通用模块（已包含 MyBatis Plus、Swagger 等）
    implementation(project(":common"))
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // Commons IO
    implementation("commons-io:commons-io:2.15.1")
}

