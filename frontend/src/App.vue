<script setup>
import { computed, ref } from 'vue'
import JsonInputPanel from './components/JsonInputPanel.vue'
import PreviewTable from './components/PreviewTable.vue'
import ColumnConfig from './components/ColumnConfig.vue'
import ExcelImportPanel from './components/ExcelImportPanel.vue'
import JsonOutputPanel from './components/JsonOutputPanel.vue'
import FloatingPreview from './components/FloatingPreview.vue'
import JsonModal from './components/JsonModal.vue'

const SAMPLE_JSON = `{
  "data": [
    { "id": 1001, "name": "张三", "age": 28, "active": true, "address": { "city": "上海", "district": "浦东新区" }, "tags": ["java", "spring", "vue"], "score": 95.5 },
    { "id": 1002, "name": "李四", "age": 34, "active": false, "address": { "city": "北京", "district": "海淀区" }, "tags": ["excel", "json"], "score": 88 },
    { "id": 1003, "name": "王五", "age": 25, "active": true, "address": { "city": "深圳", "district": "南山区" }, "tags": ["vue"], "score": 91.2 }
  ]
}`

const mode = ref('json2excel')
const floatingPreview = ref(false)
const jsonModal = ref(false)
const splitRatio = ref(45)

function startSplitDrag(e) {
  const body = e.currentTarget.parentElement
  const rect = body.getBoundingClientRect()
  function onMove(ev) {
    const pct = ((rect.bottom - ev.clientY) / rect.height) * 100
    splitRatio.value = Math.round(Math.min(75, Math.max(20, pct)))
  }
  function onUp() {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

const jsonText = ref('')
const fileName = ref('json-export')
const sheetName = ref('Sheet1')
const emptyValue = ref('')
const keySeparator = ref('.')

const preview = ref(null)
const columnConfig = ref([])
const rawColumns = ref([])
const previewLoading = ref(false)
const exportLoading = ref(false)

const excelFileName = ref('')
const excelFile = ref(null)
const excelHasHeader = ref(true)
const excelExpandNested = ref(true)
const excelAutoType = ref(true)
const excelAggregateMerged = ref(false)
const excelResult = ref(null)
const excelLoading = ref(false)
const excelColumnConfig = ref([])
const excelRawColumns = ref([])
const excelRecords = ref([])
const excelOutputText = ref('')
const excelOutputHeaders = ref([])
const excelOutputRows = ref([])
const EXCEL_PREVIEW_ROWS = 200

const errorMessage = ref('')
const toast = ref(null)
let toastTimer = null
let previewTimer = null
let excelConfigTimer = null

const charCount = computed(() => jsonText.value.length)
const lineCount = computed(() => (jsonText.value ? jsonText.value.split('\n').length : 0))

function showToast(message, type = 'success') {
  toast.value = { message, type }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = null), 2600)
}

function enabledColumns() {
  return columnConfig.value.filter((c) => c.enabled)
}

function payload() {
  const enabled = enabledColumns()
  const aliases = {}
  enabled.forEach((c) => {
    if (c.alias.trim()) aliases[c.field] = c.alias.trim()
  })
  return {
    json: jsonText.value,
    fileName: fileName.value,
    sheetName: sheetName.value,
    emptyValue: emptyValue.value,
    keySeparator: keySeparator.value,
    columns: enabled.map((c) => c.field),
    headerAliases: aliases
  }
}

function syncColumns(available) {
  const old = columnConfig.value
  const next = []
  old.forEach((c) => {
    if (available.indexOf(c.field) >= 0) next.push({ ...c })
  })
  available.forEach((field) => {
    if (!next.some((c) => c.field === field)) next.push({ field, enabled: true, alias: '' })
  })
  columnConfig.value = next
}

function schedulePreview() {
  if (!preview.value) return
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => doPreview(true), 400)
}

function toggleColumn(field) {
  const item = columnConfig.value.find((c) => c.field === field)
  if (item) item.enabled = !item.enabled
  schedulePreview()
}

function renameColumn(field, alias) {
  const item = columnConfig.value.find((c) => c.field === field)
  if (item) item.alias = alias
  schedulePreview()
}

function moveColumn(index, delta) {
  const target = index + delta
  if (target < 0 || target >= columnConfig.value.length) return
  const list = columnConfig.value
  ;[list[index], list[target]] = [list[target], list[index]]
  schedulePreview()
}

function pinColumn(index) {
  const list = columnConfig.value
  if (index <= 0 || index >= list.length) return
  const [moved] = list.splice(index, 1)
  list.unshift(moved)
  schedulePreview()
}

function bottomColumn(index) {
  const list = columnConfig.value
  if (index < 0 || index >= list.length - 1) return
  const [moved] = list.splice(index, 1)
  list.push(moved)
  schedulePreview()
}

function setAllColumns(enabled) {
  columnConfig.value = columnConfig.value.map((c) => ({ ...c, enabled }))
  schedulePreview()
}

function sortColumn(from, to) {
  const list = columnConfig.value
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return
  const [moved] = list.splice(from, 1)
  list.splice(to, 0, moved)
  schedulePreview()
}

function resetColumns() {
  const base = rawColumns.value.length ? rawColumns.value : columnConfig.value.map((c) => c.field)
  columnConfig.value = base.map((field) => ({ field, enabled: true, alias: '' }))
  schedulePreview()
}

function loadSample() {
  jsonText.value = SAMPLE_JSON
  errorMessage.value = ''
  showToast('已载入示例数据')
}

function formatJson() {
  if (!jsonText.value.trim()) {
    errorMessage.value = '请先输入 JSON 内容'
    return
  }
  try {
    jsonText.value = JSON.stringify(JSON.parse(jsonText.value), null, 2)
    errorMessage.value = ''
    showToast('格式化完成')
  } catch (e) {
    errorMessage.value = 'JSON 格式错误：' + e.message
  }
}

function clearAll() {
  jsonText.value = ''
  preview.value = null
  columnConfig.value = []
  errorMessage.value = ''
}

async function handleResponse(res) {
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error((data && data.message) || `请求失败（${res.status}）`)
  return data
}

async function doPreview(silent = false) {
  if (!jsonText.value.trim()) {
    if (!silent) errorMessage.value = '请先输入 JSON 内容'
    return
  }
  if (columnConfig.value.length && !enabledColumns().length) {
    if (!silent) errorMessage.value = '至少保留一个导出列'
    return
  }
  if (!silent) {
    previewLoading.value = true
    errorMessage.value = ''
  }
  try {
    const res = await fetch('/api/convert/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload())
    })
    const data = await handleResponse(res)
    preview.value = data
    if (data.availableColumns) {
      rawColumns.value = data.availableColumns
      syncColumns(data.availableColumns)
    }
    if (!silent) showToast(`解析成功，共 ${data.totalRows} 行数据`)
  } catch (e) {
    if (!silent) {
      preview.value = null
      columnConfig.value = []
      errorMessage.value = e.message || '预览失败，请检查后端服务是否已启动'
    }
  } finally {
    if (!silent) previewLoading.value = false
  }
}

async function doExport() {
  if (!jsonText.value.trim()) {
    errorMessage.value = '请先输入 JSON 内容'
    return
  }
  if (columnConfig.value.length && !enabledColumns().length) {
    errorMessage.value = '至少保留一个导出列'
    return
  }
  exportLoading.value = true
  errorMessage.value = ''
  try {
    const res = await fetch('/api/convert/export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload())
    })
    if (!res.ok) {
      const data = await res.json().catch(() => null)
      throw new Error((data && data.message) || `导出失败（${res.status}）`)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${(fileName.value || 'data').replace(/\.xlsx$/i, '')}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    showToast('Excel 已开始下载')
  } catch (e) {
    errorMessage.value = e.message || '导出失败，请检查后端服务是否已启动'
  } finally {
    exportLoading.value = false
  }
}

function syncExcelColumns(available) {
  const old = excelColumnConfig.value
  const next = []
  old.forEach((c) => {
    if (available.indexOf(c.field) >= 0) next.push({ ...c })
  })
  available.forEach((field) => {
    if (!next.some((c) => c.field === field)) next.push({ field, enabled: true, alias: '' })
  })
  excelColumnConfig.value = next
}

function valueByPath(record, field) {
  const sep = keySeparator.value
  if (!excelExpandNested.value || field.indexOf(sep) < 0) return record[field]
  return field.split(sep).reduce((o, k) => (o == null ? undefined : o[k]), record)
}

function setByPath(target, path, value) {
  const parts = path.split(keySeparator.value)
  let cur = target
  for (let i = 0; i < parts.length - 1; i++) {
    const next = cur[parts[i]]
    if (typeof next !== 'object' || next === null || Array.isArray(next)) {
      cur[parts[i]] = {}
    }
    cur = cur[parts[i]]
  }
  cur[parts[parts.length - 1]] = value
}

function applyExcelColumns() {
  const selected = excelColumnConfig.value.filter((c) => c.enabled)
  const sep = keySeparator.value
  const outRecords = []
  const previewRows = []
  excelRecords.value.forEach((record) => {
    const row = {}
    const flat = []
    selected.forEach((col) => {
      const value = valueByPath(record, col.field)
      flat.push(value === undefined ? null : value)
      const alias = col.alias.trim()
      if (alias) {
        row[alias] = value
      } else if (excelExpandNested.value && col.field.indexOf(sep) >= 0) {
        setByPath(row, col.field, value)
      } else {
        row[col.field] = value
      }
    })
    outRecords.push(row)
    if (previewRows.length < EXCEL_PREVIEW_ROWS) previewRows.push(flat)
  })
  excelOutputText.value = JSON.stringify(outRecords, null, 2)
  excelOutputHeaders.value = selected.map((c) => c.alias.trim() || c.field)
  excelOutputRows.value = previewRows
}

function scheduleExcelApply() {
  if (!excelRecords.value.length) return
  if (excelConfigTimer) clearTimeout(excelConfigTimer)
  excelConfigTimer = setTimeout(applyExcelColumns, 300)
}

function toggleExcelColumn(field) {
  const item = excelColumnConfig.value.find((c) => c.field === field)
  if (item) item.enabled = !item.enabled
  scheduleExcelApply()
}

function renameExcelColumn(field, alias) {
  const item = excelColumnConfig.value.find((c) => c.field === field)
  if (item) item.alias = alias
  scheduleExcelApply()
}

function moveExcelColumn(index, delta) {
  const target = index + delta
  if (target < 0 || target >= excelColumnConfig.value.length) return
  const list = excelColumnConfig.value
  ;[list[index], list[target]] = [list[target], list[index]]
  scheduleExcelApply()
}

function pinExcelColumn(index) {
  const list = excelColumnConfig.value
  if (index <= 0 || index >= list.length) return
  const [moved] = list.splice(index, 1)
  list.unshift(moved)
  scheduleExcelApply()
}

function bottomExcelColumn(index) {
  const list = excelColumnConfig.value
  if (index < 0 || index >= list.length - 1) return
  const [moved] = list.splice(index, 1)
  list.push(moved)
  scheduleExcelApply()
}

function setAllExcelColumns(enabled) {
  excelColumnConfig.value = excelColumnConfig.value.map((c) => ({ ...c, enabled }))
  scheduleExcelApply()
}

function sortExcelColumns(from, to) {
  const list = excelColumnConfig.value
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return
  const [moved] = list.splice(from, 1)
  list.splice(to, 0, moved)
  scheduleExcelApply()
}

function resetExcelColumns() {
  const base = excelRawColumns.value.length ? excelRawColumns.value : excelColumnConfig.value.map((c) => c.field)
  excelColumnConfig.value = base.map((field) => ({ field, enabled: true, alias: '' }))
  scheduleExcelApply()
}

function setExcelFile(file) {
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    errorMessage.value = '仅支持 .xlsx / .xls 格式的文件'
    return
  }
  excelFile.value = file
  excelFileName.value = file.name
  errorMessage.value = ''
}

async function doExcelConvert() {
  if (!excelFile.value) {
    errorMessage.value = '请先选择 Excel 文件'
    return
  }
  excelLoading.value = true
  errorMessage.value = ''
  try {
    const form = new FormData()
    form.append('file', excelFile.value)
    form.append('hasHeader', excelHasHeader.value)
    form.append('expandNested', excelExpandNested.value)
    form.append('autoType', excelAutoType.value)
    form.append('keySeparator', keySeparator.value)
    form.append('aggregateMerged', excelAggregateMerged.value)
    const res = await fetch('/api/convert/excel-to-json', { method: 'POST', body: form })
    excelResult.value = await handleResponse(res)
    excelRecords.value = JSON.parse(excelResult.value.jsonText)
    excelRawColumns.value = excelResult.value.headers
    syncExcelColumns(excelResult.value.headers)
    applyExcelColumns()
    showToast(`转换成功，共 ${excelResult.value.totalRows} 行数据`)
  } catch (e) {
    excelResult.value = null
    errorMessage.value = e.message || '转换失败，请检查后端服务是否已启动'
  } finally {
    excelLoading.value = false
  }
}

async function copyJson() {
  try {
    await navigator.clipboard.writeText(excelOutputText.value)
    showToast('JSON 已复制到剪贴板')
  } catch (e) {
    errorMessage.value = '复制失败，请手动选择文本复制'
  }
}

function downloadJson() {
  const blob = new Blob([excelOutputText.value], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${(excelFileName.value || 'data').replace(/\.(xlsx|xls)$/i, '')}.json`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  showToast('JSON 文件已开始下载')
}
</script>

<template>
  <div>
    <header class="hero">
      <div class="hero__inner">
        <span class="hero__badge"><span class="dot" /> 服务运行中 · 前后端分离</span>
        <h1>JSON ⇄ Excel 转换工作台</h1>
        <p>JSON 一键导出为带样式表头的 Excel，Excel 一键转换为结构化 JSON。支持嵌套字段扁平化、自定义导出列与中英文表头映射。</p>
        <div class="hero__tags">
          <span class="tag">Spring Boot</span>
          <span class="tag">Apache POI</span>
          <span class="tag">Vue 3</span>
          <span class="tag">Vite</span>
        </div>
      </div>
    </header>

    <div class="mode-bar">
      <div class="segmented">
        <button type="button" :class="{ active: mode === 'json2excel' }" @click="mode = 'json2excel'">
          JSON → Excel
        </button>
        <button type="button" :class="{ active: mode === 'excel2json' }" @click="mode = 'excel2json'">
          Excel → JSON
        </button>
      </div>
    </div>

    <main v-if="mode === 'json2excel'" class="layout">
      <section class="panel">
        <div class="panel__head">
          <div class="panel__title">
            <span class="panel__icon">{ }</span>
            JSON 输入
          </div>
        </div>
        <div class="panel__body">
          <div v-if="errorMessage" class="alert alert--error">
            <span>⚠</span>
            <span>{{ errorMessage }}</span>
          </div>

          <JsonInputPanel
            v-model="jsonText"
            :char-count="charCount"
            :line-count="lineCount"
            @format="formatJson"
            @sample="loadSample"
            @clear="clearAll"
          />

          <div class="options">
            <div class="field">
              <label>文件名</label>
              <input v-model="fileName" type="text" placeholder="json-export" />
            </div>
            <div class="field">
              <label>Sheet 名称</label>
              <input v-model="sheetName" type="text" placeholder="Sheet1" />
            </div>
            <div class="field">
              <label>空值占位符</label>
              <input v-model="emptyValue" type="text" placeholder="留空则保持空白" />
            </div>
            <div class="field">
              <label>嵌套字段分隔符</label>
              <input v-model="keySeparator" type="text" placeholder="." />
            </div>
          </div>

          <ColumnConfig
            v-if="columnConfig.length"
            :config="columnConfig"
            @toggle="toggleColumn"
            @rename="renameColumn"
            @move="moveColumn"
            @pin="pinColumn"
            @bottom="bottomColumn"
            @sort="sortColumn"
            @reset="resetColumns"
            @enable-all="setAllColumns(true)"
            @disable-all="setAllColumns(false)"
          />

          <div class="actions">
            <button
              class="btn btn--primary btn--lg btn--block"
              type="button"
              :disabled="previewLoading"
              @click="doPreview(false)"
            >
              {{ previewLoading ? '解析中 …' : '预览数据' }}
            </button>
            <button
              class="btn btn--dark btn--lg btn--block"
              type="button"
              :disabled="exportLoading"
              @click="doExport"
            >
              {{ exportLoading ? '导出中 …' : '导出 Excel 文件' }}
            </button>
          </div>
        </div>
      </section>

      <section class="panel panel--sticky">
        <div class="panel__head">
          <div class="panel__title">
            <span class="panel__icon">▦</span>
            数据预览
          </div>
          <div class="panel__actions">
            <button
              v-if="preview"
              class="mini-btn"
              type="button"
              :title="floatingPreview ? '关闭浮动预览窗' : '弹出可拖动的浮动预览窗，配置列时随时对照'"
              @click="floatingPreview = !floatingPreview"
            >{{ floatingPreview ? '关闭浮动窗' : '浮动预览' }}</button>
            <div v-if="preview" class="stats">
              <span class="pill">{{ preview.totalRows }} 行</span>
              <span class="pill">{{ preview.headers.length }} 列</span>
              <span v-if="preview.truncated" class="pill pill--muted">仅显示前 {{ preview.previewRows }} 行</span>
            </div>
          </div>
        </div>
        <div class="panel__body">
          <PreviewTable
            :headers="preview ? preview.headers : []"
            :rows="preview ? preview.rows : []"
            :loading="previewLoading"
            :empty-value="emptyValue"
          />
        </div>
      </section>
    </main>

    <main v-else class="layout">
      <section class="panel">
        <div class="panel__head">
          <div class="panel__title">
            <span class="panel__icon">⬆</span>
            Excel 导入
          </div>
        </div>
        <div class="panel__body">
          <div v-if="errorMessage" class="alert alert--error">
            <span>⚠</span>
            <span>{{ errorMessage }}</span>
          </div>

          <ExcelImportPanel
            v-model:has-header="excelHasHeader"
            v-model:expand-nested="excelExpandNested"
            v-model:auto-type="excelAutoType"
            v-model:aggregate-merged="excelAggregateMerged"
            v-model:key-separator="keySeparator"
            :file-name="excelFileName"
            :loading="excelLoading"
            @select-file="setExcelFile"
            @convert="doExcelConvert"
          />

          <ColumnConfig
            v-if="excelColumnConfig.length"
            :config="excelColumnConfig"
            alias-label="字段"
            tip="勾选输出字段 · 填写字段名重命名 JSON 键 · 拖 ⠿ 或 ↑↓ 调整顺序 · ⤒ 置顶 ⤓ 置底 · 变更即时生效"
            @toggle="toggleExcelColumn"
            @rename="renameExcelColumn"
            @move="moveExcelColumn"
            @pin="pinExcelColumn"
            @bottom="bottomExcelColumn"
            @sort="sortExcelColumns"
            @reset="resetExcelColumns"
            @enable-all="setAllExcelColumns(true)"
            @disable-all="setAllExcelColumns(false)"
          />
        </div>
      </section>

      <section class="panel panel--sticky">
        <div class="panel__head">
          <div class="panel__title">
            <span class="panel__icon">▦</span>
            数据预览
          </div>
          <div class="panel__actions">
            <button
              v-if="excelOutputHeaders.length"
              class="mini-btn"
              type="button"
              :title="floatingPreview ? '关闭浮动预览窗' : '弹出可拖动的浮动预览窗，配置列时随时对照'"
              @click="floatingPreview = !floatingPreview"
            >{{ floatingPreview ? '关闭浮动窗' : '浮动预览' }}</button>
            <div v-if="excelResult" class="stats">
              <span class="pill">{{ excelResult.totalRows }} 行</span>
              <span class="pill">{{ excelOutputHeaders.length }} 列</span>
              <span v-if="excelResult.dataTruncated" class="pill pill--muted">已截断至 {{ excelResult.totalRows }} 行</span>
            </div>
          </div>
        </div>
        <div class="panel__body" :style="{ '--json-basis': splitRatio + '%' }">
          <PreviewTable
            :headers="excelOutputHeaders"
            :rows="excelOutputRows"
            :loading="excelLoading"
            empty-value=""
          />
          <div
            class="panel-splitter"
            title="拖动调整 JSON 预览区高度"
            @mousedown.prevent="startSplitDrag"
          />
          <JsonOutputPanel
            :text="excelOutputText"
            :sheet-name="excelResult ? excelResult.sheetName : ''"
            :truncated="excelResult ? excelResult.dataTruncated : false"
            @copy="copyJson"
            @download="downloadJson"
            @expand="jsonModal = true"
          />
        </div>
      </section>
    </main>

    <footer class="footer">JsonExcelConvert · 基于 Spring Boot + Vue 3 构建</footer>

    <Transition name="fade">
      <div v-if="toast" class="toast" :class="{ 'toast--error': toast.type === 'error' }">
        <span>{{ toast.type === 'error' ? '⚠' : '✓' }}</span>
        <span>{{ toast.message }}</span>
      </div>
    </Transition>

    <FloatingPreview
      v-if="mode === 'json2excel' && floatingPreview && preview"
      :headers="preview.headers"
      :rows="preview.rows"
      :total-rows="preview.totalRows"
      :empty-value="emptyValue"
      @close="floatingPreview = false"
    />

    <FloatingPreview
      v-if="mode === 'excel2json' && floatingPreview && excelOutputHeaders.length"
      :headers="excelOutputHeaders"
      :rows="excelOutputRows"
      :total-rows="excelResult ? excelResult.totalRows : 0"
      empty-value=""
      @close="floatingPreview = false"
    />

    <JsonModal
      v-if="jsonModal && mode === 'excel2json'"
      :text="excelOutputText"
      :sheet-name="excelResult ? excelResult.sheetName : ''"
      :truncated="excelResult ? excelResult.dataTruncated : false"
      @copy="copyJson"
      @download="downloadJson"
      @close="jsonModal = false"
    />
  </div>
</template>
