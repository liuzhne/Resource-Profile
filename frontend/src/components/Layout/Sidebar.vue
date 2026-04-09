<template>
  <div class="sidebar" :class="{ collapsed: sidebarCollapsed }">
    <!-- Logo -->
    <div class="logo">
      <img src="/vite.svg" alt="logo" />
      <span v-show="!sidebarCollapsed" class="title">师生画像系统</span>
    </div>

    <!-- 菜单 -->
    <el-scrollbar class="menu-scrollbar">
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
        background-color="#001529"
        text-color="rgba(255, 255, 255, 0.65)"
        active-text-color="#fff"
      >
        <SidebarItem
          v-for="route in routes"
          :key="route.path"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import SidebarItem from './SidebarItem.vue'
import { routes } from '@/router'

const route = useRoute()
const appStore = useAppStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const activeMenu = computed(() => route.path)

// 过滤掉不需要显示的路由
const menuRoutes = computed(() => {
  return routes.filter(r => !r.meta?.hidden && r.path !== '/login' && r.path !== '/:pathMatch(.*)*')
})
</script>

<style scoped lang="scss">
.sidebar {
  width: var(--sidebar-width);
  background: #001529;
  transition: width 0.3s;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;

  &.collapsed {
    width: var(--sidebar-collapsed-width);
  }

  .logo {
    height: var(--header-height);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 16px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    img {
      width: 32px;
      height: 32px;
    }

    .title {
      margin-left: 12px;
      color: #fff;
      font-size: 16px;
      font-weight: 600;
      white-space: nowrap;
    }
  }

  .menu-scrollbar {
    flex: 1;

    :deep(.el-scrollbar__wrap) {
      overflow-x: hidden;
    }
  }

  :deep(.el-menu) {
    border-right: none;
  }
}
</style>
