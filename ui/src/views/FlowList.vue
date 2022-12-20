<script setup lang="ts">
import { onBeforeMount, onMounted, onUnmounted, ref, watchEffect } from 'vue'
import type { TreeProps } from 'ant-design-vue';
import FlowService from '@/service/FlowService'

const flowService = new FlowService()
const autoRefresh = ref(true)
const currentTab = ref('summary')
const searchValue = ref()
const flowTreeData = ref<TreeProps['treeData']>([])
const treeHeight = ref<number>(0)
const selectedFlow = ref()

const parsePathSegments = (path: string) => {
    return path.replace(/^\//, '').split(/\//)
        .filter((segment: string, index: number, segments: string[]) => segment.length > 0 || index < segments.length - 1)
        .map((segment: string) => `/${segment}`)
}

const buildTreeData = (treeData: any, flow: any) => {
    const segments = parsePathSegments(flow.url.pathname)
    const buildSubTreeData = (tree: any, segments: string[], path: string[], flow: any) => {
        if (segments.length <= 0) {
            return
        }

        const segment = segments[0]
        const id = segments.length > 1 ? null : flow.id
        const url = segments.length > 1 ? null : flow.url
        const title = segment === '/' ? segment : segment.replace(/^\//, '')
        const prefix = [...path, segment]
        const key = `${prefix.join('')}${segments.length > 1 ? '' : `#${flow.id}`}`
        const child = tree.children.find((it: any) => it.key === key) || { id, key, title, url, children: [] }
        const others = tree.children.filter((it: any) => it.key !== key)
        tree.children = [...others, child].sort((a, b) => a.title < b.title ? 1 : a.title > b.title ? -1 : a.id < b.id ? 1 : a.id > b.id ? -1 : 0)
        segments.shift()
        buildSubTreeData(child, segments, prefix, flow)
    }

    buildSubTreeData(treeData[flow.url.origin], segments, [flow.url.origin], flow)
}

const refresh = async () => {
    const flows = await flowService.getFlows()
    const baseUrls = flows.reduce((hosts: any, flow: any) => {
        hosts[flow.url.origin] = {
            key: flow.url.origin,
            title: flow.url.origin,
            children: [],
        }
        return hosts
    }, {})

    flows.forEach((flow: any) => buildTreeData(baseUrls, flow))
    flowTreeData.value = Object.keys(baseUrls).map(key => baseUrls[key])

    if (autoRefresh.value) {
        setTimeout(refresh, 3000)
    }
}

const calculateTreeHeight = () => {
    treeHeight.value = (
        window.innerHeight
        - 64 /* page header */
        - 32 /* page margin */
        - 48 /* card padding */
        - 40 /* search box */
        - 4 /* hidden tree node */
    )
}

const onFlowSelected: TreeProps['onSelect'] = async (selectedKeys, e) => {
    if (e.selectedNodes.length < 1) {
        selectedFlow.value = null
        return
    }

    const selected = e.selectedNodes[e.selectedNodes.length - 1]
    if (selected.children && selected.children.length > 0) {
        selectedFlow.value = null
        return
    }

    selectedFlow.value = await flowService.getFlow(selected.id)
}

onBeforeMount(refresh)

watchEffect(() => {
    calculateTreeHeight()
    window.onresize = calculateTreeHeight
})

onUnmounted(async () => {
    autoRefresh.value = false
})

</script>

<template>
    <a-row :gutter="16" style="height: 100%" class="flow-list">
        <a-col :span="selectedFlow ? 8 : 24" style="height: 100%">
            <a-card style="height: 100%">
                <a-input-search v-model:value="searchValue" style="margin-bottom: 8px" placeholder="Search" />
                <a-tree :tree-data="flowTreeData" :height="treeHeight" :block-node="true" :autoExpandParent="true" @select="onFlowSelected">
                    <template #title="{ title, key, path }">
                        <span style="white-space: nowrap" :title="path">{{ title }}</span>
                    </template>
                </a-tree>
            </a-card>
        </a-col>
        <a-col :span="selectedFlow ? 16 : 0" style="height: 100%" v-if="selectedFlow">
            <a-card style="height: 100%">
                <a-tabs v-model:activeKey="currentTab">
                    <a-tab-pane key="summary" tab="Summary">
                        <a-descriptions :column="1" title="General">
                            <a-descriptions-item label="Id">{{ selectedFlow.id }}</a-descriptions-item>
                        </a-descriptions>
                        <a-descriptions :column="1" title="Client">
                            <a-descriptions-item label="Address">{{ selectedFlow.client.address }}</a-descriptions-item>
                        </a-descriptions>
                        <a-descriptions :column="1" title="SSL">
                            <a-descriptions-item label="Protocols">{{ selectedFlow.ssl.protocols.join(', ') }}</a-descriptions-item>
                            <a-descriptions-item label="Cipher Suites">{{ selectedFlow.ssl.cipherSuites.join(', ') }}</a-descriptions-item>
                        </a-descriptions>
                    </a-tab-pane>
                    <a-tab-pane key="request" tab="Request">
                        <a-descriptions :column="1" title="General">
                            <a-descriptions-item label="Method">{{ selectedFlow.request.method }}</a-descriptions-item>
                            <a-descriptions-item label="URL">{{ selectedFlow.request.url }}</a-descriptions-item>
                        </a-descriptions>
                        <a-divider />
                        <a-descriptions :column="1" title="Headers">
                            <a-descriptions-item v-for="header in Object.entries(selectedFlow.request.headers)" :label="header[0]">{{ header[1] }}</a-descriptions-item>
                        </a-descriptions>
                        <a-divider />
                        <a-descriptions :column="1" title="Body">
                            <a-descriptions-item :lable="''">
                                <pre v-if="selectedFlow.request.body" style="white-space: pre-wrap;"> {{ selectedFlow.request.body }} </pre>
                                <pre v-else>/* no content */</pre>
                            </a-descriptions-item>
                        </a-descriptions>
                    </a-tab-pane>
                    <a-tab-pane key="response" tab="Response">
                        <a-descriptions title="General">
                            <a-descriptions-item label="Status">{{ selectedFlow.response.status }}</a-descriptions-item>
                        </a-descriptions>
                        <a-divider />
                        <a-descriptions :column="1" title="Headers">
                            <a-descriptions-item v-for="header in Object.entries(selectedFlow.response.headers)" :label="header[0]">{{ header[1] }}</a-descriptions-item>
                        </a-descriptions>
                        <a-divider />
                        <a-descriptions :column="1" title="Body">
                            <a-descriptions-item :lable="''">
                                <pre v-if="selectedFlow.response.body" style="white-space: pre-wrap;"> {{ selectedFlow.response.body }} </pre>
                                <pre v-else>/* no content */</pre>
                            </a-descriptions-item>
                        </a-descriptions>
                    </a-tab-pane>
                    <a-tab-pane key="preview" tab="Preview">
                        <div v-if="/html/.test(selectedFlow.response.headers['Content-Type'] || selectedFlow.response.headers['content-type'])" v-html="selectedFlow.response.body"></div>
                        <vue-json-pretty v-if="/json/.test(selectedFlow.response.headers['Content-Type'] || selectedFlow.response.headers['content-type'])" :data="selectedFlow.response.body" :showDoubleQuotes="true" :showIcon="true" :showLength="true" :showLineNumber="true" />
                    </a-tab-pane>
                </a-tabs>
            </a-card>
        </a-col>
    </a-row>
</template>

<style scoped>
.flow-list .ant-tabs-tabpane {
    max-height: calc(100vh - 64px - 32px - 48px - 60px - 4px);
    overflow-y: auto;
}
</style>
