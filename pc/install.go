package main

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

type progress struct {
	Pkg   string  `json:"pkg"`
	Stage string  `json:"stage"`
	Done  int64   `json:"done"`
	Total int64   `json:"total"`
	Index int     `json:"index"`
	Count int     `json:"count"`
	Speed float64 `json:"speed"`
}

type countWriter struct {
	n     int64
	total int64
	last  time.Time
	start time.Time
	p     progress
}

func (c *countWriter) Write(b []byte) (int, error) {
	c.n += int64(len(b))
	if time.Since(c.last) > 150*time.Millisecond || c.n == c.total {
		c.last = time.Now()
		c.p.Done = c.n
		c.p.Total = c.total
		if el := time.Since(c.start).Seconds(); el > 0 {
			c.p.Speed = float64(c.n) / el
		}
		js("onProgress", c.p)
	}
	return len(b), nil
}

func installPackage(gta string, p Package, idx, count int) error {
	tmpDir := filepath.Join(os.TempDir(), "BerezaRP")
	_ = os.MkdirAll(tmpDir, 0755)
	zipPath := filepath.Join(tmpDir, p.ID+".zip")

	// завантаження
	pr := progress{Pkg: p.Name, Stage: "download", Index: idx, Count: count}
	js("onProgress", pr)
	if err := download(p, zipPath, pr); err != nil {
		return err
	}

	// розпакування
	pr.Stage = "extract"
	js("onProgress", pr)
	err := extract(gta, zipPath, p, pr)
	_ = os.Remove(zipPath)
	return err
}

func download(p Package, dst string, pr progress) error {
	cl := &http.Client{Timeout: 0}
	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		resp, err := cl.Get(p.URL)
		if err != nil {
			lastErr = err
			time.Sleep(2 * time.Second)
			continue
		}
		if resp.StatusCode != 200 {
			resp.Body.Close()
			lastErr = fmt.Errorf("сервер файлів відповів %d", resp.StatusCode)
			time.Sleep(2 * time.Second)
			continue
		}
		f, err := os.Create(dst)
		if err != nil {
			resp.Body.Close()
			return errors.New("не вдалося створити тимчасовий файл: " + err.Error())
		}
		total := resp.ContentLength
		if total <= 0 {
			total = p.Size
		}
		h := sha256.New()
		cw := &countWriter{total: total, start: time.Now(), p: pr}
		_, err = io.Copy(io.MultiWriter(f, h, cw), resp.Body)
		resp.Body.Close()
		f.Close()
		if err != nil {
			lastErr = errors.New("обірвався зв'язок під час завантаження")
			continue
		}
		if p.Size > 0 && cw.n != p.Size {
			lastErr = fmt.Errorf("файл завантажився не повністю (%d з %d байт)", cw.n, p.Size)
			continue
		}
		if p.Sha256 != "" && !strings.EqualFold(hex.EncodeToString(h.Sum(nil)), p.Sha256) {
			lastErr = errors.New("файл пошкоджено (не збігається контрольна сума)")
			continue
		}
		return nil
	}
	return errors.New("«" + p.Name + "»: " + lastErr.Error())
}

func extract(gta, zipPath string, p Package, pr progress) error {
	zr, err := zip.OpenReader(zipPath)
	if err != nil {
		return errors.New("архів пошкоджено: " + err.Error())
	}
	defer zr.Close()
	var total int64
	for _, f := range zr.File {
		total += int64(f.UncompressedSize64)
	}
	cw := &countWriter{total: total, start: time.Now(), p: pr}
	root, _ := filepath.Abs(gta)
	hasAsi := exists(filepath.Join(gta, "dinput8.dll"))
	for _, f := range zr.File {
		name := strings.ReplaceAll(f.Name, "\\", "/")
		if p.Strip != "" {
			if !strings.HasPrefix(name, p.Strip) {
				continue
			}
			name = strings.TrimPrefix(name, p.Strip)
		}
		if name == "" {
			continue
		}
		if p.AsiLoader != "" && name == p.AsiLoader {
			if hasAsi {
				continue
			}
			name = "dinput8.dll"
		} else {
			skip := false
			for _, s := range p.Skip {
				if strings.HasPrefix(name, s) {
					skip = true
					break
				}
			}
			if skip {
				continue
			}
		}
		target := filepath.Join(root, filepath.FromSlash(name))
		if !strings.HasPrefix(strings.ToLower(target), strings.ToLower(root)+string(os.PathSeparator)) {
			return errors.New("небезпечний шлях в архіві: " + f.Name)
		}
		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0755); err != nil {
				return permErr(err)
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
			return permErr(err)
		}
		rc, err := f.Open()
		if err != nil {
			return errors.New("архів пошкоджено: " + err.Error())
		}
		tmp := target + ".bztmp"
		out, err := os.Create(tmp)
		if err != nil {
			rc.Close()
			return permErr(err)
		}
		_, err = io.Copy(io.MultiWriter(out, cw), rc)
		rc.Close()
		out.Close()
		if err != nil {
			_ = os.Remove(tmp)
			return errors.New("помилка запису: " + err.Error())
		}
		_ = os.Remove(target)
		if err := os.Rename(tmp, target); err != nil {
			_ = os.Remove(tmp)
			return errors.New("файл зайнятий: " + name + ". Закрийте гру і спробуйте ще раз")
		}
	}
	return nil
}

func permErr(err error) error {
	if os.IsPermission(err) {
		return errors.New("немає прав на запис у папку гри. Запустіть лаунчер від імені адміністратора")
	}
	return err
}
