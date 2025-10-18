package com.zzy.blog.controller

import com.zzy.common.dto.ApiResponse
import com.zzy.blog.dto.response.StatsResponse
import com.zzy.blog.service.StatsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 仪表盘统计控制器
 */
@Tag(name = "仪表盘统计接口")
@RestController
@RequestMapping("/api/admin/dashboard")
class DashboardController(
    private val statsService: StatsService
) {
    
    /**
     * 获取仪表盘统计数据
     */
    @Operation(summary = "获取统计数据")
    @GetMapping("/stats")
    fun getStats(): ApiResponse<StatsResponse> {
        val stats = statsService.getDashboardStats()
        return ApiResponse.success(stats)
    }
}

