<script setup lang="ts">
import { RouterView } from 'vue-router' // 1
</script>

<template>
  <RouterView />
</template>

<style>
/* 3 */
html, body, #app {
  margin: 0;
  padding: 0;
  width: 100vw;
  height: 100%;
  overflow: hidden;
}
</style>