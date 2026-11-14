import test from 'node:test';
import { runFrontendQuality } from './frontend-quality.mjs';

test('UI1/UI2/UI3: axe prüft Basisansichten, Fehler, Dialoge und Mieterkonto; Streaming bleibt sicher',
    { timeout: 180000 }, async () => {
        await runFrontendQuality();
    });
