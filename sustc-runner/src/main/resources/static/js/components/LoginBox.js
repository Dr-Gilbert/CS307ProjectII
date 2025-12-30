Vue.component('login-box', {
    template: `
    <div class="login-wrapper">
        <div class="login-box">
            <h2 style="text-align: center; color: #409EFF">SUSTC 食谱系统</h2>
            <el-tabs v-model="activeName" stretch>
                <el-tab-pane label="登录" name="login">
                    <el-form label-position="top">
                        <el-form-item label="用户ID">
                            <el-input v-model="loginForm.mid" type="number"></el-input>
                        </el-form-item>
                        <el-form-item label="密码">
                            <el-input v-model="loginForm.password" type="password" show-password></el-input>
                        </el-form-item>
                        <el-button type="primary" style="width: 100%" @click="handleLogin" :loading="loading">登录</el-button>
                    </el-form>
                </el-tab-pane>
                <el-tab-pane label="注册" name="register">
                    <el-form label-width="60px">
                        <el-form-item label="用户名"><el-input v-model="regForm.name"></el-input></el-form-item>
                        <el-form-item label="密码"><el-input v-model="regForm.password" type="password"></el-input></el-form-item>
                        <el-form-item label="性别">
                            <el-radio-group v-model="regForm.gender">
                                <el-radio label="MALE">男</el-radio>
                                <el-radio label="FEMALE">女</el-radio>
                            </el-radio-group>
                        </el-form-item>
                        <el-form-item label="生日"><el-input v-model="regForm.birthday"></el-input></el-form-item>
                        <el-button type="success" style="width: 100%" @click="handleRegister">注册</el-button>
                    </el-form>
                </el-tab-pane>
            </el-tabs>
        </div>
    </div>
    `,
    data() {
        return {
            activeName: 'login',
            loading: false,
            loginForm: { mid: '', password: '' },
            regForm: { name: '', password: '', gender: 'Male', birthday : '2000-01-01' }
        };
    },
    methods: {
        async handleLogin() {
            this.loading = true;
            try {
                const res = await API.login(this.loginForm.mid, this.loginForm.password);
                this.$emit('login-success', { id: res.data, password: this.loginForm.password });
            } catch (e) {
                this.$message.error('登录失败：ID或密码错误');
            } finally {
                this.loading = false;
            }
        },
        async handleRegister() {
            try {
                const res = await API.register(this.regForm);
                this.$message.success(`注册成功！您的ID是: ${res.data}，请去登录`);
                this.activeName = 'login';
                this.loginForm.mid = res.data;
            } catch (e) {
                this.$message.error('注册失败');
            }
        }
    }
});