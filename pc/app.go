package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"golang.org/x/sys/windows/registry"
)

const ConfigURL = "https://mrkifir.github.io/Bereza-Role-Play/launcher/pc.json"

type ServerCfg struct {
	Name string `json:"name"`
	Host string `json:"host"`
	Port int    `json:"port"`
}

type Package struct {
	ID        string   `json:"id"`
	Name      string   `json:"name"`
	Version   string   `json:"version"`
	URL       string   `json:"url"`
	Size      int64    `json:"size"`
	Sha256    string   `json:"sha256"`
	Strip     string   `json:"strip"`     // префікс шляху в архіві, який відкидається
	Skip      []string `json:"skip"`      // що не розпаковувати
	AsiLoader string   `json:"asiLoader"` // dinput8.dll в архіві — ставиться, лише якщо його ще нема
	Check     []string `json:"check"`     // файли, які мають бути в папці гри
	Optional  bool     `json:"optional"`
}

type NewsItem struct {
	Title string `json:"title"`
	Body  string `json:"body"`
	Date  string `json:"date"`
	Tag   string `json:"tag"`
	URL   string `json:"url"`
}

type Config struct {
	Version  string     `json:"version"`
	Server   ServerCfg  `json:"server"`
	Packages []Package  `json:"packages"`
	News     []NewsItem `json:"news"`
	Site     string     `json:"site"`
	Discord  string     `json:"discord"`
	Telegram string     `json:"telegram"`
	Donate   string     `json:"donate"`
}

type LocalState struct {
	GtaDir   string            `json:"gtaDir"`
	Packages map[string]string `json:"packages"` // id -> встановлена версія (для поточної папки)
}

type App struct {
	mu      sync.Mutex
	cfg     Config
	state   LocalState
	busy    bool
	dataDir string
}

func NewApp() *App {
	a := &App{}
	base := os.Getenv("APPDATA")
	if base == "" {
		base = os.TempDir()
	}
	a.dataDir = filepath.Join(base, "BerezaRP")
	_ = os.MkdirAll(a.dataDir, 0755)
	a.loadState()
	a.cfg = defaultConfig()
	return a
}

func (a *App) DataDir() string { return filepath.Join(a.dataDir, "webview") }

func defaultConfig() Config {
	return Config{
		Version: "0",
		Server:  ServerCfg{Name: "Bereza Role Play", Host: "188.127.241.74", Port: 2406},
	}
}

func (a *App) statePath() string { return filepath.Join(a.dataDir, "launcher.json") }

func (a *App) loadState() {
	b, err := os.ReadFile(a.statePath())
	if err == nil {
		_ = json.Unmarshal(b, &a.state)
	}
	if a.state.Packages == nil {
		a.state.Packages = map[string]string{}
	}
}

func (a *App) saveState() {
	b, _ := json.MarshalIndent(a.state, "", "  ")
	tmp := a.statePath() + ".tmp"
	if os.WriteFile(tmp, b, 0644) == nil {
		_ = os.Rename(tmp, a.statePath())
	}
}

// ---------------- SA-MP: папка гри і нік ----------------

func sampKey(write bool) (registry.Key, error) {
	acc := uint32(registry.QUERY_VALUE)
	if write {
		acc |= registry.SET_VALUE
		k, _, err := registry.CreateKey(registry.CURRENT_USER, `Software\SAMP`, acc)
		return k, err
	}
	return registry.OpenKey(registry.CURRENT_USER, `Software\SAMP`, acc)
}

func regGet(name string) string {
	k, err := sampKey(false)
	if err != nil {
		return ""
	}
	defer k.Close()
	v, _, _ := k.GetStringValue(name)
	return v
}

func regSet(name, val string) error {
	k, err := sampKey(true)
	if err != nil {
		return err
	}
	defer k.Close()
	return k.SetStringValue(name, val)
}

func exists(p string) bool { _, err := os.Stat(p); return err == nil }

func (a *App) gtaDir() string {
	if a.state.GtaDir != "" && exists(filepath.Join(a.state.GtaDir, "gta_sa.exe")) {
		return a.state.GtaDir
	}
	if exe := regGet("gta_sa_exe"); exe != "" && exists(exe) {
		return filepath.Dir(exe)
	}
	for _, d := range []string{`C:\Program Files (x86)\Rockstar Games\GTA San Andreas`, `C:\Program Files\Rockstar Games\GTA San Andreas`, `C:\Program Files (x86)\SAMP`, `C:\Games\GTA San Andreas`} {
		if exists(filepath.Join(d, "gta_sa.exe")) {
			return d
		}
	}
	return ""
}

// ---------------- конфіг з сайту ----------------

func (a *App) fetchConfig() error {
	cl := &http.Client{Timeout: 12 * time.Second}
	resp, err := cl.Get(fmt.Sprintf("%s?t=%d", ConfigURL, time.Now().Unix()))
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	var c Config
	if err := json.NewDecoder(resp.Body).Decode(&c); err != nil {
		return err
	}
	if c.Server.Host == "" {
		c.Server = defaultConfig().Server
	}
	a.cfg = c
	return nil
}

type InitResult struct {
	Nick     string     `json:"nick"`
	Gta      string     `json:"gta"`
	GtaOk    bool       `json:"gtaOk"`
	SampOk   bool       `json:"sampOk"`
	Need     []string   `json:"need"`
	Online   bool       `json:"online"`
	Server   ServerCfg  `json:"server"`
	News     []NewsItem `json:"news"`
	Site     string     `json:"site"`
	Discord  string     `json:"discord"`
	Telegram string     `json:"telegram"`
	Donate   string     `json:"donate"`
	Error    string     `json:"error"`
}

func (a *App) Init() InitResult {
	a.mu.Lock()
	defer a.mu.Unlock()
	r := InitResult{}
	if err := a.fetchConfig(); err != nil {
		r.Error = "Немає зв'язку з сервером оновлень: " + err.Error()
	} else {
		r.Online = true
	}
	r.Nick = regGet("PlayerName")
	r.Gta = a.gtaDir()
	r.GtaOk = r.Gta != ""
	r.SampOk = r.GtaOk && exists(filepath.Join(r.Gta, "samp.exe"))
	r.Need = a.needList(r.Gta)
	r.Server = a.cfg.Server
	r.News = a.cfg.News
	r.Site, r.Discord, r.Telegram, r.Donate = a.cfg.Site, a.cfg.Discord, a.cfg.Telegram, a.cfg.Donate
	return r
}

// які пакети треба встановити / оновити
func (a *App) needList(gta string) []string {
	var need []string
	if gta == "" {
		return need
	}
	for _, p := range a.cfg.Packages {
		if a.pkgNeeded(gta, p) {
			need = append(need, p.Name)
		}
	}
	return need
}

func (a *App) pkgNeeded(gta string, p Package) bool {
	key := strings.ToLower(gta) + "|" + p.ID
	if a.state.Packages[key] != p.Version {
		return true
	}
	for _, c := range p.Check {
		if !exists(filepath.Join(gta, filepath.FromSlash(c))) {
			return true
		}
	}
	return false
}

var nickRe = regexp.MustCompile(`^[A-Z][a-z]{1,15}_[A-Z][a-z]{1,15}$`)

func (a *App) SetNick(nick string) error {
	nick = strings.TrimSpace(nick)
	if !nickRe.MatchString(nick) {
		return errors.New("Нік має бути у форматі Ім'я_Прізвище латиницею, наприклад Ivan_Petrenko")
	}
	if err := regSet("PlayerName", nick); err != nil {
		return errors.New("Не вдалося зберегти нік: " + err.Error())
	}
	return nil
}

func (a *App) PickGta() (string, error) {
	p, ok := openFileDialog("Вкажіть gta_sa.exe у папці з грою", "GTA San Andreas (gta_sa.exe)\x00gta_sa.exe\x00Програми (*.exe)\x00*.exe\x00\x00")
	if !ok {
		return "", nil
	}
	if !strings.EqualFold(filepath.Base(p), "gta_sa.exe") {
		return "", errors.New("Оберіть саме файл gta_sa.exe")
	}
	a.mu.Lock()
	a.state.GtaDir = filepath.Dir(p)
	a.saveState()
	a.mu.Unlock()
	return a.state.GtaDir, nil
}

type StatusResult struct {
	Ok      bool   `json:"ok"`
	Players int    `json:"players"`
	Max     int    `json:"max"`
	Name    string `json:"name"`
	Ping    int    `json:"ping"`
}

func (a *App) Status() StatusResult {
	s := a.cfg.Server
	info, ping, err := sampQuery(s.Host, s.Port, 2500*time.Millisecond)
	if err != nil {
		return StatusResult{}
	}
	return StatusResult{Ok: true, Players: info.Players, Max: info.MaxPlayers, Name: info.Hostname, Ping: ping}
}

// Install запускає встановлення у фоні; прогрес іде в сторінку (onProgress / onInstalled)
func (a *App) Install() error {
	a.mu.Lock()
	if a.busy {
		a.mu.Unlock()
		return errors.New("Встановлення вже йде")
	}
	gta := a.gtaDir()
	if gta == "" {
		a.mu.Unlock()
		return errors.New("Не знайдено папку з грою. Натисніть «Змінити» і вкажіть gta_sa.exe")
	}
	a.busy = true
	pkgs := []Package{}
	for _, p := range a.cfg.Packages {
		if a.pkgNeeded(gta, p) {
			pkgs = append(pkgs, p)
		}
	}
	a.mu.Unlock()

	go func() {
		var err error
		for i, p := range pkgs {
			err = installPackage(gta, p, i, len(pkgs))
			if err != nil {
				if p.Optional {
					js("onWarn", "Не вдалося встановити «"+p.Name+"»: "+err.Error())
					err = nil
					continue
				}
				break
			}
			a.mu.Lock()
			a.state.Packages[strings.ToLower(gta)+"|"+p.ID] = p.Version
			a.saveState()
			a.mu.Unlock()
		}
		a.mu.Lock()
		a.busy = false
		a.mu.Unlock()
		if err != nil {
			js("onInstalled", map[string]string{"error": err.Error()})
		} else {
			js("onInstalled", map[string]string{"error": ""})
		}
	}()
	return nil
}

func (a *App) Play(nick string) error {
	if err := a.SetNick(nick); err != nil {
		return err
	}
	gta := a.gtaDir()
	if gta == "" {
		return errors.New("Не знайдено папку з грою")
	}
	samp := filepath.Join(gta, "samp.exe")
	if !exists(samp) {
		return errors.New("У папці гри немає samp.exe — встановіть клієнт SA-MP 0.3.7")
	}
	s := a.cfg.Server
	cmd := exec.Command(samp, fmt.Sprintf("%s:%d", s.Host, s.Port))
	cmd.Dir = gta
	if err := cmd.Start(); err != nil {
		return errors.New("Не вдалося запустити гру: " + err.Error())
	}
	closeLater(2500 * time.Millisecond)
	return nil
}
