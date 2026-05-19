/** 打开顶部消息中心抽屉 */
export const OPEN_MESSAGE_DRAWER_EVENT = 'cordys:open-message-drawer';

export function dispatchOpenMessageDrawer() {
  window.dispatchEvent(new CustomEvent(OPEN_MESSAGE_DRAWER_EVENT));
}
