<template>
  <div class="page-container">
    <el-card>
      <div class="profile-container">
        <!-- 左侧信息 -->
        <div class="profile-sidebar">
          <div class="avatar-section">
            <el-avatar :size="120" :src="userInfo.avatar" />
            <h3>{{ userInfo.name }}</h3>
            <p class="role">{{ userInfo.role }}</p>
          </div>

          <el-divider />

          <div class="info-section">
            <div class="info-item">
              <span class="label">用户名</span>
              <span class="value">{{ userInfo.username }}</span>
            </div>
            <div class="info-item">
              <span class="label">邮箱</span>
              <span class="value">{{ userInfo.email }}</span>
            </div>
            <div class="info-item">
              <span class="label">电话</span>
              <span class="value">{{ userInfo.phone }}</span>
            </div>
            <div class="info-item">
              <span class="label">部门</span>
              <span class="value">{{ userInfo.dept }}</span>
            </div>
          </div>
        </div>

        <!-- 右侧内容 -->
        <div class="profile-content">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本设置" name="basic">
              <el-form :model="form" label-width="100px" style="max-width: 500px">
                <el-form-item label="昵称">
                  <el-input v-model="form.nickname" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="form.email" />
                </el-form-item>
                <el-form-item label="电话">
                  <el-input v-model="form.phone" />
                </el-form-item>
                <el-form-item label="个人简介">
                  <el-input v-model="form.bio" type="textarea" :rows="4" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary">保存</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="安全设置" name="security">
              <el-form :model="passwordForm" label-width="120px" style="max-width: 500px">
                <el-form-item label="原密码">
                  <el-input v-model="passwordForm.oldPassword" type="password" />
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input v-model="passwordForm.newPassword" type="password" />
                </el-form-item>
                <el-form-item label="确认新密码">
                  <el-input v-model="passwordForm.confirmPassword" type="password" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary">修改密码</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

const activeTab = ref('basic')

const userInfo = ref({
  name: '管理员',
  username: 'admin',
  role: '系统管理员',
  avatar: '',
  email: 'admin@edu.edu.cn',
  phone: '13800138000',
  dept: '信息中心'
})

const form = reactive({
  nickname: '管理员',
  email: 'admin@edu.edu.cn',
  phone: '13800138000',
  bio: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
</script>

<style scoped lang="scss">
.profile-container {
  display: flex;
  gap: 40px;

  .profile-sidebar {
    width: 280px;
    flex-shrink: 0;

    .avatar-section {
      text-align: center;
      padding: 20px 0;

      h3 {
        margin: 16px 0 8px;
        font-size: 20px;
      }

      .role {
        color: rgba(0, 0, 0, 0.45);
      }
    }

    .info-section {
      .info-item {
        display: flex;
        justify-content: space-between;
        padding: 12px 0;
        border-bottom: 1px solid #f0f0f0;

        &:last-child {
          border-bottom: none;
        }

        .label {
          color: rgba(0, 0, 0, 0.45);
        }

        .value {
          color: rgba(0, 0, 0, 0.85);
        }
      }
    }
  }

  .profile-content {
    flex: 1;
    padding: 20px 0;
  }
}
</style>
