new Vue({
    el: '#app',
    data: {
        currentUser: { id: null, name: '', password: '', gender: '', age: '', followers: 0, following: 0 },
        activeTab: 'feed'
    },
    methods: {
        async onLoginSuccess(userCredentials) {
            // 保存基本的ID和密码
            this.currentUser.id = userCredentials.id;
            this.currentUser.password = userCredentials.password;
            await this.fetchUserInfo();
        },
        async fetchUserInfo() {
            try {
                const res = await API.getUserInfo(this.currentUser.id);
                // 合并详情信息
                this.currentUser = { ...this.currentUser, ...res.data };
                this.currentUser.name = res.data.authorName; // 字段映射
            } catch (e) {
                this.$message.error('获取用户信息失败');
            }
        },
        onLogout() {
            this.currentUser = { id: null };
            this.activeTab = 'feed';
        }
    }
});