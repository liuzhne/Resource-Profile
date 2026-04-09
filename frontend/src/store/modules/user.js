import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, getUserInfo } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const userRoles = computed(() => userInfo.value?.roles || [])

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const loginAction = async (form) => {
    const res = await login(form)
    setToken(res.data.token)
    await getUserInfoAction()
    router.push('/')
  }

  const getUserInfoAction = async () => {
    const res = await getUserInfo()
    userInfo.value = res.data
    return res.data
  }

  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    router.push('/login')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    userRoles,
    loginAction,
    getUserInfoAction,
    logout
  }
})
