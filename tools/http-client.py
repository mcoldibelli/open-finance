#!/usr/bin/env python3
"""HTTP client com suporte a mTLS para smoke tests. Substitui curl em ambientes Windows (Schannel)."""
import ssl
import sys
import urllib.request


def main():
    url = None
    cert = None
    key = None
    cacert = None
    headers = {}

    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--cert" and i + 1 < len(args):
            cert = args[i + 1]; i += 2
        elif args[i] == "--key" and i + 1 < len(args):
            key = args[i + 1]; i += 2
        elif args[i] == "--cacert" and i + 1 < len(args):
            cacert = args[i + 1]; i += 2
        elif args[i] == "-H" and i + 1 < len(args):
            k, v = args[i + 1].split(": ", 1)
            headers[k] = v; i += 2
        else:
            url = args[i]; i += 1

    if not url:
        print("Usage: http-client.py [--cert F] [--key F] [--cacert F] [-H 'K: V'] URL", file=sys.stderr)
        sys.exit(1)

    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    if cert and key:
        ctx.load_cert_chain(cert, key)
    if cacert:
        ctx.load_verify_locations(cacert)
    else:
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

    req = urllib.request.Request(url, headers=headers)
    try:
        r = urllib.request.urlopen(req, context=ctx, timeout=5)
        print(r.status)
    except urllib.error.HTTPError as e:
        print(e.code)
    except (ssl.SSLError, ConnectionError, ConnectionRefusedError, OSError, TimeoutError):
        print("000")


if __name__ == "__main__":
    main()
