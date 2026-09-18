# XAU SENT Android

Widget Android minimale per XAUUSD/Gold.

## Cosa mostra
- XAUUSD Analisi 5m: FLOW BUY% / SELL% e numero entrate
- Trend 5m, stato del canale e grafico reale con Fibonacci, trendline e FVG
- Livelli chiave: Resistenza 1/2 e Supporto 1/2
- Pannello FVG ribassista/rialzista
- FLOW TRADER 10m reale, mantenuto dal feed esistente
- Entrate BUY / SELL (solo nuove aperture; chiusure escluse)
- Ora dell'ultimo aggiornamento

## Fonti
Il client prova, in ordine:
1. https://mds-wss.forexfactory.com/trades
2. https://calendar.forexfactory.com/trades
3. https://www.forexfactory.com/trades

Il feed viene letto direttamente dall'app al tocco su ↻. Le righe Scaled In vengono contate una volta per trader/direzione nella finestra di 10 minuti. Le chiusure sono escluse.

## Feed 5m
Il grafico, Fibonacci, trendline, FVG e livelli chiave usano le candele reali BiQuote. Le barre vengono ordinate cronologicamente; le barre complete alimentano i calcoli e l'eventuale barra aperta viene mostrata solo nel grafico.

Feed: https://biquote.io/api/XAUUSD/ohlc?interval=5m&limit=100

## Build
Il workflow GitHub Actions `Build Android APK` crea `XAU-SENT.apk` e lo pubblica nella release `latest`.
