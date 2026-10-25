<script setup>
defineProps({
  text: { type: String, default: '' },
  sheetName: { type: String, default: '' },
  truncated: { type: Boolean, default: false }
})

defineEmits(['copy', 'download', 'expand'])
</script>

<template>
  <div class="json-output">
    <div class="json-output__head">
      <div class="panel__title">
        <span class="panel__icon">{ }</span>
        JSON 输出
      </div>
      <div class="stats">
        <span v-if="sheetName" class="pill pill--muted">Sheet：{{ sheetName }}</span>
        <button class="btn btn--ghost" type="button" :disabled="!text" @click="$emit('copy')">复制</button>
        <button class="btn btn--ghost" type="button" :disabled="!text" @click="$emit('download')">下载 .json</button>
        <button class="btn btn--ghost" type="button" :disabled="!text" title="弹出大窗口查看 JSON" @click="$emit('expand')">⤢ 放大</button>
      </div>
    </div>

    <div v-if="truncated" class="alert alert--info" style="margin: 12px 0 0">
      <span>ℹ</span>
      <span>文件行数超出上限，已截断转换，请拆分后重试</span>
    </div>

    <div class="editor-wrap" style="margin-top: 12px">
      <textarea class="editor editor--output" :value="text" readonly spellcheck="false" placeholder="转换后的 JSON 将显示在此处" />
      <div class="editor-meta">
        <span>{{ text ? text.split('\n').length : 0 }} 行 · {{ text.length }} 字符</span>
        <span>可直接复制或下载为 .json 文件</span>
      </div>
    </div>
  </div>
</template>
