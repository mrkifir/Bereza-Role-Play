package main

import (
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

type openFileName struct {
	lStructSize       uint32
	hwndOwner         uintptr
	hInstance         uintptr
	lpstrFilter       *uint16
	lpstrCustomFilter *uint16
	nMaxCustFilter    uint32
	nFilterIndex      uint32
	lpstrFile         *uint16
	nMaxFile          uint32
	lpstrFileTitle    *uint16
	nMaxFileTitle     uint32
	lpstrInitialDir   *uint16
	lpstrTitle        *uint16
	flags             uint32
	nFileOffset       uint16
	nFileExtension    uint16
	lpstrDefExt       *uint16
	lCustData         uintptr
	lpfnHook          uintptr
	lpTemplateName    *uint16
	pvReserved        uintptr
	dwReserved        uint32
	flagsEx           uint32
}

var (
	comdlg32         = windows.NewLazySystemDLL("comdlg32.dll")
	pGetOpenFileName = comdlg32.NewProc("GetOpenFileNameW")
)

const (
	ofnFileMustExist = 0x00001000
	ofnPathMustExist = 0x00000800
	ofnNoChangeDir   = 0x00000008
	ofnExplorer      = 0x00080000
)

// filter: пари "Назва\x00маска\x00" і в кінці ще "\x00"
func openFileDialog(title, filter string) (string, bool) {
	buf := make([]uint16, 1024)
	f := utf16Raw(filter)
	t, _ := syscall.UTF16PtrFromString(title)
	var hwnd uintptr
	if w != nil {
		hwnd = uintptr(w.Window())
	}
	ofn := openFileName{
		hwndOwner:   hwnd,
		lpstrFilter: &f[0],
		lpstrFile:   &buf[0],
		nMaxFile:    uint32(len(buf)),
		lpstrTitle:  t,
		flags:       ofnFileMustExist | ofnPathMustExist | ofnNoChangeDir | ofnExplorer,
	}
	ofn.lStructSize = uint32(unsafe.Sizeof(ofn))
	r, _, _ := pGetOpenFileName.Call(uintptr(unsafe.Pointer(&ofn)))
	if r == 0 {
		return "", false
	}
	return syscall.UTF16ToString(buf), true
}

// UTF-16 без обрізання на першому нулі (для фільтрів із \x00)
func utf16Raw(s string) []uint16 {
	out := make([]uint16, 0, len(s)+2)
	for _, r := range s {
		if r == 0 {
			out = append(out, 0)
			continue
		}
		out = append(out, syscall.StringToUTF16(string(r))[0])
	}
	return append(out, 0, 0)
}
