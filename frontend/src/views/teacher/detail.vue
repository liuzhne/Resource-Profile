<template>
  <div class="page-container">
    <el-page-header @back="goBack" title="教师详情" />

    <el-row :gutter="20" class="detail-content">
      <el-col :xs="24" :lg="8">
        <el-card>
          <div class="profile-header">
            <el-avatar :size="100" :src="teacherInfo.avatar" />
            <h3>{{ teacherInfo.name }}</h3>
            <p class="subtitle">{{ teacherInfo.dept }} · {{ teacherInfo.title }}</p>
            <el-tag :type="teacherInfo.status === '在职' ? 'success' : 'info'">
              {{ teacherInfo.status }}
            </el-tag>
          </div>

          <el-divider />

          <div class="info-list">
            <div class="info-item">
              <span class="label">工号：</span>
              <span class="value">{{ teacherInfo.employeeId }}</span>
            </div>
            <div class="info-item">
              <span class="label">入职时间：</span>
              <span class="value">{{ teacherInfo.joinDate }}</span>
            </div>
            <div class="info-item">
              <span class="label">联系电话：</span>
              <span class="value">{{ teacherInfo.phone }}</span>
            </div>
            <div class="info-item">
              <span class="label">电子邮箱：</span>
              <span class="value">{{ teacherInfo.email }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本信息" name="basic">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="姓名">{{ teacherInfo.name }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ teacherInfo.gender }}</el-descriptions-item>
                <el-descriptions-item label="出生日期">{{ teacherInfo.birthDate }}</el-descriptions-item>
                <el-descriptions-item label="政治面貌">{{ teacherInfo.political }}</el-descriptions-item>
                <el-descriptions-item label="学历" :span="2">{{ teacherInfo.education }}</el-descriptions-item>
                <el-descriptions-item label="毕业院校" :span="2">{{ teacherInfo.school }}</el-descriptions-item>
                <el-descriptions-item label="专业方向" :span="2">{{ teacherInfo.major }}</el-descriptions-item>
                <el-descriptions-item label="研究方向" :span="2">{{ teacherInfo.researchArea }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="教学成果" name="teaching">
              <el-timeline>
                <el-timeline-item
                  v-for="(item, index) in teachingAchievements"
                  :key="index"
                  :timestamp="item.year"
                  type="primary"
                >
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.description }}</p>
                </el-timeline-item>
              </el-timeline>
            </el-tab-pane>

            <el-tab-pane label="科研项目" name="research">
              <el-table :data="researchProjects" stripe>
                <el-table-column prop="name" label="项目名称" min-width="200" />
                <el-table-column prop="level" label="级别" width="120" />
                <el-table-column prop="role" label="角色" width="100" />
                <el-table-column prop="amount" label="经费" width="120">
                  <template #default="{ row }">
                    ¥{{ row.amount }}万
                  </template>
                </el-table-column>
                <el-table-column prop="period" label="周期" width="180" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="教学评价" name="evaluation">
              <div class="evaluation-stats">
                <el-row :gutter="20">
                  <el-col :span="8">
                    <div class="stat-item">
                      <div class="stat-value">{{ teacherInfo.evaluationScore }}</div>
                      <div class="stat-label">综合评分</div>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="stat-item">
                      <div class="stat-value">{{ teacherInfo.evaluationCount }}</div>
                      <div class="stat-label">评价次数</div>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="stat-item">
                      <div class="stat-value">{{ teacherInfo.satisfaction }}%</div>
                      <div class="stat-label">满意度</div>
                    </div>
                  </el-col>
                </el-row>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const activeTab = ref('basic')

const teacherInfo = ref({
  id: 1,
  name: '张教授',
  avatar: '',
  dept: '计算机学院',
  title: '教授',
  status: '在职',
  employeeId: 'T2024001',
  joinDate: '2015-09-01',
  phone: '13800138001',
  email: 'zhang@edu.edu.cn',
  gender: '男',
  birthDate: '1980-03-15',
  political: '中共党员',
  education: '博士',
  school: '清华大学',
  major: '计算机科学与技术',
  researchArea: '人工智能、机器学习',
  evaluationScore: 4.8,
  evaluationCount: 256,
  satisfaction: 95
})

const teachingAchievements = ref([
  {
    year: '2025',
    title: '《数据结构》精品课程建设',
    description: '主持校级精品课程建设，获优秀评价'
  },
  {
    year: '2024',
    title: '教学成果奖二等奖',
    description: '《基于项目的计算机专业实践教学改革》'
  },
  {
    year: '2023',
    title: '优秀教学奖',
    description: '年度教学质量评估优秀'
  }
])

const researchProjects = ref([
  {
    name: '基于深度学习的图像识别研究',
    level: '国家自然科学基金',
    role: '主持',
    amount: 50,
    period: '2024-2027'
  },
  {
    name: '智能教育系统关键技术研究',
    level: '省部级项目',
    role: '主持',
    amount: 20,
    period: '2023-2025'
  }
])

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="scss">
.detail-content {
  margin-top: 20px;
}

.profile-header {
  text-align: center;
  padding: 20px 0;

  h3 {
    margin: 16px 0 8px;
    font-size: 20px;
  }

  .subtitle {
    color: rgba(0, 0, 0, 0.45);
    margin-bottom: 12px;
  }
}

.info-list {
  .info-item {
    display: flex;
    padding: 12px 0;
    border-bottom: 1px solid #f0f0f0;

    &:last-child {
      border-bottom: none;
    }

    .label {
      width: 80px;
      color: rgba(0, 0, 0, 0.45);
    }

    .value {
      flex: 1;
      color: rgba(0, 0, 0, 0.85);
    }
  }
}

.evaluation-stats {
  padding: 20px;

  .stat-item {
    text-align: center;
    padding: 20px;
    background: #f6ffed;
    border-radius: 8px;

    .stat-value {
      font-size: 32px;
      font-weight: 600;
      color: #52c41a;
    }

    .stat-label {
      margin-top: 8px;
      color: rgba(0, 0, 0, 0.45);
    }
  }
}
</style>
