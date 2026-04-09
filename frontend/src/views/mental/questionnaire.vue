<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>问卷管理</span>
          <el-button type="primary"><Plus /> 新建问卷</el-button>
        </div>
      </template>

      <el-table :data="questionnaires" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="title" label="问卷标题" min-width="200" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="questions" label="题目数" width="80" />
        <el-table-column prop="participants" label="参与人数" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '进行中' ? 'success' : row.status === '已结束' ? 'info' : ''">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="150" />
        <el-table-column prop="endTime" label="结束时间" width="150" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary">查看</el-button>
            <el-button link type="primary">编辑</el-button>
            <el-button link type="primary" v-if="row.status === '未开始'">发布</el-button>
            <el-button link type="danger" v-if="row.status === '进行中'">结束</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const questionnaires = ref([
  {
    id: 1,
    title: '2026年春季心理健康普查',
    type: '心理普查',
    questions: 90,
    participants: 2456,
    status: '进行中',
    startTime: '2026-03-01',
    endTime: '2026-03-31'
  },
  {
    id: 2,
    title: '学业压力调查问卷',
    type: '专题调查',
    questions: 30,
    participants: 1890,
    status: '进行中',
    startTime: '2026-04-01',
    endTime: '2026-04-15'
  },
  {
    id: 3,
    title: '2025年秋季心理健康普查',
    type: '心理普查',
    questions: 90,
    participants: 2389,
    status: '已结束',
    startTime: '2025-09-01',
    endTime: '2025-09-30'
  }
])
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
