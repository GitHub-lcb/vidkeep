<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome(): void {
  void router.replace('/')
}
</script>

<template>
  <div class="not-found">
    <p class="not-found__code">404</p>
    <h1 class="not-found__title">页面不存在</h1>
    <p class="not-found__desc">你访问的地址可能已被移动或删除。</p>
    <el-button class="not-found__action" type="primary" @click="goHome">回到首页</el-button>
  </div>
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 20px;
  text-align: center;
  color: $vk-text;
  background: linear-gradient(165deg, #070b11 0%, #0c121c 55%, #080d15 100%);
}

.not-found__code {
  font-size: 72px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.12);
}

.not-found__title {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 600;
}

.not-found__desc {
  margin-top: 10px;
  font-size: 13.5px;
  color: $vk-text-muted;
}

.not-found__action {
  margin-top: 28px;
}
</style>
