# 文档批量上传 - 前端适配说明

> 适用版本：新增异步批量上传（`/api/documents/batch-upload/async`）与进度查询接口。

## 1. 接口速览

| 功能 | Method & URL | 说明 |
| --- | --- | --- |
| 发起异步批量上传 | `POST /api/documents/batch-upload/async` | 上传 ZIP，立即返回 `jobId` |
| 查询任务进度 | `GET /api/documents/batch-upload/progress/{jobId}` | 轮询任务状态、进度、结果 |
| 原同步上传（兼容） | `POST /api/documents/batch-upload` | 可留作应急或旧入口 |

所有接口继续使用统一的 `ApiResponse` 包装，`code=0` 表示成功。

## 2. 发起任务

```ts
const formData = new FormData();
formData.append('file', file);             // ZIP 文件
if (parentGroupId) formData.append('parentGroupId', parentGroupId.toString());

const res = await api.post('/api/documents/batch-upload/async', formData);
const jobId = res.data.data.jobId;
```

- 建议在上传请求上展示“正在上传 ZIP”进度条（如使用 axios `onUploadProgress`），该进度与服务器处理进度互补。
- 上传成功后立即进入轮询阶段。

## 3. 进度查询

```ts
const res = await api.get(`/api/documents/batch-upload/progress/${jobId}`);
const progress = res.data.data;
```

返回体示例：

```json
{
  "jobId": "1763984120842-bc9e5ffc",
  "status": "RUNNING",
  "totalFiles": 120,
  "totalFolders": 18,
  "processedFiles": 45,
  "successCount": 44,
  "failedCount": 1,
  "message": "正在处理文件: docs/chapter1.md",
  "result": null
}
```

字段含义：

- `status`：`PENDING`（排队）、`RUNNING`（处理中）、`COMPLETED`、`FAILED`。
- `totalFiles` / `totalFolders`：在处理前已完整统计，整个任务中保持不变；用于展示百分比。
- `processedFiles`：已尝试处理的文件数（成功或失败都会 +1）。
- `successCount` / `failedCount`：与 `processedFiles` 对应，`successCount + failedCount = processedFiles`。
- `message`：最新状态提示，可用于 UI 的“当前阶段”描述。
- `result`：任务结束（完成/失败）后返回完整的 `BatchUploadResponse`，包含逐项结果。

百分比计算：

```ts
const percent = progress.totalFiles > 0
  ? Math.round((progress.processedFiles / progress.totalFiles) * 100)
  : 0;
```

## 4. 轮询策略

1. **开始**：获取 `jobId` 后立即轮询；建议 1~2 秒一次，可根据页面负载适当调整。
2. **停止条件**：当 `status` 为 `COMPLETED` 或 `FAILED` 即可停止。
3. **异常处理**：
   - 请求网络错误：提示“查询进度失败，稍后重试”，并允许手动刷新或自动几次重试后停下。
   - `code != 0` 或报错信息提示“任务不存在或已过期”：停止轮询并告知用户任务已失效。

## 5. 结束态展示

- **COMPLETED**：
  - 展示成功提示，可根据 `result.success` 判断是否全量成功或“部分失败”。
  - 列表展示 `result.items` 中的成功/失败条目，或至少展示 `successCount/failedCount`。
- **FAILED**：
  - 使用 `message` 作为错误提示。
  - 如果 `result` 不为空，可展示部分已完成的记录供用户参考。

## 6. 状态提示文案建议

| status | message 示例 | 前端展示建议 |
| --- | --- | --- |
| `PENDING` | “任务已创建，等待执行” | “任务排队中…” |
| `RUNNING` | “正在处理文件: xxx.md” | 结合进度条显示当前文件路径 |
| `COMPLETED` | “批量上传成功” / “批量上传完成，部分失败” | 绿色完成提示；显示统计 |
| `FAILED` | “批量上传失败: xxx” | 红色失败提示，附错误原因 |

## 7. 其他注意事项

- 进度数据目前存储在后端内存中，任务完成后会保留一段时间（约 60 分钟）；如看到“任务不存在或已过期”，需要重新上传。
- 同一个用户可并发多个任务，但需要在 UI 中区分不同 `jobId`，避免轮询错乱。
- 若需取消任务，目前后端尚未提供取消接口，只能等待任务结束或刷新后重新上传。

---
如需更多字段或 SSE/WebSocket 方案，可在前端完成该轮询版适配后再行评估。*** End Patch
