<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <span>待填写问卷</span>
      </template>

      <el-row :gutter="16" v-loading="loading">
        <el-col v-for="item in questionnaires" :key="item.id" :xs="24" :sm="12" :lg="8" style="margin-bottom: 16px;">
          <el-card shadow="hover" class="q-card">
            <h3 style="margin: 0 0 8px;">{{ item.title }}</h3>
            <p style="color: #909399; font-size: 13px; margin: 0 0 12px;">{{ item.description }}</p>
            <div class="q-meta">
              <el-tag size="small">{{ item.type }}</el-tag>
              <span class="q-time">{{ item.startTime }} ~ {{ item.endTime }}</span>
            </div>
            <div class="q-footer">
              <el-tag size="small" type="success">{{ item.questions }} 题</el-tag>
              <el-button type="primary" size="small" @click="goFill(item.id)">填写问卷</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-if="!loading && questionnaires.length === 0" description="暂无待填写问卷" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAvailableQuestionnaires } from '@/api/mental'

const router = useRouter()
const loading = ref(false)
const questionnaires = ref<any[]>([])

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getAvailableQuestionnaires()
    questionnaires.value = res.data || []
  } catch (e) {
    console.error('获取问卷列表失败', e)
  } finally {
    loading.value = false
  }
}

const goFill = (id: number) => {
  router.push(`/mental/fill/${id}`)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped lang="scss">
.q-card {
  height: 100%;

  .q-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;

    .q-time {
      font-size: 12px;
      color: #909399;
    }
  }

  .q-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}
</style>
