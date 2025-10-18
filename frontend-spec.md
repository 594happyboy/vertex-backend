## 前端详细设计文档（个人博客/知识库）

版本: v1.0  
作者: 项目组  
最后更新: 2025-10-18

---

### 1. 概要

- **目标**: 构建一个简洁优雅的个人博客/知识库前端，支持登录/游客访问、文档分组管理、Markdown/PDF 预览与（登录用户）编辑、导入本地文件。
- **技术栈**: Vue 3、Vite、Vue Router、Pinia、Axios、markdown-it、pdfjs-dist、vuedraggable、PrismJS、（可选）Naive UI 或 Element Plus。
- **运行环境**: 现代浏览器（Chromium/Firefox/Safari），移动端基础适配（阅读为主）。

---

### 2. 信息架构与路由

- 顶层页面
  - `/login` 登录与游客入口
  - `/me` 个人管理页（左侧目录树 + 右侧文档区）
  - `/me/doc/:id` 管理页下直达某一文档
  - `/public/:username?` 可选公开阅读页（只读，隐藏管理控件）

- 路由守卫
  - 若不存在 `accessToken` 与 `visitorToken`，重定向 `/login`
  - `visitorToken` 角色进入时，屏蔽编辑/写操作按钮，接口仅允许访问 `published` 资源
  - 令牌注入请求头：`Authorization: Bearer <token>`

---

### 3. 页面与布局

- `LoginView`
  - 表单：账号、密码、登录按钮
  - 游客进入：一键生成 `visitorToken` 并跳转管理页（只读）
  - 常见状态：加载中 / 错误提示 / 表单校验

- `AdminLayout`（已存在，可扩展）
  - 结构：顶部栏（搜索、用户菜单、主题切换）+ 左侧目录树 + 右侧内容区
  - 顶部栏：
    - 搜索框（标题/内容/标签，触发文档列表过滤或全文搜索接口）
    - 全局「＋」按钮（新建分组/文档/导入）
    - 用户菜单（个人信息、退出）
  - 左侧：`DocTree`（见组件）
  - 右侧：`DocWorkspace`（文档预览/编辑 + 工具栏）

- `PublicLayout`（可选）
  - 仅显示 `DocViewer`，隐藏编辑与管理控件

---

### 4. 组件设计

- `DocTree`
  - 功能：展示分组/文档树，支持展开/收起、新建、重命名、拖拽排序、移动、删除、右键菜单
  - 交互：
    - 节点 hover 出现操作（＋、更多）或右键菜单
    - 拖拽迁移节点（`vuedraggable`），更新 `sort_index`、`parent_id`
  - Props：
    - `treeData: TreeNode[]`
    - `selectedId: string | number | null`
    - `readOnly: boolean`（游客只读）
  - Emits：`select(node)`, `create(type, parent)`, `rename(node, name)`, `move(node, parent, index)`, `remove(node)`

- `DocWorkspace`
  - 组成：`DocToolbar` + `DocViewer`/`MdEditor`
  - Props：`doc`, `readOnly`
  - 逻辑：根据 `doc.type` 切换 `MdViewer` 或 `PdfViewer`；编辑态显示 `MdEditor`

- `DocToolbar`
  - 操作：编辑/预览切换（仅登录用户）、保存、发布状态切换、导出、更多（移动、删除）
  - 状态：保存中/已保存、草稿/已发布

- `MdViewer`
  - 技术：`markdown-it` + `prismjs` 代码高亮
  - 特性：目录（TOC）、表格、任务列表、链接外开、（可选）Mermaid/Katex

- `MdEditor`
  - 模式：左右分栏（编辑 + 实时预览）或单栏 + 预览开关
  - 功能：快捷键（Ctrl/Cmd+S 保存）、粘贴上传（可选）、图片粘贴/拖拽上传（可选）
  - 自动保存：节流 2~3s，失焦保存，手动保存按钮

- `PdfViewer`
  - 技术：`pdfjs-dist`
  - 功能：分页、缩放、页内搜索、加载进度、错误展示

- `UploadDialog` / `ImportDialog`
  - 支持选择 `.md`、`.pdf`；校验大小与类型；指定导入到的分组 `groupId`

---

### 5. 状态管理（Pinia）

- `useAuthStore`
  - state：`user`, `accessToken`, `visitorToken`, `role: 'user' | 'visitor'`
  - actions：`login`, `loginAsVisitor`, `logout`, `refresh`

- `useTreeStore`
  - state：`tree`, `expandedKeys`, `selectedId`, `loading`
  - actions：`fetchTree`, `createGroup`, `renameGroup`, `moveGroup`, `deleteGroup`, `createDoc`, `moveDoc`, `deleteDoc`, `sortDocs`

- `useDocStore`
  - state：`currentDoc`, `content`, `mode: 'view'|'edit'`, `saving`, `dirty`, `searchKeyword`
  - actions：`openDoc`, `saveDoc`, `publishDoc`, `unpublishDoc`, `importFiles`

- `useUiStore`
  - state：`theme`, `toasts`, `dialogs`

---

### 6. 接口与前端 API 封装

- 统一请求：`src/api/request.js` 基于 Axios，拦截器自动注入令牌、处理 401/403、全局错误提示
- 模块划分：
  - `auth.js`: `login`, `visitor`
  - `group.js`: `list`, `create`, `update`, `remove`, `sort`
  - `article.js`（或 `document.js`）: `list`, `detail`, `create`, `update`, `remove`, `sort`
  - `file.js`: `upload`, `batchImport`

- 约定请求头：`Authorization: Bearer <accessToken|visitorToken>`
- 游客：仅能访问 GET 类接口（后端也强校验）

---

### 7. 前端“类型”定义（文档用，便于对齐后端）

```ts
type User = { id: number; username: string; nickname?: string; avatar?: string };

type Group = {
  id: number;
  userId: number;
  name: string;
  parentId: number | null;
  sortIndex: number;
  deleted: boolean;
  children?: Group[];
};

type DocumentItem = {
  id: number;
  userId: number;
  groupId: number | null;
  title: string;
  type: 'md' | 'pdf';
  status: 'draft' | 'published';
  sortIndex: number;
  updatedAt: string;
};

type DocumentDetail = DocumentItem & {
  contentMd?: string; // md 专用
  fileId?: number; // pdf 专用
};

type UploadResult = { fileId: number; url: string; mime: string; size: number };
```

---

### 8. 关键交互流程

- 登录/游客进入
  1) 用户提交账号密码 → `POST /api/auth/login` → 存储 `accessToken`、用户信息
  2) 点击游客进入 → `POST /api/auth/visitor` → 存储 `visitorToken`、只读角色
  3) 成功后跳转 `/me`

- 加载目录树
  1) 进入 `/me` → `GET /api/groups` 返回树结构
  2) 默认选中最近打开/首个文档 → 调 `GET /api/documents/:id`

- 新建文档
  1) 点击「＋」选择「新建 Markdown」→ `POST /api/documents`
  2) 切换到编辑态，初始内容模板

- 导入 `.md/.pdf`
  1) 打开导入对话框 → 选择文件 → `POST /api/uploads`
  2) 成功后 `POST /api/documents`（type=pdf/md，关联 `fileId` 或内容）

- 拖拽移动/排序
  1) 拖拽节点 → 计算新 `parentId` 与 `sortIndex`
  2) `POST /api/sort/groups` 或 `POST /api/sort/documents`

- 发布/取消发布
  1) 工具栏切换 → `PATCH /api/documents/:id { status }`
  2) 游客仅可见 `published`

---

### 9. 权限与安全（前端侧）

- 路由守卫：无令牌跳转 `/login`
- 按角色隐藏按钮（`readOnly`）：编辑/保存/删除/新建在游客模式隐藏或禁用
- XSS 处理：`markdown-it` 配置安全白名单；外链 `rel="noopener noreferrer"`
- 文件类型/大小校验（前端初步校验 + 后端强制校验）

---

### 10. 性能优化

- 路由与大型依赖（`pdfjs`, 编辑器）按需懒加载
- 文档切换内容缓存（Pinia + keep-alive 可选）
- 目录树与文档列表分页/惰性渲染（数量巨大时）
- 编辑自动保存使用节流/去抖，减少写请求

---

### 11. 无障碍 / 国际化 / 主题

- 无障碍：键盘可达、语义化标签、对比度达标、图片替代文本
- i18n：接口错误与界面文案集中管理，便于后续接入 `vue-i18n`
- 主题：`src/styles/tokens.css` 维护 CSS 变量，支持明暗色与品牌色切换

---

### 12. 错误处理与反馈

- 统一错误拦截：鉴权失败 → 清理令牌并引导重新登录
- 操作结果：使用全局 toast；危险操作（删除/发布）使用二次确认对话框
- 空态与骨架屏：树与内容区在加载时显示骨架；空态给出引导

---

### 13. 测试与质量

- 单元：Pinia store、工具函数、渲染器插件
- 组件：`DocTree` 拖拽、`MdEditor` 保存/切换、`PdfViewer` 分页
- E2E：登录/游客、导入、发布、权限隐藏
- 可维护性：约定提交信息、ESLint + Prettier（项目已有规范保持一致）

---

### 14. 与现有项目的整合

- 目录与文件：
  - `src/components` 新增 `DocTree.vue`, `DocWorkspace.vue`, `MdViewer.vue`, `MdEditor.vue`, `PdfViewer.vue`, `DocToolbar.vue`, `UploadDialog.vue`
  - `src/stores` 新增 `doc.js`, `tree.js`, （复用现有 `auth.js`）
  - `src/api` 复用/扩展 `article.js`（或新增 `document.js`）与 `file.js`
- 路由：在 `src/router/index.js` 增加 `/me`, `/me/doc/:id`, `/public/:username?`
- 样式：继续使用 `styles/tokens.css`

---

### 15. 里程碑与交付物

- M1 鉴权与空骨架：登录/游客、路由守卫、空白树与内容区
- M2 目录树 CRUD 与拖拽：分组/文档增删改查、移动/排序
- M3 渲染与编辑：Markdown 渲染/编辑、PDF 预览、导入
- M4 发布与只读：发布切换、游客只读、公开页（可选）
- M5 打磨与优化：性能、安全、无障碍、测试

---

### 16. 风险与边界

- 超大文档渲染性能与内存占用（需分段渲染/虚拟化或延迟高亮）
- 拖拽频繁写入导致排序一致性问题（采用批量排序接口、前端乐观更新 + 回滚）
- 游客与登录令牌并存时的优先级与来源切换（明确优先使用 `accessToken`）

---

### 17. 后续可选增强

- 文档版本历史与对比回滚
- 标签体系、全文搜索、引用/反向链接
- 外链分享（签名 + 过期）
- 导出 PDF/HTML、一键发布为静态站


