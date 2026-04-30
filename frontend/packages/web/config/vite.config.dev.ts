import baseConfig from './vite.config.base';
import { config } from 'dotenv';
import { mergeConfig } from 'vite';
import eslint from 'vite-plugin-eslint';

// 注入本地/开发配置环境变量(先导入的配置优先级高)
config({ path: ['.env.development.local', '.env.development'] });

export default mergeConfig(
  {
    mode: 'development',
    server: {
      // 从环境变量读取端口
      port: 5173,
      // 从环境变量读取 host
      host: '0.0.0.0',
      open: true,
      fs: {
        strict: true,
      },
      proxy: {
        '/sse': {
          target: process.env.VITE_DEV_DOMAIN,
          changeOrigin: true,
        },
        '/front': {
          target: process.env.VITE_DEV_DOMAIN,
          changeOrigin: true,
          rewrite: (path: string) => path.replace(/^\/front/, ''),
        },
        '/pic': {
          target: process.env.VITE_DEV_DOMAIN,
          changeOrigin: true,
          rewrite: (path: string) => path.replace(/^\/pic/, ''),
        },
        '/attachment': {
          target: process.env.VITE_DEV_DOMAIN,
          changeOrigin: true,
          rewrite: (path: string) => path.replace(/^\/attachment/, ''),
        },
        '/ui': {
          target: process.env.VITE_DEV_DOMAIN,
          changeOrigin: true,
          rewrite: (path: string) => path.replace(/^\/ui/, ''),
        },
      },
    },
    plugins: [
      eslint({
        overrideConfigFile: 'eslint.config.cjs',
        cache: false,
        include: ['src/**/*.ts', 'src/**/*.tsx', 'src/**/*.vue'],
        exclude: ['node_modules'],
      }),
    ],
  },
  baseConfig
);
