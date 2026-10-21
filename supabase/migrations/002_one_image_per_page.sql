-- Vegam Stories: each comic page is now ONE finished image (panels, captions and speech
-- bubbles are drawn into the image). Replaces the `panels` JSON list with a single `image_url`.
--
-- Run once in Supabase → SQL Editor. Safe to run on existing data: every existing page keeps
-- its FIRST panel's image as the page image. Re-upload those pages from the admin site later
-- if they should show a full-page image instead.

begin;

-- 1. New column for the page image.
alter table story_pages add column if not exists image_url text;

-- 2. Carry over existing pages (first panel's image → page image).
update story_pages
set image_url = panels -> 0 ->> 'image_url'
where image_url is null
  and panels is not null
  and jsonb_typeof(panels) = 'array'
  and jsonb_array_length(panels) > 0;

-- 3. Pages that had no image at all can't be shown → remove them.
delete from story_pages where image_url is null;

-- 4. Every page must have an image from now on.
alter table story_pages alter column image_url set not null;

-- 5. Captions and speech bubbles are no longer stored as text.
alter table story_pages drop column if exists panels;

commit;

-- Check: every page should now be one row with an image_url.
select story_id, page_index, image_url from story_pages order by story_id, page_index;
