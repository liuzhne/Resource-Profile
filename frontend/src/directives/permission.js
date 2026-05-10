/**
 * v-permission 指令 + usePermission 组合式 API（E-5：前端二次脱敏）
 *
 * 用法：
 *   <span v-permission="['psychologist','admin']">原始得分: {{ row.score }}</span>
 *   <el-table-column v-if="hasRole(['psychologist','admin'])" prop="score" label="得分" />
 *
 * 命中策略：
 *   - 取 useUserStore().userRoles，用户角色与 binding 数组取交集
 *   - 没命中则把元素从 DOM 中移除（而非 display:none），避免 devtools 翻看
 *   - binding 为空 / 非数组：警告并默认拒绝（fail-secure）
 *
 * 注意：仅作前端二次防线，后端 mental-service 已对原始问卷答案做接口层鉴权。
 */
import { useUserStore } from "@/store/modules/user";

const PRIVILEGED_FALLBACK = ["admin"];

const normalizeRoles = (binding) => {
  const v = binding.value;
  if (Array.isArray(v) && v.length > 0) return v;
  if (typeof v === "string" && v.trim()) return [v.trim()];
  console.warn("[v-permission] 缺少角色数组，默认仅 admin 可见：", binding);
  return PRIVILEGED_FALLBACK;
};

const detach = (el) => {
  if (el && el.parentNode) {
    // 用注释占位便于后续 update 复位（Vue 3 nextTick 时插入节点更稳定）
    const placeholder = document.createComment("v-permission");
    el.parentNode.replaceChild(placeholder, el);
    el._permissionPlaceholder = placeholder;
  }
};

const reattach = (el) => {
  const ph = el._permissionPlaceholder;
  if (ph && ph.parentNode) {
    ph.parentNode.replaceChild(el, ph);
    el._permissionPlaceholder = null;
  }
};

const hasAnyRole = (allowed) => {
  const store = useUserStore();
  const roles = store.userRoles || [];
  return allowed.some((r) => roles.includes(r));
};

export const permission = {
  mounted(el, binding) {
    const allowed = normalizeRoles(binding);
    if (!hasAnyRole(allowed)) detach(el);
  },
  updated(el, binding) {
    const allowed = normalizeRoles(binding);
    const ok = hasAnyRole(allowed);
    if (ok) reattach(el);
    else detach(el);
  },
  unmounted(el) {
    el._permissionPlaceholder = null;
  },
};

/** 在 setup 中用 v-if / 计算属性的场景。 */
export const usePermission = () => {
  const hasRole = (allowed) => {
    const list = Array.isArray(allowed)
      ? allowed
      : allowed
        ? [allowed]
        : PRIVILEGED_FALLBACK;
    return hasAnyRole(list);
  };
  return { hasRole };
};

export default {
  install(app) {
    app.directive("permission", permission);
  },
};
