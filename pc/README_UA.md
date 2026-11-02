# Bereza RP Launcher (ПК)

Go + WebView2 (інтерфейс — `ui/index.html`). Конфіг береться з
`https://mrkifir.github.io/Bereza-Role-Play/launcher/pc.json` (сервер, пакети файлів, новини).

Збірка (Linux/Windows, Go 1.24):

    cp <Oswald.ttf> <Manrope.ttf> ui/          # шрифти з cef/assets/bereza/fonts
    go install github.com/akavel/rsrc@latest
    rsrc -manifest app.manifest -ico icon.ico -arch amd64 -o rsrc_windows_amd64.syso
    GOOS=windows GOARCH=amd64 go build -ldflags "-H windowsgui -s -w" -o BerezaLauncher.exe .

У go.mod є `replace golang.org/x/sys => ../gosys` (локальна копія golang/sys v0.30.0) —
приберіть цей рядок, якщо збираєте з доступом до proxy.golang.org.
