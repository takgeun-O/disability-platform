-- Read-only preflight. The host/Neon branch must also match the Render datasource.
\set ON_ERROR_STOP on
SELECT current_database() AS database, current_user AS db_role,
       current_setting('server_version') AS postgres_version,
       (SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL
        ORDER BY installed_rank DESC LIMIT 1) AS latest_migration;
SELECT (SELECT count(*) FROM public.members) AS members,
       (SELECT count(*) FROM public.community_posts) AS posts,
       (SELECT count(*) FROM public.community_comments WHERE parent_id IS NULL) AS roots,
       (SELECT count(*) FROM public.community_comments WHERE parent_id IS NOT NULL) AS replies,
       (SELECT count(*) FROM public.community_post_likes) AS post_likes,
       (SELECT count(*) FROM public.community_comment_likes) AS comment_likes;
