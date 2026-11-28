const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '../app/src/main/assets/web');
const output = path.resolve(__dirname, '../.build/verification');
fs.mkdirSync(output, { recursive: true });
const snapshot = JSON.parse(fs.readFileSync(path.join(root, 'offline/data.json'), 'utf8').replace(/^\uFEFF/, ''));
const { createLocalProvider } = require(path.join(root, 'offline/provider.js'));
const mainPath = fs.readFileSync(path.join(root, 'index.html'), 'utf8').match(/src="(\/assets\/[^\"]+\.js)"/)[1];
const main = fs.readFileSync(path.join(root, mainPath), 'utf8');
const normalizeCode = main.slice(main.indexOf('const Qm='), main.indexOf('function jd('));
const matches = vm.runInNewContext(normalizeCode + ';Zm');

(async () => {
  const provider = createLocalProvider(snapshot, matches);
  assert.equal(new Set(snapshot.cards.map(c => c.id)).size, 219);
  assert.equal(snapshot.index.cards.length, 219);
  assert.equal(snapshot.reviewCards.length, 219);
  for (const card of snapshot.cards) {
    assert.equal(card.answers.length, (card.prompt.match(/_{2,}/g) || []).length, card.id);
    assert((await provider.check(card.id, { inputs: card.answers })).ok.every(Boolean), card.id);
    assert((await provider.check(card.id, { inputs: card.answers.map(() => '') })).ok.every(v => !v), card.id);
    assert((await provider.check(card.id, { inputs: card.answers.map((answer, i) => card.accept?.[i]?.[0] || answer) })).ok.every(Boolean), card.id);
    assert.equal((await provider.check(card.id)).ok, null);
    assert.equal((await provider.card(card.id)).id, card.id);
    assert.equal((await provider.cards([card.id]))[0].id, card.id);
    const reviewCard = (await provider.cards([card.id]))[0];
    if (reviewCard.options?.length) {
      const accepted = [card.answers[0], ...(card.accept?.[0] || [])];
      const correct = reviewCard.options.find(option => accepted.includes(option));
      assert.notEqual(correct, undefined, 'No correct option: ' + card.id);
      assert.deepEqual((await provider.check(card.id, { pick: correct })).ok, [true]);
      const wrong = reviewCard.options.find(option => !accepted.includes(option));
      if (wrong !== undefined) assert.deepEqual((await provider.check(card.id, { pick: wrong })).ok, [false]);
    }
  }
  await assert.rejects(provider.card('missing'));
  assert(matches(' Ｈ₂Ｏ ', 'H2O', []));
  console.log('PASS: all 219 local cards, answers, accepted alternatives, empty answers, and missing IDs.');
  if (process.argv.includes('--unit-only')) return;

  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 760 }, deviceScaleFactor: 1,
      ...(process.env.CHEM_TEST_PROXY ? { proxy: { server: process.env.CHEM_TEST_PROXY } } : {}) });
    const errors = [], apiRequests = [];
    let local = true, networkUnavailable = false;
    await context.addInitScript(() => { window.NativeFiles = { saveJson: (filename, json) => { window.savedExport = { filename, json }; } }; });
    await context.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.hostname !== 'chem-review-hh57.pages.dev') return route.abort();
      if (url.pathname.startsWith('/api/')) {
        apiRequests.push(url.pathname);
        if (local) throw new Error('Local mode attempted network API: ' + url.pathname);
        if (networkUnavailable) return route.abort();
        try { return await route.fulfill({ response: await route.fetch({ timeout: 25000 }) }); }
        catch { return route.abort(); }
      }
      const file = path.join(root, url.pathname === '/' ? 'index.html' : url.pathname);
      if (!file.startsWith(root) || !fs.existsSync(file)) return route.fulfill({ status: 404, body: '' });
      return route.fulfill({ path: file });
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await context.setOffline(true);
    await page.goto('https://chem-review-hh57.pages.dev/?dataSource=local');
    await page.getByRole('heading', { name: '化学复习', exact: true }).waitFor();
    await page.getByText('Designed By HuTao', { exact: true }).waitFor();
    const navBounds = await page.getByRole('navigation', { name: '主导航' }).boundingBox();
    const creditBounds = await page.getByText('Designed By HuTao', { exact: true }).boundingBox();
    assert(creditBounds.y >= navBounds.y + navBounds.height, 'Designer credit must sit below the floating navigation');
    assert.equal(await page.locator('.watermark-layer, .watermark-bar').count(), 0);
    assert(!/正版|支持原作者|抖音/.test(await page.locator('body').innerText()));
    assert.equal(apiRequests.length, 0);
    await page.screenshot({ path: path.join(output, 'local-home.png'), fullPage: true });
    const progressBeforeBrowsing = await page.evaluate(() => localStorage.getItem('chem-review-v1'));
    for (const part of ['choice', 'process', 'organic', 'principle', 'equilibrium']) {
      await page.goto('https://chem-review-hh57.pages.dev/?dataSource=local#topic/' + part);
      const cardsInPart = snapshot.index.cards.filter(card => card.part.includes(part));
      const buttons = page.locator('.topic-open');
      await buttons.first().waitFor();
      assert.equal(await buttons.count(), cardsInPart.length);
      for (const index of [0, cardsInPart.length - 1]) {
        await buttons.nth(index).click();
        const dialog = page.getByRole('dialog');
        await dialog.locator('.knowledge-full').waitFor();
        const fullCard = snapshot.cards.find(card => card.id === cardsInPart[index].id);
        assert.equal(await dialog.locator('#knowledge-title').innerText(), fullCard.topic);
        assert((await dialog.locator('.knowledge-answers').innerText()).includes(fullCard.answers[0]));
        if (part === 'organic' && index === 0) await page.screenshot({ path: path.join(output, 'knowledge-detail.png') });
        assert.equal(await page.evaluate(() => localStorage.getItem('chem-review-v1')), progressBeforeBrowsing);
        await page.getByRole('button', { name: '关闭知识点详情', exact: true }).click();
        await dialog.waitFor({ state: 'detached' });
        assert(page.url().endsWith('#topic/' + part));
      }
      // Android's system back uses WebView history; it should dismiss the detail first.
      await buttons.first().click();
      await page.getByRole('dialog').locator('.knowledge-full').waitFor();
      await page.goBack();
      await page.getByRole('dialog').waitFor({ state: 'detached' });
    }
    console.log('PASS: knowledge details open without edit mode in all five topics, show answers, preserve progress, and close with back.');
    await page.goto('https://chem-review-hh57.pages.dev/?dataSource=local');
    await page.getByRole('heading', { name: '化学复习', exact: true }).waitFor();
    await page.getByRole('button', { name: '开始复习', exact: true }).click();
    await page.locator('.card-shell').waitFor();
    console.log('Review buttons:', await page.locator('.card-shell button').allTextContents());
    await page.screenshot({ path: path.join(output, 'local-review.png'), fullPage: true });
    const answerButton = page.getByRole('button', { name: /看答案|显示答案|查看答案|翻看答案/ });
    await answerButton.click();
    await page.getByRole('button', { name: '记得', exact: true }).click();
    const progress = await page.evaluate(() => localStorage.getItem('chem-review-v1'));
    assert.equal(Object.keys(JSON.parse(progress).progress).length, 1);
    await page.goto('https://chem-review-hh57.pages.dev/?dataSource=local');
    await page.getByRole('heading', { name: '化学复习', exact: true }).waitFor();
    assert.equal(await page.evaluate(() => localStorage.getItem('chem-review-v1')), progress);
    await page.getByRole('button', { name: '设置', exact: true }).click();
    await page.getByText('数据提供：原作者B站 Lnt1c', { exact: true }).waitFor();
    assert.equal(await page.getByRole('link', { name: 'GitHub · HuTaoHome/ChemistryReview' }).getAttribute('href'), 'https://github.com/HuTaoHome/ChemistryReview');
    assert(!(await page.locator('body').innerText()).includes('多设备同步'));
    await page.screenshot({ path: path.join(output, 'about-hutao.png'), fullPage: true });
    await page.getByRole('button', { name: '导出进度（JSON）', exact: true }).click();
    await page.waitForFunction(() => !!window.savedExport);
    assert.equal(await page.evaluate(() => JSON.parse(window.savedExport.json).history.length), 1);
    console.log('PASS: offline home, card/answer/rating, local reload and progress persistence; no API traffic.');
    if (process.argv.includes('--local-only')) { assert.deepEqual(errors, []); await context.close(); return; }

    local = false;
    await context.setOffline(false);
    await page.goto('https://chem-review-hh57.pages.dev/?dataSource=website');
    await page.getByRole('heading', { name: '化学复习', exact: true }).waitFor({ timeout: 30000 });
    assert.equal(await page.evaluate(() => localStorage.getItem('chem-review-v1')), progress);
    assert(apiRequests.includes('/api/index'));
    await page.getByRole('button', { name: '开始复习', exact: true }).click();
    await page.locator('.card-shell').waitFor({ timeout: 30000 });
    await page.getByRole('button', { name: /看答案|显示答案|查看答案|翻看答案/ }).click();
    await page.getByRole('button', { name: '记得', exact: true }).waitFor({ timeout: 30000 });
    assert(apiRequests.includes('/api/cards'));
    assert(apiRequests.includes('/api/check'));
    await page.screenshot({ path: path.join(output, 'website-review.png'), fullPage: true });
    console.log('PASS: live website index/cards/check APIs and progress shared with local mode.');
    networkUnavailable = true;
    await page.goto('https://chem-review-hh57.pages.dev/?dataSource=website');
    await page.getByText('题库没加载出来', { exact: true }).waitFor();
    await page.getByText(/可在顶部切换到本地数据继续复习/).waitFor();
    local = true;
    await context.setOffline(true);
    await page.goto('https://chem-review-hh57.pages.dev/?dataSource=local');
    await page.getByRole('heading', { name: '化学复习', exact: true }).waitFor();
    assert.equal(await page.evaluate(() => localStorage.getItem('chem-review-v1')), progress);
    console.log('PASS: JSON export, website network failure, explicit local recovery and preserved progress.');
    assert.deepEqual(errors, []);
    await context.close();
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
