Vue.component('recipe-feed', {
    props: ['user'],
    template: `
    <div class="card-box">
        <!-- 搜索栏 -->
        <el-row :gutter="20" style="margin-bottom: 20px;">
            <el-col :span="16">
                <el-input placeholder="搜索食谱关键词..." v-model="keyword" class="input-with-select" @keyup.enter.native="doSearch">
                    <el-button slot="append" icon="el-icon-search" @click="doSearch"></el-button>
                </el-input>
            </el-col>
            <el-col :span="4">
                <el-select v-model="category" placeholder="分类" clearable @change="doSearch">
                    <el-option label="Chinese" value="Chinese"></el-option>
                    <el-option label="Western" value="Western"></el-option>
                    <el-option label="Dessert" value="Dessert"></el-option>
                    <el-option label="Breads" value="Breads"></el-option>
                </el-select>
            </el-col>
            <el-col :span="4" style="text-align: right;">
                <el-button type="primary" icon="el-icon-plus" @click="showDialog = true">发布</el-button>
            </el-col>
        </el-row>

        <!-- 列表区域 -->
        <div v-loading="loading" style="min-height: 200px;">
            <!-- 暂无数据提示 -->
            <div v-if="!list || list.length === 0" style="text-align: center; padding: 40px; color: #999;">
                <div>暂无数据</div>
            </div>

            <!-- 食谱列表项 -->
            <div v-for="item in list" :key="item.recipeId" class="recipe-item" style="border-bottom: 1px solid #eee; padding: 15px 0;">
                <div style="display:flex; justify-content:space-between; align-items: flex-start;">
                    <div>
                        <!-- 标题 -->
                        <h3 style="margin:0 0 8px 0; color:#409EFF; cursor:pointer" @click="openDetail(item.recipeId)">
                            {{ item.name }}
                        </h3>
                        
                        <!-- 基础信息行 -->
                        <div style="font-size:12px; color:#888; margin-bottom: 8px;">
                            <span style="margin-right:15px;"><i class="el-icon-user"></i> {{ item.authorName }}</span>
                            <span style="margin-right:15px;"><i class="el-icon-time"></i> {{ formatDate(item.datePublished) }}</span>
                            <span v-if="item.recipeCategory" style="margin-right:15px; background: #f0f2f5; padding: 2px 6px; border-radius: 4px;">{{ item.recipeCategory }}</span>
                        </div>

                        <!-- 评分与数据 -->
                        <div style="display: flex; align-items: center;">
                            <el-rate 
                                :value="item.aggregatedRating || 0" 
                                disabled 
                                show-score 
                                text-color="#ff9900"
                                score-template="{value}"
                                style="display:inline-block; margin-right: 15px;">
                            </el-rate>
                            <span style="font-size: 12px; color: #666;">
                                <i class="el-icon-chat-dot-square"></i> {{ item.reviewCount }} 评论
                            </span>
                        </div>
                    </div>
                    <el-button size="small" type="primary" plain @click="openDetail(item.recipeId)">查看详情</el-button>
                </div>
                <!-- 描述 -->
                <div style="color:#666; font-size:14px; margin-top:10px; line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden;">
                    {{ item.description || '暂无描述' }}
                </div>
            </div>
        </div>

        <!-- 分页 -->
        <el-pagination 
            background 
            layout="prev, pager, next" 
            :total="total" 
            :page-size="10" 
            :current-page.sync="page" 
            @current-change="handlePageChange" 
            style="text-align:center; margin-top:20px;"
            v-if="total > 0">
        </el-pagination>

        <!-- 发布对话框 -->
        <el-dialog title="发布食谱" :visible.sync="showDialog">
            <el-form :model="form" label-width="80px">
                <el-form-item label="名称"><el-input v-model="form.name"></el-input></el-form-item>
                <el-form-item label="分类"><el-input v-model="form.recipeCategory"></el-input></el-form-item>
                <el-form-item label="描述"><el-input type="textarea" v-model="form.description"></el-input></el-form-item>
            </el-form>
            <span slot="footer"><el-button type="primary" @click="createRecipe">发布</el-button></span>
        </el-dialog>

        <!-- 详情组件引用 -->
        <recipe-detail ref="detailRef" :user="user"></recipe-detail>
    </div>
    `,
    data() {
        return {
            list: [],
            total: 0,
            page: 1,
            loading: false,
            keyword: '',
            category: '',
            isSearch: false,
            showDialog: false,
            form: { name: '', recipeCategory: '', description: '' }
        };
    },
    mounted() {
        // 如果是从搜索框过来（例如有默认keyword），直接搜索，否则加载Feed
        if (this.keyword) {
            this.doSearch();
        } else {
            // 这里我们默认加载搜索列表（因为 Feed 需要关注关系，刚注册用户可能没关注任何人导致 Feed 为空）
            // 或者你可以保留 loadFeed() 逻辑
            this.doSearch();
        }
    },
    methods: {
        formatDate(val) {
            if (!val) return '';
            return new Date(val).toLocaleDateString();
        },
        getAuth() {
            return { authorId: this.user.authorId, password: this.user.password };
        },
        // 处理数据加载的通用逻辑
        handleResponse(res) {
            // 核心修复点：根据你提供的 JSON，数据直接在 res.data.items
            // res.data 结构: { items: [...], total: 336, page: 2, size: 10 }
            const data = res.data;
            if (data && data.items) {
                this.list = data.items;
                this.total = data.total;
            } else {
                this.list = [];
                this.total = 0;
            }
        },
        async loadFeed() {
            this.loading = true;
            this.isSearch = false;
            try {
                // 如果是 Feed 接口，请确认它的返回结构是否和 Search 一致
                // 如果 Feed 返回结构不同，这里可能需要单独处理
                const res = await API.getFeed(this.getAuth(), this.page, 10);
                this.handleResponse(res);
            } catch(e) {
                console.error(e);
                this.list = [];
            } finally {
                this.loading = false;
            }
        },
        async doSearch() {
            this.loading = true;
            this.isSearch = true;
            try {
                // 调用 Search API
                const res = await API.searchRecipes({
                    keyword: this.keyword,
                    category: this.category,
                    page: this.page,
                    size: 10
                });
                this.handleResponse(res);
            } catch(e) {
                console.error(e);
                this.$message.error('搜索失败');
            } finally {
                this.loading = false;
            }
        },
        handlePageChange(newPage) {
            this.page = newPage;
            // 保持当前模式（搜索模式或Feed模式）
            if (this.isSearch) {
                this.doSearch();
            } else {
                this.loadFeed();
            }
            // 回到顶部
            window.scrollTo(0, 0);
        },
        async createRecipe() {
            try {
                await API.createRecipe({ auth: this.getAuth(), dto: this.form });
                this.$message.success('发布成功');
                this.showDialog = false;
                this.doSearch(); // 刷新列表
            } catch(e) {
                this.$message.error('发布失败');
            }
        },
        openDetail(id) {
            this.$refs.detailRef.show(id);
        }
    }
});