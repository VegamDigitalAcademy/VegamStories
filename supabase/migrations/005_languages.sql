-- Vegam Stories: story text languages are data, like categories.
--
-- Languages are added in the admin site (e.g. Odia), and the app shows whatever is here:
-- nothing about languages is fixed in the app. story_texts.language must be one of these codes.
--
-- code:        short ISO 639-1 code, lowercase: 'te', 'en', 'or' (Odia), 'hi', 'ta', 'kn' …
--              The app matches it against the phone's / app's language, so use the standard code.
-- name:        English name, shown in the admin site: 'Telugu', 'Odia'.
-- native_name: the name in its own script, shown in the app: 'తెలుగు', 'ଓଡ଼ିଆ'.
-- sort_order:  order in the app; the first is the default text when nothing else matches.
--
-- Run once in Supabase → SQL Editor, after 004_story_texts.sql. Safe to run again.

begin;

create table if not exists languages (
  code        text    primary key check (code ~ '^[a-z]{2,3}$'),
  name        text    not null check (length(trim(name)) > 0),
  native_name text    not null check (length(trim(native_name)) > 0),
  sort_order  integer not null default 0
);

-- The two languages the app started with.
insert into languages (code, name, native_name, sort_order) values
  ('te', 'Telugu', 'తెలుగు', 0),
  ('en', 'English', 'English', 1)
on conflict (code) do nothing;

-- Readable by the app, like categories. Only the admin site (secret key) writes.
alter table languages enable row level security;
drop policy if exists "Languages are public" on languages;
create policy "Languages are public" on languages for select to anon, authenticated using (true);

-- story_texts: any language from the table instead of only 'te' / 'en'.
alter table story_texts drop constraint if exists story_texts_language_check;
alter table story_texts drop constraint if exists story_texts_language_fkey;
alter table story_texts
  add constraint story_texts_language_fkey
  foreign key (language) references languages (code) on update cascade on delete restrict;

commit;

-- Check: languages and how many stories have text in each.
select l.code, l.name, l.native_name, l.sort_order, count(t.story_id) as stories_with_text
from languages l
left join story_texts t on t.language = l.code
group by l.code, l.name, l.native_name, l.sort_order
order by l.sort_order, l.code;
