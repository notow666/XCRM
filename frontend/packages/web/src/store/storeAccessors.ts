import { getActivePinia } from 'pinia';

import type useAppStoreType from './modules/app/index';

type AppStore = ReturnType<typeof useAppStoreType>;

/** Pinia 内部 store 注册表，用于在不 import app store 的前提下按 id 取实例 */
type PiniaWithStoreRegistry = ReturnType<typeof getActivePinia> & {
  _s: Map<string, AppStore>;
};

/**
 * 在 user store 内访问 app store，避免 user ↔ app 顶层互相 import 导致 TDZ。
 * 调用时 app store 应已由 App/布局组件完成注册。
 */
export default function useAppStoreAccessor(): AppStore {
  const pinia = getActivePinia();
  if (!pinia) {
    throw new Error('[useAppStoreAccessor] Pinia is not active');
  }
  const store = (pinia as PiniaWithStoreRegistry)._s.get('app');
  if (!store) {
    throw new Error('[useAppStoreAccessor] App store is not registered');
  }
  return store;
}
