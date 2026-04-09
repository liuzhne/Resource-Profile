import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/components/Layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据面板', icon: 'DataLine' }
      },
      {
        path: '/teacher',
        name: 'Teacher',
        redirect: '/teacher/list',
        meta: { title: '教师画像', icon: 'UserFilled' },
        children: [
          {
            path: '/teacher/list',
            name: 'TeacherList',
            component: () => import('@/views/teacher/list.vue'),
            meta: { title: '教师列表' }
          },
          {
            path: '/teacher/detail/:id',
            name: 'TeacherDetail',
            component: () => import('@/views/teacher/detail.vue'),
            meta: { title: '教师详情', hidden: true }
          }
        ]
      },
      {
        path: '/student',
        name: 'Student',
        redirect: '/student/list',
        meta: { title: '学生画像', icon: 'Reading' },
        children: [
          {
            path: '/student/list',
            name: 'StudentList',
            component: () => import('@/views/student/list.vue'),
            meta: { title: '学生列表' }
          },
          {
            path: '/student/detail/:id',
            name: 'StudentDetail',
            component: () => import('@/views/student/detail.vue'),
            meta: { title: '学生详情', hidden: true }
          }
        ]
      },
      {
        path: '/mental',
        name: 'Mental',
        redirect: '/mental/overview',
        meta: { title: '心理健康', icon: 'FirstAidKit' },
        children: [
          {
            path: '/mental/overview',
            name: 'MentalOverview',
            component: () => import('@/views/mental/overview.vue'),
            meta: { title: '心理概览' }
          },
          {
            path: '/mental/questionnaire',
            name: 'MentalQuestionnaire',
            component: () => import('@/views/mental/questionnaire.vue'),
            meta: { title: '问卷管理' }
          },
          {
            path: '/mental/analysis',
            name: 'MentalAnalysis',
            component: () => import('@/views/mental/analysis.vue'),
            meta: { title: '分析报告' }
          }
        ]
      },
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '我的画像', icon: 'Avatar' }
      },
      {
        path: '/admin',
        name: 'Admin',
        redirect: '/admin/users',
        meta: { title: '系统管理', icon: 'Setting', roles: ['admin'] },
        children: [
          {
            path: '/admin/users',
            name: 'AdminUsers',
            component: () => import('@/views/admin/users.vue'),
            meta: { title: '用户管理' }
          },
          {
            path: '/admin/roles',
            name: 'AdminRoles',
            component: () => import('@/views/admin/roles.vue'),
            meta: { title: '角色权限' }
          }
        ]
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to, from, next) => {
  NProgress.start()

  const userStore = useUserStore()
  const token = userStore.token

  if (token) {
    if (to.path === '/login') {
      next('/')
    } else {
      if (!userStore.userInfo) {
        try {
          await userStore.getUserInfo()
          next()
        } catch {
          userStore.logout()
          next('/login')
        }
      } else {
        next()
      }
    }
  } else {
    if (to.meta.public) {
      next()
    } else {
      next('/login')
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export { routes }
export default router
