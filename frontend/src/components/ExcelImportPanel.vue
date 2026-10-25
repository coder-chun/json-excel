<script setup>
import { ref } from 'vue'

const hasHeader = defineModel('hasHeader', { type: Boolean, default: true })
const expandNested = defineModel('expandNested', { type: Boolean, default: true })
const autoType = defineModel('autoType', { type: Boolean, default: true })
const aggregateMerged = defineModel('aggregateMerged', { type: Boolean, default: false })
const keySeparator = defineModel('keySeparator', { type: String, default: '.' })

const props = defineProps({
  fileName: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['select-file', 'convert'])

const dragOver = ref(false)
const fileInput = ref(null)

function pickFile() {
  fileInput.value && fileInput.value.click()
}

function onFileInput(event) {
  const file = event.target.files && event.target.files[0]
  if (file) emit('select-file', file)
  event.target.value = ''
}

function onDrop(event) {
  dragOver.value = false
  const file = event.dataTransfer.files && event.dataTransfer.files[0]
  if (file) emit('select-file', file)
}
</script>

<template>
  <div>
    <div
      class="dropzone"
      :class="{ 'dropzone--active': dragOver, 'dropzone--filled': !!props.fileName }"
      @click="pickFile"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="onDrop"
    >
      <input ref="fileInput" type="file" accept=".xlsx,.xls" hidden @change="onFileInput" />
      <div class="dropzone__icon">⬆</div>
      <div v-if="props.fileName" class="dropzone__name">{{ props.fileName }}</div>
      <div v-else class="dropzone__hint">点击选择或拖拽 .xlsx / .xls 文件到此处</div>
      <div class="dropzone__sub">支持 .xlsx 与 .xls，最大 20MB</div>
    </div>

    <div class="options">
      <label class="check-field">
        <input v-model="hasHeader" type="checkbox" />
        <span>首行为表头</span>
      </label>
      <label class="check-field">
        <input v-model="expandNested" type="checkbox" />
        <span>表头还原嵌套字段</span>
      </label>
      <label class="check-field">
        <input v-model="autoType" type="checkbox" />
        <span>自动识别数值 / 布尔</span>
      </label>
      <label class="check-field" title="合并单元格 / 空白延续的列作为对象属性，明细列（如设备ID）收为数组，每组输出一个对象">
        <input v-model="aggregateMerged" type="checkbox" />
        <span>合并单元格聚合</span>
      </label>
      <div class="field">
        <label>嵌套字段分隔符</label>
        <input v-model="keySeparator" type="text" placeholder="." />
      </div>
    </div>

    <div class="actions">
      <button
        class="btn btn--primary btn--lg btn--block"
        type="button"
        :disabled="props.loading"
        @click="emit('convert')"
      >
        {{ props.loading ? '转换中 …' : '转换并预览' }}
      </button>
    </div>
  </div>
</template>
