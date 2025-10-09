# Credible sidecar + Maru/Besu bundle

Use `docker/package-maru-besu-sidecar.sh` to copy the stack (compose file,
configs, dashboards, etc.) into another repository such as
`../../rust/credible-sdk`.

```bash
./docker/package-maru-besu-sidecar.sh
# or specify an alternative destination
./docker/package-maru-besu-sidecar.sh /path/to/target/folder
```

The script rewrites volume paths so everything resolves relative to the target
directory and places a `docker-compose.yml` plus accompanying configuration
under `<dest>/`.

After packaging, jump to the destination directory and launch the stack:

```bash
cd /path/to/target/folder
docker compose up -d
```

Re-run the script whenever the compose file or configs change to refresh the
bundle.
