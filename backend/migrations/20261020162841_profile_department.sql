begin;

alter table public.profiles
    add column department_id text references public.offices(id),
    add constraint profiles_department_catalog
        check (department_id is null or department_id ~ '^DEP-[A-Z0-9]+$');

commit;
