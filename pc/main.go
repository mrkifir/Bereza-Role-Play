// Bereza RP Launcher (ПК) — встановлення/оновлення файлів сервера та запуск SA-MP.
// Інтерфейс — HTML у вбудованому WebView2 (є у Windows 10/11).
package main

import (
	_ "embed"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"time"
	"unsafe"

	webview2 "github.com/jchv/go-webview2"
	"golang.org/x/sys/windows"
)

//go:embed ui/index.html
var pageHTML string

//go:embed ui/Oswald.ttf
var fontOswald []byte

//go:embed ui/Manrope.ttf
var fontManrope []byte

const LauncherVersion = "1.0.0"

var w webview2.WebView

func main() {
	app := NewApp()

	w = webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     false,
		AutoFocus: true,
		DataPath:  app.DataDir(),
		WindowOptions: webview2.WindowOptions{
			Title:  "Bereza Role Play — Лаунчер",
			Width:  1100,
			Height: 680,
			IconId: 2,
			Center: true,
		},
	})
	if w == nil {
		msgBox("Не вдалося запустити лаунчер.\n\nПотрібен компонент Microsoft Edge WebView2 Runtime.\nВстановіть його з сайту Microsoft і запустіть лаунчер знову.", "Bereza RP")
		openURL("https://developer.microsoft.com/microsoft-edge/webview2/")
		os.Exit(1)
	}
	defer w.Destroy()
	w.SetSize(1100, 680, webview2.HintFixed)

	// мережеві виклики - у фоні, щоб вікно не підвисало; результат іде в onInit / onStatus
	must(w.Bind("lInit", func() { go func() { js("onInit", app.Init()) }() }))
	must(w.Bind("lSetNick", app.SetNick))
	must(w.Bind("lPickGta", app.PickGta))
	must(w.Bind("lStatus", func() { go func() { js("onStatus", app.Status()) }() }))
	must(w.Bind("lInstall", app.Install))
	must(w.Bind("lPlay", app.Play))
	must(w.Bind("lOpen", func(u string) { openURL(u) }))

	html := strings.Replace(pageHTML, "/*FONTS*/", fontCSS(), 1)
	html = strings.Replace(html, "{{VERSION}}", LauncherVersion, -1)
	w.SetHtml(html)
	w.Run()
}

func fontCSS() string {
	return fmt.Sprintf(`@font-face{font-family:"Oswald";src:url(data:font/ttf;base64,%s) format("truetype");font-weight:200 700}
@font-face{font-family:"Manrope";src:url(data:font/ttf;base64,%s) format("truetype");font-weight:200 800}`,
		base64.StdEncoding.EncodeToString(fontOswald), base64.StdEncoding.EncodeToString(fontManrope))
}

// js викликає функцію в сторінці з головного потоку вікна
func js(fn string, arg interface{}) {
	b, _ := json.Marshal(arg)
	s := fn + "(" + string(b) + ")"
	w.Dispatch(func() { w.Eval(s) })
}

func closeLater(d time.Duration) {
	go func() {
		time.Sleep(d)
		w.Dispatch(func() { w.Terminate() })
	}()
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func msgBox(text, title string) {
	t, _ := windows.UTF16PtrFromString(text)
	c, _ := windows.UTF16PtrFromString(title)
	windows.MessageBox(0, t, c, windows.MB_OK|windows.MB_ICONWARNING)
}

func openURL(u string) {
	if !strings.HasPrefix(u, "https://") && !strings.HasPrefix(u, "http://") {
		return
	}
	verb, _ := windows.UTF16PtrFromString("open")
	file, _ := windows.UTF16PtrFromString(u)
	_ = windows.ShellExecute(0, verb, file, nil, nil, windows.SW_SHOWNORMAL)
}

var _ = unsafe.Sizeof(0)
