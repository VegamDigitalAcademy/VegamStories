-- Vegam Stories: every story (and category) can exist in several languages.
--
-- Shared by all languages (stories table): id, category, episode, publish date, published, likes.
-- Per language (story_translations): title, summary, cover, video, story text; and the comic
-- pages (story_pages.language). Category names per language (category_translations).
-- The app shows a story in a language only if it has a translation in that language.
--
-- Existing content becomes the Telugu ('te') version. Story texts saved in other languages
-- (004) become translations of their own, starting with the Telugu title, summary and cover:
-- fix those in the admin site afterwards (the check at the end lists them).
--
-- Run ONCE in Supabase → SQL Editor, after 005_languages.sql, then deploy the new admin site and
-- the new app right away: older admin/app versions read the columns removed here.
-- Everything runs in one transaction: if anything fails, nothing changes.

begin;

-- 1. Category names per language ----------------------------------------------------------

create table category_translations (
  category_id text not null references categories (id) on delete cascade,
  language    text not null references languages (code) on update cascade on delete restrict,
  name        text not null check (length(trim(name)) > 0),
  primary key (category_id, language)
);

insert into category_translations (category_id, language, name)
select id, 'te', name from categories where name is not null and length(trim(name)) > 0;

alter table category_translations enable row level security;
create policy "Category names are public"
  on category_translations for select to anon, authenticated using (true);

alter table categories drop column name;

-- 2. Story content per language -------------------------------------------------------------

create table story_translations (
  story_id            text        not null references stories (id) on delete cascade,
  language            text        not null references languages (code) on update cascade on delete restrict,
  title               text        not null check (length(trim(title)) > 0),
  summary             text        not null default '',
  cover_url           text        not null,
  -- The story as plain text (the app's Read tab); null = no text in this language.
  body                text        check (body is null or length(trim(body)) > 0),
  video_url           text,
  video_title         text,
  video_description   text,
  video_thumbnail_url text,
  updated_at          timestamptz not null default now(),
  primary key (story_id, language)
);

-- Existing stories → their Telugu version.
insert into story_translations
  (story_id, language, title, summary, cover_url, video_url, video_title, video_description, video_thumbnail_url)
select id, 'te', title, coalesce(summary, ''), cover_url, video_url, video_title, video_description, video_thumbnail_url
from stories;

-- Texts from 004: into the matching translation, or a new translation for other languages.
update story_translations tr
set body = t.body
from story_texts t
where t.story_id = tr.story_id and t.language = tr.language;

insert into story_translations (story_id, language, title, summary, cover_url, body)
select t.story_id, t.language, s.title, coalesce(s.summary, ''), s.cover_url, t.body
from story_texts t
join stories s on s.id = t.story_id
on conflict (story_id, language) do nothing;

alter table story_translations enable row level security;
create policy "Translations of published stories are public"
  on story_translations for select to anon, authenticated
  using (exists (select 1 from stories s where s.id = story_translations.story_id and s.is_published));

drop table story_texts;

alter table stories
  drop column title,
  drop column summary,
  drop column cover_url,
  drop column video_url,
  drop column video_title,
  drop column video_description,
  drop column video_thumbnail_url;

-- 3. Comic pages per language ----------------------------------------------------------------

alter table story_pages add column language text;
update story_pages set language = 'te';
alter table story_pages alter column language set not null;
alter table story_pages
  add constraint story_pages_language_fkey
  foreign key (language) references languages (code) on update cascade on delete restrict;

-- Primary key becomes (story, language, page). The old key's name isn't known here, so look it up.
do $$
declare
  pk text;
begin
  select conname into pk from pg_constraint
  where conrelid = 'story_pages'::regclass and contype = 'p';
  if pk is not null then
    execute format('alter table story_pages drop constraint %I', pk);
  end if;
end $$;
alter table story_pages add primary key (story_id, language, page_index);

commit;

-- Check: every story's languages. Rows marked "fix title" came from a 004 text in another
-- language and still have the Telugu title/cover: edit them in the admin site.
select tr.story_id, tr.language, tr.title,
       (select count(*) from story_pages p where p.story_id = tr.story_id and p.language = tr.language) as pages,
       tr.body is not null as has_text,
       tr.video_url is not null as has_video,
       case when tr.language <> 'te' and tr.title = (select te.title from story_translations te
              where te.story_id = tr.story_id and te.language = 'te') then 'fix title' end as note
from story_translations tr
order by tr.story_id, tr.language;
