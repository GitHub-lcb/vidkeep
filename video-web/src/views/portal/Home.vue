<script setup lang="ts">
// 前台首页占位：M0 只展示站点名与建设中状态，M2 接入视频列表
interface FeatureItem {
  title: string
  desc: string
}

const features: FeatureItem[] = [
  { title: '后台上传', desc: '管理员在后台上传自有视频文件，不对外收集内容。' },
  { title: '自动转码', desc: '按源文件自动选择 remux 或转码，输出 HLS 切片。' },
  { title: '流畅播放', desc: 'ArtPlayer + hls.js 适配桌面与移动端播放。' },
]
</script>

<template>
  <div class="home">
    <section class="home-hero">
      <p class="home-hero__eyebrow">self-hosted video</p>
      <h1 class="home-hero__title">vidkeep</h1>
      <p class="home-hero__desc">把自有内容留在自己的服务器上，安静地看。</p>
      <p class="home-hero__status">
        <span class="home-hero__dot" aria-hidden="true"></span>
        视频站建设中
      </p>
    </section>

    <section class="home-features">
      <article v-for="item in features" :key="item.title" class="home-feature">
        <h2 class="home-feature__title">{{ item.title }}</h2>
        <p class="home-feature__desc">{{ item.desc }}</p>
      </article>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.home {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px;

  @include vk-mobile {
    padding: 0 16px;
  }
}

.home-hero {
  padding: 118px 0 72px;

  @include vk-tablet {
    padding: 84px 0 56px;
  }

  @include vk-mobile {
    padding: 64px 0 44px;
  }
}

.home-hero__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: $vk-text-muted;
}

.home-hero__title {
  margin-top: 18px;
  font-size: clamp(56px, 10vw, 104px);
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.03em;
  color: #ffffff;
  background: linear-gradient(180deg, #ffffff 12%, #9fb0c6 92%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.home-hero__desc {
  max-width: 30em;
  margin-top: 22px;
  font-size: 15px;
  color: #a6b2c3;
}

.home-hero__status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 30px;
  padding: 7px 14px;
  font-size: 12.5px;
  letter-spacing: 0.04em;
  color: #c7d2e0;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
}

.home-hero__dot {
  width: 6px;
  height: 6px;
  background: $vk-accent;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(91, 141, 239, 0.16);
}

.home-features {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px;
  padding: 34px 0 88px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);

  @include vk-tablet {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 22px;
  }

  @include vk-mobile {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
    padding-bottom: 56px;
  }
}

.home-feature__title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #eef2f8;
}

.home-feature__desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: $vk-text-muted;
}
</style>
