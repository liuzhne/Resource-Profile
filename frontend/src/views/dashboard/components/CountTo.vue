<template>
  <span>{{ displayValue }}</span>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

interface Props {
  start?: number
  end: number
  duration?: number
  decimals?: number
}

const props = withDefaults(defineProps<Props>(), {
  start: 0,
  duration: 2000,
  decimals: 0
})

const displayValue = ref(props.start)
let rafId: number | null = null

onMounted(() => {
  const startTime = performance.now()
  const diff = props.end - props.start

  const animate = (currentTime: number) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / props.duration, 1)

    // 使用 easeOutQuart 缓动函数
    const easeProgress = 1 - Math.pow(1 - progress, 4)
    const currentValue = props.start + diff * easeProgress

    displayValue.value = Number(currentValue.toFixed(props.decimals))

    if (progress < 1) {
      rafId = requestAnimationFrame(animate)
    }
  }

  rafId = requestAnimationFrame(animate)
})

onUnmounted(() => {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
  }
})
</script>
