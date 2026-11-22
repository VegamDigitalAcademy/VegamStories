# Vegam Stories website

vegamstories.com: the home page, the privacy policy and the terms, as plain HTML files in `public/`.
Cloudflare serves them from a Worker with static files only (no code), which is free.

| Address | File |
|---|---|
| https://vegamstories.com/ | `public/index.html` |
| https://vegamstories.com/privacy | `public/privacy.html` |
| https://vegamstories.com/terms | `public/terms.html` |
| any other address | `public/404.html` (status 404) |

`www.vegamstories.com` shows the same pages. For Play Console → App content → Privacy policy, use
`https://vegamstories.com/privacy`.

The app shows the same policy and terms itself (Menu → Privacy Policy / Terms & Conditions, from
`info_privacy_body` and `info_terms_body` in `strings.xml`, English and Telugu). When you change one, change
the other too, with the same "Last updated" date.

## Try it on your computer

```bash
npm install          # once
npx wrangler dev     # then open http://localhost:8787
```

## Put it live

```bash
npx wrangler deploy
```

One-time setup: in the Cloudflare dashboard (vegamstories.com → DNS → Records), `vegamstories.com` and
`www.vegamstories.com` must have no other A, AAAA or CNAME records. The first deploy then creates its own records
for both. Email records (MX, TXT) aren't touched.
