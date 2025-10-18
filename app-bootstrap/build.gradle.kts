plugins {
    id("org.springframework.boot")
}

dependencies {
    // Spring Boot 核心依赖
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // 引入所有业务模块
    implementation(project(":module-file"))
    implementation(project(":module-blog"))
    
    // MySQL 驱动
    runtimeOnly("com.mysql:mysql-connector-j")
    
    // Override MyBatis Spring 版本（修复 Spring 6.1+ 兼容性）
    implementation("org.mybatis:mybatis-spring:3.0.3")
    implementation("org.mybatis:mybatis:3.5.15")
    
    // 开发工具
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    
    // 测试依赖
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("multifunctional-backend.jar")
}

