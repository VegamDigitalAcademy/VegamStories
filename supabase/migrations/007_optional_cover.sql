-- Vegam Stories: the cover image is optional.
--
-- Without a cover the app shows the story's first comic page, or else a title card it draws
-- itself (coloured background + title). Nothing else changes; no existing data is touched.
--
-- Run once in Supabase → SQL Editor, after 006_story_translations.sql. Safe to run again.

alter table story_translations alter column cover_url drop not null;

-- Check: stories without a cover, per language.
select story_id, language, title from story_translations where cover_url is null order by story_id, language;
