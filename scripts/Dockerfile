# One-shot Kafka topic provisioner — runs to completion, does not stay up.
# Used as the `kafka-topic-init` service in the root docker-compose.yml; the
# same image/script can be run standalone against any Kafka cluster (local,
# Docker, or AWS MSK) by overriding KAFKA_BOOTSTRAP_SERVERS.
#
# Pinned to 3.11, not 3.12: kafka-python==2.0.2 vendors its own copy of `six`
# with a sys.meta_path-based lazy loader for `six.moves` that breaks under
# Python 3.12's import machinery (ModuleNotFoundError: kafka.vendor.six.moves).
# kafka-python is effectively unmaintained upstream, so downgrading the
# interpreter is the safer fix here vs. chasing a fork/replacement dependency.
FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY provision_kafka_topics.py .

ENTRYPOINT ["python", "provision_kafka_topics.py"]
