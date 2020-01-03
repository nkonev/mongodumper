#!/usr/bin/env bash
# https://medium.com/@gchudnov/trapping-signals-in-docker-containers-7a57fdda7d86
set -x

pid=0

# SIGTERM-handler
# see `man 7 signal` for nubers
term_handler() {
  if [ $pid -ne 0 ]; then
    # Exec AFTER_HOOK
    [[ ! -z "${AFTER_HOOK}" ]] && eval ${AFTER_HOOK}

    kill -SIGTERM "$pid"
    wait "$pid"
  fi
  exit 143; # 128 + 15 -- SIGTERM
}

# setup handlers on callback, kill the last background process, which is `tail -f /dev/null` and execute the specified handler
trap 'kill ${!}; term_handler' SIGTERM
trap 'kill ${!}; term_handler' SIGINT

# Exec BEFORE_HOOK
[[ ! -z "${BEFORE_HOOK}" ]] && eval ${BEFORE_HOOK}

# run application
java -jar mongodumper.jar &
pid="$!"

# wait forever
while true
do
  tail -f /dev/null & wait ${!}
done