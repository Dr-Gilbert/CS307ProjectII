// 全局 Axios 配置
axios.defaults.baseURL = 'http://localhost:8080/api';

// 简单封装一些常用请求，方便组件调用
const API = {
    login: (mid, password) => axios.post('/users/login', { mid, password }),
    register: (form) => axios.post('/users/register', form),
    getUserInfo: (id) => axios.get(`/users/${id}`),

    // Feed & Recipe
    getFeed: (auth, page, size) => axios.post('/users/feed', { auth, page, size, category: null }),
    searchRecipes: (params) => axios.get('/recipes/search', { params }),
    createRecipe: (data) => axios.post('/recipes', data),
    getRecipeDetail: (id) => axios.get(`/recipes/${id}`),

    // Review
    getReviews: (params) => axios.get('/reviews/list', { params }), // 注意这里对应刚才补充的接口
    addReview: (recipeId, data) => axios.post(`/recipes/${recipeId}/reviews`, data),
    likeReview: (reviewId, auth) => axios.post(`/reviews/${reviewId}/like`, auth),

    // User Actions
    updateProfile: (data) => axios.put('/users/profile', data),
    deleteAccount: (id, auth) => axios.delete(`/users/${id}`, { data: auth }),
    follow: (id, auth) => axios.post(`/users/${id}/follow`, auth),

    // Stats
    getTopInfluencer: () => axios.get('/users/highest-ratio'),
    getComplexRecipes: () => axios.get('/recipes/most-complex')
};