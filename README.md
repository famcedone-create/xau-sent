# XAU SENT Android

Widget Android minimale per XAUUSD/Gold.

## Cosa mostra
- SENT XAU 10m: BUY% / SELL%
- Entrate BUY / SELL (solo nuove aperture; chiusure escluse)
- Prezzo indicativo ricavato dai trade XAU più recenti del feed
- T1 / T2 indicativi: +/- 1.50 e +/- 3.00 nella direzione del flow
- Flow BUY / SELL / NEUTRO
- Posizionamento trader Gold/USD quando leggibile
- Ora dell'ultimo aggiornamento

## Fonti
Il client prova, in ordine:
1. https://mds-wss.forexfactory.com/trades
2. https://calendar.forexfactory.com/trades
3. https://www.forexfactory.com/trades

Il feed viene letto direttamente dall'app al tocco su ↻. Le righe Scaled In vengono contate una volta per trader/direzione nella finestra di 10 minuti. Le chiusure sono escluse.

## Nota sul prezzo
La cifra è volutamente indicativa: usa la mediana dei prezzi XAU delle aperture più recenti del feed, non un broker quote feed. In una v2 si può sostituire con OANDA/Twelve Data mantenendo identico il widget.

## Build
Il workflow GitHub Actions `Build Android APK` crea `XAU-SENT.apk` e lo pubblica nella release `latest`.
