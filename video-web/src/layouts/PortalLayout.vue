<script setup lang="ts">
// 前台布局：品牌顶栏 + 首页导航 + 路由出口 + 页脚；详情页/播放页复用同一套外壳
</script>

<template>
  <div class="portal">
    <header class="portal-header">
      <div class="portal-header__inner">
        <RouterLink class="portal-brand" to="/" aria-label="vidkeep 首页">
          <span class="portal-brand__mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path d="M9 7l8 5-8 5z" fill="currentColor" />
            </svg>
          </span>
          <span class="portal-brand__name">vidkeep</span>
        </RouterLink>

        <nav class="portal-nav" aria-label="主导航">
          <RouterLink class="portal-nav__link" to="/">首页</RouterLink>
          <RouterLink class="portal-nav__link portal-nav__link--muted" to="/admin/login">管理员登录</RouterLink>
        </nav>
      </div>
    </header>

    <main class="portal-main">
      <RouterView />
    </main>

    <footer class="portal-footer">
      <p class="portal-footer__text">vidkeep · 自有内容视频站</p>
    </footer>
  </div>
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.portal {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  color: $vk-text;
  background:
    radial-gradient(1100px 560px at 50% -18%, rgba(91, 141, 239, 0.16), transparent 62%),
    linear-gradient(180deg, $vk-ink-900 0%, #0b111b 58%, $vk-ink-900 100%);
}

.portal-header {
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(8, 12, 19, 0.72);
  backdrop-filter: blur(12px);
}

.portal-header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1120px;
  height: 64px;
  margin: 0 auto;
  padding: 0 24px;

  @include vk-mobile {
    height: 56px;
    padding: 0 16px;
  }
}

.portal-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #ffffff;
}

.portal-brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: #ffffff;
  background: $vk-accent;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(91, 141, 239, 0.32);
}

.portal-brand__name {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0.04em;
}

.portal-nav {
  display: flex;
  align-items: center;
  gap: 22px;
  font-size: 13px;

  @include vk-mobile {
    gap: 16px;
  }
}

.portal-nav__link {
  position: relative;
  color: $vk-text-muted;
  transition: color 0.2s ease;

  &:hover {
    color: $vk-text;
  }

  &.router-link-exact-active {
    color: $vk-text;
  }

  // 首页导航选中态：底部短横线，不抢视觉
  &::after {
    position: absolute;
    bottom: -6px;
    left: 50%;
    width: 14px;
    height: 2px;
    content: '';
    background: $vk-accent;
    border-radius: 1px;
    opacity: 0;
    transform: translateX(-50%);
    transition: opacity 0.2s ease;
  }

  &.router-link-exact-active::after {
    opacity: 1;
  }
}

.portal-nav__link--muted {
  color: #6d7889;
  font-size: 12.5px;

  &::after {
    display: none;
  }

  &:hover {
    color: $vk-text-muted;
  }
}

.portal-main {
  flex: 1;
  width: 100%;
}

.portal-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding: 22px 16px 26px;
  text-align: center;
}

.portal-footer__text {
  font-size: 12px;
  letter-spacing: 0.04em;
  color: #5f6b7d;
}
</style>
