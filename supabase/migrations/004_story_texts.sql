-- Vegam Stories: stories as text. A story can now come as a comic (story_pages), as text
-- (this table) and as a video (stories.video_*), in any combination.
--
-- Plain text, one row per story and language: 'te' (Telugu) and/or 'en' (English).
-- Paragraphs are separated by a blank line. The app reads it together with the story row.
--
-- Run once in Supabase → SQL Editor, BEFORE releasing an app version that reads story_texts
-- (older app versions ignore it). Safe to run again.

begin;

create table if not exists story_texts (
  story_id   text        not null references stories (id) on delete cascade,
  language   text        not null check (language in ('te', 'en')),
  body       text        not null check (length(trim(body)) > 0),
  updated_at timestamptz not null default now(),
  primary key (story_id, language)
);

-- Read-only for the app, and only for published stories (same rule as the stories themselves).
alter table story_texts enable row level security;

drop policy if exists "Texts of published stories are public" on story_texts;
create policy "Texts of published stories are public"
  on story_texts for select
  to anon, authenticated
  using (exists (select 1 from stories s where s.id = story_texts.story_id and s.is_published));

commit;

-- Check: which stories have text, in which languages.
select s.id, s.title, string_agg(t.language, ', ' order by t.language) as text_languages
from stories s
left join story_texts t on t.story_id = s.id
group by s.id, s.title
order by s.id;
