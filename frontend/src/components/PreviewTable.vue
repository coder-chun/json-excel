<script setup>
const props = defineProps({
  headers: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyValue: { type: String, default: '' }
})

function cellText(value) {
  if (value === null || value === undefined || value === '') {
    return props.emptyValue
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}
</script>

<template>
  <div class="preview-table">
    <div v-if="loading" class="loading-state">
      <div class="spinner" />
      <div>正在解析 JSON …</div>
    </div>

    <div v-else-if="!headers.length" class="empty-state">
      <div class="icon"> </div>
      <h3>暂无预览数据</h3>
      <p>在左侧粘贴 JSON，点击「预览数据」即可在此查看表格效果</p>
    </div>

    <div v-else class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th class="col-index">#</th>
            <th v-for="header in headers" :key="header" :title="header">{{ header }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
            <td class="col-index">{{ rowIndex + 1 }}</td>
            <td
              v-for="(header, colIndex) in headers"
              :key="header"
              :title="cellText(row[colIndex])"
            >{{ cellText(row[colIndex]) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
