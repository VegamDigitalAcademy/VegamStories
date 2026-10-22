// Vegam Stories admin page. Plain JavaScript, no build step.
// Talks only to this site's own /api (the Worker holds all keys).

// Cover and thumbnail are cropped to an exact size. Pages are finished comic images, so they are
// never cropped: only scaled down to at most 1080 px wide, keeping their shape.
const SIZES = {
  cover: { w: 480, h: 640 },
  thumb: { w: 960, h: 540 },
  page: { maxWidth: 1080 },
};

const state = {
  me: null,
  categories: [],
  languages: [], // [{ code, name, native_name, sort_order }] — story text languages, from the server
  stories: [],
  current: null, // story being edited (see emptyStory())
  previewPage: 0,
  dirty: false,
  busy: false,
};

const $ = (id) => document.getElementById(id);

// -------------------------------------------------------------------------------------------
// API
// -------------------------------------------------------------------------------------------

async function api(method, path, body, contentType) {
  const init = { method, headers: {} };
  if (body instanceof Blob) {
    init.body = body;
    init.headers['content-type'] = contentType || body.type;
  } else if (body !== undefined) {
    init.body = JSON.stringify(body);
    init.headers['content-type'] = 'application/json';
  }
  const res = await fetch(`/api/${path}`, init);
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { error: text };
  }
  if (res.status === 401) throw new Error('Your login expired. Reload the page to sign in again.');
  if (!res.ok) throw new Error((data && data.error) || `Request failed (${res.status})`);
  return data;
}

// -------------------------------------------------------------------------------------------
// Startup
// -------------------------------------------------------------------------------------------

async function init() {
  bindStaticEvents();
  try {
    state.me = await api('GET', 'me');
    $('whoami').textContent = state.me.email;
    await Promise.all([loadCategories(), loadLanguages(), loadStories()]);
  } catch (err) {
    $('storyList').innerHTML = '';
    $('storyList').append(el('p', { class: 'muted small pad' }, err.message));
    toast(err.message, true);
  }
}

async function loadCategories() {
  state.categories = await api('GET', 'categories');
  renderCategoryOptions();
}

async function loadLanguages() {
  state.languages = await api('GET', 'languages');
  if (state.current) renderVersionTabs();
}

async function loadStories() {
  state.stories = await api('GET', 'stories');
  renderStoryList();
}

// -------------------------------------------------------------------------------------------
// Story list
// -------------------------------------------------------------------------------------------

/** e.g. "Comic · Text TE EN · Video", from the list row (page count, text languages, video link). */
/** e.g. "TE · EN · OR": the story's language versions, from the list row. */
function formatsLabel(s) {
  const languages = (s.story_translations || [])
    .map((t) => t.language)
    .sort((a, b) => languageOrder(a) - languageOrder(b))
    .map((code) => code.toUpperCase());
  return languages.join(' · ') || 'No language versions';
}

function storyTitle(s) {
  return firstByLanguage(s.story_translations, 'title') || s.id;
}

function storyCover(s) {
  return firstByLanguage(s.story_translations, 'cover_url');
}

function renderStoryList() {
  const list = $('storyList');
  const query = $('searchInput').value.trim().toLowerCase();
  list.innerHTML = '';
  const matches = state.stories.filter(
    (s) =>
      !query ||
      s.id.includes(query) ||
      (s.story_translations || []).some((t) => (t.title || '').toLowerCase().includes(query)),
  );
  if (!matches.length) {
    list.append(el('p', { class: 'muted small pad' }, state.stories.length ? 'No matches.' : 'No stories yet.'));
    return;
  }
  const byCategory = new Map();
  matches.forEach((s) => {
    if (!byCategory.has(s.category_id)) byCategory.set(s.category_id, []);
    byCategory.get(s.category_id).push(s);
  });
  for (const [categoryId, stories] of byCategory) {
    const cat = state.categories.find((c) => c.id === categoryId);
    list.append(el('div', { class: 'group-title te' }, cat ? categoryName(cat) : categoryId));
    stories.forEach((s) => {
      const item = el(
        'div',
        { class: `story-item${state.current && state.current.id === s.id && !state.current.isNew ? ' active' : ''}` },
        storyCover(s) ? el('img', { src: storyCover(s), alt: '', loading: 'lazy' }) : el('div', { class: 'noimg' }),
        el(
          'div',
          { class: 'meta' },
          el('div', { class: 'title ellipsis' }, storyTitle(s)),
          el('div', { class: 'muted small' }, `Ep ${s.episode} · ${s.id} · ♥ ${s.like_count ?? 0}`),
          el('div', { class: 'muted small formats', title: 'Language versions' }, formatsLabel(s)),
        ),
        el('span', { class: `badge ${s.is_published ? 'live' : 'draft'}` }, s.is_published ? 'LIVE' : 'DRAFT'),
      );
      item.addEventListener('click', () => openStory(s.id));
      list.append(item);
    });
  }
}

// -------------------------------------------------------------------------------------------
// Editor state
// -------------------------------------------------------------------------------------------

const emptyImage = () => ({ url: null, blob: null, preview: null });

function emptyVersion(language) {
  return {
    language,
    title: '',
    summary: '',
    cover: emptyImage(),
    thumb: emptyImage(),
    video_url: '',
    video_title: '',
    video_description: '',
    body: '',
    pages: [], // [{ image: {url, blob, preview} }]
  };
}

function emptyStory() {
  const firstCategory = state.categories[0]?.id || '';
  const nextEpisode =
    Math.max(0, ...state.stories.filter((s) => s.category_id === firstCategory).map((s) => s.episode)) + 1;
  const firstLanguage = state.languages[0]?.code || 'te';
  return {
    isNew: true,
    idEdited: false,
    id: firstCategory ? `${firstCategory}-${nextEpisode}` : '',
    category_id: firstCategory,
    episode: nextEpisode,
    published_at: toLocalInput(new Date()),
    is_published: false,
    // One version per language; a new story starts with the first (default) language.
    versions: [emptyVersion(firstLanguage)],
    active: firstLanguage,
  };
}

function fromServer(row) {
  const img = (url) => ({ url: url || null, blob: null, preview: null });
  const versions = (row.story_translations || [])
    .map((t) => ({
      language: t.language,
      title: t.title || '',
      summary: t.summary || '',
      cover: img(t.cover_url),
      thumb: img(t.video_thumbnail_url),
      video_url: t.video_url || '',
      video_title: t.video_title || '',
      video_description: t.video_description || '',
      body: t.body || '',
      pages: (row.story_pages || [])
        .filter((p) => p.language === t.language)
        .sort((a, b) => a.page_index - b.page_index)
        .map((p) => ({ image: img(p.image_url) })),
    }))
    .sort((a, b) => languageOrder(a.language) - languageOrder(b.language));
  return {
    isNew: false,
    idEdited: true,
    id: row.id,
    category_id: row.category_id,
    episode: row.episode,
    published_at: row.published_at ? toLocalInput(new Date(row.published_at)) : '',
    is_published: !!row.is_published,
    versions,
    active: versions[0]?.language || state.languages[0]?.code || 'te',
  };
}

/** Position of a language in the server's order (unknown languages last). */
function languageOrder(code) {
  const i = state.languages.findIndex((l) => l.code === code);
  return i < 0 ? Infinity : i;
}

/** The language version being edited. */
function activeVersion() {
  const s = state.current;
  return s.versions.find((v) => v.language === s.active) || s.versions[0];
}

/** A name in the first language that has one, in the server's order: for story titles and category names in lists. */
function firstByLanguage(items, key) {
  const sorted = [...(items || [])].sort((a, b) => languageOrder(a.language) - languageOrder(b.language));
  return sorted[0]?.[key] || '';
}

function categoryName(category) {
  return firstByLanguage(category?.category_translations, 'name') || category?.id || '';
}

function newStory() {
  if (!confirmDiscard()) return;
  if (!state.categories.length) {
    toast('Add a category first (the + next to Category).', true);
  }
  state.current = emptyStory();
  state.previewPage = 0;
  setDirty(false);
  renderEditor();
  renderStoryList();
}

async function openStory(id) {
  if (state.current && !state.current.isNew && state.current.id === id) return;
  if (!confirmDiscard()) return;
  try {
    setStatus('Loading…');
    const row = await api('GET', `stories/${id}`);
    state.current = fromServer(row);
    state.previewPage = 0;
    setDirty(false);
    renderEditor();
    renderStoryList();
    setStatus('');
  } catch (err) {
    toast(err.message, true);
    setStatus('');
  }
}

function confirmDiscard() {
  return !state.dirty || confirm('You have unsaved changes. Discard them?');
}

function setDirty(value) {
  state.dirty = value;
  setStatus(value ? 'Unsaved changes' : '');
}

// -------------------------------------------------------------------------------------------
// Editor rendering
// -------------------------------------------------------------------------------------------

function renderEditor() {
  const s = state.current;
  $('emptyState').classList.add('hidden');
  $('storyForm').classList.remove('hidden');
  $('actionBar').classList.remove('hidden');
  $('previewPane').classList.remove('hidden');
  $('deleteBtn').classList.toggle('hidden', s.isNew);

  $('categoryInput').value = s.category_id;
  $('episodeInput').value = s.episode;
  $('idInput').value = s.id;
  $('idInput').disabled = !s.isNew;
  $('publishedAtInput').value = s.published_at;
  $('publishedInput').checked = s.is_published;
  updatePublishedLabel();
  renderVersion();
}

/** Tabs + every per-language card, for the selected language version. */
function renderVersion() {
  const s = state.current;
  const v = activeVersion();
  $('editorTitle').textContent = s.isNew ? 'New story' : v?.title || s.id;
  renderVersionTabs();
  if (!v) return;

  const label = `— ${languageLabel(v.language)}`;
  ['versionLabel1', 'versionLabel2', 'versionLabel3', 'versionLabel4'].forEach((id) => ($(id).textContent = label));
  for (const id of ['titleInput', 'summaryInput', 'videoTitleInput', 'videoDescInput', 'bodyInput']) {
    $(id).setAttribute('lang', v.language);
    $(id).classList.toggle('te', v.language === 'te');
  }
  $('titleInput').value = v.title;
  $('summaryInput').value = v.summary;
  $('videoUrlInput').value = v.video_url;
  $('videoTitleInput').value = v.video_title;
  $('videoDescInput').value = v.video_description;
  $('bodyInput').value = v.body;
  $('bodyCount').textContent = textCount(v.body);

  renderDrop($('coverDrop'), v.cover, 'cover', 'Drop cover image\nor click');
  renderDrop($('thumbDrop'), v.thumb, 'thumb', 'Drop video thumbnail\nor click');
  renderPages();
  renderPreview();
}

function renderVersionTabs() {
  const s = state.current;
  const tabs = $('versionTabs');
  tabs.innerHTML = '';
  s.versions.forEach((v) => {
    const l = state.languages.find((x) => x.code === v.language);
    const tab = el(
      'button',
      { type: 'button', role: 'tab', class: `lang-tab${v.language === s.active ? ' active' : ''}`, 'aria-selected': String(v.language === s.active) },
      l ? l.native_name : v.language,
      el('span', { class: 'muted' }, v.language.toUpperCase()),
    );
    tab.addEventListener('click', () => switchVersion(v.language));
    tabs.append(tab);
  });

  const select = $('addVersionLanguage');
  select.innerHTML = '';
  const unused = state.languages.filter((l) => !s.versions.some((v) => v.language === l.code));
  unused.forEach((l) => select.append(el('option', { value: l.code }, `${l.name} · ${l.native_name}`)));
  if (!unused.length) select.append(el('option', { value: '' }, state.languages.length ? 'All languages added' : 'No languages yet'));
  select.disabled = !unused.length;
  $('addVersionBtn').disabled = !unused.length;
  $('removeVersionBtn').disabled = s.versions.length < 2;
}

function switchVersion(language) {
  readVersion();
  state.current.active = language;
  state.previewPage = 0;
  renderVersion();
}

function addVersion() {
  const code = $('addVersionLanguage').value;
  if (!code) return;
  readVersion();
  state.current.versions.push(emptyVersion(code));
  state.current.versions.sort((a, b) => languageOrder(a.language) - languageOrder(b.language));
  state.current.active = code;
  state.previewPage = 0;
  setDirty(true);
  renderVersion();
  $('titleInput').focus();
}

function removeVersion() {
  const s = state.current;
  const v = activeVersion();
  if (!v || s.versions.length < 2) return;
  if (!confirm(`Remove the ${languageLabel(v.language)} version of this story (title, cover, video, text and pages)?\n\nIt's deleted when you click Save.`)) return;
  s.versions = s.versions.filter((x) => x !== v);
  s.active = s.versions[0].language;
  state.previewPage = 0;
  setDirty(true);
  renderVersion();
}

function renderCategoryOptions() {
  const select = $('categoryInput');
  const current = select.value;
  select.innerHTML = '';
  state.categories.forEach((c) => select.append(el('option', { value: c.id }, `${categoryName(c)} (${c.id})`)));
  if (current) select.value = current;
}

function updatePublishedLabel() {
  $('publishedLabel').textContent = $('publishedInput').checked ? 'Published (visible in the app)' : 'Draft';
}

/**
 * An image slot: shows the current image, accepts a drop or click, prepares the file.
 * Replaces [container] with a fresh copy each time (so old click/drop handlers don't pile up)
 * and returns the new element.
 */
function renderDrop(container, image, kind, emptyText) {
  const node = container.cloneNode(false);
  container.replaceWith(node); // no-op if container isn't in the page yet
  const src = image.preview || image.url;
  node.classList.toggle('filled', !!src);
  if (src) {
    node.append(el('img', { src, alt: '' }));
    if (image.blob) node.append(el('span', { class: 'new-dot' }, 'NEW'));
  } else {
    node.append(el('span', { style: 'white-space:pre-line' }, emptyText));
  }
  bindDrop(node, false, async ([file]) => {
    try {
      await setImage(image, file, SIZES[kind]);
      setDirty(true);
      renderDrop(node, image, kind, emptyText);
      renderPreview();
    } catch (err) {
      toast(err.message, true);
    }
  });
  return node;
}

function renderPages() {
  const wrap = $('pagesContainer');
  wrap.innerHTML = '';
  const pages = activeVersion().pages;
  pages.forEach((page, pi) => {
    const drop = renderDrop(el('div', { class: 'drop page-drop' }), page.image, 'page', 'Drop page image\nor click');
    const card = el(
      'div',
      { class: `page-card${pi === state.previewPage ? ' selected' : ''}` },
      drop,
      el(
        'div',
        { class: 'page-card-foot' },
        el('b', {}, `Page ${pi + 1}`),
        el(
          'div',
          { class: 'row' },
          iconButton('←', 'Move page earlier', pi > 0, () => movePage(pi, -1)),
          iconButton('→', 'Move page later', pi < pages.length - 1, () => movePage(pi, 1)),
          iconButton('✕', 'Delete page', true, () => deletePage(pi)),
        ),
      ),
    );
    // Clicking the card footer selects the page for the preview; clicking the image replaces it.
    card.querySelector('.page-card-foot b').addEventListener('click', () => {
      state.previewPage = pi;
      renderPages();
      renderPreview();
    });
    wrap.append(card);
  });

  // "+ Add pages": one page per image, in file-name order (01.png, 02.png … or p0.webp, p1.webp …).
  const add = el('div', { class: 'drop add-pages' }, el('span', { style: 'white-space:pre-line' }, '+ Add pages\n(drop one or many\npage images)'));
  bindDrop(add, true, async (files) => {
    const sorted = [...files].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
    setStatus(`Preparing ${sorted.length} image(s)…`);
    try {
      for (const file of sorted) {
        const image = emptyImage();
        await setImage(image, file, SIZES.page);
        pages.push({ image });
      }
      state.previewPage = pages.length - 1;
      setDirty(true);
      renderPages();
      renderPreview();
    } catch (err) {
      toast(err.message, true);
      setStatus('Unsaved changes');
    }
  });
  wrap.append(add);
}

function movePage(index, delta) {
  const pages = activeVersion().pages;
  [pages[index], pages[index + delta]] = [pages[index + delta], pages[index]];
  state.previewPage = index + delta;
  setDirty(true);
  renderPages();
  renderPreview();
}

function deletePage(index) {
  if (!confirm(`Delete page ${index + 1}?`)) return;
  const pages = activeVersion().pages;
  pages.splice(index, 1);
  state.previewPage = Math.max(0, Math.min(state.previewPage, pages.length - 1));
  setDirty(true);
  renderPages();
  renderPreview();
}

// -------------------------------------------------------------------------------------------
// App preview (same layout rules as the Android ComicPage)
// -------------------------------------------------------------------------------------------

function renderPreview() {
  const s = state.current;
  if (!s) return;
  const pages = activeVersion()?.pages || [];
  state.previewPage = Math.max(0, Math.min(state.previewPage, pages.length - 1));
  $('previewTitle').textContent = $('titleInput').value || 'Story title';
  $('previewPageLabel').textContent = pages.length ? `${state.previewPage + 1} / ${pages.length}` : '0 / 0';
  $('prevPageBtn').disabled = state.previewPage <= 0;
  $('nextPageBtn').disabled = state.previewPage >= pages.length - 1;

  const comic = $('previewPage');
  comic.innerHTML = '';
  const page = pages[state.previewPage];
  const src = page && (page.image.preview || page.image.url);
  if (src) comic.append(el('img', { src, alt: '' }));
  else comic.append(el('p', { class: 'muted small pad' }, 'No pages yet. Add page images on the left.'));

  const old = document.querySelector('.end-badge');
  if (old) old.remove();
  if (page && state.previewPage === pages.length - 1) comic.after(el('span', { class: 'end-badge' }, 'THE END'));
}

// -------------------------------------------------------------------------------------------
// Images: resize + crop + WebP in the browser
// -------------------------------------------------------------------------------------------

async function setImage(image, file, size) {
  if (!file || !file.type.startsWith('image/')) throw new Error('Please choose an image file');
  const blob = size.maxWidth ? await scaleImage(file, size.maxWidth) : await prepareImage(file, size.w, size.h);
  if (image.preview) URL.revokeObjectURL(image.preview);
  image.blob = blob;
  image.preview = URL.createObjectURL(blob);
}

/** Keeps the whole image (no crop), only shrinking it to at most [maxWidth] px wide. */
async function scaleImage(file, maxWidth) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxWidth / bitmap.width);
  const w = Math.round(bitmap.width * scale);
  const h = Math.round(bitmap.height * scale);
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap, 0, 0, w, h);
  bitmap.close();
  return encode(canvas);
}

/** Center-crops to exactly w×h and encodes WebP (JPEG if the browser can't make WebP). */
async function prepareImage(file, w, h) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.max(w / bitmap.width, h / bitmap.height);
  const sw = w / scale;
  const sh = h / scale;
  const sx = (bitmap.width - sw) / 2;
  const sy = (bitmap.height - sh) / 2;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap, sx, sy, sw, sh, 0, 0, w, h);
  bitmap.close();
  return encode(canvas);
}

async function encode(canvas) {
  let blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/webp', 0.85));
  if (!blob || blob.type !== 'image/webp') {
    blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85));
  }
  if (!blob) throw new Error('Could not process this image');
  return blob;
}

// -------------------------------------------------------------------------------------------
// Save / publish / delete
// -------------------------------------------------------------------------------------------

function readForm() {
  const s = state.current;
  s.category_id = $('categoryInput').value;
  s.episode = Number($('episodeInput').value);
  if (s.isNew) s.id = $('idInput').value.trim();
  s.published_at = $('publishedAtInput').value;
  s.is_published = $('publishedInput').checked;
  readVersion();
}

/** Copies the per-language fields from the page into the selected version. */
function readVersion() {
  const v = state.current && activeVersion();
  if (!v) return;
  v.title = $('titleInput').value;
  v.summary = $('summaryInput').value;
  v.video_url = $('videoUrlInput').value.trim();
  v.video_title = $('videoTitleInput').value;
  v.video_description = $('videoDescInput').value;
  v.body = $('bodyInput').value;
}

/** Paragraphs are separated by a blank line, as the app splits them. */
function paragraphs(text) {
  return text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
}

function textCount(text) {
  const paras = paragraphs(text);
  if (!paras.length) return '(empty: no text version)';
  const words = text.trim().split(/\s+/).length;
  return `(${paras.length} paragraph${paras.length === 1 ? '' : 's'}, ${words} words)`;
}

function languageLabel(code) {
  const l = state.languages.find((x) => x.code === code);
  return l ? `${l.name} · ${l.native_name} (${code})` : `${code} (not in the language list)`;
}

/** One box per text, then "Add text in…" listing the languages this story has no text in yet. */




function addLanguage() {
  $('newLangCode').value = '';
  $('newLangName').value = '';
  $('newLangNative').value = '';
  $('newLangOrder').value = state.languages.length;
  $('languageDialog').showModal();
}

async function onLanguageDialogClose() {
  if ($('languageDialog').returnValue !== 'ok') return;
  try {
    const language = {
      code: $('newLangCode').value.trim().toLowerCase(),
      name: $('newLangName').value.trim(),
      native_name: $('newLangNative').value.trim(),
      sort_order: Number($('newLangOrder').value) || 0,
    };
    await api('POST', 'languages', language);
    await loadLanguages();
    renderCategoryOptions();
    if (state.current) {
      renderVersionTabs();
      $('addVersionLanguage').value = language.code;
    }
    toast(`Language “${language.name}” added. Add it to stories (and category names) to show it in the app.`);
  } catch (err) {
    toast(err.message, true);
  }
}

function validate(s) {
  if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(s.id)) return 'Story id: lowercase letters, numbers and dashes, e.g. moral-12';
  if (s.isNew && state.stories.some((x) => x.id === s.id)) return `A story with id “${s.id}” already exists`;
  if (!s.category_id) return 'Choose a category';
  if (!Number.isInteger(s.episode) || s.episode < 1) return 'Episode must be 1 or more';
  if (!s.versions.length) return 'Add at least one language version';
  for (const v of s.versions) {
    const where = `${languageLabel(v.language)}:`;
    if (!v.title.trim()) return `${where} add a title`;
    if (!v.cover.url && !v.cover.blob) return `${where} add a cover image`;
    if (v.video_url && !/^https:\/\/(www\.|m\.)?(youtube\.com|youtu\.be)\//.test(v.video_url)) return `${where} the video link must be a YouTube link`;
    if (v.video_url && !v.thumb.url && !v.thumb.blob) return `${where} add a video thumbnail (or remove the video link)`;
    if (!v.pages.length && !v.body.trim() && !v.video_url) {
      return `${where} add at least one format: page images, story text or a YouTube video`;
    }
    for (const [pi, page] of v.pages.entries()) {
      if (!page.image.url && !page.image.blob) return `${where} page ${pi + 1} has no image`;
    }
  }
  return null;
}

async function save() {
  if (state.busy) return;
  readForm();
  const s = state.current;
  const problem = validate(s);
  if (problem) {
    // Show the version with the problem.
    const bad = s.versions.find((v) => problem.startsWith(`${languageLabel(v.language)}:`));
    if (bad && bad.language !== s.active) switchVersion(bad.language);
    return toast(problem, true);
  }

  setBusy(true);
  try {
    // 1) Upload new images. Each upload gets a fresh name, so phones never show a cached old picture.
    // Each language's images go in their own folder: <story-id>/<language>/…
    const uploads = [];
    for (const v of s.versions) {
      if (v.cover.blob) uploads.push([v.cover, `${v.language}/cover`]);
      if (v.video_url && v.thumb.blob) uploads.push([v.thumb, `${v.language}/video-thumb`]);
      v.pages.forEach((p, pi) => p.image.blob && uploads.push([p.image, `${v.language}/page-${pi}`]));
    }
    let done = 0;
    for (const [image, name] of uploads) {
      setStatus(`Uploading images ${++done}/${uploads.length}…`);
      const ext = image.blob.type === 'image/webp' ? 'webp' : 'jpg';
      const key = `${s.id}/${name}-${randomSuffix()}.${ext}`;
      const result = await api('PUT', `upload/${key}`, image.blob);
      image.url = result.url;
      image.blob = null;
    }

    // 2) Save the story and its pages to Supabase (the Worker also removes replaced images from R2).
    setStatus('Saving story…');
    const payload = {
      story: {
        category_id: s.category_id,
        episode: s.episode,
        published_at: s.published_at ? new Date(s.published_at).toISOString() : undefined,
        is_published: s.is_published,
      },
      translations: s.versions.map((v) => ({
        language: v.language,
        title: v.title,
        summary: v.summary,
        cover_url: v.cover.url,
        body: v.body,
        video_url: v.video_url,
        video_title: v.video_title,
        video_description: v.video_description,
        video_thumbnail_url: v.video_url ? v.thumb.url : null,
        pages: v.pages.map((p) => ({ image_url: p.image.url })),
      })),
    };
    const result = await api('PUT', `stories/${s.id}`, payload);
    s.isNew = false;
    setDirty(false);
    await loadStories();
    renderEditor();
    toast(
      `Saved “${activeVersion().title}” (${s.versions.map((v) => v.language.toUpperCase()).join(', ')})${s.is_published ? ' — live in the app' : ' as draft'}` +
        (result.imagesRemoved ? ` · ${result.imagesRemoved} old image(s) removed` : ''),
    );
  } catch (err) {
    toast(err.message, true);
    setStatus('Not saved — fix the problem and try again');
  } finally {
    setBusy(false);
  }
}

async function deleteStory() {
  const s = state.current;
  if (!s || s.isNew) return;
  if (!confirm(`Delete “${activeVersion()?.title || s.id}” (${s.id}) in every language?\n\nIt disappears from the app and its images are removed from R2. This can't be undone.`)) return;
  setBusy(true);
  try {
    await api('DELETE', `stories/${s.id}`);
    toast(`Deleted ${s.id}`);
    state.current = null;
    setDirty(false);
    $('storyForm').classList.add('hidden');
    $('actionBar').classList.add('hidden');
    $('previewPane').classList.add('hidden');
    $('emptyState').classList.remove('hidden');
    await loadStories();
  } catch (err) {
    toast(err.message, true);
  } finally {
    setBusy(false);
  }
}

/** New category, or (with [category]) edit an existing one's names and order. */
function addCategory(category = null) {
  const editing = !!category;
  state.editingCategory = category;
  $('categoryDialogTitle').textContent = editing ? `Edit category “${category.id}”` : 'New category';
  $('categoryDialogOk').textContent = editing ? 'Save' : 'Add';
  $('newCatId').value = editing ? category.id : '';
  $('newCatId').disabled = editing;
  $('newCatOrder').value = editing ? category.sort_order : state.categories.length;
  const names = $('newCatNames');
  names.innerHTML = '';
  state.languages.forEach((l) => {
    const current = (category?.category_translations || []).find((t) => t.language === l.code)?.name || '';
    const input = el('input', { class: `input${l.code === 'te' ? ' te' : ''}`, lang: l.code, 'data-language': l.code, value: current });
    names.append(el('label', { class: 'field' }, el('span', {}, `Name in ${l.name} · ${l.native_name}`), input));
  });
  names.append(el('p', { class: 'muted small' }, 'Fill in the languages you have; the app shows the name in the reader\'s language.'));
  $('categoryDialog').showModal();
}

function editCategory() {
  const category = state.categories.find((c) => c.id === $('categoryInput').value);
  if (category) addCategory(category);
}

async function onCategoryDialogClose() {
  if ($('categoryDialog').returnValue !== 'ok') return;
  try {
    const names = {};
    $('newCatNames').querySelectorAll('input[data-language]').forEach((input) => {
      if (input.value.trim()) names[input.dataset.language] = input.value.trim();
    });
    const category = {
      id: state.editingCategory ? state.editingCategory.id : $('newCatId').value.trim(),
      sort_order: Number($('newCatOrder').value) || 0,
      names,
    };
    await api('POST', 'categories', category);
    await loadCategories();
    $('categoryInput').value = category.id;
    if (!state.editingCategory) onCategoryOrEpisodeChange();
    renderStoryList();
    toast(`Category “${Object.values(names)[0] || category.id}” ${state.editingCategory ? 'saved' : 'added'}`);
  } catch (err) {
    toast(err.message, true);
  }
}

// -------------------------------------------------------------------------------------------
// Events
// -------------------------------------------------------------------------------------------

function bindStaticEvents() {
  $('newStoryBtn').addEventListener('click', newStory);
  $('saveBtn').addEventListener('click', save);
  $('deleteBtn').addEventListener('click', deleteStory);
  $('addCategoryBtn').addEventListener('click', () => addCategory());
  $('editCategoryBtn').addEventListener('click', editCategory);
  $('categoryDialog').addEventListener('close', onCategoryDialogClose);
  $('addVersionBtn').addEventListener('click', addVersion);
  $('removeVersionBtn').addEventListener('click', removeVersion);
  $('addLanguageBtn').addEventListener('click', addLanguage);
  $('languageDialog').addEventListener('close', onLanguageDialogClose);
  $('searchInput').addEventListener('input', renderStoryList);
  $('prevPageBtn').addEventListener('click', () => {
    state.previewPage--;
    renderPages();
    renderPreview();
  });
  $('nextPageBtn').addEventListener('click', () => {
    state.previewPage++;
    renderPages();
    renderPreview();
  });
  $('publishedInput').addEventListener('change', () => {
    updatePublishedLabel();
    setDirty(true);
  });
  $('categoryInput').addEventListener('change', onCategoryOrEpisodeChange);
  $('episodeInput').addEventListener('input', onCategoryOrEpisodeChange);
  $('idInput').addEventListener('input', () => {
    state.current.idEdited = true;
    setDirty(true);
  });
  ['titleInput', 'summaryInput', 'videoUrlInput', 'videoTitleInput', 'videoDescInput', 'publishedAtInput', 'bodyInput'].forEach((id) =>
    $(id).addEventListener('input', () => {
      setDirty(true);
      if (id === 'titleInput') renderPreview();
      if (id === 'bodyInput') $('bodyCount').textContent = textCount($('bodyInput').value);
    }),
  );
  $('storyForm').addEventListener('submit', (e) => e.preventDefault());
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 's' && state.current) {
      e.preventDefault();
      save();
    }
  });
  window.addEventListener('beforeunload', (e) => {
    if (state.dirty) e.preventDefault();
  });
}

/** For new stories, keep the id as "<category>-<episode>" until the user types their own. */
function onCategoryOrEpisodeChange() {
  const s = state.current;
  if (!s) return;
  setDirty(true);
  if (s.isNew && !s.idEdited) {
    $('idInput').value = `${$('categoryInput').value}-${$('episodeInput').value}`;
  }
}

function bindDrop(target, multiple, onFiles) {
  const input = el('input', { type: 'file', accept: 'image/*', style: 'display:none' });
  if (multiple) input.multiple = true;
  target.append(input);
  target.addEventListener('click', (e) => {
    if (e.target !== input) input.click();
  });
  input.addEventListener('change', () => {
    if (input.files.length) onFiles([...input.files]);
    input.value = '';
  });
  target.addEventListener('dragover', (e) => {
    e.preventDefault();
    target.classList.add('over');
  });
  target.addEventListener('dragleave', () => target.classList.remove('over'));
  target.addEventListener('drop', (e) => {
    e.preventDefault();
    target.classList.remove('over');
    const files = [...e.dataTransfer.files].filter((f) => f.type.startsWith('image/'));
    if (files.length) onFiles(multiple ? files : files.slice(0, 1));
  });
}

// -------------------------------------------------------------------------------------------
// Small helpers
// -------------------------------------------------------------------------------------------

/** Creates an element. Text children are inserted as text (never as HTML). */
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k === 'value') node.value = v;
    else node.setAttribute(k, v);
  }
  for (const child of children) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  return node;
}

function iconButton(label, title, enabled, onClick) {
  const b = el('button', { type: 'button', class: 'icon-btn', title }, label);
  b.disabled = !enabled;
  if (!enabled) b.style.visibility = 'hidden';
  b.addEventListener('click', (e) => {
    e.stopPropagation();
    onClick();
  });
  return b;
}

function setStatus(text) {
  $('statusText').textContent = text;
}

function setBusy(busy) {
  state.busy = busy;
  $('saveBtn').disabled = busy;
  $('deleteBtn').disabled = busy;
  $('saveBtn').textContent = busy ? 'Saving…' : 'Save';
}

let toastTimer;
function toast(message, isError = false) {
  const t = $('toast');
  t.textContent = message;
  t.classList.toggle('error', isError);
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), isError ? 6000 : 3500);
}

function randomSuffix() {
  return Math.random().toString(36).slice(2, 8);
}

function toLocalInput(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

init();
