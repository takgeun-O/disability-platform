ALTER TABLE community_comments
    ADD COLUMN parent_id BIGINT
        REFERENCES community_comments (id);