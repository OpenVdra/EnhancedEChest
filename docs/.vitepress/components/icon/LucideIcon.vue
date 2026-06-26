<script setup>
import { computed } from 'vue'
import {
  Package, Archive, Terminal, ShieldCheck, Settings, Download,
  Sparkles, Palette, Key, Globe, ArrowRightLeft, Layers,
  MousePointer2, Sliders, List, Star, Wrench, Eye, BarChart2,
  RefreshCw, Bell, FileText, Box, SquareStack
} from 'lucide-vue-next'

const ICONS = {
  Package, Archive, Terminal, ShieldCheck, Settings, Download,
  Sparkles, Palette, Key, Globe, ArrowRightLeft, Layers,
  MousePointer2, Sliders, List, Star, Wrench, Eye, BarChart2,
  RefreshCw, Bell, FileText, Box, SquareStack
}

const props = defineProps({
  name: String,
  size: { type: Number, default: 22 }
})

const icon = computed(() => props.name ? (ICONS[props.name] ?? null) : null)
</script>

<template>
  <component :is="icon" v-if="icon" :size="size" :stroke-width="1.75" />
</template>
