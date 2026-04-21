// Polyfill for sockjs-client which expects Node.js 'global' to be available
(window as any).global = window;
