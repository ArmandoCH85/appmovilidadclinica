// Package database inicializa el pool de conexiones a MariaDB/MySQL.
//
// El diseño evita bibliotecas de configuración (koanf, viper, etc.): un
// struct con valores por defecto + os.Getenv basta para un MVP con <50
// usuarios. Cualquier parámetro ausente cae en un valor seguro y operativo.
package database

import (
	"database/sql"
	"fmt"
	"net/url"
	"os"
	"strconv"
	"time"

	_ "github.com/go-sql-driver/mysql"
)

// Config agrupa los parámetros de conexión. Las etiquetas `env` documentan
// la variable de entorno esperada; LoadConfig las lee con os.Getenv.
type Config struct {
	Host         string `env:"DB_HOST"`
	Port         string `env:"DB_PORT"`
	User         string `env:"DB_USER"`
	Password     string `env:"DB_PASSWORD"`
	Name         string `env:"DB_NAME"`
	MaxOpenConns int    `env:"DB_MAX_OPEN_CONNS"`
	MaxIdleConns int    `env:"DB_MAX_IDLE_CONNS"`
}

// LoadConfig construye Config desde variables de entorno usando valores por
// defecto razonables para un VPS pequeño (1 vCPU / 1.9 GB).
func LoadConfig() Config {
	return Config{
		Host:         envOrDefault("DB_HOST", "127.0.0.1"),
		Port:         envOrDefault("DB_PORT", "3306"),
		User:         envOrDefault("DB_USER", "root"),
		Password:     envOrDefault("DB_PASSWORD", ""),
		Name:         envOrDefault("DB_NAME", "transporte_corporativo_mvp"),
		MaxOpenConns: envOrDefaultInt("DB_MAX_OPEN_CONNS", 20),
		MaxIdleConns: envOrDefaultInt("DB_MAX_IDLE_CONNS", 10),
	}
}

// NewPool crea y verifica un *sql.DB contra MariaDB. El DSN habilita
// multiStatements (necesario para ejecutar el schema idempotente en un solo
// db.Exec al arrancar), parseTime (mapea DATETIME a time.Time), la zona
// horaria operativa America/Lima, un charset utf8mb4 consistente y
// clientFoundRows.
//
// time_zone en el DSN es un parametro de sistema: go-sql-driver/mysql manda
// "SET time_zone='-05:00'" en CADA conexion nueva que abre el pool. Es
// necesario ademas de loc=America/Lima: loc solo le dice al driver Go como
// ETIQUETAR el DATETIME que lee, pero CURRENT_TIMESTAMP en los stored
// procedures corre en la timezone de la SESION MySQL. Sin este parametro,
// esa sesion cae en el default del server (tipicamente UTC), y el driver
// Go termina etiquetando una hora UTC como si fuera hora de Lima -> 5h de
// desfasaje en actual_arrival_at y timestamps similares.
//
// clientFoundRows es critico: sin el, el protocolo MySQL reporta
// RowsAffected() como filas REALMENTE CAMBIADAS, no filas que matchearon el
// WHERE. Todo Update* de admin/repository.go llama ensureAffected(res, ...)
// para decidir si el id existe (RowsAffected()==0 -> 404 NotFound). Si un
// PUT llega con los mismos valores que ya tiene la fila (edicion sin
// cambios reales, o un campo que vuelve a su valor original), MySQL no
// "cambia" nada -> RowsAffected()==0 -> ensureAffected devuelve 404 aunque
// el recurso exista y el UPDATE haya sido valido. clientFoundRows=true hace
// que RowsAffected() cuente filas matcheadas (semantica correcta para un
// chequeo de existencia, que es lo que ensureAffected realmente necesita).
func NewPool(cfg Config) (*sql.DB, error) {
	dsn := fmt.Sprintf(
		"%s:%s@tcp(%s:%s)/%s?multiStatements=true&parseTime=true&loc=%s&time_zone=%s&charset=utf8mb4&collation=utf8mb4_unicode_ci&timeout=5s&readTimeout=10s&writeTimeout=10s&clientFoundRows=true",
		cfg.User, cfg.Password, cfg.Host, cfg.Port, cfg.Name,
		url.QueryEscape("America/Lima"),
		url.QueryEscape("'-05:00'"),
	)

	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, fmt.Errorf("abriendo driver mysql: %w", err)
	}

	db.SetMaxOpenConns(cfg.MaxOpenConns)
	db.SetMaxIdleConns(cfg.MaxIdleConns)
	// 5 minutos evita reconexiones silenciosas tras timeouts del servidor.
	db.SetConnMaxLifetime(5 * time.Minute)

	if err := db.Ping(); err != nil {
		db.Close()
		return nil, fmt.Errorf("ping a la base de datos: %w", err)
	}

	return db, nil
}

// envOrDefault devuelve el valor de la variable o fallback si no está definida.
func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// envOrDefaultInt parsea la variable como entero; usa fallback ante ausencia
// o error de formato. Separada para no repetir strconv en cada campo.
func envOrDefaultInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}
