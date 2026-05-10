<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="q-header">
          <div>
            <h2 style="margin: 0 0 8px;">{{ questionnaire.title }}</h2>
            <p style="color: #909399; margin: 0 0 8px;">{{ questionnaire.description }}</p>
            <div>
              <el-tag type="info" style="margin-right: 8px;">{{ questionnaire.type }}</el-tag>
              <span style="font-size: 13px; color: #909399;">
                {{ questionnaire.startTime }} ~ {{ questionnaire.endTime }}
              </span>
            </div>
          </div>
          <el-button @click="goBack">返回列表</el-button>
        </div>
      </template>

      <el-alert
        v-if="alreadySubmitted"
        title="您已完成该问卷"
        description="感谢您的参与，您已提交过该问卷的作答。"
        type="success"
        :closable="false"
        show-icon
        style="margin-bottom: 20px;"
      />

      <el-form v-else ref="formRef" :model="formData" label-position="top">
        <div v-for="question in questions" :key="question.id" class="question-item">
          <el-form-item
            :label="`${question.sortOrder}. ${question.content}`"
            :prop="`answers.${question.id}`"
            :rules="question.required === 1 ? [{ required: true, message: '请作答此题' }] : []"
          >
            <!-- 单选题 -->
            <el-radio-group
              v-if="question.questionType === 'single_choice'"
              v-model="formData.answers[question.id]"
            >
              <el-radio
                v-for="opt in parseOptions(question.options)"
                :key="opt.value"
                :value="opt.value"
                style="display: block; margin-bottom: 8px; height: auto; white-space: normal;"
              >
                {{ opt.label }}
              </el-radio>
            </el-radio-group>

            <!-- 多选题 -->
            <el-checkbox-group
              v-else-if="question.questionType === 'multiple_choice'"
              v-model="formData.answers[question.id]"
            >
              <el-checkbox
                v-for="opt in parseOptions(question.options)"
                :key="opt.value"
                :label="opt.value"
                style="display: block; margin-bottom: 8px; height: auto; white-space: normal;"
              >
                {{ opt.label }}
              </el-checkbox>
            </el-checkbox-group>

            <!-- 文本题 -->
            <el-input
              v-else-if="question.questionType === 'text'"
              v-model="formData.answers[question.id]"
              type="textarea"
              :rows="3"
              placeholder="请输入您的回答"
            />

            <!-- 量表题 -->
            <div v-else-if="question.questionType === 'scale'" class="scale-widget">
              <el-slider
                v-model="formData.answers[question.id]"
                :min="question.scaleMin || 1"
                :max="question.scaleMax || 10"
                show-input
              />
              <div class="scale-labels" v-if="question.scaleLabels">
                <span>{{ parseScaleLabels(question.scaleLabels)?.min || '' }}</span>
                <span>{{ parseScaleLabels(question.scaleLabels)?.max || '' }}</span>
              </div>
            </div>
          </el-form-item>
        </div>

        <el-form-item style="margin-top: 24px;">
          <el-button type="primary" @click="handleSubmit" :loading="submitting" size="large">
            提交问卷
          </el-button>
          <el-button @click="goBack" size="large">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getQuestionnaireDetail,
  getQuestionnaireQuestions,
  submitQuestionnaireResponse,
  checkQuestionnaireResponse
} from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const questionnaireId = Number(route.params.id)
const loading = ref(false)
const submitting = ref(false)
const alreadySubmitted = ref(false)

const questionnaire = ref<any>({})
const questions = ref<any[]>([])
const formData = reactive<Record<string, any>>({ answers: {} })
const formRef = ref()

const parseOptions = (jsonStr: string) => {
  try {
    return jsonStr ? JSON.parse(jsonStr) : []
  } catch {
    return []
  }
}

const parseScaleLabels = (jsonStr: string) => {
  try {
    return jsonStr ? JSON.parse(jsonStr) : {}
  } catch {
    return {}
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const [qRes, questionsRes] = await Promise.all([
      getQuestionnaireDetail(questionnaireId),
      getQuestionnaireQuestions(questionnaireId)
    ])
    questionnaire.value = qRes.data
    questions.value = questionsRes.data || []

    // 初始化表单数据
    for (const q of questions.value) {
      if (q.questionType === 'multiple_choice') {
        formData.answers[q.id] = []
      } else if (q.questionType === 'scale') {
        formData.answers[q.id] = q.scaleMin || 1
      } else {
        formData.answers[q.id] = ''
      }
    }

    // 检查是否已提交
    const userId = userStore.userInfo?.id || userStore.userInfo?.userId
    if (userId) {
      try {
        const checkRes = await checkQuestionnaireResponse(userId, questionnaireId)
        if (checkRes.data?.submitted) {
          alreadySubmitted.value = true
        }
      } catch {
        // ignore check failure
      }
    }
  } catch (e) {
    console.error('获取问卷失败', e)
    ElMessage.error('获取问卷信息失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完成所有必答题')
    return
  }

  submitting.value = true
  try {
    const userId = userStore.userInfo?.id || userStore.userInfo?.userId
    const answersStr = JSON.stringify(formData.answers)
    await submitQuestionnaireResponse({
      studentId: userId,
      questionnaireId: questionnaireId,
      answers: answersStr
    })
    ElMessage.success('问卷提交成功')
    alreadySubmitted.value = true
  } catch (e) {
    console.error('提交问卷失败', e)
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  router.push('/mental/student')
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped lang="scss">
.q-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.question-item {
  padding: 16px;
  margin-bottom: 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.scale-widget {
  width: 100%;
  padding: 0 12px;

  .scale-labels {
    display: flex;
    justify-content: space-between;
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
}
</style>
