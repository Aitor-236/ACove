import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '@/router';

/*
 * 自动保存这类后台请求失败时不该弹 toast（页面自己显示「保存失败 · 重试」），
 * 调用方传 `{ silent: true }`，拦截器里读这个开关跳过 ElMessage。
 * 用模块扩展声明，避免调用处到处写 as any。
 */
declare module 'axios' {
    interface AxiosRequestConfig {
        silent?: boolean;
    }
}

const request = axios.create({
    baseURL: '/api',
    timeout: 5000,
});

// Add token to request headers if it exists
request.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Handle responses and errors
request.interceptors.response.use(
    response => {
        const res = response.data;
        // If the response code is not 200, show an error message
        if (res.code !== 200) {
            if (!response.config.silent) {
                ElMessage.error(res.message || 'Request failed');
            }
            return Promise.reject(new Error(res.message));
        }
        return res;
    },
    error => {
        // If the error response code is 401, redirect to login page
        if (error.response?.status === 401) {
            localStorage.removeItem('token');
            router.push('/login');
            ElMessage.error('Session expired, please log in again');
        } else if (!error.config?.silent) {
            ElMessage.error(error.message || 'Network error');
        }
        return Promise.reject(error);
    }
);

export default request;
