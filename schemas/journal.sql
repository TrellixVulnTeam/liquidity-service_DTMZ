CREATE DATABASE liquidity_journal_v2;

USE liquidity_journal_v2;

CREATE TABLE IF NOT EXISTS journal (
  ordering SERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE,
  tags VARCHAR(255) DEFAULT NULL,
  message BLOB NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX journal_ordering_idx ON journal(ordering);

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BLOB NOT NULL,
  PRIMARY KEY (persistence_id, sequence_number)
);
