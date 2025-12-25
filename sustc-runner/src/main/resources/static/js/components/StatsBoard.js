Vue.component('stats-board', {
    template: `
    <div>
        <div class="card-box">
            <div class="section-title">🏆 粉丝比例最高用户</div>
            <div v-if="influencer">
                <h2 style="color:#E6A23C; margin:0">{{ influencer.AuthorName }}</h2>
                <p>ID: {{ influencer.AuthorId }} | Ratio: {{ influencer.Ratio.toFixed(2) }}</p>
            </div>
            <el-button size="mini" @click="loadInfluencer">刷新</el-button>
        </div>
        <div class="card-box">
            <div class="section-title">🥗 食材最复杂食谱 Top3</div>
            <el-table :data="complexList" stripe>
                <el-table-column prop="Name" label="食谱"></el-table-column>
                <el-table-column prop="IngredientCount" label="食材数"></el-table-column>
            </el-table>
            <el-button size="mini" @click="loadComplex" style="margin-top:10px">刷新</el-button>
        </div>
    </div>
    `,
    data() { return { influencer: null, complexList: [] }; },
    mounted() { this.loadInfluencer(); this.loadComplex(); },
    methods: {
        async loadInfluencer() { const res = await API.getTopInfluencer(); this.influencer = res.data; },
        async loadComplex() { const res = await API.getComplexRecipes(); this.complexList = res.data; }
    }
});