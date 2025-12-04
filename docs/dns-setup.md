# Cloudflare DNS Setup for Mintlify

Follow these steps to point `docs.brane.sh` to your Mintlify documentation.

## 1. Get Target from Mintlify
1. Go to your **Mintlify Dashboard**.
2. Navigate to **Settings** > **Custom Domain**.
3. Ensure `docs.brane.sh` is added.
4. Copy the **CNAME Value** provided by Mintlify (e.g., `cname.mintlify.com` or `custom.mintlify.com`).

## 2. Configure Cloudflare
1. Log in to your **Cloudflare Dashboard**.
2. Select the `brane.sh` domain.
3. Go to **DNS** > **Records**.
4. Click **Add Record**.
5. Enter the following details:
    - **Type**: `CNAME`
    - **Name**: `docs`
    - **Target**: `cname.mintlify-dns.com`
    - **Proxy Status**: **DNS Only** (Grey Cloud) ☁️
        - *Important*: Do not use "Proxied" (Orange Cloud). Mintlify handles the SSL certificate.
6. Click **Save**.

## 3. Verification
- It may take a few minutes for DNS to propagate.
- Visit `https://docs.brane.sh` to verify.
