create table if not exists aster_config_item (
  id varchar(64) primary key,
  env varchar(64) not null,
  namespace varchar(128) not null,
  config_key varchar(512) not null,
  config_value text,
  value_type varchar(32) not null,
  source_format varchar(32) not null,
  encrypted boolean not null default false,
  enabled boolean not null default true,
  description varchar(1024),
  revision bigint not null default 0,
  created_at timestamp not null,
  updated_at timestamp not null,
  unique key uk_aster_config_item (env, namespace, config_key)
);

create table if not exists aster_config_draft (
  id varchar(64) primary key,
  item_id varchar(64),
  operation_type varchar(32) not null,
  env varchar(64) not null,
  namespace varchar(128) not null,
  config_key varchar(512) not null,
  config_value text,
  value_type varchar(32) not null,
  source_format varchar(32) not null,
  encrypted boolean not null default false,
  enabled boolean not null default true,
  description varchar(1024),
  status varchar(32) not null,
  operator varchar(128),
  created_at timestamp not null,
  updated_at timestamp not null,
  key idx_aster_config_draft_scope (env, namespace, status)
);

create table if not exists aster_config_document (
  env varchar(64) not null,
  namespace varchar(128) not null,
  source_format varchar(32) not null,
  source_content mediumtext,
  revision bigint not null default 0,
  operator varchar(128),
  updated_at timestamp not null,
  primary key (env, namespace, source_format)
);

create table if not exists aster_config_release (
  id varchar(64) primary key,
  env varchar(64) not null,
  namespace varchar(128) not null,
  revision bigint not null,
  release_note varchar(1024),
  operator varchar(128),
  published_at timestamp not null,
  unique key uk_aster_config_release (env, namespace, revision)
);

create table if not exists aster_config_publish_event (
  id varchar(64) primary key,
  env varchar(64) not null,
  namespace varchar(128) not null,
  revision bigint not null,
  source_node_id varchar(128),
  created_at timestamp not null,
  unique key uk_aster_publish_event (env, namespace, revision),
  key idx_aster_publish_event_created_at (created_at)
);
