<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  config: { type: Array, default: () => [] },
  aliasLabel: { type: String, default: '表头' },
  tip: { type: String, default: '' }
})

const emit = defineEmits(['toggle', 'rename', 'move', 'pin', 'bottom', 'sort', 'reset', 'enable-all', 'disable-all'])

const expanded = ref(false)
const keyword = ref('')
const dragIndex = ref(-1)
const overIndex = ref(-1)
const listRef = ref(null)

const filtered = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return props.config
  return props.config.filter(
    (c) =>
      c.field.toLowerCase().indexOf(key) >= 0 ||
      (c.alias || '').toLowerCase().indexOf(key) >= 0
  )
})

const sortable = computed(() => !keyword.value.trim())

function onDragStart(index, event) {
  if (!sortable.value) return
  dragIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(index))
}

function onDragOver(index, event) {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
  overIndex.value = index
  autoScroll(event)
}

function autoScroll(event) {
  const el = listRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const edge = 46
  if (event.clientY < rect.top + edge) {
    el.scrollTop -= 14
  } else if (event.clientY > rect.bottom - edge) {
    el.scrollTop += 14
  }
}

function onDrop(index) {
  if (sortable.value && dragIndex.value >= 0 && dragIndex.value !== index) {
    emit('sort', dragIndex.value, index)
  }
  resetDragState()
}

function resetDragState() {
  dragIndex.value = -1
  overIndex.value = -1
}
</script>

<template>
  <div class="column-config">
    <div class="column-config__head">
      <div class="column-config__head-row">
        <span>列配置 · {{ config.filter((c) => c.enabled).length }}/{{ config.length }} 列</span>
        <div class="column-config__head-actions">
          <button class="link-btn" type="button" @click="expanded = !expanded">
            {{ expanded ? '收起列表' : '展开列表' }}
          </button>
          <button class="link-btn" type="button" @click="$emit('reset')">恢复默认</button>
        </div>
      </div>
      <div class="column-config__toolbar">
        <input
          v-model="keyword"
          class="column-search"
          type="text"
          placeholder="搜索字段名或表头别名…"
        />
        <button class="mini-btn" type="button" @click="$emit('enable-all')">全选</button>
        <button class="mini-btn" type="button" @click="$emit('disable-all')">全不选</button>
      </div>
    </div>

    <div ref="listRef" class="column-list" :class="{ 'column-list--expanded': expanded }">
      <div
        v-for="(item, index) in filtered"
        :key="item.field"
        class="column-item"
        :class="{
          'column-item--off': !item.enabled,
          'column-item--dragging': dragIndex === index,
          'column-item--drop-target': overIndex === index && dragIndex !== index
        }"
        @dragover="onDragOver(index, $event)"
        @drop.prevent="onDrop(index)"
      >
        <span
          class="column-item__grip"
          :class="{ 'column-item__grip--locked': !sortable }"
          :draggable="sortable"
          :title="sortable ? '拖动排序' : '搜索状态下请清空搜索框后排序'"
          @dragstart="onDragStart(index, $event)"
          @dragend="resetDragState"
        >⠿</span>
        <input
          class="column-item__check"
          type="checkbox"
          :checked="item.enabled"
          @change="emit('toggle', item.field)"
        />
        <span class="column-item__field" :title="item.field">{{ item.field }}</span>
        <input
          class="column-item__alias"
          type="text"
          :value="item.alias"
          :placeholder="aliasLabel + '：' + item.field"
          @input="emit('rename', item.field, $event.target.value)"
        />
        <button
          class="icon-btn"
          type="button"
          title="置顶"
          :disabled="!sortable || index === 0"
          @click="emit('pin', index)"
        >⤒</button>
        <button
          class="icon-btn"
          type="button"
          title="上移"
          :disabled="!sortable || index === 0"
          @click="emit('move', index, -1)"
        >↑</button>
        <button
          class="icon-btn"
          type="button"
          title="下移"
          :disabled="!sortable || index === filtered.length - 1"
          @click="emit('move', index, 1)"
        >↓</button>
        <button
          class="icon-btn"
          type="button"
          title="置底"
          :disabled="!sortable || index === filtered.length - 1"
          @click="emit('bottom', index)"
        >⤓</button>
      </div>

      <div v-if="!filtered.length" class="column-list__empty">没有匹配「{{ keyword }}」的字段</div>
    </div>

    <div class="column-config__tip">
      {{ tip || '勾选导出列 · 填写表头做中英文映射 · 拖 ⠿ 或 ↑↓ 调整顺序 · ⤒ 置顶 ⤓ 置底 · 列多时点「展开列表」' }}
    </div>
  </div>
</template>
