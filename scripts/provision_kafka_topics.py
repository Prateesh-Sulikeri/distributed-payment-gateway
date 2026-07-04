#!/usr/bin/env python3
"""
Kafka topic provisioning for the distributed payment gateway.

KAFKA_AUTO_CREATE_TOPICS_ENABLE=false everywhere this system runs (local Docker
Compose today, AWS/MSK later), so the three business topics that producers
publish to without a prior subscription must be created explicitly before any
service starts. This script is the single source of truth for that topic list.

Reusability by design: every Kafka-specific value (bootstrap servers, topic
names, partition/replication counts) comes from a CLI flag or environment
variable, nothing is hardcoded to "localhost" or to Docker Compose. The same
script runs unmodified:
  - on a developer machine against a Kafka started via `docker compose up`
  - as a one-shot init container inside docker-compose.yml (see the
    `kafka-topic-init` service)
  - against AWS MSK during the Phase 9 deployment, by pointing
    --bootstrap-servers (or KAFKA_BOOTSTRAP_SERVERS) at the MSK broker list

Does NOT attempt to pre-create Spring Kafka's internal `@RetryableTopic`
retry-index topics (e.g. `payment.initiated-retry-0`) — those are created by
Spring Kafka's own admin client at consumer startup (each service's
`@RetryableTopic` leaves `autoCreateTopics` at its default of `true`,
independent of the broker's KAFKA_AUTO_CREATE_TOPICS_ENABLE flag), so
pre-provisioning them here would just duplicate work Spring already does
safely. The `.DLT` topics ARE pre-created here since their names are fixed
and this gives the still-unbuilt DLT monitor endpoint (see ROADMAP.md Stage 6)
somewhere to read from immediately, even before any message ever lands there.

Usage:
    python provision_kafka_topics.py [--bootstrap-servers localhost:9092]
                                      [--partitions 3] [--replication-factor 1]
                                      [--wait-timeout 60]

Requires: kafka-python (see requirements.txt in this directory).
"""

import argparse
import os
import sys
import time

from kafka.admin import KafkaAdminClient, NewTopic
from kafka.errors import NoBrokersAvailable, TopicAlreadyExistsError

# The three business topics that flow between services (see root
# .claude/CLAUDE.md "Kafka topic map"). Add here first if a new async flow
# is ever introduced.
BUSINESS_TOPICS = [
    "payment.initiated",
    "payment.processed",
    "payment.succeeded",
]

# Every @RetryableTopic in the system uses dltTopicSuffix=".DLT" on each of
# the topics above — pre-create them so they exist from the very first run,
# even before any message is ever dead-lettered.
DLT_SUFFIX = ".DLT"


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--bootstrap-servers",
        default=os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
        help="Kafka bootstrap servers, comma-separated (default: $KAFKA_BOOTSTRAP_SERVERS or localhost:9092)",
    )
    parser.add_argument(
        "--partitions",
        type=int,
        default=int(os.environ.get("KAFKA_TOPIC_PARTITIONS", "3")),
        help="Partitions per topic (default: 3 — single-broker dev/Docker friendly, safe to raise in a real cluster)",
    )
    parser.add_argument(
        "--replication-factor",
        type=int,
        default=int(os.environ.get("KAFKA_TOPIC_REPLICATION_FACTOR", "1")),
        help="Replication factor per topic (default: 1 — matches the single-broker Compose setup; raise for MSK)",
    )
    parser.add_argument(
        "--wait-timeout",
        type=int,
        default=int(os.environ.get("KAFKA_WAIT_TIMEOUT_SECONDS", "60")),
        help="Seconds to wait for the broker to become reachable before giving up (default: 60)",
    )
    return parser.parse_args()


def connect_with_retry(bootstrap_servers, wait_timeout):
    """Kafka often isn't accepting connections yet when this runs as a
    Compose init container right after `depends_on: kafka`, so retry instead
    of failing on the first attempt."""
    deadline = time.monotonic() + wait_timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            client = KafkaAdminClient(
                bootstrap_servers=bootstrap_servers,
                client_id="topic-provisioner",
            )
            return client
        except NoBrokersAvailable as exc:
            last_error = exc
            print(f"Kafka not reachable yet at {bootstrap_servers}, retrying...", flush=True)
            time.sleep(2)
    raise SystemExit(f"Could not reach Kafka at {bootstrap_servers} within {wait_timeout}s: {last_error}")


def main():
    args = parse_args()

    topic_names = []
    for topic in BUSINESS_TOPICS:
        topic_names.append(topic)
        topic_names.append(topic + DLT_SUFFIX)

    print(f"Connecting to Kafka at {args.bootstrap_servers} ...")
    admin = connect_with_retry(args.bootstrap_servers, args.wait_timeout)

    try:
        existing = set(admin.list_topics())
        to_create = [
            NewTopic(
                name=name,
                num_partitions=args.partitions,
                replication_factor=args.replication_factor,
            )
            for name in topic_names
            if name not in existing
        ]

        if not to_create:
            print("All required topics already exist — nothing to do.")
            return

        try:
            admin.create_topics(new_topics=to_create, validate_only=False)
        except TopicAlreadyExistsError:
            # Idempotent re-run raced another provisioner instance — fine.
            pass

        print(f"Created {len(to_create)} topic(s): {', '.join(t.name for t in to_create)}")

        already_present = existing.intersection(topic_names)
        if already_present:
            print(f"Already present, skipped: {', '.join(sorted(already_present))}")
    finally:
        admin.close()


if __name__ == "__main__":
    try:
        main()
    except SystemExit as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
