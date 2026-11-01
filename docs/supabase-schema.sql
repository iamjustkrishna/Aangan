-- FamilyVault — Supabase (Postgres) schema
-- STATUS: written for the two-phone test app (composeApp). Not yet run against a real project.
-- How to apply: Supabase dashboard → SQL Editor → New query → paste this whole file → Run.
-- If anything fails, fix it here so this file always matches the database. Note it in SESSION_LOG.md.
--
-- Video files live in each uploader's Google Drive. This database only stores facts:
-- who is in which family, pending invites, and an index of videos (Drive file ids).
--
-- Test-version choices (owner, 2026-11-01): invites are by Google email (phone invites later);
-- a person may belong to more than one family.

-- ---------- Tables ----------

create table public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  display_name  text not null,
  phone         text unique,                -- E.164, e.g. +919820041733 (not used in the test app)
  google_email  text not null,              -- needed to share Drive files with this person
  avatar_tone   smallint not null default 1 check (avatar_tone between 1 and 6),
  created_at    timestamptz not null default now()
);

create table public.families (
  id          uuid primary key default gen_random_uuid(),
  name        text not null check (char_length(name) between 1 and 80),
  created_by  uuid not null references public.profiles (id),
  created_at  timestamptz not null default now()
);

create table public.family_members (
  family_id  uuid not null references public.families (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  role       text not null default 'member' check (role in ('admin', 'member')),
  relation   text,                          -- free text the family uses: "Dadi", "Bua"
  joined_at  timestamptz not null default now(),
  primary key (family_id, user_id)
);

create table public.invites (
  id          uuid primary key default gen_random_uuid(),
  family_id   uuid not null references public.families (id) on delete cascade,
  email       text,                         -- Google email of the invitee (test app)
  phone       text,                         -- phone invites, later
  relation    text,
  invited_by  uuid not null references public.profiles (id),
  token       text not null unique default replace(gen_random_uuid()::text, '-', ''),
  status      text not null default 'pending' check (status in ('pending', 'accepted', 'cancelled')),
  accepted_by uuid references public.profiles (id),
  created_at  timestamptz not null default now(),
  check (email is not null or phone is not null)
);

create index invites_by_email on public.invites (lower(email)) where status = 'pending';

create table public.videos (
  id             uuid primary key default gen_random_uuid(),
  family_id      uuid not null references public.families (id) on delete cascade,
  uploader_id    uuid not null references public.profiles (id),
  drive_file_id  text not null unique,
  title          text not null,
  duration_s     integer,
  size_bytes     bigint,
  status         text not null default 'ready' check (status in ('ready', 'removed')),
  thumbnail_path text,                      -- path in the public `thumbnails` storage bucket
  created_at     timestamptz not null default now()
);

create index videos_feed on public.videos (family_id, created_at desc);

-- ---------- Helpers ----------

create or replace function public.is_member(fid uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from family_members where family_id = fid and user_id = auth.uid());
$$;

create or replace function public.my_email()
returns text language sql stable as $$
  select lower(coalesce(auth.jwt() ->> 'email', ''));
$$;

-- ---------- Row-level security ----------

alter table public.profiles       enable row level security;
alter table public.families       enable row level security;
alter table public.family_members enable row level security;
alter table public.invites        enable row level security;
alter table public.videos         enable row level security;

-- profiles: see yourself and people who share a family with you; edit only yourself
create policy profiles_select on public.profiles for select using (
  id = auth.uid() or exists (
    select 1 from family_members me join family_members them on me.family_id = them.family_id
    where me.user_id = auth.uid() and them.user_id = profiles.id));
create policy profiles_insert on public.profiles for insert with check (id = auth.uid());
create policy profiles_update on public.profiles for update using (id = auth.uid());

-- families: members can read; creating goes through create_family()
create policy families_select on public.families for select using (public.is_member(id));

-- family_members: members can read their family; joining goes through accept_invite_by_id()
create policy members_select on public.family_members for select using (public.is_member(family_id));

-- invites: members see and create invites for their family; invitees see invites sent to their email
create policy invites_select on public.invites for select using (
  public.is_member(family_id) or lower(email) = public.my_email());
create policy invites_insert on public.invites for insert with check (
  public.is_member(family_id) and invited_by = auth.uid());
create policy invites_update on public.invites for update using (public.is_member(family_id));

-- videos: members read; you add and change only your own
create policy videos_select on public.videos for select using (public.is_member(family_id));
create policy videos_insert on public.videos for insert with check (public.is_member(family_id) and uploader_id = auth.uid());
create policy videos_update on public.videos for update using (uploader_id = auth.uid());

-- ---------- Functions the app calls (RPC) ----------

-- Create a family and make the caller its admin, in one step. Returns the family id.
create or replace function public.create_family(family_name text)
returns uuid language plpgsql security definer set search_path = public as $$
declare fid uuid;
begin
  insert into families (name, created_by) values (family_name, auth.uid()) returning id into fid;
  insert into family_members (family_id, user_id, role) values (fid, auth.uid(), 'admin');
  return fid;
end $$;

-- Invites addressed to the signed-in person's Google email, with what the invite card shows.
create or replace function public.my_invites()
returns table (id uuid, family_id uuid, family_name text, invited_by_name text, member_count bigint, video_count bigint)
language sql stable security definer set search_path = public as $$
  select i.id, f.id, f.name, p.display_name,
         (select count(*) from family_members m where m.family_id = f.id),
         (select count(*) from videos v where v.family_id = f.id and v.status = 'ready')
  from invites i
  join families f on f.id = i.family_id
  join profiles p on p.id = i.invited_by
  where i.status = 'pending'
    and lower(i.email) = public.my_email()
    and not exists (select 1 from family_members m where m.family_id = f.id and m.user_id = auth.uid())
  order by i.created_at desc;
$$;

-- Accept an invite sent to your email. Returns the family id.
create or replace function public.accept_invite_by_id(invite_id uuid)
returns uuid language plpgsql security definer set search_path = public as $$
declare inv invites%rowtype;
begin
  select * into inv from invites
   where id = invite_id and status = 'pending' and lower(email) = public.my_email()
   for update;
  if not found then raise exception 'Invite not found or already used'; end if;
  insert into family_members (family_id, user_id, relation)
       values (inv.family_id, auth.uid(), inv.relation)
       on conflict do nothing;
  update invites set status = 'accepted', accepted_by = auth.uid() where id = inv.id;
  return inv.family_id;
end $$;

-- Accept an invite from a link token (for phone-number invites later). Returns the family id.
create or replace function public.accept_invite(invite_token text)
returns uuid language plpgsql security definer set search_path = public as $$
declare inv invites%rowtype;
begin
  select * into inv from invites where token = invite_token and status = 'pending' for update;
  if not found then raise exception 'Invite not found or already used'; end if;
  insert into family_members (family_id, user_id, relation) values (inv.family_id, auth.uid(), inv.relation)
       on conflict do nothing;
  update invites set status = 'accepted', accepted_by = auth.uid() where id = inv.id;
  return inv.family_id;
end $$;

-- ---------- Thumbnails and background logs (added 2026-11-02) ----------
-- Same as docs/supabase-update-2026-11-02.sql, minus the videos column (already above).

insert into storage.buckets (id, name, public) values ('thumbnails', 'thumbnails', true) on conflict (id) do nothing;

create policy thumbnails_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'thumbnails' and public.is_member(((storage.foldername(name))[1])::uuid));

create table public.app_logs (
  id           bigint generated always as identity primary key,
  received_at  timestamptz not null default now(),
  logged_at    timestamptz,
  user_id      uuid default auth.uid(),
  email        text,
  device       text,
  app_version  text,
  session_id   text,
  level        text,
  message      text not null check (char_length(message) <= 4000)
);
create index app_logs_time on public.app_logs (logged_at desc);
alter table public.app_logs enable row level security;
-- Insert-only for the app; read logs in the dashboard.
create policy app_logs_insert on public.app_logs for insert to anon, authenticated with check (true);
