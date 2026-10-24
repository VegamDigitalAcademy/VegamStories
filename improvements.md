# Vegam Stories: architecture

Single `app` module. The code is split into layers by package, and dependencies only point inward:

```
Biggest saving: check “has anything changed?” first. Each download is the full catalogue and gets bigger with every story you add. The admin site could bump a version number whenever you save. The app would fetch that one number first, a few bytes, and download the catalogue only when it changed. On most days there would be no download at all.
Even better: serve the catalogue from R2. On every save, the admin site writes the whole catalogue as one JSON file to R2. R2 doesn’t charge for downloads, and Cloudflare caches the file. When the file hasn’t changed, the app’s request costs almost nothing. Supabase would then handle only likes and the admin site.
Likes: each tap is one request. If someone taps like and unlike repeatedly, the app could send only the final state after a short pause.
Measure it: after release, compare request counts and data transfer on the Supabase dashboard’s Usage page against before.              Colors, Material 3 theme
```
