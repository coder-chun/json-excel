<script setup>
import { onBeforeUnmount, ref } from 'vue'
import PreviewTable from './PreviewTable.vue'

defineProps({
  headers: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  totalRows: { type: Number, default: 0 },
  emptyValue: { type: String, default: '' }
})

const emit = defineEmits(['close'])

const elRef = ref(null)
const pos = ref({ x: 0, y: 0 })
let dragStart = null

function placeDefault() {
  const width = Math.min(620, window.innerWidth * 0.92)
  pos.value = {
    x: Math.max(16, window.innerWidth - width - 28),
    y: Math.max(16, window.innerHeight - 430)
  }
}

placeDefault()

function onBarDown(event) {
  dragStart = { px: event.clientX, py: event.clientY, x: pos.value.x, y: pos.value.y }
  window.addEventListener('mousemove', onBarMove)
  window.addEventListener('mouseup', onBarUp)
  event.preventDefault()
}

function onBarMove(event) {
  if (!dragStart) return
  const width = elRef.value ? elRef.value.offsetWidth : 620
  const height = elRef.value ? elRef.value.offsetHeight : 400
  const margin = 8
  pos.value = {
    x: Math.min(
      Math.max(margin, dragStart.x + event.clientX - dragStart.px),
      window.innerWidth - width - margin
    ),
    y: Math.min(
      Math.max(margin, dragStart.y + event.clientY - dragStart.py),
      window.innerHeight - height - margin
    )
  }
}

function onBarUp() {
  dragStart = null
  window.removeEventListener('mousemove', onBarMove)
  window.removeEventListener('mouseup', onBarUp)
}

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onBarMove)
  window.removeEventListener('mouseup', onBarUp)
})
</script>

<template>
  <div ref="elRef" class="floating-preview" :style="{ left: pos.x + 'px', top: pos.y + 'px' }">
    <div class="floating-preview__bar" @mousedown="onBarDown">
      <span class="floating-preview__title">  浮动预览 · 拖动标题栏移动</span>
      <button
        class="floating-preview__close"
        type="button"
        title="关闭"
        @click="emit('close')"
      >×</button>
    </div>
    <div class="floating-preview__body">
      <PreviewTable :headers="headers" :rows="rows" :loading="false" :empty-value="emptyValue" />
    </div>
    <div class="floating-preview__meta">
      共 {{ totalRows }} 行 · 当前显示 {{ rows.length }} 行 · {{ headers.length }} 列
    </div>
  </div>
</template>
