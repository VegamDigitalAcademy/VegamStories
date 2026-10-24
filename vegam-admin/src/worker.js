/**
 * Vegam Stories admin — Cloudflare Worker.
 *
 * Serves the admin page (from /public) and a small JSON API under /api/*:
 *   - images go straight into the R2 bucket (binding MEDIA, no keys needed)
 *   - story rows go into Supabase with the service-role key (a Worker secret, never sent to the browser)
 * Every /api request must carry a valid Cloudflare Access login for an allowed email.
 */

const JSON_HEADERS = { 'content-type': 'application/json; charset=utf-8' };

// Story ids look like "moral-12"; image keys like "moral-12/or/page-0-k3x9qa.webp" (a folder per
// language) or, for images saved before languages, "moral-12/page-0-k3x9qa.webp".
const ID_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const KEY_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*\/(?:[a-z]{2,3}\/)?[a-z0-9]+(?:-[a-z0-9]+)*\.(webp|jpg|png)$/;
const YOUTUBE_PATTERN = /^https:\/\/(www\.|m\.)?(youtube\.com|youtu\.be)\//;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const LANGUAGE_CODE_PATTERN = /^[a-z]{2,3}$/;
const MAX_TEXT_CHARS = 50000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (!url.pathname.startsWith('/api/')) {
      // Static admin page. Cloudflare Access already blocks strangers before this point.
      return env.ASSETS.fetch(request);
    }

    const email = await authenticate(request, env, url);
    if (!email) return json({ error: 'Not signed in or not allowed' }, 401);

    try {
      return await route(request, env, url, email);
    } catch (err) {
      const status = err instanceof HttpError ? err.status : 500;
      console.error(`API error on ${request.method} ${url.pathname}:`, err);
      return json({ error: err.message || 'Something went wrong' }, status);
    }
  },
};

// ---------------------------------------------------------------------------------------------
// Routing
// ---------------------------------------------------------------------------------------------

async function route(request, env, url, email) {
  const { method } = request;
  const parts = url.pathname.replace(/^\/api\//, '').split('/').filter(Boolean);

  // GET /api/me
  if (method === 'GET' && parts[0] === 'me') {
    return json({ email, mediaBaseUrl: env.MEDIA_BASE_URL });
  }

  const db = supabase(env);

  // /api/categories
  if (parts[0] === 'categories' && parts.length === 1) {
    if (method === 'GET') {
      return json(
        await db.get('categories?select=id,sort_order,category_translations(language,name)&order=sort_order.asc'),
      );
    }
    // Body: { id, sort_order, names: { te: "నీతి కథలు", en: "Moral stories", … } } — creates or edits.
    if (method === 'POST') {
      const body = await readJson(request);
      const category = {
        id: requireId(body.id, 'Category id'),
        sort_order: Number.isInteger(body.sort_order) ? body.sort_order : 0,
      };
      const knownLanguages = await languageCodes(db);
      const names = Object.entries(body.names || {})
        .map(([language, name]) => ({ category_id: category.id, language, name: typeof name === 'string' ? name.trim() : '' }))
        .filter((n) => n.name);
      for (const n of names) {
        if (!knownLanguages.has(n.language)) throw new HttpError(400, `Unknown language: ${n.language}`);
      }
      if (!names.length) throw new HttpError(400, 'Give the category a name in at least one language');
      await db.upsert('categories', [category]);
      await db.delete(`category_translations?category_id=eq.${category.id}`);
      await db.insert('category_translations', names);
      return json({ ...category, category_translations: names }, 201);
    }
  }

  // /api/languages   (story text languages: Telugu, English, Odia, …)
  if (parts[0] === 'languages' && parts.length === 1) {
    if (method === 'GET') {
      return json(await db.get('languages?select=code,name,native_name,sort_order&order=sort_order.asc,code.asc'));
    }
    if (method === 'POST') {
      const body = await readJson(request);
      const code = typeof body.code === 'string' ? body.code.trim().toLowerCase() : '';
      if (!LANGUAGE_CODE_PATTERN.test(code)) {
        throw new HttpError(400, 'Language code: 2–3 lowercase letters, the standard ISO code (e.g. or for Odia, hi for Hindi)');
      }
      const language = {
        code,
        name: requireText(body.name, 'Language name'),
        native_name: requireText(body.native_name, 'Name in its own script'),
        sort_order: Number.isInteger(body.sort_order) ? body.sort_order : 0,
      };
      return json(await db.upsert('languages', [language]), 201);
    }
  }

  // /api/stories
  if (parts[0] === 'stories') {
    if (parts.length === 1 && method === 'GET') {
      return json(
        await db.get(
          'stories?select=id,category_id,episode,is_published,published_at,like_count,' +
            'story_translations(language,title,cover_url,video_url)&order=category_id.asc,episode.asc',
        ),
      );
    }

    const id = requireId(parts[1], 'Story id');

    if (parts.length === 2 && method === 'GET') {
      const rows = await db.get(
        `stories?id=eq.${id}&select=*,story_translations(*),story_pages(language,page_index,image_url)` +
          '&story_pages.order=page_index.asc',
      );
      if (!rows.length) throw new HttpError(404, 'Story not found');
      return json(rows[0]);
    }

    if (parts.length === 2 && method === 'PUT') {
      return json(await saveStory(db, env, id, await readJson(request)));
    }

    if (parts.length === 2 && method === 'DELETE') {
      await db.delete(`stories?id=eq.${id}`); // translations and pages go too (ON DELETE CASCADE)
      const removed = await deleteImages(env, `${id}/`, new Set());
      return json({ deleted: id, imagesRemoved: removed });
    }

    if (parts.length === 3 && parts[2] === 'publish' && method === 'POST') {
      const body = await readJson(request);
      const rows = await db.patch(`stories?id=eq.${id}`, { is_published: body.is_published === true });
      if (!rows.length) throw new HttpError(404, 'Story not found');
      return json(rows[0]);
    }
  }

  // PUT /api/upload/<story-id>/<file>.webp   (raw image bytes in the body)
  if (parts[0] === 'upload' && method === 'PUT') {
    const key = parts.slice(1).join('/');
    if (!KEY_PATTERN.test(key)) throw new HttpError(400, `Bad image name: ${key}`);
    const type = request.headers.get('content-type') || '';
    if (!type.startsWith('image/')) throw new HttpError(400, 'Only images can be uploaded');
    const bytes = await request.arrayBuffer();
    if (bytes.byteLength === 0) throw new HttpError(400, 'Empty image');
    if (bytes.byteLength > MAX_IMAGE_BYTES) throw new HttpError(413, 'Image is larger than 5 MB');
    await env.MEDIA.put(key, bytes, {
      httpMetadata: {
        contentType: type,
        // File names change on every upload, so each URL's content never changes → cache for a year.
        cacheControl: 'public, max-age=31536000, immutable',
      },
    });
    return json({ key, url: `${env.MEDIA_BASE_URL}/${key}` }, 201);
  }

  throw new HttpError(404, `No API route for ${method} ${url.pathname}`);
}

// ---------------------------------------------------------------------------------------------
// Saving a story
// ---------------------------------------------------------------------------------------------

/**
 * Body: { story: { category_id, episode, published_at, is_published },   — shared by all languages
 *         translations: [{ language, title, summary, cover_url, body,
 *                          video_url, video_title, video_description, video_thumbnail_url,
 *                          pages: [{ image_url }] }] }                     — one per language
 * Every language version needs a title and at least one format (pages, text or video); the cover is optional.
 * Writes the story row, replaces its translations and pages, then deletes unused images in R2.
 */
async function saveStory(db, env, id, body) {
  const s = body.story || {};
  const story = {
    id,
    category_id: requireId(s.category_id, 'Category'),
    episode: requirePositiveInt(s.episode, 'Episode'),
    is_published: s.is_published === true,
  };
  if (s.published_at) story.published_at = s.published_at;

  const knownLanguages = await languageCodes(db);
  const translations = [];
  const pages = [];
  for (const t of Array.isArray(body.translations) ? body.translations : []) {
    const { translation, pages: translationPages } = readTranslation(t, env, id, knownLanguages);
    if (translations.some((x) => x.language === translation.language)) {
      throw new HttpError(400, `Two versions in ${translation.language}`);
    }
    translations.push(translation);
    pages.push(...translationPages);
  }
  if (!translations.length) throw new HttpError(400, 'Add the story in at least one language');

  const [saved] = await db.upsert('stories', [story]);
  await db.delete(`story_pages?story_id=eq.${id}`);
  await db.delete(`story_translations?story_id=eq.${id}`);
  await db.insert('story_translations', translations);
  if (pages.length) await db.insert('story_pages', pages);

  // Images that were replaced or removed in this edit are no longer referenced → delete them.
  const used = new Set(
    [...translations.flatMap((t) => [t.cover_url, t.video_thumbnail_url]), ...pages.map((p) => p.image_url)]
      .filter(Boolean)
      .map((u) => u.slice(env.MEDIA_BASE_URL.length + 1)),
  );
  const imagesRemoved = await deleteImages(env, `${id}/`, used);

  return { story: saved, languages: translations.map((t) => t.language), pages: pages.length, imagesRemoved };
}

/** One language version → a story_translations row and its story_pages rows. Throws on anything missing. */
function readTranslation(t, env, storyId, knownLanguages) {
  const language = t && t.language;
  if (!knownLanguages.has(language)) throw new HttpError(400, `Unknown language: ${language} (add it first)`);
  const label = `${language.toUpperCase()} version`;
  const optional = (v) => (typeof v === 'string' && v.trim() ? v.trim() : null);

  const body = typeof t.body === 'string' ? t.body.replace(/\r\n?/g, '\n').trim() : '';
  if (body.length > MAX_TEXT_CHARS) throw new HttpError(413, `${label}: the text is longer than ${MAX_TEXT_CHARS} characters`);

  const translation = {
    story_id: storyId,
    language,
    title: requireText(t.title, `${label}: title`),
    summary: typeof t.summary === 'string' ? t.summary.trim() : '',
    // Optional: without one the app shows the first comic page, or a title card.
    cover_url: t.cover_url ? requireMediaUrl(t.cover_url, env, storyId, `${label}: cover image`) : null,
    body: body || null,
    video_url: optional(t.video_url),
    video_title: optional(t.video_title),
    video_description: optional(t.video_description),
    video_thumbnail_url: t.video_thumbnail_url ? requireMediaUrl(t.video_thumbnail_url, env, storyId, `${label}: video thumbnail`) : null,
    updated_at: new Date().toISOString(),
  };
  if (translation.video_url && !YOUTUBE_PATTERN.test(translation.video_url)) {
    throw new HttpError(400, `${label}: the video link must be a YouTube link`);
  }
  if (!translation.video_url) translation.video_thumbnail_url = null;

  const pages = (Array.isArray(t.pages) ? t.pages : []).map((page, pageIndex) => ({
    story_id: storyId,
    language,
    page_index: pageIndex,
    image_url: requireMediaUrl(page && page.image_url, env, storyId, `${label}: page ${pageIndex + 1} image`),
  }));
  if (!pages.length && !translation.body && !translation.video_url) {
    throw new HttpError(400, `${label}: add at least one format (comic pages, story text or a YouTube video)`);
  }
  return { translation, pages };
}

async function languageCodes(db) {
  return new Set((await db.get('languages?select=code')).map((l) => l.code));
}

/** Deletes every object under [prefix] whose key is not in [keep]. Returns how many were removed. */
async function deleteImages(env, prefix, keep) {
  // List everything first, then delete: deleting while paging through a listing can skip objects.
  const stale = [];
  let cursor;
  do {
    const listing = await env.MEDIA.list({ prefix, cursor });
    for (const o of listing.objects) if (!keep.has(o.key)) stale.push(o.key);
    cursor = listing.truncated ? listing.cursor : undefined;
  } while (cursor);
  for (let i = 0; i < stale.length; i += 1000) {
    await env.MEDIA.delete(stale.slice(i, i + 1000)); // R2 deletes up to 1000 keys per call
  }
  return stale.length;
}

// ---------------------------------------------------------------------------------------------
// Supabase (PostgREST) with the service-role key
// ---------------------------------------------------------------------------------------------

function supabase(env) {
  if (!env.SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY) {
    throw new HttpError(500, 'SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY is not configured');
  }
  const key = env.SUPABASE_SERVICE_ROLE_KEY;
  const base = `${env.SUPABASE_URL.replace(/\/$/, '')}/rest/v1/`;

  async function call(method, path, body, prefer) {
    const headers = { apikey: key, 'content-type': 'application/json' };
    // Legacy keys are JWTs ("eyJ…") and also go in Authorization; new "sb_secret_…" keys must not.
    if (key.startsWith('eyJ')) headers.authorization = `Bearer ${key}`;
    if (prefer) headers.prefer = prefer;
    const res = await fetch(base + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
    const text = await res.text();
    if (!res.ok) {
      let message = text;
      try {
        const err = JSON.parse(text);
        message = [err.message, err.details, err.hint].filter(Boolean).join(' — ');
      } catch {}
      throw new HttpError(res.status === 409 ? 409 : 502, `Supabase: ${message || res.status}`);
    }
    return text ? JSON.parse(text) : [];
  }

  return {
    get: (path) => call('GET', path),
    insert: (table, rows) => call('POST', table, rows, 'return=representation'),
    upsert: (table, rows) => call('POST', table, rows, 'resolution=merge-duplicates,return=representation'),
    patch: (path, values) => call('PATCH', path, values, 'return=representation'),
    delete: (path) => call('DELETE', path, undefined, 'return=minimal'),
  };
}

// ---------------------------------------------------------------------------------------------
// Authentication: Cloudflare Access JWT
// ---------------------------------------------------------------------------------------------

let certCache = { team: null, keys: null, fetchedAt: 0 };

/**
 * Returns the signed-in email, or null. Checks the Cf-Access-Jwt-Assertion header that Cloudflare
 * Access adds after login: signature (team public keys), audience, issuer, expiry and email allow-list.
 * Fails closed: if Access isn't configured, nobody gets in.
 */
async function authenticate(request, env, url) {
  // Local testing only: never active on a real domain.
  const isLocal = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if (isLocal && env.DEV_BYPASS_EMAIL) return env.DEV_BYPASS_EMAIL;

  const token = request.headers.get('cf-access-jwt-assertion');
  const team = (env.ACCESS_TEAM_DOMAIN || '').replace(/\/$/, '');
  if (!token || !team || !env.ACCESS_AUD) return null;

  try {
    const [h, p, s] = token.split('.');
    const header = JSON.parse(b64urlToText(h));
    const payload = JSON.parse(b64urlToText(p));

    const keys = await accessKeys(team);
    const jwk = keys.find((k) => k.kid === header.kid);
    if (!jwk || header.alg !== 'RS256') return null;
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, [
      'verify',
    ]);
    const valid = await crypto.subtle.verify(
      'RSASSA-PKCS1-v1_5',
      key,
      b64urlToBytes(s),
      new TextEncoder().encode(`${h}.${p}`),
    );
    if (!valid) return null;

    const now = Math.floor(Date.now() / 1000);
    const audiences = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
    if (!audiences.includes(env.ACCESS_AUD)) return null;
    if (payload.iss !== team) return null;
    if (!payload.exp || payload.exp < now) return null;

    const email = String(payload.email || '').toLowerCase();
    const allowed = String(env.ADMIN_EMAILS || '')
      .split(',')
      .map((e) => e.trim().toLowerCase())
      .filter(Boolean);
    return allowed.includes(email) ? email : null;
  } catch (err) {
    console.error('Access token check failed:', err);
    return null;
  }
}

async function accessKeys(team) {
  const fresh = certCache.team === team && Date.now() - certCache.fetchedAt < 60 * 60 * 1000;
  if (!fresh) {
    const res = await fetch(`${team}/cdn-cgi/access/certs`);
    if (!res.ok) throw new Error(`Could not load Access certs (${res.status})`);
    certCache = { team, keys: (await res.json()).keys || [], fetchedAt: Date.now() };
  }
  return certCache.keys;
}

function b64urlToBytes(value) {
  const b64 = value.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((value.length + 3) % 4);
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

function b64urlToText(value) {
  return new TextDecoder().decode(b64urlToBytes(value));
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    throw new HttpError(400, 'Request body must be JSON');
  }
}

function requireId(value, label) {
  if (typeof value !== 'string' || !ID_PATTERN.test(value)) {
    throw new HttpError(400, `${label} must use lowercase letters, numbers and dashes (e.g. moral-12)`);
  }
  return value;
}

function requireText(value, label) {
  if (typeof value !== 'string' || !value.trim()) throw new HttpError(400, `${label} is required`);
  return value.trim();
}

function requirePositiveInt(value, label) {
  const n = Number(value);
  if (!Number.isInteger(n) || n < 1) throw new HttpError(400, `${label} must be a whole number from 1`);
  return n;
}

/** Only accept image URLs inside this story's own folder on the media domain, e.g. https://media…/moral-12/p0-0-ab12cd.webp */
function requireMediaUrl(value, env, storyId, label) {
  const base = `${env.MEDIA_BASE_URL}/`;
  const ok =
    typeof value === 'string' &&
    value.startsWith(`${base}${storyId}/`) &&
    KEY_PATTERN.test(value.slice(base.length));
  if (!ok) throw new HttpError(400, `${label} is missing`);
  return value;
}
