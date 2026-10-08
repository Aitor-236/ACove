<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

interface LoginResult {
  token: string
  username: string
  email: string
}

const formRef = ref<FormInstance>()
const loading = ref(false)
const router = useRouter()
const route = useRoute()

const loginForm = reactive({
  account: '',
  password: ''
})

const rules: FormRules<typeof loginForm> = {
  account: [{ required: true, message: '请输入邮箱或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const account = loginForm.account.trim()
    const isEmail = account.includes('@')
    const payload = isEmail
      ? { email: account, password: loginForm.password }
      : { username: account, password: loginForm.password }

    const response = (await request.post('/auth/login', payload)) as unknown as {
      code: number
      message: string
      data: LoginResult
    }

    localStorage.setItem('token', response.data.token)
    localStorage.setItem('username', response.data.username)
    if (response.data.email) {
      localStorage.setItem('email', response.data.email)
    }

    ElMessage.success(`登录成功，欢迎回来，${response.data.username}`)
    // 登录的是管理员账号，直接进后台；被拦截时回到原本要去的后台页面
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    void router.push(redirect || '/admin')
  } catch (error: any) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <main class="login-card surface-panel">
      <div class="login-header">
        <div class="logo">A</div>
        <h1>登录 ACove</h1>
        <p>使用邮箱或用户名登录，欢迎回来</p>
      </div>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="account">
          <el-input v-model.trim="loginForm.account" placeholder="邮箱 / 用户名" clearable />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            show-password
            clearable
          />
        </el-form-item>

        <el-button class="login-button" type="primary" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <p class="login-footer">还没有账号？请联系管理员开通</p>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  --el-color-primary: #8a5a3b;
  --el-color-primary-light-3: #b98a5e;
  --el-color-primary-light-5: #cfa986;
  --el-color-primary-light-7: #e2cbaf;
  --el-color-primary-light-8: #ecdcc6;
  --el-color-primary-light-9: #f4ebdd;
  --el-color-primary-dark-2: #6f4730;

  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--bg-cream);
}

.login-card {
  width: min(420px, 100%);
  padding: 40px 38px 32px;
  border-radius: 24px;
}

.login-header {
  margin-bottom: 28px;
  text-align: center;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  margin: 0 auto 18px;
  border-radius: 18px;
  color: #fdf9f2;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #b98a5e, #8a5a3b);
  box-shadow: 0 10px 24px rgba(138, 90, 59, 0.3);
}

.login-header h1 {
  margin: 0 0 8px;
  color: var(--text-strong);
  font-size: 24px;
  font-weight: 600;
}

.login-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.login-card :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-card :deep(.el-input__wrapper) {
  border-radius: 12px;
  background: var(--panel-alt-bg);
  box-shadow:
    0 0 0 1px rgba(138, 90, 59, 0.24) inset,
    0 4px 14px rgba(120, 88, 58, 0.08);
}

.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 4px 16px rgba(120, 88, 58, 0.16);
}

.login-button {
  width: 100%;
  height: 46px;
  margin-top: 2px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  letter-spacing: 8px;
  box-shadow: 0 10px 24px rgba(138, 90, 59, 0.26);
}

.login-footer {
  margin: 22px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  text-align: center;
}
</style>
