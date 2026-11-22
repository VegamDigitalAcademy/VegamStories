# Vegam Stories: architecture

Single `app` module. The code is split into layers by package, and dependencies only point inward:

```
Biggest saving: check “has anything changed?” first. Each download is the full catalogue and gets bigger with every story you add. The admin site could bump a version number whenever you save. The app would fetch that one number first, a few bytes, and download the catalogue only when it changed. On most days there would be no download at all.
Even better: serve the catalogue from R2. On every save, the admin site writes the whole catalogue as one JSON file to R2. R2 doesn’t charge for downloads, and Cloudflare caches the file. When the file hasn’t changed, the app’s request costs almost nothing. Supabase would then handle only likes and the admin site.
Likes: each tap is one request. If someone taps like and unlike repeatedly, the app could send only the final state after a short pause.
Measure it: after release, compare request counts and data transfer on the Supabase dashboard’s Usage page against before.              Colors, Material 3 theme
```

```
Feel like a real book

Book thickness shows progress (S). The page edges on the right get thinner as you read, and a stack builds up on the left. You can “see” how much of the story is left, like holding a book. It builds on the page edges we just made.
Page-turn sound and a light vibration (S). A soft paper rustle as the page curls, with an on/off switch in Settings.
Corner peek hint (S). The first time someone opens a story, the bottom corner lifts slightly and falls back, so they learn the page can be turned.
Tap the edges to turn (S). Tap the right side for the next page and the left side for the previous one, like Kindle. Kids find tapping easier than swiping.
Bookmark ribbon (S). Tap a ribbon at the top of the page to save that page; it appears in the library.

Reading help for kids
6. Read aloud with highlighting (M). The phone reads the text story out loud and highlights each sentence as it’s read. The page turns by itself when the narration reaches the end. Telugu and English voices are on most phones; Odia depends on the phone. Recorded narration uploaded from the admin site would sound better, but costs more to produce.
7. Smarter auto-turn for text (S). Wait according to how much text is on the page instead of a fixed 30 s. Pause while a finger is on the page.
8. Tap a word for its meaning (L). Long-press a word to see it in the other language (Telugu ↔ English). Good for learning, but needs dictionary data.
9. Two languages side by side (M). The same paragraph in Telugu and English, one under the other.

Bedtime and comfort
10. Bedtime mode (S). Warmer, dimmer pages, plus a sleep timer (“stop after 15 minutes”) for auto-turn and read-aloud.
11. Story audio while the screen is off (M). Listen to the video’s sound like an audiobook.

Coming back and sharing
12. Moral card to share (M). At the end of a story, show a nice card with the cover, title and moral that can be shared on WhatsApp. This also brings new users.
13. My bookshelf (M). Finished stories shown as book spines on a wooden shelf, with small stickers for finishing a series.
14. Gentle reading streak (S). “You read 3 days in a row 🌟”, with no pressure.
15. Continue reading from the app icon (S). Long-press the app icon to jump back into the last story.
```