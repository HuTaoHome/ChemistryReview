/* Data adapters for the Android edition. Original website UI and attribution are retained. */
(function (root) {
  'use strict';
  const clone = value => JSON.parse(JSON.stringify(value));

  function createLocalProvider(snapshot, matches) {
    const full = new Map(snapshot.cards.map(card => [card.id, card]));
    const review = new Map(snapshot.reviewCards.map(card => [card.id, card]));
    function get(map, id) {
      const card = map.get(id);
      if (!card) throw new Error('本地题库没有这张卡，请切换到网站数据。');
      return card;
    }
    return {
      async index() { return clone(snapshot.index); },
      async cards(ids) { return ids.map(id => clone(get(review, id))); },
      async card(id) { return clone(get(full, id)); },
      async check(id, input = {}) {
        const card = get(full, id);
        const answers = card.answers.map((answer, index) => [answer, ...(card.accept?.[index] || [])]);
        let ok = null;
        if (typeof input.pick === 'string') ok = [answers[0].includes(input.pick)];
        else if (Array.isArray(input.inputs)) {
          ok = answers.map((values, index) => matches(input.inputs[index] || '', values[0], values.slice(1)));
        }
        return { ok, answers: clone(answers), note: card.note || null };
      }
    };
  }

  function createProvider(website, matches) {
    if (new URLSearchParams(root.location.search).get('dataSource') === 'local') {
      let pending;
      function local() {
        return pending || (pending = fetch('/offline/data.json', { cache: 'no-store' })
          .then(response => { if (!response.ok) throw new Error('本地题库读取失败'); return response.json(); })
          .then(snapshot => createLocalProvider(snapshot, matches))
          .catch(error => { pending = null; throw error; }));
      }
      return Object.fromEntries(['index', 'cards', 'card', 'check'].map(name => [name,
        async (...args) => (await local())[name](...args)]));
    }
    async function request(path, body) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 20000);
      try {
        const response = await fetch('/api/' + path, {
          method: body ? 'POST' : 'GET', cache: 'no-store', signal: controller.signal,
          headers: body ? { 'content-type': 'application/json' } : undefined,
          body: body ? JSON.stringify(body) : undefined
        });
        const value = await response.json();
        if (!response.ok) throw new Error(value.error || '网站数据读取失败');
        return value;
      } catch (error) {
        throw new Error((error.name === 'AbortError' ? '网站连接超时' : '无法读取网站数据') + '，可在顶部切换到本地数据继续复习。');
      } finally { clearTimeout(timer); }
    }
    return {
      index: () => request('index'),
      cards: async ids => (await request('cards', { ids })).cards,
      card: async id => (await request('card', { id })).card,
      check: (id, input = {}) => request('check', { id, ...input })
    };
  }

  root.ChemData = { createProvider, createLocalProvider };
  // The original UI exports JSON through detached blob links; hand these to Android's save picker.
  if (root.NativeFiles && root.HTMLAnchorElement) {
    const originalClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      if (this.download && this.href.startsWith('blob:')) {
        const filename = this.download;
        fetch(this.href).then(response => response.text()).then(text => root.NativeFiles.saveJson(filename, text))
          .catch(() => root.alert('导出失败，请重试'));
      } else originalClick.call(this);
    };
  }
  if (typeof module !== 'undefined') module.exports = root.ChemData;
})(globalThis);
