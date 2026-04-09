<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>学生列表</span>
          <el-button type="primary">新增学生</el-button>
        </div>
      </template>

      <!-- 搜索区域 -->
      <el-form :model="searchForm" inline>
        <el-form-item label="姓名">
          <el-input v-model="searchForm.name" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="学院">
          <el-select v-model="searchForm.dept" placeholder="请选择学院" clearable>
            <el-option label="计算机学院" value="cs" />
            <el-option label="数学学院" value="math" />
            <el-option label="物理学院" value="physics" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="searchForm.grade" placeholder="请选择年级" clearable>
            <el-option label="2025级" value="2025" />
            <el-option label="2024级" value="2024" />
            <el-option label="2023级" value="2023" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="studentList" stripe v-loading="loading">
        <el-table-column type="index" width="50" />
        <el-table-column prop="studentId" label="学号" min-width="120" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="dept" label="学院" min-width="150" />
        <el-table-column prop="major" label="专业" min-width="150" />
        <el-table-column prop="grade" label="年级" width="100" />
        <el-table-column prop="gpa" label="GPA" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.gpa >= 3.5 ? 'success' : row.gpa >= 2.5 ? 'warning' : 'danger'">
              {{ row.gpa }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mentalStatus" label="心理状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.mentalStatus === '良好' ? 'success' : 'warning'">
              {{ row.mentalStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">查看</el-button>
            <el-button link type="primary">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        class="pagination"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(2456)

const searchForm = reactive({
  name: '',
  dept: '',
  grade: ''
})

const studentList = ref([
  {
    id: 1,
    studentId: '2025010001',
    name: '张三',
    dept: '计算机学院',
    major: '软件工程',
    grade: '2025级',
    gpa: 3.8,
    mentalStatus: '良好'
  },
  {
    id: 2,
    studentId: '2024010025',
    name: '李四',
    dept: '数学学院',
    major: '应用数学',
    grade: '2024级',
    gpa: 3.2,
    mentalStatus: '良好'
  },
  {
    id: 3,
    studentId: '2023010156',
    name: '王五',
    dept: '物理学院',
    major: '物理学',
    grade: '2023级',
    gpa: 2.1,
    mentalStatus: '关注'
  }
])

const viewDetail = (row: any) => {
  router.push(`/student/detail/${row.id}`)
}
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
