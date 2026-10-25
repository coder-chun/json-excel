<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  charCount: { type: Number, default: 0 },
  lineCount: { type: Number, default: 0 }
})

defineEmits(['update:modelValue', 'format', 'sample', 'clear'])
</script>

<template>
  <div>
    <div class="toolbar">
      <button class="btn btn--ghost" type="button" @click="$emit('format')">格式化</button>
      <button class="btn btn--ghost" type="button" @click="$emit('sample')">载入示例</button>
      <button class="btn btn--ghost" type="button" @click="$emit('clear')">清空</button>
    </div>

    <div class="editor-wrap">
      <textarea
        class="editor"
        :value="modelValue"
        spellcheck="false"
        placeholder='在此粘贴 JSON，例如：&#10;[&#10;  { "id": 1, "name": "张三", "address": { "city": "上海" } }&#10;]'
        @input="$emit('update:modelValue', $event.target.value)"
      />
      <div class="editor-meta">
        <span>{{ lineCount }} 行 · {{ charCount }} 字符</span>
        <span>支持数组 / 对象 / 含单个数组的对象</span>
      </div>
    </div>
  </div>
</template>
