Vue.component('recipe-detail', {
    props: ['user'],
    template: `
    <el-dialog :title="info.name" :visible.sync="visible" width="80%" top="5vh">
        <div v-loading="loading" style="padding: 0 20px;">
            <!-- 顶部基本信息 -->
            <el-descriptions border :column="3" size="medium">
                <el-descriptions-item label="作者">{{ info.authorName }}</el-descriptions-item>
                <el-descriptions-item label="分类">
                    <el-tag size="small">{{ info.recipeCategory }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="发布时间">{{ formatDate(info.datePublished) }}</el-descriptions-item>
                
                <el-descriptions-item label="综合评分">
                    <el-rate :value="info.aggregatedRating" disabled show-score text-color="#ff9900"></el-rate>
                </el-descriptions-item>
                <el-descriptions-item label="评论数">{{ info.reviewCount }}</el-descriptions-item>
                <el-descriptions-item label="产出/份量">{{ info.recipeYield || info.recipeServings + ' 人份' }}</el-descriptions-item>

                <el-descriptions-item label="准备时间">{{ formatDuration(info.prepTime) }}</el-descriptions-item>
                <el-descriptions-item label="烹饪时间">{{ formatDuration(info.cookTime) }}</el-descriptions-item>
                <el-descriptions-item label="总时长">{{ formatDuration(info.totalTime) }}</el-descriptions-item>
            </el-descriptions>

            <!-- 描述 -->
            <div style="margin: 15px 0; padding: 10px; background-color: #f8f8f8; border-radius: 4px; font-style: italic; color: #555;">
                {{ info.description }}
            </div>

            <el-row :gutter="20">
                <!-- 左侧：食材列表 -->
                <el-col :span="12">
                    <el-card shadow="never" class="box-card">
                        <div slot="header" class="clearfix">
                            <span style="font-weight: bold">🥕 食材清单</span>
                        </div>
                        <div v-if="info.recipeIngredientParts && info.recipeIngredientParts.length">
                            <el-tag 
                                v-for="(item, index) in info.recipeIngredientParts" 
                                :key="index" 
                                style="margin: 5px;"
                                type="success"
                                effect="plain">
                                {{ item }}
                            </el-tag>
                        </div>
                        <div v-else style="color:#999">暂无食材信息</div>
                    </el-card>
                </el-col>

                <!-- 右侧：营养成分 -->
                <el-col :span="12">
                    <el-card shadow="never" class="box-card">
                        <div slot="header" class="clearfix">
                            <span style="font-weight: bold">🔥 营养成分 (每份)</span>
                        </div>
                        <el-form label-position="left" inline class="demo-table-expand">
                            <el-form-item label="热量:" style="width: 45%; margin-bottom: 0;"><b>{{ info.calories }} kcal</b></el-form-item>
                            <el-form-item label="蛋白质:" style="width: 45%; margin-bottom: 0;">{{ info.proteinContent }} g</el-form-item>
                            <el-form-item label="脂肪:" style="width: 45%; margin-bottom: 0;">{{ info.fatContent }} g</el-form-item>
                            <el-form-item label="碳水:" style="width: 45%; margin-bottom: 0;">{{ info.carbohydrateContent }} g</el-form-item>
                            <el-form-item label="钠:" style="width: 45%; margin-bottom: 0;">{{ info.sodiumContent }} mg</el-form-item>
                            <el-form-item label="糖:" style="width: 45%; margin-bottom: 0;">{{ info.sugarContent }} g</el-form-item>
                        </el-form>
                    </el-card>
                </el-col>
            </el-row>

            <el-divider><i class="el-icon-chat-dot-square"></i> 评论区</el-divider>
            
            <!-- 评论列表 -->
            <div v-if="reviews.length > 0" style="max-height: 400px; overflow-y: auto;">
                <div v-for="r in reviews" :key="r.reviewId" style="margin-bottom:15px; border-bottom:1px solid #eee; padding-bottom:10px;">
                    <div style="display:flex; justify-content:space-between; align-items: center;">
                        <div>
                            <span style="color:#409EFF; font-weight:bold; font-size: 14px;">{{ r.authorName }}</span>
                            <span style="color:#999; font-size: 12px; margin-left: 10px;">{{ formatDate(r.dateSubmitted) }}</span>
                        </div>
                        <div>
                            <el-rate v-model="r.rating" disabled show-score text-color="#ff9900" style="display:inline-block; transform: scale(0.9);"></el-rate>
                            <el-button type="text" size="small" icon="el-icon-thumb" @click="like(r)">
                                {{ r.likes ? r.likes.length : 0 }} 点赞
                            </el-button>
                        </div>
                    </div>
                    <div style="margin-top: 5px; color: #333;">{{ r.review }}</div>
                </div>
            </div>
            <div v-else style="text-align: center; color: #999; margin: 20px 0;">暂无评论，快来抢沙发！</div>

            <!-- 发表评论 -->
            <div style="margin-top:20px; background: #fafafa; padding: 15px; border-radius: 5px;">
                <el-input type="textarea" :rows="2" v-model="myReview.review" placeholder="分享你的看法..."></el-input>
                <div style="margin-top:10px; display:flex; justify-content:space-between; align-items: center;">
                    <el-rate v-model="myReview.rating" show-text texts="['极差', '失望', '一般', '满意', '惊喜']"></el-rate>
                    <el-button type="primary" size="small" @click="postReview">提交评论</el-button>
                </div>
            </div>
        </div>
    </el-dialog>
    `,
    data() {
        return {
            visible: false,
            loading: false,
            info: {},
            reviews: [],
            // 修正：后端接收字段为 review 而非 content
            myReview: { review: '', rating: 5 }
        };
    },
    methods: {
        // 格式化 ISO 8601 Duration (PT1H5M -> 1小时5分钟)
        formatDuration(isoDuration) {
            if (!isoDuration) return '未知';
            let match = isoDuration.match(/PT(\d+H)?(\d+M)?/);
            if (!match) return isoDuration;
            let h = match[1] ? match[1].replace('H', '小时 ') : '';
            let m = match[2] ? match[2].replace('M', '分钟') : '';
            return (h + m) || '少于1分钟';
        },
        // 格式化时间戳
        formatDate(timestamp) {
            if (!timestamp) return '';
            const date = new Date(timestamp);
            return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
        },
        async show(id) {
            this.visible = true;
            this.loading = true;
            try {
                // 并行请求：详情 + 评论
                const [resInfo, resReviews] = await Promise.all([
                    API.getRecipeDetail(id),
                    API.getReviews({ recipeId: id, page: 1, size: 50, sort: 'date_desc' })
                ]);
                this.info = resInfo.data;
                this.reviews = resReviews.data.data || [];
            } catch (e) {
                console.error(e);
                this.$message.error('数据加载失败');
            } finally {
                this.loading = false;
            }
        },
        async postReview() {
            if (!this.myReview.review.trim()) return this.$message.warning("请输入评论内容");
            try {
                await API.addReview(this.info.recipeId, {
                    auth: { authorId: this.user.authorId, password: this.user.password },
                    review: this.myReview.review,
                    rating: this.myReview.rating
                });
                this.$message.success('评论成功');
                this.myReview.review = ''; // 清空
                this.show(this.info.recipeId); // 刷新数据
            } catch(e) {
                this.$message.error('评论失败，请检查登录状态');
            }
        },
        async like(review) {
            try {
                await API.likeReview(review.reviewId, { authorId: this.user.authorId, password: this.user.password });
                this.show(this.info.recipeId); // 刷新以更新点赞数
            } catch(e) {
                this.$message.error('点赞失败');
            }
        }
    }
});