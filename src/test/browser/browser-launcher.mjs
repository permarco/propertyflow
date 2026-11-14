import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium, firefox, webkit } = require('playwright');
export const browserName = process.env.DEMO_BROWSER || 'msedge';

export function launchBrowser() {
    switch (browserName) {
        case 'msedge': return chromium.launch({ channel: 'msedge', headless: true });
        case 'chromium': return chromium.launch({ headless: true });
        case 'firefox': return firefox.launch({ headless: true });
        case 'webkit': return webkit.launch({ headless: true });
        default: throw new Error(`Unsupported DEMO_BROWSER: ${browserName}`);
    }
}
