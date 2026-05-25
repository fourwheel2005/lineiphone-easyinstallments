# LINE Webhook Deploy Checklist

Use this after changing `.env`, `docker-compose.yaml`, or Nginx on the server.

## Required `.env` keys

`application.yaml` reads these exact LINE variables:

```dotenv
LINE_CHANNEL_TOKEN=...
LINE_CHANNEL_SECRET=...
```

Make sure both values come from the same LINE Messaging API channel that uses:

```text
https://lineiphoneeasyinstallmentsddmobile.fourwheel.in.th/callback
```

## Recreate the app container

Run from the compose directory on the server:

```bash
docker compose down
docker compose up -d --build
docker logs -f lineiphoneeasyinstallments
```

## Recommended Nginx proxy headers

Inside the HTTPS `server` block for `lineiphoneeasyinstallmentsddmobile.fourwheel.in.th`, use:

```nginx
location / {
    proxy_pass http://localhost:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection 'upgrade';
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_cache_bypass $http_upgrade;
}
```

Then validate and reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## Smoke checks

```bash
curl -i https://lineiphoneeasyinstallmentsddmobile.fourwheel.in.th/actuator/health
curl -i https://lineiphoneeasyinstallmentsddmobile.fourwheel.in.th/callback
```

Expected results:

- `/actuator/health` returns `200` and `{"status":"UP"}`.
- `/callback` with `GET` returns `405` and `Allow: POST`.

After that, verify the webhook in LINE Developers Console and send a real message to the LINE OA.
