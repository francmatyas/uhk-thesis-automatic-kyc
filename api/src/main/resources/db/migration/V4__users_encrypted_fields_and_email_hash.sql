do
$$
    begin
        if exists (select 1
                   from information_schema.tables
                   where table_schema = 'public'
                     and table_name = 'users') then

            alter table users
                add column if not exists email_hash varchar(64);

            alter table users
                alter column email type text;
            alter table users
                alter column given_name type text;
            alter table users
                alter column middle_name type text;
            alter table users
                alter column family_name type text;
            alter table users
                alter column full_name type text;

            alter table users
                drop constraint if exists uq_users_email;
            alter table users
                drop constraint if exists uq_users_email_hash;

            drop index if exists uq_users_email;
            drop index if exists users_email_key;

            create unique index if not exists uq_users_email_hash
                on users (email_hash)
                where email_hash is not null;
        end if;
    end
$$;
