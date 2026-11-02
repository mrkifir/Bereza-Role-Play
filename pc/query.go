package main

import (
	"bytes"
	"encoding/binary"
	"errors"
	"net"
	"strconv"
	"time"
)

type sampInfo struct {
	Players    int
	MaxPlayers int
	Hostname   string
}

// SA-MP query, пакет 'i' (інформація про сервер)
func sampQuery(host string, port int, timeout time.Duration) (sampInfo, int, error) {
	var info sampInfo
	addr, err := net.ResolveUDPAddr("udp4", net.JoinHostPort(host, strconv.Itoa(port)))
	if err != nil {
		return info, 0, err
	}
	conn, err := net.DialUDP("udp4", nil, addr)
	if err != nil {
		return info, 0, err
	}
	defer conn.Close()
	ip := addr.IP.To4()
	if ip == nil {
		return info, 0, errors.New("ipv6")
	}
	pkt := bytes.NewBufferString("SAMP")
	pkt.Write(ip)
	_ = binary.Write(pkt, binary.LittleEndian, uint16(port))
	pkt.WriteByte('i')
	start := time.Now()
	_ = conn.SetDeadline(time.Now().Add(timeout))
	if _, err := conn.Write(pkt.Bytes()); err != nil {
		return info, 0, err
	}
	buf := make([]byte, 2048)
	n, err := conn.Read(buf)
	if err != nil {
		return info, 0, err
	}
	ping := int(time.Since(start).Milliseconds())
	b := buf[:n]
	if len(b) < 11+5+4 {
		return info, ping, errors.New("коротка відповідь")
	}
	b = b[11:]
	info.Players = int(binary.LittleEndian.Uint16(b[1:3]))
	info.MaxPlayers = int(binary.LittleEndian.Uint16(b[3:5]))
	l := int(binary.LittleEndian.Uint32(b[5:9]))
	if 9+l <= len(b) {
		info.Hostname = cp1251(b[9 : 9+l])
	}
	return info, ping, nil
}

// мінімальна CP1251 -> UTF-8 (кирилиця)
func cp1251(b []byte) string {
	r := make([]rune, 0, len(b))
	for _, c := range b {
		switch {
		case c < 0x80:
			r = append(r, rune(c))
		case c >= 0xC0:
			r = append(r, rune(0x410+int(c)-0xC0))
		case c == 0xA8:
			r = append(r, 'Ё')
		case c == 0xB8:
			r = append(r, 'ё')
		case c == 0xAA:
			r = append(r, 'Є')
		case c == 0xBA:
			r = append(r, 'є')
		case c == 0xB2:
			r = append(r, 'І')
		case c == 0xB3:
			r = append(r, 'і')
		case c == 0xAF:
			r = append(r, 'Ї')
		case c == 0xBF:
			r = append(r, 'ї')
		case c == 0xA5:
			r = append(r, 'Ґ')
		case c == 0xB4:
			r = append(r, 'ґ')
		default:
			r = append(r, '?')
		}
	}
	return string(r)
}
