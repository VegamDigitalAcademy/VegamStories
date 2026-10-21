-- Vegam Stories: likes. The heart in the app is now a public like with a count.
--
-- The app has no login, so each install sends a random device id (UUID it generates once).
-- One like per (story, device). The app never touches story_likes directly: it calls the
-- set_story_like() function, which keeps stories.like_count in step.
--
-- Run once in Supabase → SQL Editor. Safe to run again.

begin;

-- 1. Total likes per story, read by the app together with the rest of the story row.
alter table stories add column if not exists like_count integer not null default 0;

-- 2. Who liked what. Deleting a story deletes its likes.
create table if not exists story_likes (
  story_id   text        not null references stories (id) on delete cascade,
  device_id  uuid        not null,
  created_at timestamptz not null default now(),
  primary key (story_id, device_id)
);

-- RLS on with NO policies: anon/authenticated can't read or write this table directly.
-- Only set_story_like() (security definer) writes to it.
alter table story_likes enable row level security;

-- 3. Like / unlike. Idempotent, so the app can safely retry: liking twice counts once.
--    Returns the story's new like_count.
create or replace function set_story_like(p_story_id text, p_device_id uuid, p_liked boolean)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  changed   integer;
  new_count integer;
begin
  if not exists (select 1 from stories where id = p_story_id and is_published) then
    raise exception 'Story % not found', p_story_id using errcode = 'P0002';
  end if;

  if p_liked then
    insert into story_likes (story_id, device_id) values (p_story_id, p_device_id)
    on conflict do nothing;
  else
    delete from story_likes where story_id = p_story_id and device_id = p_device_id;
  end if;
  get diagnostics changed = row_count; -- 1 if the like really changed, 0 if it was already so

  update stories
  set like_count = greatest(like_count + (case when p_liked then changed else -changed end), 0)
  where id = p_story_id
  returning like_count into new_count;

  return new_count;
end;
$$;

revoke all on function set_story_like(text, uuid, boolean) from public;
grant execute on function set_story_like(text, uuid, boolean) to anon, authenticated;

-- 4. Make sure existing counts match the likes table (all 0 on the first run).
update stories s
set like_count = (select count(*) from story_likes l where l.story_id = s.id);

commit;

-- Check: like counts per story.
select id, title, like_count from stories order by like_count desc, id;
