Vue.component('recipe-feed', {
    props: ['user'],
    template: `
    <div class="card-box">
        <el-row :gutter="20" style="margin-bottom: 20px;">
            <el-col :span="16">
                <el-input placeholder="搜索食谱关键词..." v-model="keyword" class="input-with-select" @keyup.enter.native="doSearch">
                    <el-button slot="append" icon="el-icon-search" @click="doSearch"></el-button>
                </el-input>
            </el-col>
            <el-col :span="4">
                <el-select v-model="category" placeholder="分类" clearable @change="doSearch">
                    <el-option label="中餐" value="Chinese"></el-option>
                    <el-option label="西餐" value="Western"></el-option>
                </el-select>
            </el-col>
            <el-col :span="4" style="text-align: right;">
                <el-button type="primary" icon="el-icon-plus" @click="showDialog = true">发布</el-button>
            </el-col>
        </el-row>

        <div v-loading="loading">
            <div v-if="list.length === 0" style="text-align: center; padding: 40px; color: #999;">暂无数据</div>
            <div v-for="item in list" :key="item.recipeId" class="recipe-item">
                <div style="display:flex; justify-content:space-between">
                    <div>
                        <h3 style="margin:0 0 5px 0; color:#409EFF; cursor:pointer" @click="openDetail(item.recipeId)">{{ item.name }}</h3>
                        <span style="font-size:12px; color:#888; margin-right:10px;">作者: {{ item.authorName }}</span>
                        <el-tag size="mini" type="success" v-if="item.aggregatedRating">{{ item.aggregatedRating }}分</el-tag>
                    </div>
                    <el-button size="mini" @click="openDetail(item.recipeId)">查看</el-button>
                </div>
                <div style="color:#666; font-size:14px; margin-top:5px;">{{ item.description }}</div>
            </div>
        </div>

        <el-pagination background layout="prev, pager, next" :total="total" :page-size="10" 
            :current-page.sync="page" @current-change="handlePageChange" style="text-align:center; margin-top:20px;">
        </el-pagination>

        <!-- 发布弹窗 -->
        <el-dialog title="发布食谱" :visible.sync="showDialog">
            <el-form :model="form" label-width="80px">
                <el-form-item label="名称"><el-input v-model="form.name"></el-input></el-form-item>
                <el-form-item label="分类"><el-input v-model="form.recipeCategory"></el-input></el-form-item>
                <el-form-item label="描述"><el-input type="textarea" v-model="form.description"></el-input></el-form-item>
            </el-form>
            <span slot="footer"><el-button type="primary" @click="createRecipe">发布</el-button></span>
        </el-dialog>

        <!-- 详情弹窗组件 -->
        <recipe-detail ref="detailRef" :user="user"></recipe-detail>
    </div>
    `,
    data() {
        return {
            list: [], total: 0, page: 1, loading: false,
            keyword: '', category: '', isSearch: false,
            showDialog: false,
            form: { name: '', recipeCategory: '', description: '' }
        };
    },
    mounted() { this.loadFeed(); },
    methods: {
        async loadFeed() {
            this.loading = true;
            this.isSearch = false;
            try {
                const res = await API.getFeed(this.getAuth(), this.page, 10);
                this.list = res.data.data || [];
                this.total = res.data.total;
            } finally { this.loading = false; }
        },
        async doSearch() {
            this.loading = true;
            this.isSearch = true;
            try {
                const res = await API.searchRecipes({ keyword: this.keyword, category: this.category, page: this.page, size: 10 });
                this.list = res.data.data || [];
                this.total = res.data.total;
            } finally { this.loading = false; }
        },
        handlePageChange() { this.isSearch ? this.doSearch() : this.loadFeed(); },
        async createRecipe() {
            try {
                await API.createRecipe({ auth: this.getAuth(), dto: this.form });
                this.$message.success('发布成功');
                this.showDialog = false;
                this.loadFeed();
            } catch(e) { this.$message.error('发布失败'); }
        },
        openDetail(id) { this.$refs.detailRef.show(id); },
        getAuth() { return { mid: this.user.id, password: this.user.password }; }
    }
});