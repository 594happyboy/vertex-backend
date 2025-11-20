package com.zzy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    // ⭐ 扫描所有模块的包
    scanBasePackages = [
        "com.zzy.common",    // 通用模块
        "com.zzy.file",      // 文件模块
        "com.zzy.blog",      // 博客模块
        "com.zzy.search"     // 搜索模块
    ]
)
@org.springframework.scheduling.annotation.EnableScheduling  // 启用定时任务
class VertexBackendApplication

fun main(args: Array<String>) {
    runApplication<VertexBackendApplication>(*args)
}

