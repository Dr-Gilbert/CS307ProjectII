Vue.component('user-profile', {
    props: ['user'],
    template: `
    <div>
        <div class="card-box">
            <div class="section-title">基本资料</div>
            <el-descriptions :column="2" border>
                <el-descriptions-item label="ID">{{ user.id }}</el-descriptions-item>
                <el-descriptions-item label="昵称">{{ user.name }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ user.gender }}</el-descriptions-item>
                <el-descriptions-item label="年龄">{{ user.age }}</el-descriptions-item>
                <el-descriptions-item label="粉丝">{{ user.followers }}</el-descriptions-item>
                <el-descriptions-item label="关注">{{ user.following }}</el-descriptions-item>
            </el-descriptions>
            <div style="margin-top:15px">
                <el-button size="small" type="primary" @click="dialogVisible = true">修改资料</el-button>
                <el-button size="small" type="danger" @click="deleteAcc">注销账号</el-button>
            </div>
        </div>
        <div class="card-box">
            <div class="section-title">关注用户</div>
            <el-input v-model="followId" placeholder="输入ID" style="width:200px"></el-input>
            <el-button type="primary" @click="doFollow">关注/取关</el-button>
        </div>

        <el-dialog title="修改资料" :visible.sync="dialogVisible" width="30%">
            <el-form label-width="60px">
                <el-form-item label="性别">
                    <el-radio-group v-model="form.gender">
                        <el-radio label="Male">男</el-radio><el-radio label="Female">女</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="年龄"><el-input type="number" v-model="form.age"></el-input></el-form-item>
            </el-form>
            <span slot="footer"><el-button type="primary" @click="save">保存</el-button></span>
        </el-dialog>
    </div>
    `,
    data() { return { followId: '', dialogVisible: false, form: { gender: '', age: '' } }; },
    methods: {
        getAuth() { return { authorId : this.user.id, password: this.user.password }; },
        async save() {
            try {
                await API.updateProfile({ auth: this.getAuth(), gender: this.form.gender, age: this.form.age });
                this.$message.success('更新成功');
                this.dialogVisible = false;
                this.$emit('update-user');
            } catch(e) { this.$message.error('失败'); }
        },
        async doFollow() {
            try {
                await API.follow(this.followId, this.getAuth());
                this.$message.success('操作成功');
                this.$emit('update-user');
            } catch(e) { this.$message.error('操作失败'); }
        },
        deleteAcc() {
            this.$confirm('确定注销?', '警告').then(async () => {
                await API.deleteAccount(this.user.id, this.getAuth());
                this.$emit('logout');
            });
        }
    }
});