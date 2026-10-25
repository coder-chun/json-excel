<script setup>
import { onBeforeUnmount, onMounted } from 'vue'

defineProps({
  text: { type: String, default: '' },
  sheetName: { type: String, default: '' },
  truncated: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'copy', 'download'])

function onKeydown(e) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="json-modal" @click.self="emit('close')">
    <div class="json-modal__dialog">
      <div class="json-modal__head">
        <div class="panel__title">
          <span class="panel__icon">{ }</span>
          JSON 输出
          <span v-if="sheetName" class="pill pill--muted">Sheet：{{ sheetName }}</span>
          <span v-if="truncated" class="pill pill--muted">已截断</span>
        </div>
        <div class="stats">
          <button class="btn btn--ghost" type="button" :disabled="!text" @click="emit('copy')">复制</button>
          <button class="btn btn--ghost" type="button" :disabled="!text" @click="emit('download')">下载 .json</button>
          <button class="json-modal__close" type="button" title="关闭（Esc）" @click="emit('close')">×</button>
        </div>
      </div>
      <div class="json-modal__body">
        <textarea class="editor editor--output json-modal__editor" :value="text" readonly spellcheck="false" placeholder="转换后的 JSON 将显示在此处" />
      </div>
      <div class="json-modal__meta">
        <span>{{ text ? text.split('\n').length : 0 }} 行 · {{ text.length }} 字符</span>
        <span>Esc 或点击遮罩关闭</span>
      </div>
    </div>
  </div>
</template>
