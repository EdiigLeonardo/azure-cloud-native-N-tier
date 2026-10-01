#!/usr/bin/env bash
# TCK-106: reproduces "market open" -- the market data provider becomes slow and users keep refreshing FX views.
set -u
echo "1) Market data provider now answers in 30s"
curl -s -X POST "http://localhost:8080/mock/delay?ms=30000"; echo

echo "2) 25 users open the FX exposure view"
for i in $(seq 1 25); do
  curl -s -o /dev/null -u analyst:analyst --max-time 60 "http://localhost:8080/api/v1/exposure/USD" &
done
sleep 3

echo "3) Another user opens the RATES desk (does NOT use FX). How long does it take?"
time curl -s -o /dev/null -w "status=%{http_code}\n" -u analyst:analyst --max-time 45 "http://localhost:8080/api/v1/desks/RATES/summary"

echo
echo "While it hangs, in another terminal: jcmd <pid> Thread.print > threads.txt   (pid: jcmd -l)"
echo "Reset afterwards: curl -X POST 'http://localhost:8080/mock/delay?ms=0'"
wait
