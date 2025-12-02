(window as any).global = window;

(window as any).process = require('process/browser');

(window as any).global.Buffer =
    (window as any).global.Buffer || require('buffer').Buffer;

(window as any).global.util =
    (window as any).global.util || require('util').util;

