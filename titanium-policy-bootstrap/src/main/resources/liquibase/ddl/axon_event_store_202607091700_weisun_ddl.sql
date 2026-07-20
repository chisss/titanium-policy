--liquibase formatted sql
--changeset weisun:axon-1
CREATE TABLE IF NOT EXISTS axon_domain_event_entry (
    global_index         BIGINT       NOT NULL AUTO_INCREMENT,
    event_identifier     VARCHAR(255) NOT NULL,
    meta_data            BLOB,
    payload              BLOB         NOT NULL,
    payload_revision     VARCHAR(255),
    payload_type         VARCHAR(255) NOT NULL,
    time_stamp           VARCHAR(255) NOT NULL,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number      BIGINT       NOT NULL,
    type                 VARCHAR(255),
    PRIMARY KEY (global_index),
    UNIQUE KEY uk_dee_event_id (event_identifier),
    UNIQUE KEY uk_dee_agg_seq (aggregate_identifier, sequence_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--changeset weisun:axon-2
CREATE TABLE IF NOT EXISTS axon_snapshot_event_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number      BIGINT       NOT NULL,
    type                 VARCHAR(255) NOT NULL,
    event_identifier     VARCHAR(255) NOT NULL,
    meta_data            BLOB,
    payload              BLOB         NOT NULL,
    payload_revision     VARCHAR(255),
    payload_type         VARCHAR(255) NOT NULL,
    time_stamp           VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number, type),
    UNIQUE KEY uk_see_event_id (event_identifier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--changeset weisun:axon-3
CREATE TABLE IF NOT EXISTS axon_token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment        INT          NOT NULL,
    token          BLOB,
    token_type     VARCHAR(255),
    timestamp      VARCHAR(255),
    owner          VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--changeset weisun:axon-4
CREATE TABLE IF NOT EXISTS axon_saga_entry (
    saga_id         VARCHAR(255) NOT NULL,
    revision        VARCHAR(255),
    saga_type       VARCHAR(255),
    serialized_saga BLOB,
    PRIMARY KEY (saga_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--changeset weisun:axon-5
CREATE TABLE IF NOT EXISTS axon_association_value_entry (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    association_key   VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id           VARCHAR(255) NOT NULL,
    saga_type         VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_ave_saga (saga_id, saga_type),
    KEY idx_ave_key (association_key, association_value, saga_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--changeset weisun:axon-6
CREATE TABLE IF NOT EXISTS axon_dead_letter_entry (
    dead_letter_id       VARCHAR(255) NOT NULL,
    cause_message        VARCHAR(1023),
    cause_type           VARCHAR(255),
    diagnostics          BLOB,
    enqueued_at          VARCHAR(255) NOT NULL,
    last_touched         VARCHAR(255),
    aggregate_identifier VARCHAR(255),
    event_identifier     VARCHAR(255) NOT NULL,
    message_type         VARCHAR(255) NOT NULL,
    meta_data            BLOB,
    payload              BLOB         NOT NULL,
    payload_revision     VARCHAR(255),
    payload_type         VARCHAR(255) NOT NULL,
    sequence_number      BIGINT,
    time_stamp           VARCHAR(255) NOT NULL,
    token                BLOB,
    token_type           VARCHAR(255),
    type                 VARCHAR(255),
    processing_group     VARCHAR(255) NOT NULL,
    processing_started   VARCHAR(255),
    sequence_identifier  VARCHAR(255) NOT NULL,
    sequence_index       BIGINT       NOT NULL,
    PRIMARY KEY (dead_letter_id),
    UNIQUE KEY uk_dle_seq (processing_group, sequence_identifier, sequence_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
--rollback DROP TABLE IF EXISTS axon_domain_event_entry, axon_snapshot_event_entry, axon_token_entry, axon_saga_entry, axon_association_value_entry, axon_dead_letter_entry;
