-- FamilyVault — Supabase update for thumbnails and background logs (2026-11-02)
-- Run once: Supabase dashboard → SQL Editor → New query → paste this file → Run.
-- docs/supabase-schema.sql already includes these changes for a fresh project.

-- ---------- Thumbnails ----------

alter table public.videos add column if not exists thumbnail_path text;

-- Public bucket: a thumbnail is visible to anyone who has its URL (same trade-off as the videos'
-- "anyone with the link" sharing). URLs are only stored in our database.
insert into storage.buckets (id, name, public)
values ('thumbnails', 'thumbnails', true)
on conflict (id) do nothing;

-- Members may upload thumbnails only into their own family's folder: <family_id>/<drive_file_id>.jpg
create policy thumbnails_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'thumbnails' and public.is_member(((storage.foldername(name))[1])::uuid));

-- ---------- Background logs (testing phase) ----------

create table if not exists public.app_logs (
  id           bigint generated always as identity primary key,
  received_at  timestamptz not null default now(),   -- when Supabase got it
  logged_at    timestamptz,                          -- when it happened on the phone
  user_id      uuid default auth.uid(),              -- filled in automatically when signed in
  email        text,
  device       text,
  app_version  text,
  session_id   text,                                 -- one per app launch
  level        text,                                 -- info, warn, error, crash
  message      text not null check (char_length(message) <= 4000)
);

create index if not exists app_logs_time on public.app_logs (logged_at desc);

alter table public.app_logs enable row level security;

-- The app may only ADD rows (also before sign-in, so early errors arrive too).
-- There is no select/update/delete policy, so no app user can read, change or delete logs.
-- You read them in the dashboard (Table Editor → app_logs), which isn't limited by these rules.
create policy app_logs_insert on public.app_logs for insert to anon, authenticated with check (true);
