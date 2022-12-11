import { createRouter, createWebHashHistory } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'

export default createRouter({
    history: createWebHashHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            redirect: '/flow',
            children: [
                {
                    path: '/flow',
                    name: 'Flow',
                    component: () => import('@/views/FlowList.vue'),
                }
            ]
        }
    ]
})