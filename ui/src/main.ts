import { createApp } from 'vue'
import Antd from 'ant-design-vue';
import VueJsonPretty from 'vue-json-pretty';
import 'ant-design-vue/dist/antd.css';
import 'vue-json-pretty/lib/styles.css';

import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(router)
app.use(Antd)
app.component("vue-json-pretty", VueJsonPretty)
app.mount('#app')
