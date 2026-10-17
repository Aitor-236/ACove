#!/bin/sh
set -eu

case "${SITE_HTTPS:-false}" in
    true) template=nginx.https.conf.template ;;
    false) template=nginx.conf.template ;;
    *) echo "SITE_HTTPS 必须为 true 或 false" >&2; exit 1 ;;
esac

mkdir -p /etc/nginx/templates
cp "/etc/nginx/site-templates/$template" /etc/nginx/templates/default.conf.template
