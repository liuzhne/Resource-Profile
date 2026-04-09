<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>教师列表</span>
          <el-button type="primary">新增教师</el-button>
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
        <el-form-item label="职称">
          <el-select v-model="searchForm.title" placeholder="请选择职称" clearable>
            <el-option label="教授" value="professor" />
            <el-option label="副教授" value="associate" />
            <el-option label="讲师" value="lecturer" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="teacherList" stripe v-loading="loading">
        <el-table-column type="index" width="50" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="dept" label="学院" min-width="150" />
        <el-table-column prop="title" label="职称" min-width="100" />
        <el-table-column prop="phone" label="电话" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="courses" label="授课数量" width="100" align="center" />
        <el-table-column prop="projects" label="科研项目" width="100" align="center" />
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
const total = ref(128)

const searchForm = reactive({
  name: '',
  dept: '',
  title: ''
})

const teacherList = ref([
  {
    id: 1,
    name: '张教授',
    dept: '计算机学院',
    title: '教授',
    phone: '13800138001',
    email: 'zhang@edu.edu.cn',
    courses: 3,
    projects: 5
  },
  {
    id: 2,
    name: '李老师',
    dept: '数学学院',
    title: '副教授',
    phone: '13800138002',
    email: 'li@edu.edu.cn',
    courses: 2,
    projects: 3
  },
  {
    id: 3,
    name: '王老师',
    dept: '物理学院',
    title: '讲师',
    phone: '13800138003',
    email: 'wang@edu.edu.cn',
    courses: 4,
    projects: 1
  }
])

const viewDetail = (row: any) => {
  router.push(`/teacher/detail/${row.id}`)
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
