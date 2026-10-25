# JsonExcelConvert

前后端分离的 JSON ⇄ Excel 双向转换工具：JSON 一键导出带样式表头的 `.xlsx`，Excel 一键转换为结构化 JSON，全程页面实时预览。

- 后端：Spring Boot 2.7 + Apache POI（Maven 构建，JDK 8）
- 前端：Vue 3 + Vite

## 功能特性

- **JSON → Excel**：支持对象数组 `[{...}]`、单个对象 `{...}`、含单个数组的对象 `{"data":[...]}`
- **Excel → JSON**：上传 `.xlsx / .xls`，解析表头生成 JSON，支持复制与下载 `.json`
- 嵌套对象扁平化为列（如 `address.city`），分隔符可配置；Excel 侧可反向还原为嵌套对象
- **自定义导出列**：按需勾选导出列、↑↓ 调整列顺序
- **表头中英文映射**：为任意字段自定义导出表头（如 `name` → `姓名`）
- 标量数组（如 `["a","b"]`）合并为一列，复杂数组保留为 JSON 文本；Excel 单元格自动识别数值/布尔/日期
- 页面实时预览：行列统计、长文本省略、空值占位符
- 导出 xlsx：表头加粗填色、边框、冻结首行、列宽自适应、文件名中文编码

## 目录结构

```
JsonExcelConvert/
├── backend/                 # Spring Boot + Maven 后端
│   ├── pom.xml
│   └── src/main/java/com/jsonexcel/
│       ├── JsonExcelApplication.java
│       ├── config/CorsConfig.java
│       ├── controller/      # ConvertController / GlobalExceptionHandler
│       ├── dto/             # ConvertRequest / TableData / PreviewResponse / ExcelParseResponse
│       └── service/         # JsonTableService / ExcelExportService / ExcelParseService
└── frontend/                # Vue 3 + Vite 前端
    ├── package.json
    ├── vite.config.js       # /api 代理到 localhost:8080
    └── src/
        ├── App.vue          # 双模式工作台（JSON→Excel / Excel→JSON）
        ├── components/      # JsonInputPanel / PreviewTable / ColumnConfig / ExcelImportPanel / JsonOutputPanel
        └── styles/main.css
```

## 启动方式

### 1. 启动后端（端口 8080）

```bash
cd backend
mvn spring-boot:run
```

打包部署：

```bash
mvn clean package -DskipTests
java -jar target/json-excel-backend-1.0.0.jar
```

### 2. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 <http://localhost:5173>，页面请求通过 Vite 代理转发到后端。生产构建 `npm run build` 产物在 `frontend/dist`，可托管到任意静态服务器，并将 `/api` 反向代理到后端 8080。

## Docker 一键部署

前后端打包为单个镜像（前端构建产物嵌入 Spring Boot jar，由后端同源托管，无需额外配置代理）：

```bash
docker compose up -d --build
```

浏览器访问 <http://localhost:8080> 即可使用。常用命令：

```bash
docker compose logs -f    # 查看日志
docker compose down       # 停止并删除容器
```

也可手动构建镜像：

```bash
docker build -t json-excel .
docker run -d -p 8080:8080 --name json-excel json-excel
```

> 基础镜像默认走 `docker.m.daocloud.io` 加速源（见 `docker-compose.yml` 中的 `REGISTRY` 构建参数）。可访问 Docker Hub 的环境可改为 `docker.io/library`。

## Python 版（backend-py）

`backend-py/` 是后端的 Python 实现（Flask + openpyxl/xlrd），API 与 Java 版完全一致，**复用同一套前端页面**（托管 `frontend/dist`）。适合无 Java 环境的服务器部署。

本地运行（Python 3.7+）：

```bash
cd backend-py
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python app.py        # 默认 http://localhost:8081
```

Docker 一键部署（默认 8081 端口，可与 Java 版 8080 并存）：

```bash
docker compose -f docker-compose.python.yml up -d --build
```

浏览器访问 <http://localhost:8081> 即可使用。可配置环境变量：`PORT`（监听端口）、`PREVIEW_MAX_ROWS`（预览行数上限，默认 200）、`EXCEL_MAX_ROWS`（Excel 解析行数上限，默认 20000）、`MAX_UPLOAD_MB`（上传大小限制，默认 20）。

## 接口说明

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/convert/preview` | 解析 JSON 并返回表格预览（最多 200 行，应用列配置与表头映射） |
| POST | `/api/convert/export` | 解析 JSON 并导出 xlsx 文件（应用列配置与表头映射） |
| POST | `/api/convert/excel-to-json` | 上传 `.xlsx/.xls` 转换为 JSON（multipart 表单） |

### preview / export 请求体（`application/json`）

```json
{
  "json": "[{\"id\":1,\"name\":\"张三\"}]",
  "fileName": "json-export",
  "sheetName": "Sheet1",
  "emptyValue": "",
  "keySeparator": ".",
  "columns": ["name", "id"],
  "headerAliases": { "name": "姓名", "id": "编号" }
}
```

- `json`：用户输入的 JSON 文本（字符串）
- `fileName`：导出文件名（不含扩展名）
- `sheetName`：工作表名称（非法字符自动替换，最长 31 字符）
- `emptyValue`：空值占位符
- `keySeparator`：嵌套字段分隔符，默认 `.`
- `columns`：自定义导出列（原始字段名，数组顺序即导出顺序），为空导出全部列
- `headerAliases`：表头映射 `原始字段名 -> 显示名`，未映射的字段沿用原字段名

`preview` 响应额外返回 `availableColumns`（全量原始字段），供前端渲染列配置面板。

### excel-to-json 参数（multipart/form-data）

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `file` | 必填 | `.xlsx` / `.xls` 文件（最大 20MB） |
| `hasHeader` | `true` | 首行是否为表头 |
| `expandNested` | `true` | 是否将 `a.b` 形式表头还原为嵌套对象 |
| `autoType` | `true` | 是否自动识别数值/布尔（否则全部输出字符串） |
| `keySeparator` | `.` | 嵌套字段分隔符 |

响应包含 `headers`、`rows`（预览数据）、`jsonText`（完整 JSON 文本）、`sheetName`、`totalRows`、`dataTruncated`（是否因行数上限截断，默认上限 20000 行）。

错误响应统一为 `{"message": "..."}`，HTTP 状态码 400 / 500。
