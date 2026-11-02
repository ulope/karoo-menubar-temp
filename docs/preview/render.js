// Renders the preview images in docs/ from screen.html and icon.html with headless Chromium.
//
// The fonts are not checked in. Download these from Google Fonts into docs/preview/fonts/:
//   RobotoCondensed-wght.ttf          (ofl/robotocondensed/RobotoCondensed[wght].ttf, renamed)
//   IBMPlexSansCondensed-Regular.ttf  (ofl/ibmplexsanscondensed/)
//   IBMPlexSansCondensed-Medium.ttf   (ofl/ibmplexsanscondensed/)
// then run, with Playwright installed:
//   node docs/preview/render.js
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const dir = __dirname;
  const out = path.join(dir, '..');
  const browser = await chromium.launch();

  // The ride screen at the Karoo 3's 480x800, rendered at 2x for sharpness.
  const page = await browser.newPage({ viewport: { width: 480, height: 800 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.join(dir, 'screen.html'));
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(out, 'screenshot.png') });
  await page.screenshot({ path: path.join(out, 'topbar.png'), clip: { x: 0, y: 0, width: 480, height: 58 } });

  // App icon for the Karoo's extension info page.
  const icon = await browser.newPage({ viewport: { width: 192, height: 192 } });
  await icon.goto('file://' + path.join(dir, 'icon.html'));
  await icon.screenshot({ path: path.join(out, 'icon.png'), omitBackground: true });

  await browser.close();
})();
