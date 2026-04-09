<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>心理分析报告</span>
          <el-button type="primary">导出报告</el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :xs="24" :lg="8">
          <h4>各学院心理状况分布</h4>
          <div ref="deptChartRef" style="height: 300px; margin-top: 16px"></div>
        </el-col>
        <el-col :xs="24" :lg="8">
          <h4>各年级心理状况对比</h4>
          <div ref="gradeChartRef" style="height: 300px; margin-top: 16px"></div>
        </el-col>
        <el-col :xs="24" :lg="8">
          <h4>性别差异分析</h4>
          <div ref="genderChartRef" style="height: 300px; margin-top: 16px"></div>
        </el-col>
      </el-row>

      <el-divider />

      <h4>重点人群分析</h4>
      <el-table :data="focusGroups" stripe style="margin-top: 16px">
        <el-table-column type="index" width="50" />
        <el-table-column prop="group" label="人群类型" />
        <el-table-column prop="count" label="人数" width="100" />
        <el-table-column prop="percentage" label="占比" width="100">
          <template #default="{ row }">
            {{ row.percentage }}%
          </template>
        </el-table-column>
        <el-table-column prop="risk" label="风险等级" width="120">
          <template #default="{ row }">
            <el-tag :type="row.risk === '高' ? 'danger' : row.risk === '中' ? 'warning' : 'success'">
              {{ row.risk }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议措施" min-width="300" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

const deptChartRef = ref<HTMLElement>()
const gradeChartRef = ref<HTMLElement>()
const genderChartRef = ref<HTMLElement>()

let deptChart: echarts.ECharts | null = null
let gradeChart: echarts.ECharts | null = null
let genderChart: echarts.ECharts | null = null

const focusGroups = ref([
  {
    group: '学业困难学生',
    count: 156,
    percentage: 6.4,
    risk: '中',
    suggestion: '提供学业辅导，建立学习小组，定期跟踪学业进展'
  },
  {
    group: '家庭经济困难学生',
    count: 89,
    percentage: 3.6,
    risk: '中',
    suggestion: '落实资助政策，提供勤工俭学机会，关注心理健康'
  },
  {
    group: '人际关系困扰学生',
    count: 45,
    percentage: 1.8,
    risk: '高',
    suggestion: '心理咨询介入，开展团体辅导，建立支持网络'
  },
  {
    group: '近期遭遇重大事件学生',
    count: 23,
    percentage: 0.9,
    risk: '高',
    suggestion: '一对一心理干预，建立危机干预小组，定期评估'
  }
])

onMounted(() => {
  if (deptChartRef.value) {
    deptChart = echarts.init(deptChartRef.value)
    deptChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', right: '5%', top: 'center' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { value: 85, name: '良好', itemStyle: { color: '#52c41a' } },
          { value: 12, name: '关注', itemStyle: { color: '#faad14' } },
          { value: 3, name: '干预', itemStyle: { color: '#f5222d' } }
        ]
      }]
    })
  }

  if (gradeChartRef.value) {
    gradeChart = echarts.init(gradeChartRef.value)
    gradeChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['大一', '大二', '大三', '大四', '研一', '研二', '研三'] },
      yAxis: { type: 'value' },
      series: [{
        type: 'bar',
        data: [
          { value: 85, itemStyle: { color: '#52c41a' } },
          { value: 82, itemStyle: { color: '#52c41a' } },
          { value: 78, itemStyle: { color: '#faad14' } },
          { value: 75, itemStyle: { color: '#faad14' } },
          { value: 83, itemStyle: { color: '#52c41a' } },
          { value: 80, itemStyle: { color: '#52c41a' } },
          { value: 88, itemStyle: { color: '#52c41a' } }
        ]
      }]
    })
  }

  if (genderChartRef.value) {
    genderChart = echarts.init(genderChartRef.value)
    genderChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['男生', '女生'] },
      xAxis: { type: 'category', data: ['良好', '关注', '干预'] },
      yAxis: { type: 'value' },
      series: [
        {
          name: '男生',
          type: 'bar',
          data: [1200, 150, 35],
          itemStyle: { color: '#1890ff' }
        },
        {
          name: '女生',
          type: 'bar',
          data: [880, 145, 46],
          itemStyle: { color: '#eb2f96' }
        }
      ]
    })
  }
})

onUnmounted(() => {
  deptChart?.dispose()
  gradeChart?.dispose()
  genderChart?.dispose()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

h4 {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.85);
}
</style>
