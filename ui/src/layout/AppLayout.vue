<script lang="ts">
import {
    DashboardOutlined,
    GlobalOutlined,
    MenuUnfoldOutlined,
    MenuFoldOutlined,
} from '@ant-design/icons-vue';
import { defineComponent, ref } from 'vue';
import { RouteRecordRaw } from 'vue-router';

import router from '@/router'

export default defineComponent({
    components: {
        DashboardOutlined,
        GlobalOutlined,
        MenuUnfoldOutlined,
        MenuFoldOutlined,
    },
    setup() {
        return {
            collapsed: ref<boolean>(false),
            menus: ref<RouteRecordRaw[]>(),
            selectedMenus: ref<string[]>([]),
        };
    },
    beforeMount() {
        this.menus = router.getRoutes().filter((it: RouteRecordRaw) => it.path != '/' && !it.path.includes(':'))
        this.selectedMenus = this.menus
            .filter((it: RouteRecordRaw) => this.$route.path.startsWith(it.path))
            .map((it: RouteRecordRaw) => it.path)
    },
    computed: {
        title() {
            return this.$route.name
        },
    },
});
</script>

<template>
    <a-layout class="app-layout">
        <a-layout-sider v-model:collapsed="collapsed" :trigger="null" collapsible>
            <div class="logo" />
            <a-menu v-model:selectedKeys="selectedMenus" theme="dark" mode="inline">
                <a-menu-item key="/flow">
                    <global-outlined />
                    <span>Flow</span>
                </a-menu-item>
            </a-menu>
        </a-layout-sider>
        <a-layout>
            <a-layout-header style="position: sticky; top: 0; z-index: 1; width: 100%; background: #fff; padding: 0">
                <a-page-header style="padding: 0px" :title="title">
                    <template #avatar>
                        <menu-unfold-outlined v-if="collapsed" class="trigger" @click="() => (collapsed = !collapsed)" />
                        <menu-fold-outlined v-else class="trigger" @click="() => (collapsed = !collapsed)" />
                    </template>
                </a-page-header>
            </a-layout-header>
            <a-layout-content :style="{ margin: '16px 16px', minHeight: '280px' }">
                <router-view></router-view>
            </a-layout-content>
        </a-layout>
    </a-layout>
</template>

<style>
.app-layout {
    min-height: 100vh;
}

.app-layout .ant-menu-title-content>a {
    margin-left: 8px;
}

.app-layout .ant-page-header-heading-left {
    margin: 0px !important;
    justify-content: center !important;
    align-self: center;
}

.app-layout .trigger {
    font-size: 18px;
    line-height: 64px;
    padding: 0 24px;
    cursor: pointer;
    transition: color 0.3s;
}

.app-layout .trigger:hover {
    color: #1890ff;
}

.app-layout .ant-layout-sider-collapsed .logo::after {
    content: '' !important;
}

.app-layout .ant-layout-sider .logo::after {
    content: 'MIMT Proxy';
}

.app-layout .ant-layout-sider .logo {
    color: white;
    font-size: 18px;
    font-weight: bold;
    height: 32px;
    line-height: 32px;
    margin: 16px;
    padding-left: 42px;
    background: url(/logo.svg) no-repeat left center;
}

.app-layout .ant-layout-sider-collapsed .logo {
    background-position: center !important;
}

.site-layout .site-layout-background {
    background: #fff;
}
</style>
  