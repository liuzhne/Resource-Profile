<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>问卷管理</span>
          <div>
            <el-button type="success" @click="handleUpload"><Upload /> 上传问卷</el-button>
            <el-button type="primary" @click="handleAdd"><Plus /> 新建问卷</el-button>
          </div>
        </div>
      </template>

      <el-table :data="questionnaires" stripe v-loading="loading">
        <el-table-column type="index" width="50" />
        <el-table-column prop="title" label="问卷标题" min-width="200" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="questions" label="题目数" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="120" />
        <el-table-column prop="endTime" label="结束时间" width="120" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        style="margin-top: 16px; justify-content: flex-end"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑问卷' : '新建问卷'" width="500px">
      <el-form :model="formData" label-width="80px">
        <el-form-item label="问卷标题">
          <el-input v-model="formData.title" />
        </el-form-item>
        <el-form-item label="问卷类型">
          <el-select v-model="formData.type" placeholder="请选择类型">
            <el-option label="普查" value="普查" />
            <el-option label="焦虑" value="焦虑" />
            <el-option label="抑郁" value="抑郁" />
            <el-option label="专题调查" value="专题调查" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="题目数量">
          <el-input-number v-model="formData.questions" :min="1" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="formData.startTime" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="formData.endTime" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.status">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 上传问卷对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传问卷模板" width="500px">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".json"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        drag
      >
        <el-icon :size="40"><UploadFilled /></el-icon>
        <div>将JSON文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持.json格式的问卷模板文件</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUploadSubmit" :loading="uploading">确认上传</el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" title="问卷详情" width="600px">
      <el-descriptions :column="1" border v-if="viewData">
        <el-descriptions-item label="问卷标题">{{ viewData.title }}</el-descriptions-item>
        <el-descriptions-item label="问卷类型">{{ viewData.type }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ viewData.description }}</el-descriptions-item>
        <el-descriptions-item label="题目数量">{{ viewData.questions }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ viewData.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ viewData.endTime }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(viewData.status) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="viewQuestions.length > 0" style="margin-top: 20px;">
        <h4 style="margin-bottom: 12px;">题目列表</h4>
        <div v-for="q in viewQuestions" :key="q.id" class="question-preview">
          <div class="question-title">{{ q.sortOrder }}. {{ q.content }}</div>
          <el-tag size="small" type="info" style="margin-right: 8px;">{{ questionTypeLabel(q.questionType) }}</el-tag>
          <el-tag v-if="q.required === 1" size="small" type="danger">必答</el-tag>
          <div v-if="q.options" class="question-options">
            <div v-for="opt in parseOptions(q.options)" :key="opt.value" class="option-item">
              {{ opt.label }}
            </div>
          </div>
          <div v-if="q.questionType === 'scale'" class="scale-info">
            <el-tag size="small">{{ q.scaleMin }} ~ {{ q.scaleMax }}</el-tag>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  getQuestionnaireList,
  getQuestionnaireDetail,
  createQuestionnaire,
  updateQuestionnaire,
  deleteQuestionnaire,
  uploadQuestionnaireTemplate,
  getQuestionnaireQuestions
} from '@/api/mental'

const loading = ref(false)
const questionnaires = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formData = reactive({
  title: '',
  type: '',
  description: '',
  questions: 20,
  startTime: '',
  endTime: '',
  status: 0
})

const viewDialogVisible = ref(false)
const viewData = ref<any>(null)
const viewQuestions = ref<any[]>([])

const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadFile = ref<File | null>(null)

const statusLabel = (status: number) => {
  const map: Record<number, string> = { 0: '未开始', 1: '进行中', 2: '已结束' }
  return map[status] ?? '未知'
}

const statusTagType = (status: number) => {
  const map: Record<number, string> = { 0: 'info', 1: 'success', 2: 'warning' }
  return map[status] ?? ''
}

const questionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    single_choice: '单选',
    multiple_choice: '多选',
    text: '文本',
    scale: '量表'
  }
  return map[type] ?? type
}

const parseOptions = (jsonStr: string) => {
  try {
    return jsonStr ? JSON.parse(jsonStr) : []
  } catch {
    return []
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getQuestionnaireList({ page: currentPage.value, size: pageSize.value })
    const data = res.data
    questionnaires.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    console.error('获取问卷列表失败', e)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

const resetForm = () => {
  formData.title = ''
  formData.type = ''
  formData.description = ''
  formData.questions = 20
  formData.startTime = ''
  formData.endTime = ''
  formData.status = 0
}

const handleAdd = () => {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: any) => {
  isEdit.value = true
  editId.value = row.id
  try {
    const res = await getQuestionnaireDetail(row.id)
    const d = res.data
    formData.title = d.title
    formData.type = d.type
    formData.description = d.description
    formData.questions = d.questions
    formData.startTime = d.startTime
    formData.endTime = d.endTime
    formData.status = d.status
    dialogVisible.value = true
  } catch (e) {
    console.error('获取问卷详情失败', e)
  }
}

const handleView = async (row: any) => {
  try {
    const res = await getQuestionnaireDetail(row.id)
    viewData.value = res.data
    viewQuestions.value = []
    viewDialogVisible.value = true

    const qRes = await getQuestionnaireQuestions(row.id)
    viewQuestions.value = qRes.data || []
  } catch (e) {
    console.error('获取问卷详情失败', e)
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确定删除该问卷？', '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteQuestionnaire(row.id)
      ElMessage.success('删除成功')
      fetchData()
    } catch (e) {
      console.error('删除问卷失败', e)
    }
  }).catch(() => {})
}

const handleSubmit = async () => {
  try {
    if (isEdit.value && editId.value) {
      await updateQuestionnaire(editId.value, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await createQuestionnaire({ ...formData })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    console.error('提交问卷失败', e)
  }
}

// 上传相关
const handleUpload = () => {
  uploadFile.value = null
  uploadDialogVisible.value = true
}

const handleFileChange = (file: any) => {
  uploadFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const handleUploadSubmit = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', uploadFile.value)
    await uploadQuestionnaireTemplate(fd)
    ElMessage.success('问卷上传成功')
    uploadDialogVisible.value = false
    fetchData()
  } catch (e) {
    console.error('上传问卷失败', e)
  } finally {
    uploading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.question-preview {
  padding: 10px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border-radius: 4px;

  .question-title {
    font-weight: 500;
    margin-bottom: 6px;
  }

  .question-options {
    margin-top: 6px;
    padding-left: 16px;

    .option-item {
      color: #606266;
      font-size: 13px;
      line-height: 1.8;
    }
  }

  .scale-info {
    margin-top: 6px;
  }
}
</style>
