# 文档批量上传实时进度反馈方案设计

## 一、背景与现状

- **现有接口**：`POST /api/documents/batch-upload`  
  - 请求：上传 ZIP 压缩包（`file`）+ 可选 `parentGroupId`  
  - 实现：在后端一次性完成 ZIP 解压、目录创建、文档创建等所有操作，最后返回 `BatchUploadResponse`  
  - 特点：**同步长耗时请求**，前端在请求返回前没有进度信息，只能“转圈”等待

- **问题**：
  - 大 ZIP 包 / 文件较多时，请求时间可能较长，用户体验不好
  - 无法在前端展示“已处理 / 总数”的进度条
  - 不利于后续扩展（如任务监控、后台重试等）

## 二、目标与非目标

- **目标**
  - 在不破坏现有同步接口的前提下，新增一套“异步批量上传 + 进度查询”机制
  - 前端可以在上传过程中显示进度（例如：`已处理 30 / 总 100`，或百分比）
  - 支持在任务完成后获取与当前 `BatchUploadResponse` 一致的最终结果

- **非目标**
  - 不处理“取消任务”“暂停/恢复任务”等复杂控制能力（后续可扩展）
  - 不在本阶段引入复杂的分布式任务队列组件（如 MQ、调度集群等）
  - 不改动现有同步接口的调用方（保持兼容）

## 三、总体方案摘要

- **核心思路：异步任务 + `jobId` + HTTP 轮询进度接口**
  - 新增一个“发起异步批量上传”的接口，立即返回 `jobId`
  - 后端在后台线程中执行原有 `batchUpload` 逻辑，并在处理每个文件/目录时更新进度信息
  - 新增一个“查询进度”的接口，前端用 `jobId` 定时轮询，展示进度条和最终结果
  - 现有同步接口 `POST /api/documents/batch-upload` 保持不变，作为兼容路径或简单场景使用

- **任务存储**
  - 第一阶段：使用应用内存（`ConcurrentHashMap<String, BatchUploadProgress>`）保存任务进度
  - 后续如需多实例部署，可将实现替换为 Redis / 数据库存储，接口不变

## 四、后端设计

### 4.1 新增进度状态枚举

```text
BatchUploadJobStatus
- PENDING      // 已创建，等待执行
- RUNNING      // 正在处理
- COMPLETED    // 处理完成
- FAILED       // 处理失败
```

### 4.2 新增进度 DTO（返回给前端）

`BatchUploadProgress`（示意字段）：

- **`jobId: String`**  
- **`status: String`**（取值为上面的 `PENDING/RUNNING/COMPLETED/FAILED`）  
- **`totalFiles: Int`**（预计处理的文件数，可为 0，边处理边累加）  
- **`totalFolders: Int`**  
- **`processedFiles: Int`**（已尝试处理的文件数）  
- **`successCount: Int`**（成功的文件数）  
- **`failedCount: Int`**  
- **`message: String`**（描述当前阶段或错误原因，如“正在解压 ZIP”“处理完成：部分失败”等）  
- **`result: BatchUploadResponse?`**（仅在 `COMPLETED` 或 `FAILED` 时非空，包含详细结果列表）  

> 说明：`BatchUploadResponse` 为现有结构，复用即可。

### 4.3 任务存储与生命周期

- **任务存储**
  - 定义一个简单的任务管理器（如 `BatchUploadJobManager`），内部维护：
    - `Map<String, BatchUploadProgress>`：按 `jobId` 存储进度
  - 接口示意：
    - 创建任务：生成 `jobId`，插入初始 `BatchUploadProgress`（`PENDING`）
    - 查询任务：根据 `jobId` 返回进度
    - 更新任务：在后台处理过程中多次更新（状态、计数、消息等）
    - 任务结束：设置为 `COMPLETED` 或 `FAILED`，填充最终 `result`
    - 清理策略：可在任务结束后保留一段时间（如 30 分钟），定期清理

- **任务状态流转（典型流程）**
  1. 创建任务 → `PENDING`
  2. 后台线程开始执行 → `RUNNING`
  3. 处理过程中：不断更新 `processedFiles` / `successCount` / `failedCount`
  4. 处理完成（正常）：`status = COMPLETED`，写入 `result`
  5. 处理异常：`status = FAILED`，写入 `message`（简要错误信息）

### 4.4 新增接口设计

#### 4.4.1 发起异步批量上传

- **URL**：`POST /api/documents/batch-upload/async`  
- **Content-Type**：`multipart/form-data`
- **请求参数**
  - `file`: ZIP 文件（同现有接口）
  - `parentGroupId`（可选）：Long

- **响应结构示例**

```json
{
  "code": 0,
  "message": "任务已创建",
  "data": {
    "jobId": "20251124-xyz123"
  }
}
```

> 说明：外层结构继续复用你现有的 `ApiResponse`。

- **后端内部流程概述**
  1. 校验文件（与现在 `batchUpload` 相同）
  2. 创建任务，生成 `jobId`，写入初始 `BatchUploadProgress`（`PENDING`）
  3. 提交一个后台任务（线程池 / `@Async`），在后台调用新的内部方法执行真正的批量上传逻辑，并周期性更新进度
  4. 控制器立刻返回 `jobId`，不阻塞等待上传完成

#### 4.4.2 查询批量上传进度

- **URL**：`GET /api/documents/batch-upload/progress/{jobId}`  
- **请求参数**
  - `jobId`：路径参数

- **响应结构示例**

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "jobId": "20251124-xyz123",
    "status": "RUNNING",
    "totalFiles": 120,
    "totalFolders": 10,
    "processedFiles": 45,
    "successCount": 44,
    "failedCount": 1,
    "message": "正在处理文件: docs/chapter1.md",
    "result": null
  }
}
```

- 当 `status = COMPLETED` 或 `FAILED` 时，`result` 字段为完整的 `BatchUploadResponse`：

```json
"result": {
  "success": true,
  "totalFiles": 120,
  "totalFolders": 10,
  "successCount": 118,
  "failedCount": 2,
  "items": [
    // ... 与现有返回一致
  ],
  "message": "批量上传完成，部分失败"
}
```

#### 4.4.3 现有同步接口保留

- `POST /api/documents/batch-upload` 保持不变，逻辑仍然直接调用 `BatchUploadService.batchUpload` 并同步返回 `BatchUploadResponse`。
- 新增异步接口采用相同的业务逻辑，只是包装在后台任务中执行，并额外维护进度信息。

### 4.5 Service 改造思路（高层描述）

- 在 `BatchUploadService` 中：
  - 将当前 `batchUpload(file, parentGroupId)` 内部的核心逻辑抽成一个内部方法，如 `doBatchUpload(file, parentGroupId, progressUpdater)`  
  - `progressUpdater` 是一个简单的回调接口，用于在关键节点更新进度（例如处理每个 `processFile` 完成后调用）
  - 现有同步接口调用时，可以传一个“空实现”的 `progressUpdater`，不更新进度；
  - 异步接口调用时，则传入真实的进度更新实现（写入 `BatchUploadJobManager`）。

> 本阶段文档仅做概念设计，实现时再按具体代码结构细化。

### 4.6 权限、错误与边界情况

- **权限控制**
  - 与现有 `batchUpload` 一致：必须是登录用户，且只能操作自己权限范围内的分组
  - 进度查询接口应校验 `jobId` 对应任务是否属于当前用户（防止越权查看他人任务）

- **错误处理**
  - `jobId` 不存在 → 返回 404 或业务错误码（如 `code != 0`，message 提示“任务不存在或已过期”）
  - 任务执行失败 → `status=FAILED`，`message` 中简要描述原因，`result` 可选是否带上部分成功项（视实现而定）

- **任务过期**
  - 可在任务完成后保留一段时间（例如 30 分钟~1 小时），定期清理旧任务，以防内存占用过高

## 五、前端（Vue）改造设计

### 5.1 新的交互流程（高层）

1. **选择文件并提交异步上传**
   - 使用 `FormData` 构造请求（`file` + 可选 `parentGroupId`）
   - 调用 `POST /api/documents/batch-upload/async`
   - 从响应中拿到 `jobId`

2. **轮询任务进度**
   - 使用 `setInterval` 或类似机制，每 1～2 秒调用  
     `GET /api/documents/batch-upload/progress/{jobId}`
   - 根据返回的 `status`、`processedFiles`、`totalFiles` 计算并展示进度条
     - 例如：`percent = totalFiles > 0 ? processedFiles / totalFiles * 100 : 0`

3. **结束态处理**
   - 当 `status === 'COMPLETED'`：
     - 停止轮询
     - 显示“上传完成”提示
     - 展示 `result.items` 的成功/失败列表（可以只做简单统计展示）
   - 当 `status === 'FAILED'`：
     - 停止轮询
     - 弹出错误提示（可用 `message` 字段 + 后端错误码）

4. **异常与重试**
   - 轮询请求本身失败（网络问题等）时，可以做有限次重试或给出提示
   - 如需重新上传，可重新走第 1 步，发起新的 `jobId`

### 5.2 前端需要改动的点（概要）

- **API 调用层**
  - 新增两个接口方法：
    - `startBatchUploadAsync(formData) -> Promise<{ jobId: string }>`
    - `getBatchUploadProgress(jobId) -> Promise<BatchUploadProgressDto>`

- **上传页面/组件**
  - 原来直接调 `POST /batch-upload` 的地方，改为调用新的异步接口
  - 新增进度状态管理（例如在组件中增加如下状态字段）：
    - `uploadJobId`
    - `uploadStatus`（`PENDING/RUNNING/COMPLETED/FAILED/IDLE`）
    - `progressPercent`
    - `successCount`, `failedCount`（可选）
  - 新增一个显示进度条和状态信息的 UI 区域：
    - 上传中：展示进度条 + “正在解压/处理第 X 个文件”
    - 完成：展示统计结果 + 可选的详情列表入口
    - 失败：展示错误提示

- **可选：上传进度（网络层）**
  - 如果希望展示“文件上传到服务器”的进度条（不是处理进度），可以在 Axios 请求中使用 `onUploadProgress`（或等价实现）。
  - 该进度与本文的“服务器处理 ZIP”进度互补，可在 UI 上区分两段进度（上传 → 解析/处理）。

## 六、兼容性与演进

- **兼容性**
  - 原有同步接口继续保留，不影响现有调用方
  - 新异步接口可逐步推广，前端可按模块/用户逐步迁移

- **后续可演进方向（留作扩展）**
  - 将任务存储从内存迁移到 Redis，以支持多实例部署和进程重启后的任务恢复
  - 增加“取消任务”“重试任务”等控制接口
  - 将轮询模式替换为 SSE 或 WebSocket 推送（在网关/运维条件成熟的前提下）

---

**总结**：
以上是“文档批量上传实时进度反馈”的整体设计方案，采用“异步任务 + 轮询进度”的企业常用实现方式，并描述了后端接口、任务模型及前端需要调整的点。
