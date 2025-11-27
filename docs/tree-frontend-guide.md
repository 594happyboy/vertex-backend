# 目录树前端对接指南（文件夹优先 + 拖拽重排）

前端接入 `/api/tree` 新接口的完整说明。

## API 约定

- `GET /api/tree`
  - 需鉴权。
  - 返回：`tree: DirectoryTreeNode[]`, `cached: boolean`。
  - 节点结构：
    ```json
    {
      "id": 1,
      "nodeType": "group",          // "group" | "document"（大小写不敏感，推荐小写）
      "name": "Folder A",
      "parentId": null,
      "orderIndex": 100,
      "type": "md",                 // 仅文档有
      "children": [ ... ]
    }
    ```
  - 顺序：后端已按“文件夹优先 + orderIndex”返回。

- `POST /api/tree/reorder`
  - 需鉴权。
  - 请求体（一次只针对同一个 parent）：
    ```json
    {
      "parentId": 123,              // 目标父分组；根层传 null
      "items": [
        { "nodeId": 10, "nodeType": "group",    "orderIndex": 100 },
        { "nodeId": 21, "nodeType": "document", "orderIndex": 200 }
      ]
    }
    ```
  - 规则：
    - `nodeType` 用 `"group"` / `"document"`（推荐小写）。
    - `items` 必须包含该 parent 下的最终完整顺序。
    - 后端会按“文件夹优先 + orderIndex”重新规范化，并阻止把分组拖进自己的子树。
    - 成功无负载，刷新树即可。

## 拖拽协议（前端建议）

1) 允许落点：任意文件夹或根；禁止落在文件上（只能落入文件夹或根）。  
2) 拖拽结束后：
   - 确定目标父节点 `parentId`（根为 null）。
   - 生成该父节点下的最终有序列表（已知类型时可先把文件夹放前，后端也会强制）。
   - 给出顺序号 `orderIndex`（建议 100, 200, 300 递增）。  
   - 调用 `POST /api/tree/reorder`。
3) 成功：更新本地树，或重新拉 `GET /api/tree`。  
4) 失败：回滚本地 UI，并提示后端错误信息。

## 排序规则（前端渲染）

- 渲染顺序：文件夹在前，其次按 `orderIndex` 升序（与后端一致，避免抖动）。
- 客户端生成 `orderIndex` 时用固定步长（如 100），减少频繁重排；后端会再规范化。

## 需处理的边界

- 拖拽分组到其子孙：后端 400，需提示并回滚。
- 移动到根：`parentId` 传 `null`。
- 混合拖拽：允许；`items` 里带上最终顺序即可。
- 批量上传/创建/删除后：重新请求 `GET /api/tree`。

## 最小调用示例（TypeScript 伪代码）

```ts
async function reorder(parentId: number | null, nodes: { id: number; nodeType: "group" | "document" }[]) {
  const items = nodes.map((n, idx) => ({
    nodeId: n.id,
    nodeType: n.nodeType,          // "group" / "document"
    orderIndex: (idx + 1) * 100,
  }));

  await api.post("/api/tree/reorder", { parentId, items });
  const refreshed = await api.get("/api/tree");
  return refreshed.data.tree;
}
```

## 快速问答

- 问：`nodeType` 大小写？  
  答：后端大小写不敏感，推荐统一小写 `"group"` / `"document"`。
- 问：只传被移动的节点可以吗？  
  答：不可以，需传该父节点下的完整最终顺序，后端才能精确落库。
- 问：文档可以放在根吗？  
  答：可以，`parentId` 传 `null`。
