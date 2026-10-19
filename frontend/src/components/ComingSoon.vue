<script setup lang="ts">
/** 占位页：只显示页面名和"敬请期待"，画廊 / 个人简介 / 开源项目共用 */
defineProps<{
  /** 页面名，显示在占位文案上方 */
  title: string
}>()
</script>

<template>
  <div class="page-shell coming-soon-shell">
    <div class="page-container">
      <section class="coming-soon-card">
        <p class="coming-soon-eyebrow">{{ title }}</p>
        <p class="coming-soon-text">敬请期待</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.coming-soon-shell {
  display: flex;
  align-items: center;
  justify-content: center;
}

.coming-soon-shell .page-container {
  width: min(560px, 100%);
}

.coming-soon-card {
  padding: 72px 32px;
  text-align: center;
}

.coming-soon-eyebrow {
  margin: 0 0 14px;
  color: var(--accent-brown);
  font-size: 13px;
  letter-spacing: 4px;
}

.coming-soon-text {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 700;
  letter-spacing: 2px;
}
</style>
