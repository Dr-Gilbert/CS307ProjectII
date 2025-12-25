Vue.component('nav-bar', {
    props: ['user'],
    template: `
    <div class="header">
        <div style="font-size: 22px; font-weight: bold;">🍳 SUSTC Recipe</div>
        <div>
            <span style="margin-right: 15px">欢迎, {{ user.name }} (ID: {{ user.id }})</span>
            <el-button size="mini" type="info" plain @click="$emit('logout')">退出</el-button>
        </div>
    </div>
    `
});