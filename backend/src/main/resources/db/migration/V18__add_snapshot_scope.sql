ALTER TABLE environments ADD COLUMN scope_include_names    TEXT;
ALTER TABLE environments ADD COLUMN scope_include_patterns TEXT;
ALTER TABLE environments ADD COLUMN scope_exclude_names    TEXT;
ALTER TABLE environments ADD COLUMN scope_exclude_patterns TEXT;
