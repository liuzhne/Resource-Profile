<template>
  <el-card class="statistic-card" shadow="hover">
    <div class="card-content">
      <div class="icon-wrapper" :style="{ backgroundColor: color + '15' }">
        <el-icon :size="32" :style="{ color: color }">
          <component :is="icon" />
        </el-icon>
      </div>
      <div class="statistic-info">
        <div class="title">{{ title }}</div>
        <div class="value" :style="{ color: color }">
          <CountTo :end="value" :duration="2000" />
          <span class="suffix">{{ suffix }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import CountTo from './CountTo.vue'

interface Props {
  title: string
  value: number
  icon: string
  color: string
  suffix?: string
}

defineProps<Props>()
</script>

<style scoped lang="scss">
.statistic-card {
  .card-content {
    display: flex;
    align-items: center;

    .icon-wrapper {
      width: 60px;
      height: 60px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 16px;
    }

    .statistic-info {
      flex: 1;

      .title {
        font-size: 14px;
        color: rgba(0, 0, 0, 0.45);
        margin-bottom: 4px;
      }

      .value {
        font-size: 28px;
        font-weight: 600;
        line-height: 1;

        .suffix {
          font-size: 14px;
          margin-left: 4px;
        }
      }
    }
  }
}
</style>
