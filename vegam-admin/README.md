# Vegam Stories Admin

A private website at **https://admin.vegamstories.com** for adding and editing stories.
Fill in the story, drop in the images, click **Save**:

- images are cropped, resized and converted to WebP in your browser, then stored in the R2 bucket
- the story text and image links are saved in Supabase
- switch **Published** on and the story appears in the app on its next refresh

```
Your browser ──▶ admin.vegamstories.com (Cloudflare Worker) ──▶ R2 bucket  (images)
 (this page)      checks your login, holds the secret key     └─▶ Supabase   (story rows)
```

Only `admin@vegamstories.com` can get in (Cloudflare Access, email code). The Supabase
secret key lives only inside Cloudflare, never in the page.

---

## One-time setup (about 15 minutes)

You need **Node.js** on your Mac. Check with `node -v` in Terminal; if it's missing, install the
LTS version from https://nodejs.org.

### 1. Install the Cloudflare tool and sign in

```bash
cd "/Users/ajay/AJAY/Android Studio/Vegam/StoriesApp/vegam-admin"
npm install
npx wrangler login        # opens the browser, click "Allow"
```

### 2. Check the bucket name

Open `wrangler.jsonc` and make sure `bucket_name` matches your R2 bucket exactly
(it's set to `vegam-stories`). Change it if yours is named differently.

### 3. Add the Supabase secret key

In Supabase: **Project Settings → API Keys**. Copy the **`service_role`** key (legacy, starts
with `eyJ`) or a **secret** key (starts with `sb_secret_`). Then:

```bash
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
# paste the key when asked, press Enter
```

This key can change anything in your database. Never put it in the Android app, in
`wrangler.jsonc`, or in a chat.

### 4. Lock the site to your email (Cloudflare Access)

1. Cloudflare dashboard → **Zero Trust**. The first time, pick a team name (for example
   `vegam`) and the **Free** plan.
2. **Access → Applications → Add an application → Self-hosted**.
3. Application name `Vegam Admin`, domain **`admin.vegamstories.com`**.
4. Add a policy: name `Only me`, action **Allow**, include **Emails** → `admin@vegamstories.com`.
5. Login methods: keep **One-time PIN** on. Save.
6. Open the application again → **Overview** (or Basic information) → copy the
   **Application Audience (AUD) tag**.
7. In `wrangler.jsonc` fill in:
   - `ACCESS_TEAM_DOMAIN` → `https://<your-team-name>.cloudflareaccess.com`
   - `ACCESS_AUD` → the AUD tag you copied

If these two aren't set correctly, the site refuses every request. It fails closed on purpose.

### 5. Publish the site

```bash
npx wrangler deploy
```

This uploads the site and creates `admin.vegamstories.com` for you (DNS and HTTPS included).

### 6. Open it

Go to **https://admin.vegamstories.com**. Enter your email, type the code Cloudflare emails
you, and the admin page opens. The login lasts 24 hours by default.

---

## Using it

- **New story:** pick the category, the episode fills in, and the story id becomes
  `moral-12` style automatically (you can type your own before the first save).
- **Panels:** drop several images on **+ Add panels** to make one panel per image, ordered by file
  name (`01.png`, `02.png`, … or the `p0-0.webp`, `p0-1.webp` files from the YouTube-to-comic prompt).
- **Captions / speech bubbles:** one bubble per line. The preview on the right shows exactly
  how the page will look in the app.
- **Story text:** the story as plain text, in any number of languages (the app's Read tab).
  Pick a language under the text boxes and click **Add text**; leave a blank line between
  paragraphs. A story needs at least one format: page images, text or a YouTube video. The
  story list shows each story's formats, e.g. `Comic · Text TE EN OR · Video`.
- **+ New language:** adds a language for story texts, like a new category: the standard code
  (`or` Odia, `hi` Hindi, `ta` Tamil, `kn` Kannada), its English name, its name in its own script
  (shown in the app, e.g. ଓଡ଼ିଆ) and its order (the first is the app's default). The app picks up
  new languages on its next refresh; no app update needed.
- **Save** (or ⌘S) stores everything. Replaced or removed images are deleted from R2 automatically.
- **Published** switch: off = draft (not in the app), on = live. Remember to click Save after switching it.
- **Delete story** removes it from Supabase and deletes its images from R2.

Any image format works (PNG, JPG, WebP, phone photos). Each is cropped from the center to:
cover 480×640, panels 800×600, video thumbnail 960×540.

## Updating the site later

Before deploying a version that saves story text, run `supabase/migrations/004_story_texts.sql`
and then `005_languages.sql` once in Supabase → SQL Editor (they create the `story_texts` and
`languages` tables).

Edit files in `public/` (page) or `src/worker.js` (server part), then run `npx wrangler deploy` again.

## Testing on your Mac (optional)

```bash
cp .dev.vars.example .dev.vars    # then paste your service key into .dev.vars
npx wrangler dev                  # opens http://localhost:8787
```

Locally the login check is skipped (only on localhost) and images go to a simulated bucket
on your Mac, but stories are saved to your real Supabase, so use test ids like `test-1` and delete them afterwards.

## Files

| File | What it is |
|---|---|
| `public/index.html`, `app.js`, `styles.css` | The admin page (no build step) |
| `src/worker.js` | Server part: login check, image upload to R2, saving to Supabase |
| `wrangler.jsonc` | Cloudflare settings: bucket, domain, Supabase URL, allowed emails |
| `.dev.vars.example` | Template for local testing secrets |
