Vue.component('recipe-detail', {
    props: ['user'],
    template: `
    <el-dialog :title="info.name" :visible.sync="visible" width="70%">
        <div v-loading="loading">
            <el-descriptions border :column="2">
                <el-descriptions-item label="分类">{{ info.recipeCategory }}</el-descriptions-item>
                <el-descriptions-item label="发布时间">{{ info.datePublished }}</el-descriptions-item>
                <el-descriptions-item label="评分">{{ info.aggregatedRating || '暂无' }}</el-descriptions-item>
                <el-descriptions-item label="评论数">{{ info.reviewCount }}</el-descriptions-item>
            </el-descriptions>
            <p style="background:#f4f4f5; padding:10px; border-radius:4px;">{{ info.description }}</p>

            <el-divider>评论</el-divider>
            <div v-for="r in reviews" :key="r.reviewId" style="margin-bottom:15px; border-bottom:1px dashed #eee; padding-bottom:10px;">
                <div style="display:flex; justify-content:space-between">
                    <span style="color:#409EFF; font-weight:bold">{{ r.authorName }}</span>
                    <span>
                        <el-rate v-model="r.rating" disabled show-score style="display:inline-block"></el-rate>
                        <el-button type="text" icon="el-icon-thumb" @click="like(r)">{{ r.likes ? r.likes.length : 0 }}</el-button>
                    </span>
                </div>
                <div>{{ r.review }}</div>
            </div>

            <div style="margin-top:20px;">
                <el-input type="textarea" v-model="myReview.content" placeholder="写评论..."></el-input>
                <div style="margin-top:10px; display:flex; justify-content:space-between;">
                    <el-rate v-model="myReview.rating" show-text></el-rate>
                    <el-button type="primary" size="small" @click="postReview">提交</el-button>
                </div>
            </div>
        </div>
    </el-dialog>
    `,
    data() {
        return { visible: false, loading: false, info: {}, reviews: [], myReview: { content: '', rating: 5 } };
    },
    methods: {
        async show(id) {
            this.visible = true;
            this.loading = true;
            try {
                const [resInfo, resReviews] = await Promise.all([
                    API.getRecipeDetail(id),
                    API.getReviews({ recipeId: id, page: 1, size: 50, sort: 'date_desc' })
                ]);
                this.info = resInfo.data;
                this.reviews = resReviews.data.data;
            } finally { this.loading = false; }
        },
        async postReview() {
            try {
                await API.addReview(this.info.recipeId, {
                    auth: { mid: this.user.id, password: this.user.password },
                    content: this.myReview.content,
                    rating: this.myReview.rating
                });
                this.$message.success('评论成功');
                this.show(this.info.recipeId); // 刷新
            } catch(e) { this.$message.error('评论失败'); }
        },
        async like(review) {
            try {
                await API.likeReview(review.reviewId, { mid: this.user.id, password: this.user.password });
                this.show(this.info.recipeId);
            } catch(e) { this.$message.error('操作失败'); }
        }
    }
});